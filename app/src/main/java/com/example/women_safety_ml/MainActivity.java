package com.example.women_safety_ml;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends BaseActivity {

    private static final String TAG = "SafeHer";

    // ── Set FALSE only when releasing ──
    private static final boolean TEST_MODE = true;

    // ── ML check interval: every 30 seconds ──
    private static final long ML_CHECK_INTERVAL = 30_000;

    private static final int CALL_PERMISSION_CODE     = 100;
    private static final int LOCATION_PERMISSION_CODE = 101;
    private static final int AUDIO_PERMISSION_CODE     = 102;
    private static final int SMS_PERMISSION_CODE       = 103;

    // Views
    private LinearLayout btnSOS;
    private Button btnSimulateThreat;
    private CardView btnCallPolice, btnShareLocation, btnStartTracking;
    private TextView tvAddress, tvGreeting, tvStatusTime, tvStatus;
    private FrameLayout frameMapPreview;
    private TextView tvVoiceGuardStatus, tvMotionGuardStatus, tvGuardBadge;
    private final Handler voiceResetHandler = new Handler(Looper.getMainLooper());

    // Location
    private FusedLocationProviderClient fusedLocationClient;
    private Location currentLocation = null;
    private double currentLat = 0, currentLng = 0;

    // Sensors & Voice Detector
    private SensorDataCollector sensorCollector;
    private EmergencyVoiceDetector voiceDetector;

    // Tracking
    private boolean isTracking = false;

    // SOS
    private SOSManager sosManager;
    private boolean isSOSAlertActive = false; // blocks duplicate alerts
    private Handler mlHandler = new Handler();
    private Runnable mlRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_main);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        sensorCollector     = new SensorDataCollector(this);
        sosManager          = new SOSManager(this);

        initViews();
        setGreeting();
        setListeners();
        setupBottomNav(R.id.tabHome);
        requestLocationAndFetch();
        requestAudioPermission();
        requestSMSPermission();
        startStatusDotBlink();
        startSafetyService();
        setupShakeDetector();
        setupVoiceKeywordDetector();
        startMLChecks();   // ← start automatic safety checks
    }

    private void setupShakeDetector() {
        SharedPreferences prefs = getSharedPreferences("SafeHerPrefs", MODE_PRIVATE);
        boolean isShakeEnabled = prefs.getBoolean("shake_detection", true);
        if (!isShakeEnabled) {
            if (tvMotionGuardStatus != null) {
                tvMotionGuardStatus.setText("📳 Motion SOS: Disabled in Settings");
            }
            return;
        }

        sensorCollector.setOnShakeListener(() -> runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, "🚨 Vigorous Shake Detected! Simulating Threat...", Toast.LENGTH_SHORT).show();
            simulateThreatAlert();
        }));
    }

    private void setupVoiceKeywordDetector() {
        SharedPreferences prefs = getSharedPreferences("SafeHerPrefs", MODE_PRIVATE);
        boolean isVoiceEnabled = prefs.getBoolean("voice_detection", true);
        if (!isVoiceEnabled) {
            if (tvVoiceGuardStatus != null) {
                tvVoiceGuardStatus.setText("🎙️ Voice SOS: Disabled in Settings");
            }
            sensorCollector.setAudioMeterEnabled(true);
            return;
        }

        // Avoid microphone collision: disable MediaRecorder while Voice Safety is active
        sensorCollector.setAudioMeterEnabled(false);

        if (voiceDetector == null) {
            voiceDetector = new EmergencyVoiceDetector(this);
        }

        voiceDetector.setOnVoiceDistressListener(new EmergencyVoiceDetector.OnVoiceDistressListener() {
            @Override
            public void onDistressWordDetected(String keyword, int currentCount, int requiredCount) {
                runOnUiThread(() -> {
                    if (tvVoiceGuardStatus != null) {
                        tvVoiceGuardStatus.setText("🎙️ Heard '" + keyword.toUpperCase() + "' (" + currentCount + "/" + requiredCount + ") — Repeat once more to confirm SOS!");
                    }
                    Toast.makeText(MainActivity.this,
                            "⚠️ Distress phrase '" + keyword.toUpperCase() + "' heard (" + currentCount + "/" + requiredCount + ") — Repeat to trigger Emergency SOS!",
                            Toast.LENGTH_SHORT).show();

                    voiceResetHandler.removeCallbacksAndMessages(null);
                    voiceResetHandler.postDelayed(() -> {
                        if (tvVoiceGuardStatus != null) {
                            tvVoiceGuardStatus.setText("🎙️ Voice SOS: Say 'Bachao' or 'Help' 2-3 times to trigger alert.");
                        }
                    }, 10_000L);
                });
            }

            @Override
            public void onEmergencyConfirmed(String keyword, int totalCount) {
                runOnUiThread(() -> {
                    if (tvVoiceGuardStatus != null) {
                        tvVoiceGuardStatus.setText("🚨 Confirmed: '" + keyword.toUpperCase() + "' repeated (" + totalCount + "x)! Triggering SOS...");
                    }
                    Toast.makeText(MainActivity.this,
                            "🚨 Emergency SOS Confirmed: '" + keyword.toUpperCase() + "' spoken " + totalCount + " times!",
                            Toast.LENGTH_LONG).show();
                    simulateThreatAlert();
                });
            }

            @Override
            public void onSoundLevelChanged(int soundLevel) {
                // Real-time acoustic level streamed silently into SensorDataCollector for ML
            }
        });
    }

    private void startSafetyService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 104);
            }
        }
        Intent serviceIntent = new Intent(this, SafetyForegroundService.class);
        serviceIntent.setAction(SafetyForegroundService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorCollector.stop();
        if (voiceDetector != null) {
            voiceDetector.stopListening();
        }
        voiceResetHandler.removeCallbacksAndMessages(null);
        mlHandler.removeCallbacks(mlRunnable);
        Log.d(TAG, "MainActivity paused — SafetyForegroundService continues 24/7 background protection");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (voiceDetector != null) {
            voiceDetector.stopListening();
        }
        mlHandler.removeCallbacks(mlRunnable);
    }

    private void initViews() {
        btnSOS           = findViewById(R.id.btnSOS);
        btnCallPolice    = findViewById(R.id.btnCallPolice);
        btnShareLocation = findViewById(R.id.btnShareLocation);
        btnStartTracking = findViewById(R.id.btnStartTracking);
        tvAddress        = findViewById(R.id.tvAddress);
        tvGreeting       = findViewById(R.id.tvGreeting);
        tvStatusTime     = findViewById(R.id.tvStatusTime);
        tvStatus         = findViewById(R.id.tvStatus);
        frameMapPreview  = findViewById(R.id.frameMapPreview);
        btnSimulateThreat = findViewById(R.id.btnSimulateThreat);
        tvVoiceGuardStatus  = findViewById(R.id.tvVoiceGuardStatus);
        tvMotionGuardStatus = findViewById(R.id.tvMotionGuardStatus);
        tvGuardBadge        = findViewById(R.id.tvGuardBadge);
    }

    // ── TIME-BASED GREETING ──
    private void setGreeting() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour < 12)      greeting = "Good Morning! ☀️";
        else if (hour < 17) greeting = "Good Afternoon! 🌤️";
        else                greeting = "Good Evening! 🌙";
        tvGreeting.setText(greeting);

        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        tvStatusTime.setText("Updated " + sdf.format(new Date()));
    }

    // ── STATUS DOT BLINK ──
    private void startStatusDotBlink() {
        android.view.View dot = findViewById(R.id.statusDot);
        if (dot == null) return;
        AlphaAnimation blink = new AlphaAnimation(1.0f, 0.2f);
        blink.setDuration(800);
        blink.setRepeatMode(Animation.REVERSE);
        blink.setRepeatCount(Animation.INFINITE);
        dot.startAnimation(blink);
    }

    // ── AUTO ML SAFETY CHECKS every 30 seconds ──
    private void startMLChecks() {
        mlRunnable = new Runnable() {
            @Override
            public void run() {
                checkSafetyWithRealData();
                mlHandler.postDelayed(this, ML_CHECK_INTERVAL);
            }
        };
        mlHandler.postDelayed(mlRunnable, 5000); // first check after 5s
    }

    // ── DYNAMIC ML PREDICTION with real sensor data ──
    private void checkSafetyWithRealData() {
        // 1. Get real time level
        int timeLevel  = SafetyDataHelper.getTimeLevel();

        // 2. Get real speed from GPS
        int speedLevel = SafetyDataHelper.getSpeedLevel(currentLocation);

        // 3. Get real acceleration from phone sensor
        int accelLevel = sensorCollector.getAccelerationLevel();

        // 4. Get real sound level from microphone
        int soundLevel = sensorCollector.getSoundLevel();

        // 5. Estimate heart rate from movement
        int heartRate  = SafetyDataHelper.getHeartRateLevel(speedLevel, timeLevel);

        // 6. Estimate location type
        int locType    = SafetyDataHelper.getLocationTypeLevel(speedLevel, timeLevel);

        Log.d(TAG, "ML Inputs → Speed:" + speedLevel + " HR:" + heartRate
                + " Time:" + timeLevel + " Accel:" + accelLevel
                + " LocType:" + locType + " Sound:" + soundLevel);

        // 7. Build request with REAL data
        PredictionRequest request = new PredictionRequest(
                speedLevel, heartRate, timeLevel,
                accelLevel, locType, soundLevel
        );

        // 8. Call Flask API
        ApiService apiService = RetrofitClient.getInstance().create(ApiService.class);
        apiService.predictSafety(request).enqueue(new Callback<PredictionResponse>() {
            @Override
            public void onResponse(Call<PredictionResponse> call,
                                   Response<PredictionResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    PredictionResponse result = response.body();
                    handleMLResult(result, speedLevel, timeLevel, accelLevel, soundLevel);
                }
            }

            @Override
            public void onFailure(Call<PredictionResponse> call, Throwable t) {
                Log.w(TAG, "ML API failed (" + t.getMessage() + ") -> Running on-device fallback engine");
                PredictionResponse fallback = SafetyDataHelper.evaluateOfflineSafety(
                        speedLevel, heartRate, timeLevel, accelLevel, locType, soundLevel
                );
                handleMLResult(fallback, speedLevel, timeLevel, accelLevel, soundLevel);
            }
        });
    }

    // ── HANDLE ML RESULT ──
    private void handleMLResult(PredictionResponse result,
                                int speed, int time, int accel, int sound) {
        String status = result.getStatus();
        double risk   = result.getUnsafe_probability();

        Log.d(TAG, "ML Result → " + status + " | Risk: " + risk + "%");

        runOnUiThread(() -> {
            // Update status badge
            updateStatusBadge(status, risk);

            // If UNSAFE and activity is visible → show alert
            if ("Unsafe".equals(status)) {
                if (!isFinishing() && !isDestroyed()) {
                    String summary = SafetyDataHelper.getRiskSummary(speed, time, accel, sound);
                    showUnsafeAlert(risk, summary);
                }
            }
        });
    }

    // ── UPDATE STATUS BADGE ──
    private void updateStatusBadge(String status, double risk) {
        android.view.View dot = findViewById(R.id.statusDot);

        if ("Unsafe".equals(status)) {
            tvStatus.setText("⚠️ Unsafe — Risk: " + risk + "%");
            tvStatus.setTextColor(0xFFE53935);
            if (dot != null) dot.setBackgroundResource(R.drawable.circle_red_dot);
        } else {
            tvStatus.setText("✅ You are Safe");
            tvStatus.setTextColor(0xFF2E7D32);
            if (dot != null) dot.setBackgroundResource(R.drawable.circle_green_dot);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        tvStatusTime.setText("Checked " + sdf.format(new Date()));
    }

    // ── UNSAFE ALERT — launch full screen SOS Activity ──
    private void showUnsafeAlert(double risk, String summary) {
        // Block if alert already showing — prevents duplicate alerts every 30 sec
        if (isSOSAlertActive) {
            Log.d(TAG, "SOS Alert already active — skipping duplicate");
            return;
        }
        isSOSAlertActive = true;

        Intent intent = new Intent(this, SOSAlertActivity.class);
        intent.putExtra(SOSAlertActivity.EXTRA_RISK, risk);
        intent.putExtra(SOSAlertActivity.EXTRA_SUMMARY, summary);
        intent.putExtra(SOSAlertActivity.EXTRA_LAT, currentLat);
        intent.putExtra(SOSAlertActivity.EXTRA_LNG, currentLng);
        startActivity(intent);
    }

    // Called when user returns from SOSAlertActivity
    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = getSharedPreferences("SafeHerPrefs", MODE_PRIVATE);
        boolean isVoiceEnabled = prefs.getBoolean("voice_detection", true);

        setupShakeDetector();
        setupVoiceKeywordDetector();

        sensorCollector.start();
        if (isVoiceEnabled && voiceDetector != null) {
            voiceDetector.startListening();
        } else if (voiceDetector != null) {
            voiceDetector.stopListening();
        }

        // Reset SOS flag — ready for next detection
        isSOSAlertActive = false;
        // Resume ML checks with fresh delay after returning
        mlHandler.removeCallbacks(mlRunnable);
        mlHandler.postDelayed(mlRunnable, 10_000); // wait 10s before next check
        Log.d(TAG, "ML checks resumed after SOS alert");
    }

    // ── SEND SOS SMS TO ALL CONTACTS ──
    private void sendSOSToContacts() {
        if (TEST_MODE) {
            new AlertDialog.Builder(this)
                    .setTitle("🧪 TEST MODE — Auto SOS Simulated")
                    .setMessage("In real app would send SMS to all contacts:\n\n"
                            + "📍 Location: " + currentLat + ", " + currentLng + "\n"
                            + "📲 Message: SOS + Google Maps link\n\n"
                            + "Set TEST_MODE = false to go live.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        sosManager.sendSOSToAllContacts(currentLat, currentLng,
                new SOSManager.SOSCallback() {
                    @Override
                    public void onSMSSent(int count) {
                        runOnUiThread(() ->
                                Toast.makeText(MainActivity.this,
                                        "🚨 SOS sent to " + count + " contacts!",
                                        Toast.LENGTH_LONG).show());
                    }

                    @Override
                    public void onSMSFailed(String error) {
                        runOnUiThread(() ->
                                Toast.makeText(MainActivity.this,
                                        "SOS failed: " + error,
                                        Toast.LENGTH_LONG).show());
                    }
                });
    }

    // ── LISTENERS ──
    private void setListeners() {
        btnSOS.setOnClickListener(v -> {
            btnSOS.animate().scaleX(0.93f).scaleY(0.93f).setDuration(100)
                    .withEndAction(() -> btnSOS.animate().scaleX(1f).scaleY(1f)
                            .setDuration(100).start()).start();
            showSOSDialog();
        });

        frameMapPreview.setOnClickListener(v ->
                startActivity(new Intent(this, MapActivity.class)));

        findViewById(R.id.tvRefreshLocation).setOnClickListener(v -> {
            tvAddress.setText("📍 Fetching location...");
            requestLocationAndFetch();
        });

        btnCallPolice.setOnClickListener(v -> callPolice());
        btnShareLocation.setOnClickListener(v -> shareLocation());
        btnStartTracking.setOnClickListener(v -> toggleTracking());

        if (btnSimulateThreat != null) {
            btnSimulateThreat.setOnClickListener(v -> simulateThreatAlert());
        }
    }

    /**
     * Instantly simulates high-risk danger conditions to test the full ML + SOS alert flow.
     */
    public void simulateThreatAlert() {
        Toast.makeText(this, "🧪 Simulating threat inputs to ML model...", Toast.LENGTH_SHORT).show();

        // High-risk inputs: running at late night with high struggle and noise
        final int simSpeed = 2; // running
        final int simHR    = 140; // high heart rate
        final int simTime  = 2; // late night
        final int simAccel = 2; // high struggle
        final int simLoc   = 2; // risky zone
        final int simSound = 2; // scream / loud noise

        PredictionRequest testRequest = new PredictionRequest(
                simSpeed, simHR, simTime, simAccel, simLoc, simSound
        );

        // Immediate visual feedback on status badge
        updateStatusBadge("Unsafe", 94.5);

        ApiService apiService = RetrofitClient.getInstance().create(ApiService.class);
        apiService.predictSafety(testRequest).enqueue(new Callback<PredictionResponse>() {
            @Override
            public void onResponse(Call<PredictionResponse> call, Response<PredictionResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    handleMLResult(response.body(), simSpeed, simTime, simAccel, simSound);
                } else {
                    PredictionResponse fallback = SafetyDataHelper.evaluateOfflineSafety(
                            simSpeed, simHR, simTime, simAccel, simLoc, simSound
                    );
                    handleMLResult(fallback, simSpeed, simTime, simAccel, simSound);
                }
            }

            @Override
            public void onFailure(Call<PredictionResponse> call, Throwable t) {
                Log.w(TAG, "Cloud API unavailable -> Running on-device fallback for test trigger");
                PredictionResponse fallback = SafetyDataHelper.evaluateOfflineSafety(
                        simSpeed, simHR, simTime, simAccel, simLoc, simSound
                );
                handleMLResult(fallback, simSpeed, simTime, simAccel, simSound);
            }
        });
    }

    // ── LOCATION ──
    private void requestLocationAndFetch() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_CODE);
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                currentLocation = location;
                currentLat      = location.getLatitude();
                currentLng      = location.getLongitude();
                fetchAddressFromLocation(location);
            } else {
                tvAddress.setText("📍 Location unavailable");
            }
        });
    }

    private void fetchAddressFromLocation(Location location) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(
                    location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                String text  = "";
                if (addr.getSubLocality() != null) text += addr.getSubLocality() + ", ";
                if (addr.getLocality() != null)    text += addr.getLocality();
                tvAddress.setText("📍 " + (text.isEmpty() ? "Location found" : text));
            }
        } catch (Exception e) {
            tvAddress.setText("📍 Location found");
        }
    }

    // ── SOS ──
    private void showSOSDialog() {
        new AlertDialog.Builder(this)
                .setTitle("🚨 SOS Alert")
                .setMessage("This will call police and notify all emergency contacts. Continue?")
                .setPositiveButton("SEND SOS", (d, w) -> triggerSOS())
                .setNegativeButton("Cancel", null)
                .setCancelable(false)
                .show();
    }

    private void triggerSOS() {
        showUnsafeAlert(98.0, "🚨 Emergency SOS Triggered Manually");
    }

    /**
     * Only called when user MANUALLY taps "Call Police" button.
     * Never called automatically by ML or SOS system.
     */
    private void callPolice() {
        // Show confirmation first
        new AlertDialog.Builder(this)
                .setTitle("Call Police?")
                .setMessage("This will call emergency number 100. Continue?")
                .setPositiveButton("Call 100", (d, w) -> {
                    if (TEST_MODE) {
                        Toast.makeText(this, "🧪 TEST: Would call 100",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (ActivityCompat.checkSelfPermission(this,
                            Manifest.permission.CALL_PHONE)
                            == PackageManager.PERMISSION_GRANTED) {
                        startActivity(new Intent(Intent.ACTION_CALL)
                                .setData(Uri.parse("tel:100")));
                    } else {
                        ActivityCompat.requestPermissions(this,
                                new String[]{Manifest.permission.CALL_PHONE},
                                CALL_PERMISSION_CODE);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void shareLocation() {
        String url = "https://maps.google.com/?q=" + currentLat + "," + currentLng;
        startActivity(Intent.createChooser(
                new Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "🆘 My live location: " + url),
                "Share Location via"));
    }

    private void toggleTracking() {
        isTracking = !isTracking;
        Toast.makeText(this,
                isTracking ? "✅ Tracking started!" : "Tracking stopped.",
                Toast.LENGTH_SHORT).show();
    }

    private void requestSMSPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS},
                    SMS_PERMISSION_CODE);
        }
    }

    private void requestAudioPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    AUDIO_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 0 ||
                grantResults[0] != PackageManager.PERMISSION_GRANTED) return;
        if (requestCode == LOCATION_PERMISSION_CODE) requestLocationAndFetch();
        if (requestCode == CALL_PERMISSION_CODE)     callPolice();
        if (requestCode == AUDIO_PERMISSION_CODE)    sensorCollector.start();
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "SMS permission granted ✅", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "⚠️ SMS permission denied — SOS SMS won't work",
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}
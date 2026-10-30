package com.example.women_safety_ml;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * SafetyForegroundService
 * Runs 24/7 in the background with a persistent notification.
 * Keeps sensors and ML safety checks running even when the phone is locked or in a pocket.
 * Detects 4-second Volume Up button press from Lock Screen or Background to trigger emergency SOS.
 */
public class SafetyForegroundService extends Service {

    private static final String TAG = "SafetyForegroundService";
    public static final String ACTION_START = "ACTION_START_SAFETY_SERVICE";
    public static final String ACTION_STOP  = "ACTION_STOP_SAFETY_SERVICE";

    private static final String CHANNEL_ID = "safeher_protection_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final long ML_CHECK_INTERVAL = 30_000; // Check safety every 30s

    public static boolean isRunning = false;

    private SensorDataCollector sensorCollector;
    private FusedLocationProviderClient fusedLocationClient;
    private PowerManager.WakeLock wakeLock;
    private Handler serviceHandler;
    private Runnable mlRunnable;
    private Location lastLocation = null;
    private boolean isAlertActive = false;
    private RiskFusionEngine fusionEngine;

    // ── Lock Screen & Background Volume-Up SOS Trigger (Held for 4 seconds) ──
    private BroadcastReceiver volumeButtonReceiver;
    private long volumePressStartTime = 0;
    private long lastVolumeEventTime = 0;
    private int volumePressEventCount = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        sensorCollector = new SensorDataCollector(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        serviceHandler = new Handler(Looper.getMainLooper());
        fusionEngine = new RiskFusionEngine();

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SafeHer::BackgroundSensingWakeLock");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopForegroundService();
            return START_NOT_STICKY;
        }

        startForegroundService();
        return START_STICKY;
    }

    private void startForegroundService() {
        if (isRunning) return;
        isRunning = true;

        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(10 * 60 * 1000L /* 10 minutes max per acquisition */);
        }

        Notification notification = buildNotification("SafeHer is actively monitoring your safety.");
        startForeground(NOTIFICATION_ID, notification);

        // Start listening to phone sensors
        sensorCollector.start();

        // Register hardware Volume-Up lock screen listener
        registerVolumeButtonReceiver();

        // Start recurring ML check
        mlRunnable = new Runnable() {
            @Override
            public void run() {
                performBackgroundSafetyCheck();
                serviceHandler.postDelayed(this, ML_CHECK_INTERVAL);
            }
        };
        serviceHandler.postDelayed(mlRunnable, 5000); // initial check after 5s

        Log.d(TAG, "SafetyForegroundService started ✅");
    }

    private void stopForegroundService() {
        isRunning = false;
        unregisterVolumeButtonReceiver();

        if (serviceHandler != null && mlRunnable != null) {
            serviceHandler.removeCallbacks(mlRunnable);
        }
        if (sensorCollector != null) {
            sensorCollector.stop();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        stopForeground(true);
        stopSelf();
        Log.d(TAG, "SafetyForegroundService stopped 🛑");
    }

    private void registerVolumeButtonReceiver() {
        if (volumeButtonReceiver != null) return;

        volumeButtonReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("android.media.VOLUME_CHANGED_ACTION".equals(intent.getAction())) {
                    int newVol = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1);
                    int prevVol = intent.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", -1);

                    long now = System.currentTimeMillis();
                    boolean isVolumeUp = (newVol >= prevVol && newVol > 0);

                    if (isVolumeUp) {
                        if (volumePressStartTime == 0 || (now - lastVolumeEventTime > 2500)) {
                            volumePressStartTime = now;
                            volumePressEventCount = 1;
                        } else {
                            volumePressEventCount++;
                        }
                        lastVolumeEventTime = now;

                        long duration = now - volumePressStartTime;
                        Log.d(TAG, "Lock/BG Volume-Up pressed: duration=" + duration + "ms (count=" + volumePressEventCount + ")");

                        // If volume increasing button is held/pressed continuously for 4 seconds
                        if (duration >= 3800L && volumePressEventCount >= 2) {
                            volumePressStartTime = 0;
                            volumePressEventCount = 0;
                            Log.w(TAG, "🚨 Volume Up pressed for 4 seconds on lock screen! Launching SOS!");
                            triggerEmergencyAlert(99.0, "🚨 Emergency SOS: Volume Button Pressed for 4 Seconds on Lock Screen");
                        }
                    } else {
                        volumePressStartTime = 0;
                        volumePressEventCount = 0;
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
        registerReceiver(volumeButtonReceiver, filter);
        Log.d(TAG, "Lock screen Volume-Up 4s detector registered ✅");
    }

    private void unregisterVolumeButtonReceiver() {
        if (volumeButtonReceiver != null) {
            try {
                unregisterReceiver(volumeButtonReceiver);
            } catch (Exception ignored) {}
            volumeButtonReceiver = null;
        }
    }

    private void performBackgroundSafetyCheck() {
        fetchLocationAndPredict();
    }

    private void fetchLocationAndPredict() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    lastLocation = location;
                }
                executeMLPrediction(lastLocation);
            }).addOnFailureListener(e -> executeMLPrediction(lastLocation));
        } else {
            executeMLPrediction(lastLocation);
        }
    }

    private void executeMLPrediction(Location location) {
        int timeLevel  = SafetyDataHelper.getTimeLevel();
        int speedLevel = SafetyDataHelper.getSpeedLevel(location);
        int accelLevel = sensorCollector.getAccelerationLevel();
        int soundLevel = sensorCollector.getSoundLevel();
        int heartRate  = SafetyDataHelper.getHeartRateLevel(speedLevel, timeLevel);
        int locType    = SafetyDataHelper.getLocationTypeLevel(speedLevel, timeLevel);

        // ── Objective 4: On-Device Real-Time Risk Fusion Engine ──
        double soundScore    = soundLevel / 2.0;
        double motionScore   = accelLevel / 2.0;
        double locationScore = locType / 2.0;
        double timeScore     = timeLevel / 2.0;
        double devScore      = 0.0; // on path

        RiskFusionEngine.FusionResult fusionResult = fusionEngine.evaluateRisk(
                soundScore, motionScore, locationScore, timeScore, devScore
        );

        Log.d(TAG, "On-Device Fusion Result -> State: " + fusionResult.state.name()
                + " EMA Risk: " + String.format("%.1f%%", fusionResult.emaRisk * 100)
                + " TriggerReady: " + fusionResult.shouldTriggerAlert);

        if (fusionResult.shouldTriggerAlert) {
            triggerEmergencyAlert(fusionResult.emaRisk * 100.0, fusionResult.summary);
            return;
        }

        // Secondary: Send to Cloud ML endpoint for baseline comparison
        PredictionRequest request = new PredictionRequest(
                speedLevel, heartRate, timeLevel, accelLevel, locType, soundLevel
        );

        ApiService apiService = RetrofitClient.getInstance().create(ApiService.class);
        apiService.predictSafety(request).enqueue(new Callback<PredictionResponse>() {
            @Override
            public void onResponse(Call<PredictionResponse> call, Response<PredictionResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    processSafetyResult(response.body(), speedLevel, timeLevel, accelLevel, soundLevel);
                } else {
                    onOfflineFallback(speedLevel, heartRate, timeLevel, accelLevel, locType, soundLevel);
                }
            }

            @Override
            public void onFailure(Call<PredictionResponse> call, Throwable t) {
                Log.w(TAG, "Cloud API failed (" + t.getMessage() + ") -> Using on-device fallback");
                onOfflineFallback(speedLevel, heartRate, timeLevel, accelLevel, locType, soundLevel);
            }
        });
    }

    private void onOfflineFallback(int speed, int hr, int time, int accel, int locType, int sound) {
        PredictionResponse fallback = SafetyDataHelper.evaluateOfflineSafety(speed, hr, time, accel, locType, sound);
        processSafetyResult(fallback, speed, time, accel, sound);
    }

    private void processSafetyResult(PredictionResponse response, int speed, int time, int accel, int sound) {
        String status = response.getStatus();
        double risk = response.getUnsafe_probability();

        Log.d(TAG, "Cloud ML Check Result: " + status + " (" + risk + "%)");

        if ("Unsafe".equalsIgnoreCase(status) && risk >= 75.0) {
            triggerEmergencyAlert(risk, SafetyDataHelper.getRiskSummary(speed, time, accel, sound));
        }
    }

    private void triggerEmergencyAlert(double risk, String summary) {
        if (isAlertActive) return;
        isAlertActive = true;

        Log.w(TAG, "⚠️ POTENTIAL DANGER DETECTED! Waking device and launching SOS countdown!");

        // Wake screen if device is locked
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            PowerManager.WakeLock screenWakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
                    "SafeHer:LockScreenSOSWake");
            screenWakeLock.acquire(15000);
        }

        Intent alertIntent = new Intent(this, SOSAlertActivity.class);
        alertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        alertIntent.putExtra(SOSAlertActivity.EXTRA_RISK, risk);
        alertIntent.putExtra(SOSAlertActivity.EXTRA_SUMMARY, summary);
        if (lastLocation != null) {
            alertIntent.putExtra(SOSAlertActivity.EXTRA_LAT, lastLocation.getLatitude());
            alertIntent.putExtra(SOSAlertActivity.EXTRA_LNG, lastLocation.getLongitude());
        }
        startActivity(alertIntent);

        // Reset alert throttle after 60 seconds
        serviceHandler.postDelayed(() -> isAlertActive = false, 60_000);
    }

    private Notification buildNotification(String contentText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SafeHer Shield Active")
                .setContentText(contentText)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SafeHer Background Protection",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows active background monitoring status for SafeHer");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopForegroundService();
        super.onDestroy();
    }
}

package com.example.women_safety_ml;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SettingsActivity extends BaseActivity implements SensorEventListener {

    private static final String PREFS_NAME      = "SafeHerPrefs";
    private static final String KEY_VOICE       = "voice_detection";
    private static final String KEY_SHAKE       = "shake_detection";
    private static final String KEY_SOS_MSG     = "sos_message";
    private static final String DEFAULT_SOS_MSG = "🆘 HELP! I am in danger. Please contact police. My location: ";

    // Shake detection thresholds
    private static final float SHAKE_THRESHOLD = 12.0f;
    private static final int   SHAKE_WAIT_MS   = 1000;

    private SwitchCompat switchVoice, switchShake;
    private android.widget.LinearLayout layoutSOSMessage, layoutLogout;
    private TextView tvPermLocation, tvPermMic, tvPermSMS;
    private TextView tvUserEmail, tvUserName;

    private SharedPreferences prefs;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Shake sensor
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastShakeTime = 0;
    private boolean isShakeEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_settings);

        mAuth  = FirebaseAuth.getInstance();
        db     = FirebaseFirestore.getInstance();
        prefs  = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        initViews();
        loadUserInfo();
        loadSavedSettings();
        checkPermissions();
        setListeners();
        setupBottomNav(R.id.tabSettings);
    }

    private void initViews() {
        switchVoice      = findViewById(R.id.switchVoice);
        switchShake      = findViewById(R.id.switchShake);
        layoutSOSMessage = findViewById(R.id.layoutSOSMessage);
        layoutLogout     = findViewById(R.id.layoutLogout);
        tvPermLocation   = findViewById(R.id.tvPermLocation);
        tvPermMic        = findViewById(R.id.tvPermMic);
        tvPermSMS        = findViewById(R.id.tvPermSMS);
        tvUserEmail      = findViewById(R.id.tvUserEmail);
        tvUserName       = findViewById(R.id.tvUserName);
    }

    // ── LOAD USER INFO FROM FIREBASE ──
    private void loadUserInfo() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        // Show email
        if (tvUserEmail != null)
            tvUserEmail.setText(user.getEmail());

        // Load name from Firestore
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && tvUserName != null) {
                        String name = doc.getString("name");
                        if (name != null) tvUserName.setText(name);
                    }
                });
    }

    // ── LOAD SAVED SETTINGS FROM SHAREDPREFS ──
    private void loadSavedSettings() {
        boolean voiceOn = prefs.getBoolean(KEY_VOICE, false);
        boolean shakeOn = prefs.getBoolean(KEY_SHAKE, false);

        // Set switch states without triggering listeners
        switchVoice.setChecked(voiceOn);
        switchShake.setChecked(shakeOn);
        isShakeEnabled = shakeOn;
    }

    private void setListeners() {

        findViewById(R.id.ivBack).setOnClickListener(v -> finish());

        // ── Voice Detection ──
        switchVoice.setOnCheckedChangeListener((btn, isChecked) -> {
            saveSetting(KEY_VOICE, isChecked);
            if (isChecked) {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.RECORD_AUDIO}, 201);
                    switchVoice.setChecked(false);
                    return;
                }
                Toast.makeText(this, "🎤 Voice Detection ON", Toast.LENGTH_SHORT).show();
                // TODO: start VoiceDetectionService
            } else {
                Toast.makeText(this, "Voice Detection OFF", Toast.LENGTH_SHORT).show();
                // TODO: stop VoiceDetectionService
            }
            saveSettingsToFirestore();
        });

        // ── Shake Detection ──
        switchShake.setOnCheckedChangeListener((btn, isChecked) -> {
            isShakeEnabled = isChecked;
            saveSetting(KEY_SHAKE, isChecked);
            if (isChecked) {
                sensorManager.registerListener(this, accelerometer,
                        SensorManager.SENSOR_DELAY_NORMAL);
                Toast.makeText(this, "📳 Shake Detection ON — shake phone to trigger SOS",
                        Toast.LENGTH_LONG).show();
            } else {
                sensorManager.unregisterListener(this);
                Toast.makeText(this, "Shake Detection OFF", Toast.LENGTH_SHORT).show();
            }
            saveSettingsToFirestore();
        });

        // ── SOS Message ──
        layoutSOSMessage.setOnClickListener(v -> showSOSMessageDialog());

        // ── Permission rows — tap to request ──
        findViewById(R.id.layoutPermLocation).setOnClickListener(v ->
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 202));

        findViewById(R.id.layoutPermMic).setOnClickListener(v ->
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO}, 203));

        findViewById(R.id.layoutPermSMS).setOnClickListener(v ->
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.SEND_SMS}, 204));

        // ── Logout ──
        layoutLogout.setOnClickListener(v -> showLogoutDialog());
    }

    // ── SOS MESSAGE DIALOG ──
    private void showSOSMessageDialog() {
        String currentMsg = prefs.getString(KEY_SOS_MSG, DEFAULT_SOS_MSG);

        EditText etMessage = new EditText(this);
        etMessage.setText(currentMsg);
        etMessage.setPadding(48, 24, 48, 24);
        etMessage.setLines(3);

        new AlertDialog.Builder(this)
                .setTitle("✏️ Customize SOS Message")
                .setMessage("This message will be sent to your emergency contacts:")
                .setView(etMessage)
                .setPositiveButton("Save", (dialog, which) -> {
                    String msg = etMessage.getText().toString().trim();
                    if (!msg.isEmpty()) {
                        // Save to SharedPreferences
                        prefs.edit().putString(KEY_SOS_MSG, msg).apply();
                        // Save to Firestore
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            db.collection("users").document(user.getUid())
                                    .update("sosMessage", msg)
                                    .addOnSuccessListener(a ->
                                            Toast.makeText(this,
                                                    "✅ SOS message saved!",
                                                    Toast.LENGTH_SHORT).show());
                        } else {
                            Toast.makeText(this, "✅ SOS message saved locally!",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Reset Default", (d, w) -> {
                    prefs.edit().putString(KEY_SOS_MSG, DEFAULT_SOS_MSG).apply();
                    Toast.makeText(this, "Reset to default", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    // ── SAVE SETTINGS TO FIRESTORE ──
    private void saveSettingsToFirestore() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        Map<String, Object> settings = new HashMap<>();
        settings.put("voiceDetection", prefs.getBoolean(KEY_VOICE, false));
        settings.put("shakeDetection", prefs.getBoolean(KEY_SHAKE, false));

        db.collection("users").document(user.getUid())
                .update(settings)
                .addOnFailureListener(e ->
                        db.collection("users").document(user.getUid())
                                .set(settings)); // create if not exists
    }

    // ── SAVE SETTING LOCALLY ──
    private void saveSetting(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }

    // ── CHECK PERMISSIONS ──
    private void checkPermissions() {
        updatePermStatus(tvPermLocation, Manifest.permission.ACCESS_FINE_LOCATION);
        updatePermStatus(tvPermMic,      Manifest.permission.RECORD_AUDIO);
        updatePermStatus(tvPermSMS,      Manifest.permission.SEND_SMS);
    }

    private void updatePermStatus(TextView tv, String permission) {
        if (tv == null) return;
        boolean granted = checkSelfPermission(permission)
                == PackageManager.PERMISSION_GRANTED;
        tv.setText(granted ? "Granted" : "Tap to grant");
        tv.setTextColor(granted ? 0xFF4CAF50 : 0xFFE53935);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        // Refresh permission display after user responds
        checkPermissions();
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "✅ Permission granted!", Toast.LENGTH_SHORT).show();
        }
    }

    // ── SHAKE DETECTION ──
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isShakeEnabled) return;
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        float x = event.values[0], y = event.values[1], z = event.values[2];
        float magnitude = (float) Math.sqrt(x*x + y*y + z*z) - 9.8f;

        if (magnitude > SHAKE_THRESHOLD) {
            long now = System.currentTimeMillis();
            if (now - lastShakeTime > SHAKE_WAIT_MS) {
                lastShakeTime = now;
                onShakeDetected();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void onShakeDetected() {
        new AlertDialog.Builder(this)
                .setTitle("📳 Shake Detected!")
                .setMessage("Do you want to send an SOS alert?")
                .setPositiveButton("🚨 SEND SOS", (d, w) ->
                        Toast.makeText(this, "🚨 SOS triggered by shake!",
                                Toast.LENGTH_LONG).show())
                .setNegativeButton("Cancel", null)
                .setCancelable(false)
                .show();
    }

    // ── LOGOUT ──
    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    FirebaseAuth.getInstance().signOut();
                    prefs.edit().clear().apply(); // clear local settings
                    startActivity(new Intent(this, LoginActivity.class)
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissions(); // refresh every time screen is shown
        if (isShakeEnabled && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }
}
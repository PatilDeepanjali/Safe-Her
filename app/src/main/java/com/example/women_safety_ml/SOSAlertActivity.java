package com.example.women_safety_ml;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SOSAlertActivity extends AppCompatActivity {

    // Step 6: 10-second rapid response countdown gives user time to cancel or immediately dispatch SOS
    private static final long COUNTDOWN_MS = 10_000; // 10 seconds
    private static final long INTERVAL_MS  = 1000;

    public static final String EXTRA_RISK    = "risk";
    public static final String EXTRA_SUMMARY = "summary";
    public static final String EXTRA_LAT     = "lat";
    public static final String EXTRA_LNG     = "lng";

    private TextView tvRisk, tvSummary, tvCountdown;
    private ProgressBar progressBar;
    private Button btnSafe, btnSOSNow;
    private CountDownTimer countDownTimer;

    private double lat, lng, risk;
    private String summary;
    private SOSManager sosManager;
    private AudioEvidenceRecorder audioEvidenceRecorder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // Ensure activity displays over lock screen and turns display on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) km.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        setContentView(R.layout.activity_sos_alert);

        // Get data from intent
        risk    = getIntent().getDoubleExtra(EXTRA_RISK, 0);
        summary = getIntent().getStringExtra(EXTRA_SUMMARY);
        lat     = getIntent().getDoubleExtra(EXTRA_LAT, 0);
        lng     = getIntent().getDoubleExtra(EXTRA_LNG, 0);

        sosManager = new SOSManager(this);
        audioEvidenceRecorder = new AudioEvidenceRecorder(this);

        initViews();
        startCountdown();
        // Trigger loud emergency "bippp" siren and pulsing alarm
        EmergencyAlarmPlayer.getInstance(this).startAlarm();
    }

    private void initViews() {
        tvRisk      = findViewById(R.id.tvRiskLevel);
        tvSummary   = findViewById(R.id.tvSummary);
        tvCountdown = findViewById(R.id.tvCountdown);
        progressBar = findViewById(R.id.progressCountdown);
        btnSafe     = findViewById(R.id.btnImSafe);
        btnSOSNow   = findViewById(R.id.btnSendSOSNow);

        double displayRisk = (risk <= 1.0) ? (risk * 100) : risk;
        tvRisk.setText(String.format(Locale.US, "High Risk: %.1f%% Threat Confidence", displayRisk));
        tvSummary.setText(summary != null && !summary.isEmpty() ? summary : "Potential threat detected. Are you safe?");
        progressBar.setMax((int)(COUNTDOWN_MS / INTERVAL_MS));
        progressBar.setProgress((int)(COUNTDOWN_MS / INTERVAL_MS));

        // Step 6: "I'm Safe" option - cancels countdown & logs false alarm for ML review (Step 8)
        btnSafe.setOnClickListener(v -> {
            EmergencyAlarmPlayer.getInstance(this).stopAlarm();
            cancelCountdown();
            logIncidentHistory("FALSE_ALARM_CANCELLED", "USER_MARKED_SAFE");
            Toast.makeText(this, "Glad you are safe! Cancelled alarm logged for review. ✅", Toast.LENGTH_SHORT).show();
            finish();
        });

        // Step 6: "Send SOS" option - immediately dispatches emergency alert without waiting
        btnSOSNow.setOnClickListener(v -> {
            cancelCountdown();
            sendSOS();
        });
    }

    // Step 8: Log incident status & history (Confirmed SOS or Cancelled False Alarm) in Firestore
    private void logIncidentHistory(String eventType, String status) {
        com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return;

        Map<String, Object> log = new HashMap<>();
        log.put("eventType", eventType);
        log.put("status", status);
        log.put("riskLevel", risk);
        log.put("summary", summary != null ? summary : "");
        log.put("timestamp", System.currentTimeMillis());
        log.put("latitude", lat);
        log.put("longitude", lng);

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users")
                .document(auth.getCurrentUser().getUid())
                .collection("incident_history")
                .add(log);
    }

    private void startCountdown() {
        countDownTimer = new CountDownTimer(COUNTDOWN_MS, INTERVAL_MS) {
            @Override
            public void onTick(long millisLeft) {
                long secs = (millisLeft + 999) / 1000;
                tvCountdown.setText(String.format(Locale.US, "Auto SOS in: %02ds (Tap 'I’m Safe' to cancel)", secs));
                progressBar.setProgress((int)(millisLeft / INTERVAL_MS));
                tvCountdown.setTextColor(secs <= 5 ? 0xFFFF5449 : 0xFFFFB787);
            }

            @Override
            public void onFinish() {
                tvCountdown.setText("Auto SOS triggered! Sending alerts...");
                sendSOS();
            }
        }.start();
    }

    private void sendSOS() {
        btnSafe.setEnabled(false);
        btnSOSNow.setEnabled(false);
        tvCountdown.setText("Dispatching SOS & Live Location...");

        // Step 8: Log confirmed SOS in incident history
        logIncidentHistory("CONFIRMED_SOS", "DISPATCHED");

        // Objective 5: Record acoustic evidence clip locally
        audioEvidenceRecorder.startRecordingEvidence(lat, lng, new AudioEvidenceRecorder.OnEvidenceRecordedListener() {
            @Override
            public void onEvidenceReady(java.io.File evidenceFile) {
                android.util.Log.d("SOSAlertActivity", "Evidence recorded: " + evidenceFile.getName());
            }

            @Override
            public void onEvidenceFailed(String error) {
                android.util.Log.e("SOSAlertActivity", "Evidence recording error: " + error);
            }
        });

        // Step 7: SOS & Emergency Response: SMS with live GPS link to trusted contacts
        sosManager.sendSOSToAllContacts(lat, lng, new SOSManager.SOSCallback() {
            @Override
            public void onSMSSent(int count) {
                runOnUiThread(() -> {
                    Toast.makeText(SOSAlertActivity.this,
                            "🚨 SOS sent to " + count + " emergency contacts! Live tracking active.", Toast.LENGTH_LONG).show();
                    finish();
                });
            }

            @Override
            public void onSMSFailed(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(SOSAlertActivity.this,
                            "SMS Failed: " + error, Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void cancelCountdown() {
        if (countDownTimer != null) countDownTimer.cancel();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EmergencyAlarmPlayer.getInstance(this).stopAlarm();
        cancelCountdown();
    }

    @Override
    public void onBackPressed() {
        // Prevent back button — user must respond with "I'm Safe" or "Send SOS"
        Toast.makeText(this, "Please respond to the alert!", Toast.LENGTH_SHORT).show();
    }
}

package com.example.women_safety_ml;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SOSAlertActivity extends AppCompatActivity {

    private static final long COUNTDOWN_MS = 10_000; // 10 seconds rapid response prompt
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
    }

    private void initViews() {
        tvRisk      = findViewById(R.id.tvRiskLevel);
        tvSummary   = findViewById(R.id.tvSummary);
        tvCountdown = findViewById(R.id.tvCountdown);
        progressBar = findViewById(R.id.progressCountdown);
        btnSafe     = findViewById(R.id.btnImSafe);
        btnSOSNow   = findViewById(R.id.btnSendSOSNow);

        tvRisk.setText(String.format("Risk Level: %.1f%%", risk));
        tvSummary.setText(summary != null ? summary : "Potential threat detected. Are you safe?");
        progressBar.setMax((int)(COUNTDOWN_MS / INTERVAL_MS));
        progressBar.setProgress((int)(COUNTDOWN_MS / INTERVAL_MS));

        // I'm Safe button - cancels and logs false alarm for ML feedback
        btnSafe.setOnClickListener(v -> {
            cancelCountdown();
            logFalseAlarmFeedback();
            Toast.makeText(this, "Glad you are safe! False alarm logged. ✅", Toast.LENGTH_SHORT).show();
            finish();
        });

        // Send SOS Now button
        btnSOSNow.setOnClickListener(v -> {
            cancelCountdown();
            sendSOS();
        });
    }

    private void logFalseAlarmFeedback() {
        com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return;

        java.util.Map<String, Object> log = new java.util.HashMap<>();
        log.put("feedback", "user_marked_safe");
        log.put("originalRisk", risk);
        log.put("summary", summary);
        log.put("timestamp", System.currentTimeMillis());
        log.put("latitude", lat);
        log.put("longitude", lng);

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users")
                .document(auth.getCurrentUser().getUid())
                .collection("feedback_logs")
                .add(log);
    }

    private void startCountdown() {
        countDownTimer = new CountDownTimer(COUNTDOWN_MS, INTERVAL_MS) {
            @Override
            public void onTick(long millisLeft) {
                long secs = millisLeft / 1000;
                tvCountdown.setText(String.format("Auto SOS in: %02ds (Tap 'I'm Safe' to cancel)", secs));
                progressBar.setProgress((int)(millisLeft / INTERVAL_MS));
                tvCountdown.setTextColor(secs <= 5 ? 0xFFE53935 : 0xFFFF9800);
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
        tvCountdown.setText("Dispatching SOS & Capturing Evidence...");

        // Objective 5: Record 15-second acoustic evidence clip locally & log to Firestore
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

        sosManager.sendSOSToAllContacts(lat, lng, new SOSManager.SOSCallback() {
            @Override
            public void onSMSSent(int count) {
                runOnUiThread(() -> {
                    Toast.makeText(SOSAlertActivity.this,
                            "🚨 SOS sent to " + count + " emergency contacts! Evidence recording active.", Toast.LENGTH_LONG).show();
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
        cancelCountdown();
    }

    @Override
    public void onBackPressed() {
        // Prevent back button — user must respond
        Toast.makeText(this, "Please respond to the alert!", Toast.LENGTH_SHORT).show();
    }
}
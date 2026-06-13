package com.example.women_safety_ml;

import android.app.Activity;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

public class AutoSOSDialog {

    private static final long COUNTDOWN_MS = 1 * 60 * 1000; // 3 minutes
    private static final long INTERVAL_MS  = 1000;

    public interface AutoSOSListener {
        void onUserSafe();
        void onAutoSOSTriggered();
        void onUserTriggerSOS();
    }

    private final Activity activity;  // ← Use Activity not Context
    private final AutoSOSListener listener;
    private final double risk;
    private final String summary;

    private CountDownTimer countDownTimer;
    private AlertDialog dialog;

    public AutoSOSDialog(Activity activity, double risk,
                         String summary, AutoSOSListener listener) {
        this.activity = activity;
        this.risk     = risk;
        this.summary  = summary;
        this.listener = listener;
    }

    public void show() {
        // Safety check — don't show if activity is gone
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        View view = LayoutInflater.from(activity)
                .inflate(R.layout.dialog_auto_sos, null);

        TextView  tvRisk      = view.findViewById(R.id.tvRiskLevel);
        TextView  tvSummary   = view.findViewById(R.id.tvSummary);
        TextView  tvCountdown = view.findViewById(R.id.tvCountdown);
        ProgressBar progress  = view.findViewById(R.id.progressCountdown);
        Button btnSafe        = view.findViewById(R.id.btnImSafe);
        Button btnSOS         = view.findViewById(R.id.btnSendSOSNow);

        tvRisk.setText("Risk Level: " + risk + "%");
        tvSummary.setText(summary.isEmpty() ? "Stay alert!" : summary);
        progress.setMax((int)(COUNTDOWN_MS / INTERVAL_MS));
        progress.setProgress((int)(COUNTDOWN_MS / INTERVAL_MS));

        dialog = new AlertDialog.Builder(activity)
                .setView(view)
                .setCancelable(false)
                .create();

        btnSafe.setOnClickListener(v -> {
            cancelCountdown();
            if (dialog.isShowing()) dialog.dismiss();
            if (listener != null) listener.onUserSafe();
        });

        btnSOS.setOnClickListener(v -> {
            cancelCountdown();
            if (dialog.isShowing()) dialog.dismiss();
            if (listener != null) listener.onUserTriggerSOS();
        });

        // Show safely
        try {
            dialog.show();
            startCountdown(tvCountdown, progress);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startCountdown(TextView tvCountdown, ProgressBar progress) {
        countDownTimer = new CountDownTimer(COUNTDOWN_MS, INTERVAL_MS) {
            @Override
            public void onTick(long millisLeft) {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    cancel();
                    return;
                }
                long secs    = millisLeft / 1000;
                long minutes = secs / 60;
                long seconds = secs % 60;

                tvCountdown.setText(String.format(
                        "Auto SOS in: %02d:%02d", minutes, seconds));
                progress.setProgress((int)(millisLeft / INTERVAL_MS));
                tvCountdown.setTextColor(secs <= 30 ? 0xFFE53935 : 0xFFFF9800);
            }

            @Override
            public void onFinish() {
                tvCountdown.setText("Sending SOS...");
                if (dialog != null && dialog.isShowing()) dialog.dismiss();
                if (listener != null) listener.onAutoSOSTriggered();
            }
        }.start();
    }

    public void cancelCountdown() {
        if (countDownTimer != null) countDownTimer.cancel();
    }

    public void dismiss() {
        cancelCountdown();
        try {
            if (dialog != null && dialog.isShowing()) dialog.dismiss();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
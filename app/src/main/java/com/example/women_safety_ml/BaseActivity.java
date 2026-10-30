package com.example.women_safety_ml;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * BaseActivity — Extended by all screens in SafeHer.
 * 
 * Provides:
 * 1. Global Bottom Navigation wiring across all screens.
 * 2. App-Wide Voice Keyword Detection: Ensures distress keywords ("Bachao", "Help")
 *    work seamlessly from EVERY screen in the app.
 * 3. 4-Second Volume-Up SOS Trigger: Long-pressing or holding Volume Up for 4 seconds
 *    on any screen immediately dispatches Emergency SOS.
 */
public abstract class BaseActivity extends AppCompatActivity {

    private final Handler volumeHoldHandler = new Handler(Looper.getMainLooper());
    private final Runnable volumeSosRunnable = new Runnable() {
        @Override
        public void run() {
            Toast.makeText(BaseActivity.this, "🚨 Volume Up held for 4 seconds! Triggering Emergency SOS...", Toast.LENGTH_LONG).show();
            Intent alertIntent = new Intent(BaseActivity.this, SOSAlertActivity.class);
            alertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            alertIntent.putExtra(SOSAlertActivity.EXTRA_RISK, 99.0);
            alertIntent.putExtra(SOSAlertActivity.EXTRA_SUMMARY, "🚨 Emergency SOS: Volume Button Pressed for 4 Seconds");
            startActivity(alertIntent);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        // Activate app-wide voice keyword detection on every screen
        VoiceGuardManager.getInstance(this).startListening();
    }

    @Override
    protected void onPause() {
        super.onPause();
        volumeHoldHandler.removeCallbacks(volumeSosRunnable);
    }

    /**
     * Volume Increasing (Volume Up) Hardware Button Press for 4 Seconds
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    volumeHoldHandler.removeCallbacks(volumeSosRunnable);
                    volumeHoldHandler.postDelayed(volumeSosRunnable, 4000); // 4 seconds hold
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                volumeHoldHandler.removeCallbacks(volumeSosRunnable);
            }
        }
        return super.dispatchKeyEvent(event);
    }

    protected void setupBottomNav(int activeTabId) {
        LinearLayout tabHome     = findViewById(R.id.tabHome);
        LinearLayout tabContacts = findViewById(R.id.tabContacts);
        LinearLayout tabMap      = findViewById(R.id.tabMap);
        LinearLayout tabSettings = findViewById(R.id.tabSettings);

        // Highlight the active tab
        setTabActive(tabHome,     activeTabId == R.id.tabHome);
        setTabActive(tabContacts, activeTabId == R.id.tabContacts);
        setTabActive(tabMap,      activeTabId == R.id.tabMap);
        setTabActive(tabSettings, activeTabId == R.id.tabSettings);

        if (tabHome != null) {
            tabHome.setOnClickListener(v -> {
                if (activeTabId != R.id.tabHome) {
                    startActivity(new Intent(this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                    overridePendingTransition(0, 0);
                }
            });
        }

        if (tabContacts != null) {
            tabContacts.setOnClickListener(v -> {
                if (activeTabId != R.id.tabContacts) {
                    startActivity(new Intent(this, ContactsActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                    overridePendingTransition(0, 0);
                }
            });
        }

        if (tabMap != null) {
            tabMap.setOnClickListener(v -> {
                if (activeTabId != R.id.tabMap) {
                    startActivity(new Intent(this, MapActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                    overridePendingTransition(0, 0);
                }
            });
        }

        if (tabSettings != null) {
            tabSettings.setOnClickListener(v -> {
                if (activeTabId != R.id.tabSettings) {
                    startActivity(new Intent(this, SettingsActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                    overridePendingTransition(0, 0);
                }
            });
        }
    }

    private void setTabActive(LinearLayout tab, boolean isActive) {
        if (tab == null) return;
        ImageView icon  = (ImageView) tab.getChildAt(0);
        TextView  label = (TextView)  tab.getChildAt(1);
        if (icon  != null) icon.setAlpha(isActive ? 1f : 0.5f);
        if (label != null) label.setTextColor(isActive ? 0xFF432587 : 0xFF999999);
    }
}

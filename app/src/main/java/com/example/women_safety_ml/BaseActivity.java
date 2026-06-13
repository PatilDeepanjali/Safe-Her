package com.example.women_safety_ml;

import android.content.Intent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * BaseActivity — extend this in every screen that has a bottom nav bar.
 * Just call setupBottomNav(R.id.tabXxx) in your onCreate() to highlight
 * the correct tab and wire up navigation automatically.
 */
public abstract class BaseActivity extends AppCompatActivity {

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

        // ── Wire up click listeners ──
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

    /** Sets tab icon alpha and text color based on active state */
    private void setTabActive(LinearLayout tab, boolean isActive) {
        if (tab == null) return;
        ImageView icon  = (ImageView) tab.getChildAt(0);
        TextView  label = (TextView)  tab.getChildAt(1);
        if (icon  != null) icon.setAlpha(isActive ? 1f : 0.5f);
        if (label != null) label.setTextColor(isActive ? 0xFF9B2FC9 : 0xFF999999);
    }
}
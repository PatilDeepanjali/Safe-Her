package com.example.women_safety_ml;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

/**
 * VoiceGuardManager
 * 
 * Global App-Wide Distress Voice Manager:
 * Ensures voice keyword detection ("Bachao", "Help", "Madad") works
 * seamlessly from EVERY screen of the app (Home, Map, Contacts, Settings)
 * without playing Google Assistant chimes or interrupting navigation.
 */
public class VoiceGuardManager {

    private static final String TAG = "VoiceGuardManager";
    private static VoiceGuardManager instance;

    private final Context context;
    private EmergencyVoiceDetector voiceDetector;
    private EmergencyVoiceDetector.OnVoiceDistressListener externalListener;
    private boolean isVoiceEnabled = true;

    public static synchronized VoiceGuardManager getInstance(Context context) {
        if (instance == null) {
            instance = new VoiceGuardManager(context.getApplicationContext());
        }
        return instance;
    }

    private VoiceGuardManager(Context context) {
        this.context = context;
        initVoiceDetector();
    }

    private void initVoiceDetector() {
        voiceDetector = new EmergencyVoiceDetector(context);
        voiceDetector.setOnVoiceDistressListener(new EmergencyVoiceDetector.OnVoiceDistressListener() {
            @Override
            public void onDistressWordDetected(String keyword, int currentCount, int requiredCount) {
                Log.w(TAG, "Distress keyword heard: " + keyword + " (" + currentCount + "/" + requiredCount + ")");
                if (externalListener != null) {
                    externalListener.onDistressWordDetected(keyword, currentCount, requiredCount);
                } else {
                    Toast.makeText(context, "⚠️ Heard '" + keyword.toUpperCase() + "' (" + currentCount + "/" + requiredCount + ") — Repeat once more to trigger SOS!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onEmergencyConfirmed(String keyword, int totalCount) {
                Log.e(TAG, "🚨 EMERGENCY CONFIRMED via Voice: " + keyword);
                if (externalListener != null) {
                    externalListener.onEmergencyConfirmed(keyword, totalCount);
                } else {
                    // Trigger SOSAlertActivity from any screen
                    Intent alertIntent = new Intent(context, SOSAlertActivity.class);
                    alertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    alertIntent.putExtra(SOSAlertActivity.EXTRA_RISK, 98.0);
                    alertIntent.putExtra(SOSAlertActivity.EXTRA_SUMMARY, "🚨 Voice SOS Confirmed: Distress keyword '" + keyword.toUpperCase() + "' detected");
                    context.startActivity(alertIntent);
                }
            }

            @Override
            public void onSoundLevelChanged(int soundLevel) {
                if (externalListener != null) {
                    externalListener.onSoundLevelChanged(soundLevel);
                }
            }
        });
    }

    public void setExternalListener(EmergencyVoiceDetector.OnVoiceDistressListener listener) {
        this.externalListener = listener;
    }

    public void removeExternalListener(EmergencyVoiceDetector.OnVoiceDistressListener listener) {
        if (this.externalListener == listener) {
            this.externalListener = null;
        }
    }

    public void startListening() {
        SharedPreferences prefs = context.getSharedPreferences("SafeHerPrefs", Context.MODE_PRIVATE);
        isVoiceEnabled = prefs.getBoolean("voice_detection", true);
        if (!isVoiceEnabled) {
            Log.d(TAG, "Voice detection is disabled in settings");
            return;
        }

        if (voiceDetector != null && !voiceDetector.isListening()) {
            voiceDetector.startListening();
        }
    }

    public void stopListening() {
        if (voiceDetector != null && voiceDetector.isListening()) {
            voiceDetector.stopListening();
        }
    }

    public boolean isListening() {
        return voiceDetector != null && voiceDetector.isListening();
    }
}


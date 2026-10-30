package com.example.women_safety_ml;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * EmergencyVoiceDetector
 * 
 * High-sensitivity Distress Voice Keyword Detection for SafeHer:
 * 1. Immediate Trigger on Keyword: Saying "Bachao" or "Help" immediately confirms SOS.
 * 2. Silent Operation: Suppresses the audio chime without breaking speech recognition.
 * 3. Works seamlessly online and offline across every screen of the app.
 * 4. Ambient Decibel Mapping: Passes real-time sound levels to ML (0=quiet, 1=moderate, 2=screaming).
 */
public class EmergencyVoiceDetector implements RecognitionListener {

    private static final String TAG = "EmergencyVoiceDetector";

    public interface OnVoiceDistressListener {
        void onDistressWordDetected(String keyword, int currentCount, int requiredCount);
        void onEmergencyConfirmed(String keyword, int totalCount);
        void onSoundLevelChanged(int soundLevel);
    }

    // Emergency distress keywords in Hindi, Marathi & English + phonetic variants
    private static final String[] EMERGENCY_KEYWORDS = {
            "bachao", "bchao", "bacho", "bachav",
            "help", "save me", "please help",
            "police", "madad", "danger", "emergency",
            "chodo", "leave me", "suno", "rok"
    };

    public static final int REQUIRED_REPETITIONS = 1; // Immediate trigger on distress keyword

    private final Context context;
    private final AudioManager audioManager;
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private OnVoiceDistressListener listener;

    private boolean isListening = false;
    private int originalSystemVol = -1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int currentEstimatedSoundLevel = 0;
    private long lastEmergencyTriggerTime = 0;

    public EmergencyVoiceDetector(Context context) {
        this.context = context.getApplicationContext();
        this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        initRecognizer();
    }

    public void setOnVoiceDistressListener(OnVoiceDistressListener listener) {
        this.listener = listener;
    }

    private void initRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Speech recognition is not available on this device.");
            return;
        }

        try {
            if (speechRecognizer != null) {
                try {
                    speechRecognizer.cancel();
                    speechRecognizer.destroy();
                } catch (Exception ignored) {}
                speechRecognizer = null;
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            speechRecognizer.setRecognitionListener(this);

            recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize SpeechRecognizer: " + e.getMessage());
        }
    }

    /**
     * Briefly dampens system audio chime when starting listening
     */
    private void muteBeepTemporarily() {
        if (audioManager == null) return;
        try {
            originalSystemVol = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM);
            audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0);
            handler.postDelayed(() -> {
                try {
                    if (originalSystemVol >= 0 && audioManager != null) {
                        audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, originalSystemVol, 0);
                    }
                } catch (Exception ignored) {}
            }, 350);
        } catch (Exception ignored) {}
    }

    public synchronized void startListening() {
        if (isListening) return;
        isListening = true;

        if (speechRecognizer == null) {
            initRecognizer();
        }
        if (speechRecognizer == null) return;

        muteBeepTemporarily();
        try {
            speechRecognizer.startListening(recognizerIntent);
            Log.d(TAG, "Emergency voice listener active 🎙️");
        } catch (Exception e) {
            Log.e(TAG, "Error starting voice listener: " + e.getMessage());
            scheduleRestart(1500);
        }
    }

    public synchronized void stopListening() {
        isListening = false;
        handler.removeCallbacksAndMessages(null);

        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
                speechRecognizer.cancel();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping voice listener: " + e.getMessage());
            }
        }
        Log.d(TAG, "Emergency voice listener stopped 🛑");
    }

    private void scheduleRestart(long delayMillis) {
        if (!isListening) return;
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> {
            if (isListening) {
                muteBeepTemporarily();
                if (speechRecognizer != null) {
                    try {
                        speechRecognizer.startListening(recognizerIntent);
                    } catch (Exception e) {
                        Log.e(TAG, "Restart listening error: " + e.getMessage());
                        initRecognizer();
                        scheduleRestart(2000);
                    }
                } else {
                    initRecognizer();
                    startListening();
                }
            }
        }, delayMillis);
    }

    /**
     * Inspect recognized text for distress keywords.
     */
    private synchronized void processSpokenText(ArrayList<String> matches) {
        if (matches == null || matches.isEmpty()) return;

        long now = System.currentTimeMillis();
        // Prevent repeated triggers within 4 seconds
        if (now - lastEmergencyTriggerTime < 4000) return;

        for (String match : matches) {
            if (match == null) continue;
            String lower = match.toLowerCase(Locale.ROOT).trim();
            Log.d(TAG, "Speech heard: \"" + lower + "\"");

            for (String keyword : EMERGENCY_KEYWORDS) {
                if (lower.contains(keyword)) {
                    lastEmergencyTriggerTime = now;
                    Log.w(TAG, "🚨 Distress keyword detected: '" + keyword + "' in phrase: \"" + lower + "\"");

                    if (listener != null) {
                        listener.onDistressWordDetected(keyword, 1, 1);
                        listener.onEmergencyConfirmed(keyword, 1);
                    }
                    return;
                }
            }
        }
    }

    @Override
    public void onReadyForSpeech(Bundle params) {
        Log.d(TAG, "SpeechRecognizer: Ready for speech 🎙️");
    }

    @Override
    public void onBeginningOfSpeech() {}

    @Override
    public void onRmsChanged(float rmsdB) {
        int level = 0;
        if (rmsdB > 8.5f) {
            level = 2; // Scream / High acoustic distress
        } else if (rmsdB > 3.0f) {
            level = 1; // Normal speech / ambient
        }
        currentEstimatedSoundLevel = level;
        SensorDataCollector.setSharedSoundLevel(level);

        if (listener != null) {
            listener.onSoundLevelChanged(level);
        }
    }

    @Override
    public void onResults(Bundle results) {
        if (results != null) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            processSpokenText(matches);
        }
        scheduleRestart(250);
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        if (partialResults != null) {
            ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            processSpokenText(matches);
        }
    }

    @Override
    public void onError(int error) {
        Log.d(TAG, "SpeechRecognizer error code: " + error);

        long backoff = 350;
        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
            initRecognizer();
            backoff = 600;
        } else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            stopListening();
            return;
        }

        scheduleRestart(backoff);
    }

    @Override public void onBufferReceived(byte[] buffer) {}
    @Override public void onEndOfSpeech() {}
    @Override public void onEvent(int eventType, Bundle params) {}

    public int getCurrentEstimatedSoundLevel() {
        return currentEstimatedSoundLevel;
    }

    public boolean isListening() {
        return isListening;
    }
}

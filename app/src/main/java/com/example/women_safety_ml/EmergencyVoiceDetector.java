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
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * EmergencyVoiceDetector
 * 
 * Production-ready Distress Voice Keyword Detection for SafeHer:
 * 1. 100% Silent Background Operation: Suppresses the Google Assistant start/stop chime
 *    and system ding by muting streams throughout continuous listening.
 * 2. Multi-Repetition Verification: Requires 2-3 repeated utterances of distress keywords
 *    ("Bachao", "Help", etc.) within a 10-second sliding window before triggering SOS.
 * 3. Operates across every screen of the app.
 * 4. Ambient Decibel Mapping: Passes real-time sound levels to ML (0=quiet, 1=moderate, 2=screaming).
 */
public class EmergencyVoiceDetector implements RecognitionListener {

    private static final String TAG = "EmergencyVoiceDetector";

    public interface OnVoiceDistressListener {
        void onDistressWordDetected(String keyword, int currentCount, int requiredCount);
        void onEmergencyConfirmed(String keyword, int totalCount);
        void onSoundLevelChanged(int soundLevel);
    }

    // Emergency distress keywords in Hindi, Marathi & English
    private static final String[] EMERGENCY_KEYWORDS = {
            "bachao", "help", "save me", "police", "madad", "danger", "emergency", "chodo", "leave me"
    };

    public static final int REQUIRED_REPETITIONS = 2; // Saying "Bachao" 2-3 times triggers emergency
    private static final long SLIDING_WINDOW_MS  = 10_000L; // 10-second sliding window

    private final Context context;
    private final AudioManager audioManager;
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private OnVoiceDistressListener listener;

    private boolean isListening = false;
    private boolean isMutedForBeep = false;
    private int originalSystemVol = -1;
    private int originalNotifVol  = -1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Long> detectionTimestamps = new ArrayList<>();
    private int currentEstimatedSoundLevel = 0;

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
                speechRecognizer.destroy();
            }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            speechRecognizer.setRecognitionListener(this);

            recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());

            // Flags to suppress Google Assistant voice / chime / UI popups
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            recognizerIntent.putExtra("android.speech.extra.DICTATION_MODE", true);
            recognizerIntent.putExtra("android.speech.extras.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", 2500);
            recognizerIntent.putExtra("android.speech.extras.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 1500);
            recognizerIntent.putExtra("android.speech.extras.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 1500);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize SpeechRecognizer: " + e.getMessage());
        }
    }

    /**
     * Completely mute system and notification streams so Google Assistant chime never plays
     */
    private void muteBeepSounds() {
        if (audioManager == null || isMutedForBeep) return;
        try {
            originalSystemVol = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM);
            originalNotifVol  = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0);
                audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0);
            } else {
                audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0);
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0);
            }
            isMutedForBeep = true;
        } catch (Exception e) {
            Log.d(TAG, "Mute volume error: " + e.getMessage());
        }
    }

    /**
     * Restores system streams ONLY when speech recognition is explicitly stopped
     */
    private void restoreBeepSounds() {
        if (audioManager == null || !isMutedForBeep) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0);
                audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0);
            } else {
                if (originalSystemVol >= 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, originalSystemVol, 0);
                }
                if (originalNotifVol >= 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, originalNotifVol, 0);
                }
            }
            isMutedForBeep = false;
        } catch (Exception e) {
            Log.d(TAG, "Restore volume error: " + e.getMessage());
        }
    }

    public synchronized void startListening() {
        if (isListening) return;
        isListening = true;

        if (speechRecognizer == null) {
            initRecognizer();
        }
        if (speechRecognizer == null) return;

        muteBeepSounds();
        try {
            speechRecognizer.startListening(recognizerIntent);
            Log.d(TAG, "Emergency voice listener started silently (No Google Assistant beep) 🎙️");
        } catch (Exception e) {
            Log.e(TAG, "Error starting voice listener: " + e.getMessage());
            scheduleRestart(2500);
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

        // Restore system volume only after stopping completely
        handler.postDelayed(this::restoreBeepSounds, 500);
        Log.d(TAG, "Emergency voice listener stopped 🛑");
    }

    private void scheduleRestart(long delayMillis) {
        if (!isListening) return;
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> {
            if (isListening) {
                muteBeepSounds();
                if (speechRecognizer != null) {
                    try {
                        speechRecognizer.startListening(recognizerIntent);
                    } catch (Exception e) {
                        Log.e(TAG, "Restart listening error: " + e.getMessage());
                        initRecognizer();
                        scheduleRestart(3000);
                    }
                } else {
                    initRecognizer();
                    startListening();
                }
            }
        }, delayMillis);
    }

    /**
     * Inspect recognized text for distress keywords, requiring 2-3 repetitions within a 10s window.
     */
    private synchronized void processSpokenText(ArrayList<String> matches) {
        if (matches == null || matches.isEmpty()) return;

        long now = System.currentTimeMillis();
        pruneExpiredDetections(now);

        for (String match : matches) {
            if (match == null) continue;
            String lower = match.toLowerCase(Locale.ROOT);
            Log.d(TAG, "Heard speech: " + lower);

            for (String keyword : EMERGENCY_KEYWORDS) {
                int occurrences = countOccurrences(lower, keyword);
                if (occurrences > 0) {
                    for (int i = 0; i < occurrences; i++) {
                        detectionTimestamps.add(now);
                    }

                    int totalCount = detectionTimestamps.size();
                    Log.w(TAG, "Distress keyword '" + keyword + "' heard. Total in window: " 
                            + totalCount + " / " + REQUIRED_REPETITIONS);

                    if (totalCount >= REQUIRED_REPETITIONS) {
                        detectionTimestamps.clear();
                        if (listener != null) {
                            listener.onEmergencyConfirmed(keyword, totalCount);
                        }
                    } else {
                        if (listener != null) {
                            listener.onDistressWordDetected(keyword, totalCount, REQUIRED_REPETITIONS);
                        }
                    }
                    return;
                }
            }
        }
    }

    private int countOccurrences(String text, String keyword) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(keyword, idx)) != -1) {
            count++;
            idx += keyword.length();
        }
        return count;
    }

    private void pruneExpiredDetections(long currentTime) {
        Iterator<Long> iter = detectionTimestamps.iterator();
        while (iter.hasNext()) {
            if (currentTime - iter.next() > SLIDING_WINDOW_MS) {
                iter.remove();
            }
        }
    }

    @Override
    public void onReadyForSpeech(Bundle params) {
        // Keep streams muted to ensure zero beep audio cues
    }

    @Override
    public void onBeginningOfSpeech() {
        // Keep streams muted
    }

    @Override
    public void onRmsChanged(float rmsdB) {
        // Map RMS decibels into ML SoundLevel (0=quiet, 1=moderate, 2=loud/scream)
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
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        processSpokenText(matches);
        scheduleRestart(1000);
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        processSpokenText(matches);
    }

    @Override
    public void onError(int error) {
        Log.d(TAG, "SpeechRecognizer status code: " + error);

        long backoff = 1200;
        if (error == SpeechRecognizer.ERROR_AUDIO || error == SpeechRecognizer.ERROR_CLIENT) {
            backoff = 2200;
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

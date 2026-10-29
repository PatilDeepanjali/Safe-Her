package com.example.women_safety_ml;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
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
 * 1. Multi-Repetition Verification: Requires 2-3 repeated utterances of distress keywords
 *    ("Bachao", "Help", etc.) within a 10-second sliding window before triggering SOS,
 *    preventing accidental false alarms from casual conversations or background audio.
 * 2. Silent Operation: Programmatically mutes system and notification streams during
 *    recognizer start to suppress the loud Google Assistant start/stop chime.
 * 3. Graceful Backoff: Non-aggressive retry intervals (1.2s - 2.5s) to prevent audio driver choking.
 * 4. Ambient Sound Integration: Extracts real-time RMS decibels and maps them to ML SoundLevel (0/1/2).
 */
public class EmergencyVoiceDetector implements RecognitionListener {

    private static final String TAG = "EmergencyVoiceDetector";

    public interface OnVoiceDistressListener {
        /**
         * Called when a distress keyword is heard once or twice, but threshold not yet met.
         * @param keyword The detected word (e.g. "bachao")
         * @param currentCount Repetitions heard so far within the temporal window
         * @param requiredCount Total repetitions required to confirm emergency (2)
         */
        void onDistressWordDetected(String keyword, int currentCount, int requiredCount);

        /**
         * Called when the distress keyword has been repeated 2-3 times within the temporal window.
         * @param keyword The confirmed distress keyword
         * @param totalCount Total count of repetitions
         */
        void onEmergencyConfirmed(String keyword, int totalCount);

        /**
         * Real-time acoustic level update (0=quiet, 1=moderate, 2=loud/scream)
         */
        void onSoundLevelChanged(int soundLevel);
    }

    // Emergency distress keywords in Hindi, Marathi & English
    private static final String[] EMERGENCY_KEYWORDS = {
            "bachao", "help", "save me", "police", "madad", "danger", "emergency"
    };

    // Repetition requirements
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
    private int originalMusicVol  = -1;

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
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize SpeechRecognizer: " + e.getMessage());
        }
    }

    /**
     * Temporarily mute device streams so the Google Assistant chime isn't audible on start
     */
    private void muteBeepSounds() {
        if (audioManager == null || isMutedForBeep) return;
        try {
            originalSystemVol = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM);
            originalNotifVol  = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
            originalMusicVol  = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

            audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
            isMutedForBeep = true;
        } catch (Exception e) {
            Log.d(TAG, "Mute volume error: " + e.getMessage());
        }
    }

    /**
     * Restore system audio streams after the recognizer starts listening
     */
    private void restoreBeepSounds() {
        if (audioManager == null || !isMutedForBeep) return;
        try {
            if (originalSystemVol >= 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, originalSystemVol, 0);
            }
            if (originalNotifVol >= 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, originalNotifVol, 0);
            }
            if (originalMusicVol >= 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalMusicVol, 0);
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
            Log.d(TAG, "Emergency voice listener started silently 🎙️");
        } catch (Exception e) {
            Log.e(TAG, "Error starting voice listener: " + e.getMessage());
            restoreBeepSounds();
            scheduleRestart(2500);
        }
    }

    public synchronized void stopListening() {
        isListening = false;
        handler.removeCallbacksAndMessages(null);
        restoreBeepSounds();

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
                muteBeepSounds();
                if (speechRecognizer != null) {
                    try {
                        speechRecognizer.startListening(recognizerIntent);
                    } catch (Exception e) {
                        Log.e(TAG, "Restart listening error: " + e.getMessage());
                        restoreBeepSounds();
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
        // Start beep has finished; restore system volumes safely
        restoreBeepSounds();
    }

    @Override
    public void onBeginningOfSpeech() {
        restoreBeepSounds();
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
        restoreBeepSounds();
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        processSpokenText(matches);
        scheduleRestart(1200);
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        processSpokenText(matches);
    }

    @Override
    public void onError(int error) {
        restoreBeepSounds();
        Log.d(TAG, "SpeechRecognizer status code: " + error);

        long backoff = 1500;
        if (error == SpeechRecognizer.ERROR_AUDIO || error == SpeechRecognizer.ERROR_CLIENT) {
            backoff = 2500;
        } else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            stopListening();
            return;
        }

        scheduleRestart(backoff);
    }

    @Override public void onBufferReceived(byte[] buffer) {}
    @Override public void onEndOfSpeech() { restoreBeepSounds(); }
    @Override public void onEvent(int eventType, Bundle params) {}

    public int getCurrentEstimatedSoundLevel() {
        return currentEstimatedSoundLevel;
    }

    public boolean isListening() {
        return isListening;
    }
}

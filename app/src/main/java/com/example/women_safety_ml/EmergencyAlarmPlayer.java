package com.example.women_safety_ml;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

/**
 * EmergencyAlarmPlayer
 * 
 * Manages loud emergency sirens, pulsing warning beeps ("bippp... bippp..."),
 * and synchronized vibrations when an SOS trigger is activated.
 */
public class EmergencyAlarmPlayer {

    private static final String TAG = "EmergencyAlarmPlayer";
    private static EmergencyAlarmPlayer instance;

    private final Context context;
    private final AudioManager audioManager;
    private final Vibrator vibrator;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ToneGenerator toneGenerator;
    private MediaPlayer mediaPlayer;
    private boolean isPlaying = false;
    private Runnable beepCycleRunnable;

    public static synchronized EmergencyAlarmPlayer getInstance(Context context) {
        if (instance == null) {
            instance = new EmergencyAlarmPlayer(context.getApplicationContext());
        }
        return instance;
    }

    private EmergencyAlarmPlayer(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    /**
     * Starts the emergency "bippp" siren alarm sound and pulsing vibration.
     */
    public synchronized void startAlarm() {
        if (isPlaying) return;
        isPlaying = true;

        Log.w(TAG, "🚨 Starting Emergency SOS Alarm & Piercing Beeps!");

        // 1. Boost ALARM stream volume so it is clearly audible in emergency
        try {
            if (audioManager != null) {
                int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                int targetVol = Math.max((int)(maxVol * 0.85), 1);
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVol, 0);
            }
        } catch (Exception e) {
            Log.d(TAG, "Volume boost note: " + e.getMessage());
        }

        // 2. Start Pulsing High-Pitch Beep ("Bippp... Bippp...") using ToneGenerator
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            beepCycleRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!isPlaying) return;
                    try {
                        if (toneGenerator != null) {
                            // Loud emergency beep: 280ms duration
                            toneGenerator.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 280);
                        }
                    } catch (Exception ignored) {}
                    // Pulse every 500ms for urgent warning effect
                    handler.postDelayed(this, 500);
                }
            };
            handler.post(beepCycleRunnable);
        } catch (Exception e) {
            Log.e(TAG, "ToneGenerator error, falling back to system alarm: " + e.getMessage());
        }

        // 3. Concurrently play System Alarm ringtone in looping mode for rich siren
        try {
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            if (alarmUri != null) {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(context, alarmUri);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
                } else {
                    mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
                }
                mediaPlayer.setLooping(true);
                mediaPlayer.prepare();
                mediaPlayer.start();
            }
        } catch (Exception e) {
            Log.d(TAG, "MediaPlayer alarm fallback note: " + e.getMessage());
        }

        // 4. Start synchronized SOS vibration pattern
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = {0, 450, 150, 450, 150, 450, 400};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0)); // 0 = repeat indefinitely
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Vibration note: " + e.getMessage());
        }
    }

    /**
     * Instantly silences the alarm, beeps, and stops vibration.
     */
    public synchronized void stopAlarm() {
        if (!isPlaying) return;
        isPlaying = false;

        Log.d(TAG, "Silencing Emergency SOS Alarm 🔇");

        handler.removeCallbacksAndMessages(null);

        // Stop Tone Generator
        if (toneGenerator != null) {
            try {
                toneGenerator.stopTone();
                toneGenerator.release();
            } catch (Exception ignored) {}
            toneGenerator = null;
        }

        // Stop Media Player
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }

        // Stop Vibration
        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (Exception ignored) {}
        }
    }

    public synchronized boolean isPlaying() {
        return isPlaying;
    }
}


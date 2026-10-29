package com.example.women_safety_ml;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaRecorder;
import android.util.Log;

/**
 * Collects real sensor data:
 * - Accelerometer  → Acceleration level (0/1/2) with peak latching
 * - Microphone     → Sound level       (0/1/2)
 * - Immediate Shake Detection callback for real-time emergencies
 */
public class SensorDataCollector implements SensorEventListener {

    private static final String TAG = "SensorDataCollector";

    // Acceleration thresholds (m/s²)
    private static final float ACCEL_LOW    = 3.0f;
    private static final float ACCEL_HIGH   = 10.0f;
    private static final float ACCEL_SHAKE  = 13.5f; // Vigorous shake threshold

    // Sound thresholds (amplitude)
    private static final double SOUND_LOW   = 1000;
    private static final double SOUND_HIGH  = 5000;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private MediaRecorder mediaRecorder;
    private Context context;

    private float currentAcceleration = 0f;
    private float peakAcceleration    = 0f;
    private double currentSoundLevel  = 0;
    private double peakSoundLevel     = 0;
    private boolean isRecording = false;
    private boolean isAudioMeterEnabled = true;

    // Shared sound level provided by EmergencyVoiceDetector or real-time acoustic pipeline
    private static volatile int sharedSoundLevel = 0;

    public static void setSharedSoundLevel(int level) {
        sharedSoundLevel = level;
    }

    public void setAudioMeterEnabled(boolean enabled) {
        this.isAudioMeterEnabled = enabled;
        if (!enabled) {
            stopSoundMeter();
        }
    }

    // Shake detection debounce
    private long lastShakeTimestamp = 0;
    private int shakeCount = 0;

    public interface OnShakeListener {
        void onShakeDetected();
    }
    private OnShakeListener shakeListener;

    public void setOnShakeListener(OnShakeListener listener) {
        this.shakeListener = listener;
    }

    public SensorDataCollector(Context context) {
        this.context = context;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    /** Start listening to sensors */
    public void start() {
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer,
                    SensorManager.SENSOR_DELAY_UI);
        }
        startSoundMeter();
    }

    /** Stop listening to sensors */
    public void stop() {
        sensorManager.unregisterListener(this);
        stopSoundMeter();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            // Calculate total acceleration magnitude minus gravity
            currentAcceleration = (float) Math.sqrt(x * x + y * y + z * z) - 9.8f;
            if (currentAcceleration < 0) currentAcceleration = 0;

            // Retain peak acceleration in the current evaluation window
            if (currentAcceleration > peakAcceleration) {
                peakAcceleration = currentAcceleration;
            }

            // Real-time shake detection
            checkImmediateShake(currentAcceleration);
        }
    }

    private void checkImmediateShake(float accel) {
        long now = System.currentTimeMillis();
        if (accel > ACCEL_SHAKE) {
            if (now - lastShakeTimestamp > 300 && now - lastShakeTimestamp < 1500) {
                shakeCount++;
                if (shakeCount >= 2) {
                    Log.w(TAG, "🚨 IMMEDIATE SHAKE DETECTED! Accel: " + accel);
                    shakeCount = 0;
                    if (shakeListener != null) {
                        shakeListener.onShakeDetected();
                    }
                }
            } else if (now - lastShakeTimestamp >= 1500) {
                shakeCount = 1;
            }
            lastShakeTimestamp = now;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void startSoundMeter() {
        if (!isAudioMeterEnabled) {
            Log.d(TAG, "MediaRecorder disabled — using shared acoustic RMS pipeline");
            isRecording = false;
            return;
        }
        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO permission not granted — skipping sound meter");
            isRecording = false;
            return;
        }
        try {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile(context.getCacheDir() + "/sound_temp.3gp");
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            Log.d(TAG, "Sound meter started ✅");
        } catch (Exception e) {
            Log.e(TAG, "Sound meter error: " + e.getMessage());
            isRecording = false;
        }
    }

    private void stopSoundMeter() {
        if (mediaRecorder != null && isRecording) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
            } catch (Exception e) {
                Log.e(TAG, "Stop sound error: " + e.getMessage());
            }
            mediaRecorder = null;
            isRecording = false;
        }
    }

    private void updateSoundLevel() {
        if (mediaRecorder != null && isRecording) {
            currentSoundLevel = mediaRecorder.getMaxAmplitude();
            if (currentSoundLevel > peakSoundLevel) {
                peakSoundLevel = currentSoundLevel;
            }
        }
    }

    // ── GETTERS — return 0/1/2 scale for ML model ──

    /** 0=calm, 1=moderate movement, 2=high movement */
    public int getAccelerationLevel() {
        float effectiveAccel = Math.max(currentAcceleration, peakAcceleration);
        // Reset peak for the next evaluation window
        peakAcceleration = currentAcceleration;

        if (effectiveAccel < ACCEL_LOW)  return 0;
        if (effectiveAccel < ACCEL_HIGH) return 1;
        return 2;
    }

    /** 0=quiet, 1=moderate, 2=loud/noisy */
    public int getSoundLevel() {
        if (sharedSoundLevel > 0) {
            int lvl = sharedSoundLevel;
            sharedSoundLevel = 0; // reset latch
            return lvl;
        }
        if (mediaRecorder != null && isRecording) {
            updateSoundLevel();
            double effectiveSound = Math.max(currentSoundLevel, peakSoundLevel);
            peakSoundLevel = currentSoundLevel;

            if (effectiveSound < SOUND_LOW)  return 0;
            if (effectiveSound < SOUND_HIGH) return 1;
            return 2;
        }
        return 0;
    }

    /** Raw acceleration value for debugging */
    public float getRawAcceleration() { return currentAcceleration; }

    /** Raw sound amplitude for debugging */
    public double getRawSound() { return currentSoundLevel; }
}
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
 * - Accelerometer  → Acceleration level (0/1/2)
 * - Microphone     → Sound level       (0/1/2)
 */
public class SensorDataCollector implements SensorEventListener {

    private static final String TAG = "SensorDataCollector";

    // Acceleration thresholds (m/s²)
    private static final float ACCEL_LOW    = 3.0f;
    private static final float ACCEL_HIGH   = 12.0f;

    // Sound thresholds (amplitude)
    private static final double SOUND_LOW   = 1000;
    private static final double SOUND_HIGH  = 5000;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private MediaRecorder mediaRecorder;
    private Context context;

    private float currentAcceleration = 0f;
    private double currentSoundLevel  = 0;
    private boolean isRecording = false;

    public SensorDataCollector(Context context) {
        this.context = context;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    /** Start listening to sensors */
    public void start() {
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL);
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
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void startSoundMeter() {
        // Check permission before starting microphone
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
        }
    }

    // ── GETTERS — return 0/1/2 scale for ML model ──

    /** 0=calm, 1=moderate movement, 2=high movement */
    public int getAccelerationLevel() {
        if (currentAcceleration < ACCEL_LOW)  return 0;
        if (currentAcceleration < ACCEL_HIGH) return 1;
        return 2;
    }

    /** 0=quiet, 1=moderate, 2=loud/noisy */
    public int getSoundLevel() {
        updateSoundLevel();
        if (currentSoundLevel < SOUND_LOW)  return 0;
        if (currentSoundLevel < SOUND_HIGH) return 1;
        return 2;
    }

    /** Raw acceleration value for debugging */
    public float getRawAcceleration() { return currentAcceleration; }

    /** Raw sound amplitude for debugging */
    public double getRawSound() { return currentSoundLevel; }
}
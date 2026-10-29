package com.example.women_safety_ml;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * AudioEvidenceRecorder
 * 
 * Objective 5: Automatic SOS & Forensic Evidence Recording.
 * Upon emergency trigger, silently captures a 15-second acoustic evidence snippet
 * in 3GP AMR-NB format and saves it to internal app storage.
 * Records forensic metadata (timestamp, GPS coordinates, file length) into Firestore.
 */
public class AudioEvidenceRecorder {

    private static final String TAG = "AudioEvidenceRecorder";
    private static final int EVIDENCE_DURATION_MS = 15_000; // 15 seconds

    public interface OnEvidenceRecordedListener {
        void onEvidenceReady(File evidenceFile);
        void onEvidenceFailed(String error);
    }

    private final Context context;
    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private final Handler stopHandler = new Handler(Looper.getMainLooper());
    private File currentEvidenceFile;

    public AudioEvidenceRecorder(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Start recording 15 seconds of emergency audio evidence
     */
    public synchronized void startRecordingEvidence(double lat, double lng, OnEvidenceRecordedListener listener) {
        if (isRecording) {
            Log.w(TAG, "Audio evidence recording already in progress");
            return;
        }

        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot record evidence: RECORD_AUDIO permission missing");
            if (listener != null) listener.onEvidenceFailed("RECORD_AUDIO permission missing");
            return;
        }

        try {
            File evidenceDir = new File(context.getFilesDir(), "evidence");
            if (!evidenceDir.exists()) {
                evidenceDir.mkdirs();
            }

            long timestamp = System.currentTimeMillis();
            currentEvidenceFile = new File(evidenceDir, "evidence_" + timestamp + ".3gp");

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile(currentEvidenceFile.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            Log.d(TAG, "Forensic audio evidence recording started: " + currentEvidenceFile.getName());

            // Automatically stop after 15 seconds
            stopHandler.postDelayed(() -> {
                stopRecordingEvidence(lat, lng, listener);
            }, EVIDENCE_DURATION_MS);

        } catch (Exception e) {
            Log.e(TAG, "Failed to start audio evidence recording: " + e.getMessage());
            isRecording = false;
            if (listener != null) listener.onEvidenceFailed(e.getMessage());
        }
    }

    /**
     * Stop recording and log metadata to Firestore
     */
    public synchronized void stopRecordingEvidence(double lat, double lng, OnEvidenceRecordedListener listener) {
        if (!isRecording) return;
        isRecording = false;
        stopHandler.removeCallbacksAndMessages(null);

        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
            }
            Log.d(TAG, "Audio evidence captured successfully: " + currentEvidenceFile.getAbsolutePath());

            // Save evidence metadata to Firestore
            saveEvidenceMetadata(currentEvidenceFile, lat, lng);

            if (listener != null && currentEvidenceFile != null && currentEvidenceFile.exists()) {
                listener.onEvidenceReady(currentEvidenceFile);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping audio evidence recorder: " + e.getMessage());
            if (listener != null) listener.onEvidenceFailed(e.getMessage());
        }
    }

    private void saveEvidenceMetadata(File file, double lat, double lng) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null || file == null) return;

        String uid = auth.getCurrentUser().getUid();
        Map<String, Object> meta = new HashMap<>();
        meta.put("fileName", file.getName());
        meta.put("filePath", file.getAbsolutePath());
        meta.put("fileSizeBytes", file.length());
        meta.put("durationSeconds", 15);
        meta.put("timestamp", System.currentTimeMillis());
        meta.put("latitude", lat);
        meta.put("longitude", lng);

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("audio_evidence")
                .add(meta)
                .addOnSuccessListener(ref -> Log.d(TAG, "Evidence metadata recorded in Firestore: " + ref.getId()))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to save evidence metadata: " + e.getMessage()));
    }
}

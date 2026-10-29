package com.example.women_safety_ml;

import android.location.Location;
import java.util.Calendar;

/**
 * Converts real-world data into the 0/1/2 scale that the ML model expects,
 * and provides an on-device offline fallback engine when the cloud server is unreachable.
 */
public class SafetyDataHelper {

    // Speed thresholds (m/s) — (1 m/s = 3.6 km/h)
    private static final float SPEED_STATIONARY = 0.5f; // Standing / stationary (< 1.8 km/h)
    private static final float SPEED_WALKING    = 2.2f; // Normal walking (~8 km/h)
    // Speed >= 2.2 m/s is running or vehicular movement

    /**
     * 1. Dynamic Speed level from GPS Location
     * 0 = Stationary / slow walk (< 0.5 m/s)
     * 1 = Normal walking / moving (0.5 to 2.2 m/s)
     * 2 = Running / sudden high speed (> 2.2 m/s)
     */
    public static int getSpeedLevel(Location location) {
        if (location == null) return 0;
        float speed = location.hasSpeed() ? location.getSpeed() : 0.0f;
        if (speed < SPEED_STATIONARY) return 0;
        if (speed < SPEED_WALKING)    return 1;
        return 2;
    }

    /**
     * 2. Dynamic Time of Day level from System Clock
     * 0 = Daytime (6:00 AM – 6:00 PM)  → Safe
     * 1 = Evening (6:00 PM – 10:00 PM) → Moderate risk
     * 2 = Late Night (10:00 PM – 6:00 AM) → High risk
     */
    public static int getTimeLevel() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 6 && hour < 18)  return 0; // Daytime
        if (hour >= 18 && hour < 22) return 1; // Evening
        return 2;                              // Late Night
    }

    /**
     * 3. Dynamic Heart Rate Estimation from Movement & Environmental Stress
     * 72 bpm  = Normal resting rate
     * 90 bpm  = Walking rate
     * 110 bpm = Elevated exertion or stress
     * 140 bpm = Emergency sprint / panic rate
     */
    public static int getHeartRateLevel(int speedLevel, int timeLevel) {
        if (speedLevel == 2 && timeLevel == 2) return 140; // Running late at night
        if (speedLevel == 2 || timeLevel == 2) return 110; // High movement or late night
        if (speedLevel == 1) return 90;                    // Normal walking
        return 72;                                         // Resting
    }

    /**
     * 4. Dynamic Location Type / Zone Risk level
     * 0 = Safe (Daytime + stationary/walking)
     * 1 = Moderate (Evening or sudden transit)
     * 2 = Risky (Late night movement)
     */
    public static int getLocationTypeLevel(int speedLevel, int timeLevel) {
        if (timeLevel == 2 && speedLevel >= 1) return 2; // Moving late at night
        if (timeLevel == 1 || speedLevel == 2) return 1; // Evening or sudden movement
        return 0;                                        // Daytime safe
    }

    /**
     * 5. On-Device Fallback Safety Engine
     * Evaluates danger rules directly on-device if the Flask API is offline or unreachable.
     * Prevents the app from failing silently when offline.
     */
    public static PredictionResponse evaluateOfflineSafety(int speed, int hr, int time,
                                                           int accel, int locType, int sound) {
        // Condition 1: Vigorous shaking/struggle accompanied by loud noise/screaming
        boolean struggleAndSound = (accel == 2 && sound >= 1);

        // Condition 2: High physical struggle or running late at night
        boolean struggleAtNight = (accel == 2 && time == 2);

        // Condition 3: Loud scream/spikes in quiet night
        boolean screamAtNight = (sound == 2 && time == 2);

        // Condition 4: Rapid running at late night with high exertion
        boolean runningAtNight = (speed == 2 && time == 2 && hr >= 130);

        boolean isUnsafe = struggleAndSound || struggleAtNight || screamAtNight || runningAtNight;

        int prediction = isUnsafe ? 1 : 0;
        String status = isUnsafe ? "Unsafe" : "Safe";
        double unsafeProbability = isUnsafe ? 89.5 : 12.0;

        return new PredictionResponse(prediction, status, unsafeProbability);
    }

    /**
     * Build a human-readable summary of the safety assessment
     */
    public static String getRiskSummary(int speed, int time, int accel, int sound) {
        StringBuilder sb = new StringBuilder();
        if (time == 2)   sb.append("🌙 Late Night  ");
        if (speed == 2)  sb.append("🏃 High Speed  ");
        if (accel == 2)  sb.append("📳 Shaking/Struggle  ");
        if (sound == 2)  sb.append("🔊 Loud Noise/Scream  ");
        return sb.length() > 0 ? sb.toString() : "✅ Normal conditions";
    }
}
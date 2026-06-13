package com.example.women_safety_ml;

import android.location.Location;
import java.util.Calendar;

/**
 * Converts real-world data into the 0/1/2 scale
 * that the ML model expects.
 */
public class SafetyDataHelper {

    // Speed thresholds (m/s)
    private static final float SPEED_LOW  = 1.5f;  // walking slow
    private static final float SPEED_HIGH = 5.0f;  // running

    /**
     * Speed level from GPS
     * 0 = stationary/slow, 1 = walking, 2 = running/fast
     */
    public static int getSpeedLevel(Location location) {
//        if (location == null) return 0;
//        float speed = location.getSpeed(); // m/s
//        if (speed < SPEED_LOW)  return 0;
//        if (speed < SPEED_HIGH) return 1;
//        return 2;
        return 1;
    }

    /**
     * Time of day level
     * 0 = daytime (6AM–6PM) = safe
     * 1 = evening (6PM–9PM) = moderate
     * 2 = night   (9PM–6AM) = risky
     */
    public static int getTimeLevel() {
//        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
//        if (hour >= 6 && hour < 18)  return 0; // day
//        if (hour >= 18 && hour < 21) return 1; // evening
//        return 2;                               // night

        return 2;
    }

    /**
     * Heart rate level — estimated from activity
     * (Real heart rate needs wearable; we estimate from movement)
     * 0 = resting (low speed + day)
     * 1 = moderate
     * 2 = high (running at night)
     */
    public static int getHeartRateLevel(int speedLevel, int timeLevel) {
        if (speedLevel == 2 && timeLevel == 2) return 140; // running at night → high HR
        if (speedLevel == 1 || timeLevel == 1) return 90;  // moderate
        return 70;                                          // resting
    }

    /**
     * Location type from GPS coordinates
     * Uses time as proxy (can be enhanced with Places API later)
     * 0 = safe area (day + slow)
     * 1 = moderate
     * 2 = risky (night + fast movement)
     */
    public static int getLocationTypeLevel(int speedLevel, int timeLevel) {
//        int riskScore = speedLevel + timeLevel;
//        if (riskScore <= 1) return 0;
//        if (riskScore == 2) return 1;
//        return 2;
        return 2;
    }

    /**
     * Build a human-readable summary of the safety assessment
     */
    public static String getRiskSummary(int speed, int time, int accel, int sound) {
        StringBuilder sb = new StringBuilder();
        if (time == 2)   sb.append("🌙 Night time  ");
        if (speed == 2)  sb.append("🏃 Moving fast  ");
        if (accel == 2)  sb.append("📳 High movement  ");
        if (sound == 2)  sb.append("🔊 Loud environment  ");
        return sb.length() > 0 ? sb.toString() : "✅ Normal conditions";
    }
}
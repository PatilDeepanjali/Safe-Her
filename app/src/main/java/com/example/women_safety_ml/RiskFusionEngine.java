package com.example.women_safety_ml;

import android.location.Location;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * RiskFusionEngine
 * 
 * Objective 4: Real-Time Multi-Modal Risk Fusion & Hysteresis Layer
 * 
 * Computes:
 *   Risk_raw = w1*sound + w2*motion + w3*location + w4*time + w5*route_deviation
 *   Risk_ema = alpha * Risk_raw + (1 - alpha) * Risk_ema_prev
 * 
 * Features:
 * 1. Exponential Moving Average (EMA) smoothing to prevent false-alarm single spikes.
 * 2. Separate entry (>= 0.70) and exit (< 0.40) hysteresis thresholds.
 * 3. Requires 2-3 consecutive windows in HIGH risk state before activating emergency SOS.
 * 4. Free top-3 contributor explainability (identifying highest risk drivers).
 * 5. Point-to-segment route deviation tracking (> 100m off planned route for > 30s).
 */
public class RiskFusionEngine {

    private static final String TAG = "RiskFusionEngine";
    private static RiskFusionEngine instance;

    public static synchronized RiskFusionEngine getInstance() {
        if (instance == null) {
            instance = new RiskFusionEngine();
        }
        return instance;
    }

    // ── Weights (Sum = 1.00) ──
    private static final double W_SOUND     = 0.30;
    private static final double W_MOTION    = 0.30;
    private static final double W_LOCATION  = 0.15;
    private static final double W_TIME      = 0.15;
    private static final double W_DEVIATION = 0.10;

    // ── EMA Smoothing Factor ──
    private static final double EMA_ALPHA   = 0.40;

    // ── Hysteresis Thresholds ──
    public static final double THRESHOLD_HIGH_ENTER = 0.70;
    public static final double THRESHOLD_HIGH_EXIT  = 0.40;
    public static final double THRESHOLD_CAUTION    = 0.40;

    // ── Consecutive Window Requirement ──
    private static final int REQUIRED_CONSECUTIVE_HIGH_WINDOWS = 2;

    public enum RiskState {
        SAFE,
        CAUTION,
        HIGH_RISK
    }

    public static class Contributor {
        public final String name;
        public final double weightedScore;
        public final double rawScore;

        public Contributor(String name, double weightedScore, double rawScore) {
            this.name = name;
            this.weightedScore = weightedScore;
            this.rawScore = rawScore;
        }
    }

    public static class FusionResult {
        public final double rawRisk;
        public final double emaRisk;
        public final RiskState state;
        public final boolean shouldTriggerAlert;
        public final int consecutiveHighCount;
        public final List<String> topContributors;
        public final String summary;

        public FusionResult(double rawRisk, double emaRisk, RiskState state,
                            boolean shouldTriggerAlert, int consecutiveHighCount,
                            List<String> topContributors, String summary) {
            this.rawRisk = rawRisk;
            this.emaRisk = emaRisk;
            this.state = state;
            this.shouldTriggerAlert = shouldTriggerAlert;
            this.consecutiveHighCount = consecutiveHighCount;
            this.topContributors = topContributors;
            this.summary = summary;
        }
    }

    // ── State Variables ──
    private double currentEmaRisk = 0.0;
    private RiskState currentState = RiskState.SAFE;
    private int consecutiveHighWindows = 0;

    // Route deviation tracking state
    private long offRouteStartTime = 0;
    private boolean isCurrentlyOffRoute = false;

    public synchronized FusionResult evaluateRisk(double soundScore,
                                                  double motionScore,
                                                  double locationScore,
                                                  double timeScore,
                                                  double deviationScore) {

        // Clamp inputs to [0.0, 1.0]
        soundScore     = clamp(soundScore);
        motionScore    = clamp(motionScore);
        locationScore  = clamp(locationScore);
        timeScore      = clamp(timeScore);
        deviationScore = clamp(deviationScore);

        // 1. Compute raw linear weighted risk
        double rawRisk = (W_SOUND * soundScore)
                       + (W_MOTION * motionScore)
                       + (W_LOCATION * locationScore)
                       + (W_TIME * timeScore)
                       + (W_DEVIATION * deviationScore);

        // 2. Exponential Moving Average (EMA) smoothing
        if (currentEmaRisk == 0.0) {
            currentEmaRisk = rawRisk;
        } else {
            currentEmaRisk = (EMA_ALPHA * rawRisk) + ((1.0 - EMA_ALPHA) * currentEmaRisk);
        }

        // 3. Hysteresis State Machine
        boolean shouldTrigger = false;

        if (currentState == RiskState.HIGH_RISK) {
            if (currentEmaRisk < THRESHOLD_HIGH_EXIT) {
                currentState = (currentEmaRisk >= THRESHOLD_CAUTION) ? RiskState.CAUTION : RiskState.SAFE;
                consecutiveHighWindows = 0;
            } else {
                consecutiveHighWindows++;
                shouldTrigger = (consecutiveHighWindows >= REQUIRED_CONSECUTIVE_HIGH_WINDOWS);
            }
        } else {
            if (currentEmaRisk >= THRESHOLD_HIGH_ENTER) {
                currentState = RiskState.HIGH_RISK;
                consecutiveHighWindows = 1;
                shouldTrigger = (consecutiveHighWindows >= REQUIRED_CONSECUTIVE_HIGH_WINDOWS);
            } else if (currentEmaRisk >= THRESHOLD_CAUTION) {
                currentState = RiskState.CAUTION;
                consecutiveHighWindows = 0;
            } else {
                currentState = RiskState.SAFE;
                consecutiveHighWindows = 0;
            }
        }

        // 4. Free Explainability: Extract Top-3 Contributors
        List<Contributor> contributors = new ArrayList<>();
        contributors.add(new Contributor("Acoustic Distress", W_SOUND * soundScore, soundScore));
        contributors.add(new Contributor("Abnormal Motion", W_MOTION * motionScore, motionScore));
        contributors.add(new Contributor("Location Exposure", W_LOCATION * locationScore, locationScore));
        contributors.add(new Contributor("Temporal Risk (Night)", W_TIME * timeScore, timeScore));
        contributors.add(new Contributor("Route Deviation", W_DEVIATION * deviationScore, deviationScore));

        Collections.sort(contributors, (c1, c2) -> Double.compare(c2.weightedScore, c1.weightedScore));

        List<String> topContributors = new ArrayList<>();
        for (int i = 0; i < Math.min(3, contributors.size()); i++) {
            Contributor c = contributors.get(i);
            if (c.weightedScore > 0.02) {
                topContributors.add(String.format("%s (%.0f%% weight)", c.name, c.weightedScore * 100));
            }
        }

        String summary = buildSummary(topContributors);

        Log.d(TAG, String.format("Fusion: Raw=%.2f, EMA=%.2f, State=%s, Consecutive=%d, Trigger=%b",
                rawRisk, currentEmaRisk, currentState.name(), consecutiveHighWindows, shouldTrigger));

        return new FusionResult(rawRisk, currentEmaRisk, currentState,
                shouldTrigger, consecutiveHighWindows, topContributors, summary);
    }

    /**
     * Set route deviation manually / via simulation
     */
    public synchronized double updateRouteDeviation(double deviationMeters) {
        if (deviationMeters > 100.0) {
            isCurrentlyOffRoute = true;
            offRouteStartTime = System.currentTimeMillis();
            Log.w(TAG, "🚨 Deviation updated: " + (int) deviationMeters + "m off safe path");
            return 1.0;
        } else {
            isCurrentlyOffRoute = false;
            offRouteStartTime = 0;
            return 0.0;
        }
    }

    /**
     * Compute route deviation score (1.0 if > 100m off planned path for > 30s, else 0.0)
     */
    public synchronized double updateRouteDeviation(Location userLocation, List<Location> plannedRoutePoints) {
        if (userLocation == null || plannedRoutePoints == null || plannedRoutePoints.size() < 2) {
            return 0.0;
        }

        double minDistanceMeters = Double.MAX_VALUE;
        for (int i = 0; i < plannedRoutePoints.size() - 1; i++) {
            Location p1 = plannedRoutePoints.get(i);
            Location p2 = plannedRoutePoints.get(i + 1);
            double dist = distanceToSegment(userLocation, p1, p2);
            if (dist < minDistanceMeters) {
                minDistanceMeters = dist;
            }
        }

        long now = System.currentTimeMillis();
        if (minDistanceMeters > 100.0) {
            if (!isCurrentlyOffRoute) {
                isCurrentlyOffRoute = true;
                offRouteStartTime = now;
            }
            if (now - offRouteStartTime > 30_000L) { // > 30 seconds
                Log.w(TAG, "🚨 Route Deviation Confirmed: " + (int) minDistanceMeters + "m off path for >30s");
                return 1.0;
            }
        } else {
            isCurrentlyOffRoute = false;
            offRouteStartTime = 0;
        }

        return 0.0;
    }

    private double distanceToSegment(Location p, Location a, Location b) {
        double dLat = b.getLatitude() - a.getLatitude();
        double dLng = b.getLongitude() - a.getLongitude();
        if (dLat == 0 && dLng == 0) {
            return p.distanceTo(a);
        }

        double t = ((p.getLatitude() - a.getLatitude()) * dLat + (p.getLongitude() - a.getLongitude()) * dLng)
                / (dLat * dLat + dLng * dLng);
        t = Math.max(0, Math.min(1, t));

        Location projection = new Location("");
        projection.setLatitude(a.getLatitude() + t * dLat);
        projection.setLongitude(a.getLongitude() + t * dLng);
        return p.distanceTo(projection);
    }

    private String buildSummary(List<String> topContributors) {
        if (topContributors.isEmpty()) return "All monitored signals within safe parameters.";
        StringBuilder sb = new StringBuilder("Elevated Factors: ");
        for (int i = 0; i < topContributors.size(); i++) {
            sb.append(topContributors.get(i));
            if (i < topContributors.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }

    public synchronized void reset() {
        currentEmaRisk = 0.0;
        currentState = RiskState.SAFE;
        consecutiveHighWindows = 0;
        offRouteStartTime = 0;
        isCurrentlyOffRoute = false;
    }

    private double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    public double getCurrentEmaRisk() {
        return currentEmaRisk;
    }

    public RiskState getCurrentState() {
        return currentState;
    }
}

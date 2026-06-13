package com.example.women_safety_ml;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

/**
 * LiveLocationTracker
 * - Uploads real GPS location to Firebase Realtime Database every 10 seconds
 * - Emergency contacts open a web link to see live moving location
 */
public class LiveLocationTracker {

    private static final String TAG           = "LiveTracker";
    private static final long   UPDATE_MS     = 10_000; // every 10 seconds
    private static final long   FASTEST_MS    = 5_000;
    private static final String DB_URL        =
            "https://safeher-2f27d-default-rtdb.asia-southeast1.firebasedatabase.app/";

    private final Context context;
    private final FusedLocationProviderClient fusedClient;
    private final DatabaseReference locationRef;
    private final String userId;

    private LocationCallback locationCallback;
    private boolean isTracking = false;

    public interface TrackingCallback {
        void onLocationUpdated(double lat, double lng, String trackingUrl);
        void onTrackingFailed(String error);
    }

    public LiveLocationTracker(Context context) {
        this.context     = context;
        this.fusedClient = LocationServices.getFusedLocationProviderClient(context);

        FirebaseAuth auth = FirebaseAuth.getInstance();
        this.userId = auth.getCurrentUser() != null
                ? auth.getCurrentUser().getUid() : "unknown";

        // Reference: liveLocations/{userId}/
        FirebaseDatabase database = FirebaseDatabase.getInstance(DB_URL);
        this.locationRef = database.getReference("liveLocations").child(userId);
    }

    /** Start uploading live location to Firebase */
    public void startTracking(TrackingCallback callback) {
        if (isTracking) return;

        if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            if (callback != null)
                callback.onTrackingFailed("Location permission not granted");
            return;
        }

        // Mark tracking as active in database
        locationRef.child("active").setValue(true);
        locationRef.child("userId").setValue(userId);
        locationRef.child("startTime").setValue(System.currentTimeMillis());

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null) return;
                android.location.Location loc = result.getLastLocation();
                if (loc == null) return;

                double lat = loc.getLatitude();
                double lng = loc.getLongitude();

                // Upload to Firebase Realtime Database
                Map<String, Object> locationData = new HashMap<>();
                locationData.put("lat",       lat);
                locationData.put("lng",       lng);
                locationData.put("speed",     loc.getSpeed());
                locationData.put("accuracy",  loc.getAccuracy());
                locationData.put("timestamp", System.currentTimeMillis());
                locationData.put("active",    true);

                locationRef.updateChildren(locationData)
                        .addOnSuccessListener(a ->
                                Log.d(TAG, "Location uploaded: " + lat + ", " + lng))
                        .addOnFailureListener(e ->
                                Log.e(TAG, "Upload failed: " + e.getMessage()));

                // Build tracking URL for SMS
                String trackingUrl = "https://safeher-2f27d-default-rtdb.asia-southeast1.firebasedatabase.app/liveLocations/" + userId + ".json";
                String mapsUrl = "https://maps.google.com/?q=" + lat + "," + lng;

                if (callback != null)
                    callback.onLocationUpdated(lat, lng, mapsUrl);
            }
        };

        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, UPDATE_MS)
                .setMinUpdateIntervalMillis(FASTEST_MS)
                .build();

        fusedClient.requestLocationUpdates(
                request, locationCallback, Looper.getMainLooper());

        isTracking = true;
        Log.d(TAG, "Live tracking started for user: " + userId);
    }

    /** Stop uploading and mark as inactive */
    public void stopTracking() {
        if (!isTracking) return;
        if (locationCallback != null) {
            fusedClient.removeLocationUpdates(locationCallback);
        }
        locationRef.child("active").setValue(false);
        isTracking = false;
        Log.d(TAG, "Live tracking stopped");
    }

    /** Get the live tracking URL to share — opens map page for emergency contacts */
    public String getTrackingUrl() {
        return "https://safeher-2f27d.web.app/live_tracking.html?uid=" + userId;
    }

    public boolean isTracking() { return isTracking; }
}
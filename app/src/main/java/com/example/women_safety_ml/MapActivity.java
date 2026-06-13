package com.example.women_safety_ml;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.List;
import java.util.Locale;

public class MapActivity extends BaseActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_CODE = 300;
    private static final long UPDATE_INTERVAL    = 5000;  // 5 seconds
    private static final long FASTEST_INTERVAL   = 3000;  // 3 seconds

    private Button btnStartStop, btnSafeRoute, btnShareLocation;
    private TextView tvTrackingStatus, tvCurrentAddress;
    private GoogleMap mMap;
    private Marker currentMarker;
    private boolean isTracking = false;
    private LiveLocationTracker liveTracker;

    // Real GPS
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private double currentLat = 0, currentLng = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_map);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        liveTracker = new LiveLocationTracker(this);

        initViews();
        initGoogleMap();
        setupLocationCallback();
        setListeners();
        setupBottomNav(R.id.tabMap);
        requestLocationPermission();
    }

    private void initViews() {
        btnStartStop      = findViewById(R.id.btnStartStop);
        btnSafeRoute      = findViewById(R.id.btnSafeRoute);
        btnShareLocation  = findViewById(R.id.btnShareLocation);
        tvTrackingStatus  = findViewById(R.id.tvTrackingStatus);
        tvCurrentAddress  = findViewById(R.id.tvCurrentLocation);
    }

    private void initGoogleMap() {
        SupportMapFragment mapFragment = SupportMapFragment.newInstance();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.mapFragmentContainer, mapFragment)
                .commit();
        mapFragment.getMapAsync(this);
    }

    // ── MAP READY ──
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setMapToolbarEnabled(true);

        // Enable My Location button if permission granted
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            getLastKnownLocation();
        }
    }

    // ── GET LAST KNOWN LOCATION ──
    private void getLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        currentLat = location.getLatitude();
                        currentLng = location.getLongitude();
                        updateMapLocation(currentLat, currentLng);
                        fetchAddress(currentLat, currentLng);
                    }
                });
    }

    // ── REAL-TIME LOCATION CALLBACK ──
    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null) return;
                android.location.Location location = result.getLastLocation();
                if (location != null) {
                    currentLat = location.getLatitude();
                    currentLng = location.getLongitude();
                    updateMapLocation(currentLat, currentLng);
                    fetchAddress(currentLat, currentLng);
                }
            }
        };
    }

    // ── START REAL-TIME LOCATION UPDATES ──
    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL)
                .setMinUpdateIntervalMillis(FASTEST_INTERVAL)
                .build();

        fusedLocationClient.requestLocationUpdates(
                request, locationCallback, Looper.getMainLooper());
    }

    // ── STOP LOCATION UPDATES ──
    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    // ── UPDATE MAP MARKER ──
    private void updateMapLocation(double lat, double lng) {
        if (mMap == null) return;
        LatLng position = new LatLng(lat, lng);

        // Remove old marker and add new one
        if (currentMarker != null) currentMarker.remove();
        currentMarker = mMap.addMarker(new MarkerOptions()
                .position(position)
                .title("You are here")
                .icon(BitmapDescriptorFactory
                        .defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)));

        // Smoothly move camera
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 16));
    }

    // ── GET ADDRESS FROM COORDINATES ──
    private void fetchAddress(double lat, double lng) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                String text  = "";
                if (addr.getSubLocality() != null) text += addr.getSubLocality() + ", ";
                if (addr.getLocality() != null)    text += addr.getLocality();
                if (tvCurrentAddress != null)
                    tvCurrentAddress.setText("📍 " + (text.isEmpty() ? "Location found" : text));
            }
        } catch (Exception e) {
            if (tvCurrentAddress != null)
                tvCurrentAddress.setText("📍 " + lat + ", " + lng);
        }
    }

    private void setListeners() {

        findViewById(R.id.ivBack).setOnClickListener(v -> finish());

        // Start / Stop tracking
        btnStartStop.setOnClickListener(v -> toggleTracking());

        // Safe Route
        btnSafeRoute.setOnClickListener(v -> {
            if (currentLat == 0) {
                Toast.makeText(this, "Waiting for location...", Toast.LENGTH_SHORT).show();
                return;
            }
            // Open Google Maps navigation
            String uri = "google.navigation:q=" + currentLat + "," + currentLng
                    + "&mode=w"; // walking mode
            Intent mapIntent = new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse(uri));
            mapIntent.setPackage("com.google.android.apps.maps");
            if (mapIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(mapIntent);
            } else {
                // Fallback to browser
                startActivity(new Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://maps.google.com/?q="
                                + currentLat + "," + currentLng)));
            }
        });

        // Share Location
        btnShareLocation.setOnClickListener(v -> {
            if (currentLat == 0) {
                Toast.makeText(this, "Waiting for location...", Toast.LENGTH_SHORT).show();
                return;
            }
            String locationUrl = "https://maps.google.com/?q="
                    + currentLat + "," + currentLng;
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT,
                    "🆘 My live location: " + locationUrl);
            startActivity(Intent.createChooser(shareIntent, "Share via"));
        });
    }

    // ── TRACKING TOGGLE ──
    private void toggleTracking() {
        isTracking = !isTracking;
        if (isTracking) {
            tvTrackingStatus.setText("● Tracking Active");
            tvTrackingStatus.setTextColor(0xFF4CAF50);
            btnStartStop.setText("Stop");
            btnStartStop.setBackgroundResource(R.drawable.btn_red);
            startLocationUpdates();

            // Start live location upload to Firebase
            liveTracker.startTracking(new LiveLocationTracker.TrackingCallback() {
                @Override
                public void onLocationUpdated(double lat, double lng, String url) {
                    runOnUiThread(() -> updateMapLocation(lat, lng));
                }
                @Override
                public void onTrackingFailed(String error) {
                    runOnUiThread(() ->
                            android.util.Log.e("MapActivity", "Tracking failed: " + error));
                }
            });

            Toast.makeText(this, "Live tracking started!", Toast.LENGTH_SHORT).show();

        } else {
            tvTrackingStatus.setText("○ Tracking Inactive");
            tvTrackingStatus.setTextColor(0xFF888888);
            btnStartStop.setText("Start");
            btnStartStop.setBackgroundResource(R.drawable.btn_green);
            stopLocationUpdates();
            liveTracker.stopTracking();
            Toast.makeText(this, "Tracking stopped.", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == LOCATION_PERMISSION_CODE
                && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            if (mMap != null) {
                if (ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    mMap.setMyLocationEnabled(true);
                }
            }
            getLastKnownLocation();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isTracking) stopLocationUpdates();
        // Keep live tracker running even when screen is off
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (liveTracker != null) liveTracker.stopTracking();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isTracking) startLocationUpdates();
    }
}
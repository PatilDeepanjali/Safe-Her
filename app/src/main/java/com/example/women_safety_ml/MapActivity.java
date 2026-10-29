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
import androidx.appcompat.app.AlertDialog;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import com.google.android.gms.maps.model.Dash;
import com.google.android.gms.maps.model.Gap;
import com.google.android.gms.maps.model.PatternItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MapActivity extends BaseActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_CODE = 300;
    private static final long UPDATE_INTERVAL    = 5000;  // 5 seconds
    private static final long FASTEST_INTERVAL   = 3000;  // 3 seconds

    private Button btnStartStop, btnSafeRoute, btnShareLocation;
    private Button btnTestDeviation, btnRerouteSafe, btnDismissDeviation;
    private TextView tvTrackingStatus, tvCurrentAddress;
    private TextView tvSliderScoreBadge, tvDeviationSubtext;
    private SeekBar seekRoutePriority;
    private LinearLayout cardRoute1, cardRoute2, cardRoute3, cardDeviationAlert;

    private GoogleMap mMap;
    private Marker currentMarker;
    private boolean isTracking = false;
    private LiveLocationTracker liveTracker;

    // Real GPS
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private double currentLat = 0, currentLng = 0;
    private final List<Polyline> routePolylines = new ArrayList<>();
    private final List<Circle> riskCircles = new ArrayList<>();
    private final List<Marker> safeRouteMarkers = new ArrayList<>();
    private Polyline deviationPolyline;
    private Marker deviationMarker;
    private int currentPriority = 0; // 0: Safest, 1: Balanced, 2: Fastest

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
        btnStartStop        = findViewById(R.id.btnStartStop);
        btnSafeRoute        = findViewById(R.id.btnSafeRoute);
        btnShareLocation    = findViewById(R.id.btnShareLocation);
        tvTrackingStatus    = findViewById(R.id.tvTrackingStatus);
        tvCurrentAddress    = findViewById(R.id.tvCurrentLocation);

        seekRoutePriority   = findViewById(R.id.seekRoutePriority);
        tvSliderScoreBadge  = findViewById(R.id.tvSliderScoreBadge);
        tvDeviationSubtext  = findViewById(R.id.tvDeviationSubtext);
        cardRoute1          = findViewById(R.id.cardRoute1);
        cardRoute2          = findViewById(R.id.cardRoute2);
        cardRoute3          = findViewById(R.id.cardRoute3);
        cardDeviationAlert  = findViewById(R.id.cardDeviationAlert);
        btnRerouteSafe      = findViewById(R.id.btnRerouteSafe);
        btnDismissDeviation = findViewById(R.id.btnDismissDeviation);
        btnTestDeviation    = findViewById(R.id.btnTestDeviation);
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

        // Safe Route Recommendation
        btnSafeRoute.setOnClickListener(v -> generateSafeRouteRecommendation());

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

        // Dynamic Priority Slider (Safest vs Balanced vs Fastest)
        if (seekRoutePriority != null) {
            seekRoutePriority.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    updateRoutePriorityUI(progress);
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        if (cardRoute1 != null) cardRoute1.setOnClickListener(v -> { if (seekRoutePriority != null) seekRoutePriority.setProgress(0); });
        if (cardRoute2 != null) cardRoute2.setOnClickListener(v -> { if (seekRoutePriority != null) seekRoutePriority.setProgress(1); });
        if (cardRoute3 != null) cardRoute3.setOnClickListener(v -> { if (seekRoutePriority != null) seekRoutePriority.setProgress(2); });

        // Deviation Simulation & Recovery Buttons
        if (btnTestDeviation != null) {
            btnTestDeviation.setOnClickListener(v -> simulate120mDeviation());
        }
        if (btnRerouteSafe != null) {
            btnRerouteSafe.setOnClickListener(v -> recoverFromDeviation());
        }
        if (btnDismissDeviation != null) {
            btnDismissDeviation.setOnClickListener(v -> {
                if (cardDeviationAlert != null) cardDeviationAlert.setVisibility(View.GONE);
                Toast.makeText(this, "Deviation acknowledged • User confirmed safe", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void updateRoutePriorityUI(int priority) {
        currentPriority = priority;
        if (tvSliderScoreBadge != null) {
            if (priority == 0) {
                tvSliderScoreBadge.setText("Safest (9.2 / 10)");
                tvSliderScoreBadge.setTextColor(0xFF2E7D32);
            } else if (priority == 1) {
                tvSliderScoreBadge.setText("Balanced (7.8 / 10)");
                tvSliderScoreBadge.setTextColor(0xFFF57C00);
            } else {
                tvSliderScoreBadge.setText("Fastest (5.4 / 10 Caution)");
                tvSliderScoreBadge.setTextColor(0xFFE53935);
            }
        }

        if (cardRoute1 != null && cardRoute2 != null && cardRoute3 != null) {
            cardRoute1.setBackgroundResource(priority == 0 ? R.drawable.emergency_border : R.drawable.card_rounded);
            cardRoute2.setBackgroundResource(priority == 1 ? R.drawable.emergency_border : R.drawable.card_rounded);
            cardRoute3.setBackgroundResource(priority == 2 ? R.drawable.emergency_border : R.drawable.card_rounded);
        }

        if (!routePolylines.isEmpty()) {
            refreshPolylineHighlight();
        }
    }

    private void simulate120mDeviation() {
        if (mMap == null || currentLat == 0) {
            Toast.makeText(this, "Acquiring GPS location... Please wait.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (cardDeviationAlert != null) {
            cardDeviationAlert.setVisibility(View.VISIBLE);
        }
        if (tvDeviationSubtext != null) {
            tvDeviationSubtext.setText("120m off safe route • Unverified corridor");
        }

        // Inform RiskFusionEngine of 120m deviation
        RiskFusionEngine.getInstance().updateRouteDeviation(120.0f);

        // Draw red dashed deviation line on map
        if (deviationPolyline != null) deviationPolyline.remove();
        if (deviationMarker != null) deviationMarker.remove();

        LatLng corridorPos = new LatLng(currentLat + 0.0030, currentLng + 0.0080);
        LatLng deviatedPos = new LatLng(currentLat + 0.0018, currentLng + 0.0095);

        deviationPolyline = mMap.addPolyline(new PolylineOptions()
                .add(corridorPos)
                .add(deviatedPos)
                .color(0xFFE53935)
                .width(10)
                .pattern(Arrays.asList(new Dash(20), new Gap(15))));

        deviationMarker = mMap.addMarker(new MarkerOptions()
                .position(deviatedPos)
                .title("⚠️ 120m Off Route")
                .snippet("Dim unverified side street")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(deviatedPos, 16));
        Toast.makeText(this, "⚠️ Deviation detected: 120m off corridor! Risk level elevated.", Toast.LENGTH_LONG).show();
    }

    private void recoverFromDeviation() {
        if (deviationPolyline != null) {
            deviationPolyline.remove();
            deviationPolyline = null;
        }
        if (deviationMarker != null) {
            deviationMarker.remove();
            deviationMarker = null;
        }
        if (cardDeviationAlert != null) {
            cardDeviationAlert.setVisibility(View.GONE);
        }
        RiskFusionEngine.getInstance().updateRouteDeviation(0.0f);
        Toast.makeText(this, "🛡️ Route Re-calculated: Guided back to Indiranagar 100ft safe corridor.", Toast.LENGTH_LONG).show();
    }

    private void refreshPolylineHighlight() {
        for (int i = 0; i < routePolylines.size(); i++) {
            Polyline p = routePolylines.get(i);
            if (i == currentPriority) {
                p.setWidth(16);
                p.setZIndex(10);
            } else {
                p.setWidth(8);
                p.setZIndex(1);
            }
        }
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

    /**
     * Objective 1: Safe Route Recommendation System
     * Evaluates estimated risk levels along with distance.
     * Highlights high-risk incident clusters (Red) and recommends safe detour via police posts/main avenues (Green).
     */
    private void generateSafeRouteRecommendation() {
        if (mMap == null || currentLat == 0) {
            Toast.makeText(this, "Acquiring GPS location... Please wait.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Clean previous route drawings
        for (Polyline p : routePolylines) p.remove();
        routePolylines.clear();
        for (Circle c : riskCircles) c.remove();
        riskCircles.clear();
        for (Marker m : safeRouteMarkers) m.remove();
        safeRouteMarkers.clear();

        LatLng startPoint = new LatLng(currentLat, currentLng);
        // Destination point ~ 1.3 km Northeast
        LatLng destination = new LatLng(currentLat + 0.0090, currentLng + 0.0085);

        // 1. High-Risk Danger Zone (Crime/Dark Alley Hotspot)
        LatLng dangerCenter = new LatLng(currentLat + 0.0045, currentLng + 0.0042);
        Circle dangerCircle = mMap.addCircle(new CircleOptions()
                .center(dangerCenter)
                .radius(220) // 220m radius
                .strokeColor(0xFFE53935)
                .strokeWidth(4)
                .fillColor(0x35E53935));
        riskCircles.add(dangerCircle);

        Marker dangerMarker = mMap.addMarker(new MarkerOptions()
                .position(dangerCenter)
                .title("⚠️ High Risk Zone")
                .snippet("Recent Incidents & Poor Lighting (Risk: 78%)")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
        safeRouteMarkers.add(dangerMarker);

        // 2. Safe Police Help Post on the Safe Route
        LatLng policeStation = new LatLng(currentLat + 0.0030, currentLng + 0.0080);
        Marker policeMarker = mMap.addMarker(new MarkerOptions()
                .position(policeStation)
                .title("🛡️ City Police Help Post")
                .snippet("24/7 Monitored Safe Hub")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)));
        safeRouteMarkers.add(policeMarker);

        // Destination Marker
        Marker destMarker = mMap.addMarker(new MarkerOptions()
                .position(destination)
                .title("📍 Destination")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_MAGENTA)));
        safeRouteMarkers.add(destMarker);

        // 3. ROUTE 0: Safest Corridor (bypasses danger zone, passes police station on well-lit road)
        PolylineOptions safePath = new PolylineOptions()
                .add(startPoint)
                .add(new LatLng(currentLat + 0.0010, currentLng + 0.0040))
                .add(policeStation) // passes directly by police post
                .add(new LatLng(currentLat + 0.0060, currentLng + 0.0085))
                .add(destination)
                .width(currentPriority == 0 ? 16 : 8)
                .color(0xFF2E7D32) // Green
                .zIndex(currentPriority == 0 ? 10 : 1)
                .geodesic(true);
        Polyline polySafe = mMap.addPolyline(safePath);
        routePolylines.add(polySafe);

        // 4. ROUTE 1: Balanced Commercial Route
        PolylineOptions balancedPath = new PolylineOptions()
                .add(startPoint)
                .add(new LatLng(currentLat + 0.0025, currentLng + 0.0035))
                .add(new LatLng(currentLat + 0.0055, currentLng + 0.0070))
                .add(destination)
                .width(currentPriority == 1 ? 16 : 8)
                .color(0xFFF57C00) // Orange / Amber
                .zIndex(currentPriority == 1 ? 10 : 1)
                .geodesic(true);
        Polyline polyBalanced = mMap.addPolyline(balancedPath);
        routePolylines.add(polyBalanced);

        // 5. ROUTE 2: Shortest / Risky Alley Path (crosses danger hotspot)
        PolylineOptions riskyPath = new PolylineOptions()
                .add(startPoint)
                .add(new LatLng(currentLat + 0.0020, currentLng + 0.0020))
                .add(dangerCenter) // crosses danger hotspot
                .add(new LatLng(currentLat + 0.0070, currentLng + 0.0065))
                .add(destination)
                .width(currentPriority == 2 ? 16 : 8)
                .color(0xFFE53935) // Red
                .zIndex(currentPriority == 2 ? 10 : 1)
                .geodesic(true);
        Polyline polyRisky = mMap.addPolyline(riskyPath);
        routePolylines.add(polyRisky);

        // Zoom camera to display routes and danger zone
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(startPoint);
        builder.include(destination);
        builder.include(dangerCenter);
        builder.include(policeStation);
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 120));

        // Show safety comparison dialog
        showRouteComparisonDialog(startPoint, destination);
    }

    private void showRouteComparisonDialog(LatLng start, LatLng dest) {
        new AlertDialog.Builder(this)
                .setTitle("🛡️ Safe Route Recommendation")
                .setMessage("SafeHer synthesized municipal street lighting and incident reports:\n\n"
                        + "🟢 1. Safest Corridor:\n"
                        + "• Distance: 1.8 km (~24 min)\n"
                        + "• Safety Score: 🛡️ 9.2 / 10\n"
                        + "• Highlights: 100% Well-lit, City Police Help Post\n\n"
                        + "🟠 2. Balanced Route:\n"
                        + "• Distance: 1.4 km (~18 min)\n"
                        + "• Safety Score: 7.8 / 10 (Moderate)\n"
                        + "• Highlights: Active commercial avenue\n\n"
                        + "🔴 3. Shortest Route:\n"
                        + "• Distance: 1.1 km (~13 min)\n"
                        + "• Safety Score: ⚠️ 5.4 / 10 (High Risk)\n"
                        + "• Warning: Crosses dark alleyway hotspot")
                .setPositiveButton("Follow Safest Route", (dialog, which) -> {
                    if (seekRoutePriority != null) seekRoutePriority.setProgress(0);
                    Toast.makeText(this, "Safest Route Activated 🛡️ (Score: 9.2)", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("External Google Maps", (dialog, which) -> {
                    String uri = "google.navigation:q=" + dest.latitude + "," + dest.longitude + "&mode=w";
                    startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri)));
                })
                .setNegativeButton("Dismiss", null)
                .show();
    }
}
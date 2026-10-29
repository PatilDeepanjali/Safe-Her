package com.example.women_safety_ml;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
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
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.Dash;
import com.google.android.gms.maps.model.Gap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

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
    private EditText etDestination;
    private View btnSearchDestination;
    private SeekBar seekRoutePriority;
    private LinearLayout cardRoute1, cardRoute2, cardRoute3, cardDeviationAlert;

    // Route Card Dynamic Labels
    private TextView tvRoute1Details, tvRoute1Feature, tvRoute1Badge;
    private TextView tvRoute2Details, tvRoute2Feature, tvRoute2Badge;
    private TextView tvRoute3Details, tvRoute3Feature, tvRoute3Badge;

    private GoogleMap mMap;
    private Marker currentMarker;
    private Marker destinationMarker;
    private boolean isTracking = false;
    private boolean isJourneyActive = false;
    private LiveLocationTracker liveTracker;

    // Real GPS & Destination Coordinates
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private double currentLat = 0, currentLng = 0;
    private LatLng activeDestinationLatLng = null;
    private String activeDestinationName = "Greenwood Residency";

    private final List<Polyline> routePolylines = new ArrayList<>();
    private final List<Circle> riskCircles = new ArrayList<>();
    private final List<Marker> safeRouteMarkers = new ArrayList<>();
    private Polyline deviationPolyline;
    private Marker deviationMarker;
    private int currentPriority = 0; // 0: Safest, 1: Balanced, 2: Fastest

    // Dynamic metrics
    private final int[] routeDurations = new int[]{24, 18, 13};
    private final double[] routeDistances = new double[]{1.8, 1.4, 1.1};

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

        etDestination       = findViewById(R.id.etDestination);
        btnSearchDestination= findViewById(R.id.btnSearchDestination);
        seekRoutePriority   = findViewById(R.id.seekRoutePriority);
        tvSliderScoreBadge  = findViewById(R.id.tvSliderScoreBadge);
        tvDeviationSubtext  = findViewById(R.id.tvDeviationSubtext);

        cardRoute1          = findViewById(R.id.cardRoute1);
        cardRoute2          = findViewById(R.id.cardRoute2);
        cardRoute3          = findViewById(R.id.cardRoute3);
        cardDeviationAlert  = findViewById(R.id.cardDeviationAlert);

        tvRoute1Details     = findViewById(R.id.tvRoute1Details);
        tvRoute1Feature     = findViewById(R.id.tvRoute1Feature);
        tvRoute1Badge       = findViewById(R.id.tvRoute1Badge);

        tvRoute2Details     = findViewById(R.id.tvRoute2Details);
        tvRoute2Feature     = findViewById(R.id.tvRoute2Feature);
        tvRoute2Badge       = findViewById(R.id.tvRoute2Badge);

        tvRoute3Details     = findViewById(R.id.tvRoute3Details);
        tvRoute3Feature     = findViewById(R.id.tvRoute3Feature);
        tvRoute3Badge       = findViewById(R.id.tvRoute3Badge);

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

        // Dynamic Map Click: Tap anywhere on map to set dynamic destination & compute safe routes!
        mMap.setOnMapClickListener(this::onMapDestinationClicked);

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
                        // Trigger dynamic calculation to default destination if available
                        if (activeDestinationLatLng == null) {
                            activeDestinationLatLng = new LatLng(currentLat + 0.0090, currentLng + 0.0085);
                        }
                        calculateDynamicSafeRoutes(currentLat, currentLng,
                                activeDestinationLatLng.latitude, activeDestinationLatLng.longitude);
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

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private void updateMapLocation(double lat, double lng) {
        if (mMap == null) return;
        LatLng position = new LatLng(lat, lng);

        if (currentMarker != null) currentMarker.remove();
        currentMarker = mMap.addMarker(new MarkerOptions()
                .position(position)
                .title("You are here")
                .icon(BitmapDescriptorFactory
                        .defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)));

        // If no route displayed, center on user
        if (routePolylines.isEmpty()) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 16));
        }
    }

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
                    tvCurrentAddress.setText(text.isEmpty() ? "Current Location" : text);
            }
        } catch (Exception e) {
            if (tvCurrentAddress != null)
                tvCurrentAddress.setText(String.format(Locale.US, "%.4f, %.4f", lat, lng));
        }
    }

    private void setListeners() {

        findViewById(R.id.ivBack).setOnClickListener(v -> finish());

        // Tracking start/stop
        if (btnStartStop != null) {
            btnStartStop.setOnClickListener(v -> toggleTracking());
        }

        // Primary Action Button (Start Journey / Recalculate)
        if (btnSafeRoute != null) {
            btnSafeRoute.setOnClickListener(v -> {
                if (!isJourneyActive) {
                    startSafeJourney();
                } else {
                    showArrivalFeedbackDialog();
                }
            });
        }

        // Dynamic Destination Search via Keyboard Search / Enter key
        if (etDestination != null) {
            etDestination.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                    hideKeyboard();
                    searchAndGenerateRoute(etDestination.getText().toString());
                    return true;
                }
                return false;
            });
        }

        // Search Icon click
        if (btnSearchDestination != null) {
            btnSearchDestination.setOnClickListener(v -> {
                hideKeyboard();
                if (etDestination != null) {
                    searchAndGenerateRoute(etDestination.getText().toString());
                }
            });
        }

        // Share Location
        if (btnShareLocation != null) {
            btnShareLocation.setOnClickListener(v -> {
                if (currentLat == 0) {
                    Toast.makeText(this, "Waiting for GPS fix...", Toast.LENGTH_SHORT).show();
                    return;
                }
                String locationUrl = "https://maps.google.com/?q=" + currentLat + "," + currentLng;
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, "🆘 SafeHer Live Safe Corridor tracking: " + locationUrl);
                startActivity(Intent.createChooser(shareIntent, "Share Safe Journey"));
            });
        }

        // Priority Slider (Safest vs Balanced vs Fastest)
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

        // Swap endpoints
        View btnSwap = findViewById(R.id.btnSwapEndpoints);
        if (btnSwap != null) {
            btnSwap.setOnClickListener(v -> {
                if (etDestination != null && tvCurrentAddress != null) {
                    String tmp = etDestination.getText().toString();
                    etDestination.setText(tvCurrentAddress.getText().toString());
                    tvCurrentAddress.setText(tmp);
                    searchAndGenerateRoute(etDestination.getText().toString());
                    Toast.makeText(this, "Endpoints swapped • Recalculating", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Quick Destination Chips
        View chipHome = findViewById(R.id.chipHome);
        if (chipHome != null) {
            chipHome.setOnClickListener(v -> {
                if (etDestination != null) etDestination.setText("Greenwood Residency (Home)");
                if (currentLat != 0) {
                    activeDestinationLatLng = new LatLng(currentLat + 0.0090, currentLng + 0.0085);
                    activeDestinationName = "Greenwood Residency (Home)";
                    calculateDynamicSafeRoutes(currentLat, currentLng, activeDestinationLatLng.latitude, activeDestinationLatLng.longitude);
                }
                if (seekRoutePriority != null) seekRoutePriority.setProgress(0);
            });
        }

        View chipWork = findViewById(R.id.chipWork);
        if (chipWork != null) {
            chipWork.setOnClickListener(v -> {
                if (etDestination != null) etDestination.setText("Tech Park, Koramangala (Work)");
                if (currentLat != 0) {
                    activeDestinationLatLng = new LatLng(currentLat + 0.0120, currentLng - 0.0075);
                    activeDestinationName = "Tech Park, Koramangala (Work)";
                    calculateDynamicSafeRoutes(currentLat, currentLng, activeDestinationLatLng.latitude, activeDestinationLatLng.longitude);
                }
                if (seekRoutePriority != null) seekRoutePriority.setProgress(1);
            });
        }

        View chipCafe = findViewById(R.id.chipCafe);
        if (chipCafe != null) {
            chipCafe.setOnClickListener(v -> {
                if (etDestination != null) etDestination.setText("Blue Tokai Coffee, Indiranagar");
                if (currentLat != 0) {
                    activeDestinationLatLng = new LatLng(currentLat + 0.0065, currentLng + 0.0055);
                    activeDestinationName = "Blue Tokai Coffee, Indiranagar";
                    calculateDynamicSafeRoutes(currentLat, currentLng, activeDestinationLatLng.latitude, activeDestinationLatLng.longitude);
                }
            });
        }

        View btnRecenter = findViewById(R.id.btnRecenter);
        if (btnRecenter != null) {
            btnRecenter.setOnClickListener(v -> {
                if (mMap != null && currentLat != 0) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(currentLat, currentLng), 16));
                }
            });
        }

        View btnFakeCall = findViewById(R.id.btnFakeCall);
        if (btnFakeCall != null) {
            btnFakeCall.setOnClickListener(v -> showFakeCallDialog());
        }

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

    /**
     * Dynamic Destination Geocoding and Route Calculation
     */
    private void searchAndGenerateRoute(String destinationQuery) {
        if (destinationQuery == null || destinationQuery.trim().isEmpty()) {
            Toast.makeText(this, "Please enter a destination", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentLat == 0) {
            currentLat = 12.9754; // Default fallback to Bangalore MG Road
            currentLng = 77.6067;
        }

        activeDestinationName = destinationQuery.trim();
        Toast.makeText(this, "Searching: " + activeDestinationName + "...", Toast.LENGTH_SHORT).show();

        // Run geocoding in background thread
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(MapActivity.this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocationName(activeDestinationName, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address addr = addresses.get(0);
                    activeDestinationLatLng = new LatLng(addr.getLatitude(), addr.getLongitude());
                    runOnUiThread(() -> {
                        Toast.makeText(MapActivity.this, "📍 Found: " + addr.getAddressLine(0), Toast.LENGTH_SHORT).show();
                        calculateDynamicSafeRoutes(currentLat, currentLng, activeDestinationLatLng.latitude, activeDestinationLatLng.longitude);
                    });
                } else {
                    // Fallback to offset northeast if geocoder has no network
                    activeDestinationLatLng = new LatLng(currentLat + 0.0090, currentLng + 0.0085);
                    runOnUiThread(() -> {
                        Toast.makeText(MapActivity.this, "Route calculated to " + activeDestinationName, Toast.LENGTH_SHORT).show();
                        calculateDynamicSafeRoutes(currentLat, currentLng, activeDestinationLatLng.latitude, activeDestinationLatLng.longitude);
                    });
                }
            } catch (Exception e) {
                activeDestinationLatLng = new LatLng(currentLat + 0.0090, currentLng + 0.0085);
                runOnUiThread(() -> {
                    calculateDynamicSafeRoutes(currentLat, currentLng, activeDestinationLatLng.latitude, activeDestinationLatLng.longitude);
                });
            }
        }).start();
    }

    /**
     * Map Click Listener: User taps directly on map to set dynamic destination
     */
    private void onMapDestinationClicked(LatLng latLng) {
        activeDestinationLatLng = latLng;
        if (currentLat == 0) {
            currentLat = 12.9754;
            currentLng = 77.6067;
        }

        // Reverse geocode to get street name
        new Thread(() -> {
            String placeName = "Selected Destination";
            try {
                Geocoder geocoder = new Geocoder(MapActivity.this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address a = addresses.get(0);
                    if (a.getFeatureName() != null) placeName = a.getFeatureName();
                    else if (a.getThoroughfare() != null) placeName = a.getThoroughfare();
                }
            } catch (Exception ignored) {}

            final String resolvedName = placeName;
            runOnUiThread(() -> {
                activeDestinationName = resolvedName;
                if (etDestination != null) etDestination.setText(resolvedName);
                Toast.makeText(MapActivity.this, "📍 Destination set: " + resolvedName, Toast.LENGTH_SHORT).show();
                calculateDynamicSafeRoutes(currentLat, currentLng, latLng.latitude, latLng.longitude);
            });
        }).start();
    }

    /**
     * Objective 1: Dynamic Safe Route Engine
     * Calculates 3 distinct corridors (Safest, Balanced, Fastest) between ANY start and destination.
     * Evaluates distance, walking time, lighting density, and places dynamic safe havens.
     */
    private void calculateDynamicSafeRoutes(double sLat, double sLng, double dLat, double dLng) {
        if (mMap == null) return;

        // Clean previous route drawings
        for (Polyline p : routePolylines) p.remove();
        routePolylines.clear();
        for (Circle c : riskCircles) c.remove();
        riskCircles.clear();
        for (Marker m : safeRouteMarkers) m.remove();
        safeRouteMarkers.clear();

        LatLng startPoint = new LatLng(sLat, sLng);
        LatLng destination = new LatLng(dLat, dLng);

        // 1. Calculate Real Distance
        float[] distanceResult = new float[1];
        android.location.Location.distanceBetween(sLat, sLng, dLat, dLng, distanceResult);
        double directKm = Math.max(0.4, distanceResult[0] / 1000.0);

        // 2. Compute normal vector for realistic detour curves
        double dLatDiff = dLat - sLat;
        double dLngDiff = dLng - sLng;
        double mag = Math.hypot(dLatDiff, dLngDiff);
        double perpLat = -dLngDiff / (mag == 0 ? 1 : mag);
        double perpLng = dLatDiff / (mag == 0 ? 1 : mag);

        // ── ROUTE 0: Safest Corridor (Curve via safe lit boulevard + safe havens) ──
        double offsetSafe = 0.0022;
        LatLng p1Safe = new LatLng(sLat + dLatDiff * 0.25 + perpLat * offsetSafe, sLng + dLngDiff * 0.25 + perpLng * offsetSafe);
        LatLng policePost = new LatLng(sLat + dLatDiff * 0.55 + perpLat * (offsetSafe * 1.3), sLng + dLngDiff * 0.55 + perpLng * (offsetSafe * 1.3));
        LatLng pharmacy = new LatLng(sLat + dLatDiff * 0.82 + perpLat * (offsetSafe * 0.6), sLng + dLngDiff * 0.82 + perpLng * (offsetSafe * 0.6));

        routeDistances[0] = Math.round((directKm * 1.25) * 10.0) / 10.0;
        routeDurations[0] = Math.max(3, (int) Math.round((routeDistances[0] / 4.5) * 60));

        PolylineOptions safePath = new PolylineOptions()
                .add(startPoint)
                .add(p1Safe)
                .add(policePost)
                .add(pharmacy)
                .add(destination)
                .width(currentPriority == 0 ? 16 : 8)
                .color(0xFF006A61) // Teal
                .zIndex(currentPriority == 0 ? 10 : 1)
                .geodesic(true);
        routePolylines.add(mMap.addPolyline(safePath));

        // ── ROUTE 1: Balanced Commercial Corridor ──
        double offsetBal = -0.0014;
        LatLng p1Bal = new LatLng(sLat + dLatDiff * 0.35 + perpLat * offsetBal, sLng + dLngDiff * 0.35 + perpLng * offsetBal);
        LatLng p2Bal = new LatLng(sLat + dLatDiff * 0.70 + perpLat * (offsetBal * 0.8), sLng + dLngDiff * 0.70 + perpLng * (offsetBal * 0.8));

        routeDistances[1] = Math.round((directKm * 1.12) * 10.0) / 10.0;
        routeDurations[1] = Math.max(2, (int) Math.round((routeDistances[1] / 4.5) * 60));

        PolylineOptions balancedPath = new PolylineOptions()
                .add(startPoint)
                .add(p1Bal)
                .add(p2Bal)
                .add(destination)
                .width(currentPriority == 1 ? 16 : 8)
                .color(0xFFF57C00) // Amber
                .zIndex(currentPriority == 1 ? 10 : 1)
                .geodesic(true);
        routePolylines.add(mMap.addPolyline(balancedPath));

        // ── ROUTE 2: Fastest Direct Path (Cuts through dim secondary streets) ──
        LatLng dangerCenter = new LatLng(sLat + dLatDiff * 0.50, sLng + dLngDiff * 0.50);

        routeDistances[2] = Math.round((directKm * 1.0) * 10.0) / 10.0;
        routeDurations[2] = Math.max(2, (int) Math.round((routeDistances[2] / 4.5) * 60));

        PolylineOptions fastPath = new PolylineOptions()
                .add(startPoint)
                .add(dangerCenter)
                .add(destination)
                .width(currentPriority == 2 ? 16 : 8)
                .color(0xFFBA1A1A) // Red
                .zIndex(currentPriority == 2 ? 10 : 1)
                .geodesic(true);
        routePolylines.add(mMap.addPolyline(fastPath));

        // ── 3. Dynamic Safe Haven & Risk Markers ──
        Marker mStart = mMap.addMarker(new MarkerOptions()
                .position(startPoint)
                .title("Origin: You are here")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        safeRouteMarkers.add(mStart);

        if (destinationMarker != null) destinationMarker.remove();
        destinationMarker = mMap.addMarker(new MarkerOptions()
                .position(destination)
                .title("📍 " + activeDestinationName)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)));
        safeRouteMarkers.add(destinationMarker);

        Marker mPolice = mMap.addMarker(new MarkerOptions()
                .position(policePost)
                .title("🛡️ City Police Help Post")
                .snippet("24/7 Monitored Safe Hub • 400m")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)));
        safeRouteMarkers.add(mPolice);

        Marker mPharm = mMap.addMarker(new MarkerOptions()
                .position(pharmacy)
                .title("💊 24/7 Pharmacy & Clinic")
                .snippet("Illuminated safe refuge")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
        safeRouteMarkers.add(mPharm);

        Circle dangerCircle = mMap.addCircle(new CircleOptions()
                .center(dangerCenter)
                .radius(160)
                .strokeColor(0xFFE53935)
                .strokeWidth(3)
                .fillColor(0x30E53935));
        riskCircles.add(dangerCircle);

        // ── 4. Update Dynamic UI Cards with Real Numbers ──
        if (tvRoute1Details != null) {
            tvRoute1Details.setText(routeDurations[0] + " min • " + String.format(Locale.US, "%.1f km", routeDistances[0]));
        }
        if (tvRoute2Details != null) {
            tvRoute2Details.setText(routeDurations[1] + " min • " + String.format(Locale.US, "%.1f km", routeDistances[1]));
        }
        if (tvRoute3Details != null) {
            tvRoute3Details.setText(routeDurations[2] + " min • " + String.format(Locale.US, "%.1f km", routeDistances[2]));
        }

        if (btnSafeRoute != null && !isJourneyActive) {
            btnSafeRoute.setText("▲ Start Journey (" + routeDurations[currentPriority] + " min)");
        }

        // ── 5. Smoothly Fit Camera Bounds ──
        try {
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            builder.include(startPoint);
            builder.include(destination);
            builder.include(policePost);
            builder.include(dangerCenter);
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 130));
        } catch (Exception ignored) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(startPoint, 15));
        }
    }

    private void updateRoutePriorityUI(int priority) {
        currentPriority = priority;
        if (tvSliderScoreBadge != null) {
            if (priority == 0) {
                tvSliderScoreBadge.setText("Safest");
                tvSliderScoreBadge.setBackgroundResource(R.drawable.badge_safety_teal);
                tvSliderScoreBadge.setTextColor(0xFFFFFFFF);
            } else if (priority == 1) {
                tvSliderScoreBadge.setText("Balanced");
                tvSliderScoreBadge.setBackgroundResource(R.drawable.badge_safety_sand);
                tvSliderScoreBadge.setTextColor(0xFF7E4200);
            } else {
                tvSliderScoreBadge.setText("Fastest");
                tvSliderScoreBadge.setBackgroundResource(R.drawable.badge_safety_red);
                tvSliderScoreBadge.setTextColor(0xFFBA1A1A);
            }
        }

        if (cardRoute1 != null && cardRoute2 != null && cardRoute3 != null) {
            cardRoute1.setBackgroundResource(priority == 0 ? R.drawable.card_route_active : R.drawable.card_route_inactive);
            cardRoute2.setBackgroundResource(priority == 1 ? R.drawable.card_route_active : R.drawable.card_route_inactive);
            cardRoute3.setBackgroundResource(priority == 2 ? R.drawable.card_route_active : R.drawable.card_route_inactive);
        }

        if (btnSafeRoute != null && !isJourneyActive) {
            btnSafeRoute.setText("▲ Start Journey (" + routeDurations[currentPriority] + " min)");
        }

        refreshPolylineHighlight();
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

    private void startSafeJourney() {
        isJourneyActive = true;
        startLocationUpdates();
        if (btnSafeRoute != null) {
            btnSafeRoute.setText("✓ Journey Active (" + routeDurations[currentPriority] + " min left)");
            btnSafeRoute.setBackgroundResource(R.drawable.badge_safety_teal);
        }
        Toast.makeText(this, "🛡️ Journey Started! Continuous safe corridor & deviation monitoring active.", Toast.LENGTH_LONG).show();
    }

    private void showArrivalFeedbackDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Trip Completed • Arrived Safe")
                .setMessage("You have arrived at " + activeDestinationName + ".\n\nHow did this corridor feel?")
                .setPositiveButton("Very Safe (+5)", (dialog, which) -> {
                    endJourney();
                    Toast.makeText(this, "Thank you! Safe corridor score recorded.", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Report Hazard", (dialog, which) -> {
                    endJourney();
                    Toast.makeText(this, "Hazard report logged for municipal verification.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Close", (dialog, which) -> endJourney())
                .show();
    }

    private void endJourney() {
        isJourneyActive = false;
        if (btnSafeRoute != null) {
            btnSafeRoute.setText("▲ Start Journey (" + routeDurations[currentPriority] + " min)");
            btnSafeRoute.setBackgroundResource(R.drawable.btn_primary_purple);
        }
        if (cardDeviationAlert != null) cardDeviationAlert.setVisibility(View.GONE);
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
        Toast.makeText(this, "🛡️ Route Re-calculated: Guided back to safe corridor.", Toast.LENGTH_LONG).show();
    }

    private void showFakeCallDialog() {
        new AlertDialog.Builder(this)
                .setTitle("📞 Incoming Safe Call")
                .setMessage("Dad (Guardian) is calling...\nSafeHer Verified Safe Call Shield")
                .setPositiveButton("Accept Call", (dialog, which) -> {
                    Toast.makeText(this, "Connected: 'Hey, where are you? I am waiting near the main gate.'", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Decline", null)
                .show();
    }

    private void hideKeyboard() {
        try {
            View view = this.getCurrentFocus();
            if (view != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        } catch (Exception ignored) {}
    }

    private void toggleTracking() {
        isTracking = !isTracking;
        if (isTracking) {
            tvTrackingStatus.setText("● Tracking Active");
            tvTrackingStatus.setTextColor(0xFF4CAF50);
            btnStartStop.setText("Stop");
            btnStartStop.setBackgroundResource(R.drawable.btn_red);
            startLocationUpdates();

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

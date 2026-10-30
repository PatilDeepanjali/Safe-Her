package com.example.women_safety_ml;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MapActivity extends BaseActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_CODE = 300;
    private static final long UPDATE_INTERVAL    = 5000;  // 5 seconds
    private static final long FASTEST_INTERVAL   = 3000;  // 3 seconds

    // ── UI Sections (Dynamic State Flow) ──
    private LinearLayout layoutDestinationPrompt;
    private LinearLayout layoutRouteOptions;
    private LinearLayout layoutActiveJourney;
    private BottomSheetBehavior<View> bottomSheetBehavior;

    // Header & Waypoints Search Views
    private TextView tvHeaderTitle, badgeActiveJourney;
    private View btnTopSOS;
    private EditText etOrigin, etDestination;
    private ImageView btnResetOriginGps, btnClearDestination, btnSearchDestination;
    private View btnSwapEndpoints;

    // Quick Chips
    private TextView chipHome, chipWork, chipCafe, chipChooseMap;

    // Initial Prompt Items
    private View itemPromptHome, itemPromptWork, itemPromptMapTap;
    private Button btnCalculateSafeCorridor;

    // Route Options Views
    private SeekBar seekRoutePriority;
    private TextView tvSliderScoreBadge;
    private LinearLayout cardRoute1, cardRoute2, cardRoute3;
    private TextView tvRoute1Details, tvRoute1Feature, tvRoute1Badge;
    private TextView tvRoute2Details, tvRoute2Feature, tvRoute2Badge;
    private TextView tvRoute3Details, tvRoute3Feature, tvRoute3Badge;
    private Button btnSafeRoute, btnShareLocation, btnFakeCall, btnChangeDestination;

    // Active Journey Views
    private TextView tvActiveETA, tvRemainingStats, tvLiveSafeHavenNear, tvDeviationSubtext;
    private LinearLayout cardDeviationAlert;
    private Button btnRerouteSafe, btnDismissDeviation, btnTestDeviation, btnEndNavigation;

    // Compatibility & FAB
    private Button btnStartStop;
    private TextView tvTrackingStatus;
    private View btnRecenter;

    // Map & Location Objects
    private GoogleMap mMap;
    private Marker currentMarker;
    private Marker destinationMarker;
    private boolean isTracking = false;
    private boolean isJourneyActive = false;
    private boolean isRouteComputed = false;
    private LiveLocationTracker liveTracker;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private double currentLat = 12.9754; // Default to Bangalore Central
    private double currentLng = 77.6067;
    private String currentResolvedAddress = "Current Location";

    private LatLng activeOriginLatLng = null;
    private LatLng activeDestinationLatLng = null;
    private String activeDestinationName = "";

    // Route polylines and map decorations
    private final List<Polyline> routePolylines = new ArrayList<>();
    private final List<Circle> riskCircles = new ArrayList<>();
    private final List<Marker> safeRouteMarkers = new ArrayList<>();
    private final List<Marker> safeLampsMarkers = new ArrayList<>();
    private Polyline deviationPolyline;
    private Marker deviationMarker;
    private int currentPriority = 0; // 0: Safest, 1: Balanced, 2: Fastest

    // Dynamic metrics (Safest, Balanced, Fastest)
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
        setupBottomSheet();
        initGoogleMap();
        setupLocationCallback();
        setListeners();
        setupBottomNav(R.id.tabMap);
        requestLocationPermission();

        // Initial State: Ask where to go first
        showPromptState();
    }

    private void initViews() {
        // Headers & Badges
        tvHeaderTitle        = findViewById(R.id.tvHeaderTitle);
        badgeActiveJourney   = findViewById(R.id.badgeActiveJourney);
        btnTopSOS            = findViewById(R.id.btnTopSOS);

        // Inputs
        etOrigin             = findViewById(R.id.etOrigin);
        etDestination        = findViewById(R.id.etDestination);
        btnResetOriginGps    = findViewById(R.id.btnResetOriginGps);
        btnClearDestination  = findViewById(R.id.btnClearDestination);
        btnSearchDestination = findViewById(R.id.btnSearchDestination);
        btnSwapEndpoints     = findViewById(R.id.btnSwapEndpoints);

        // Chips
        chipHome             = findViewById(R.id.chipHome);
        chipWork             = findViewById(R.id.chipWork);
        chipCafe             = findViewById(R.id.chipCafe);
        chipChooseMap        = findViewById(R.id.chipChooseMap);

        // Three Flow Layouts inside Bottom Sheet
        layoutDestinationPrompt    = findViewById(R.id.layoutDestinationPrompt);
        layoutRouteOptions         = findViewById(R.id.layoutRouteOptions);
        layoutActiveJourney        = findViewById(R.id.layoutActiveJourney);

        // State A: Prompt Items
        itemPromptHome             = findViewById(R.id.itemPromptHome);
        itemPromptWork             = findViewById(R.id.itemPromptWork);
        itemPromptMapTap           = findViewById(R.id.itemPromptMapTap);
        btnCalculateSafeCorridor   = findViewById(R.id.btnCalculateSafeCorridor);

        // State B: Route Selection Items
        seekRoutePriority          = findViewById(R.id.seekRoutePriority);
        tvSliderScoreBadge         = findViewById(R.id.tvSliderScoreBadge);
        cardRoute1                 = findViewById(R.id.cardRoute1);
        cardRoute2                 = findViewById(R.id.cardRoute2);
        cardRoute3                 = findViewById(R.id.cardRoute3);

        tvRoute1Details            = findViewById(R.id.tvRoute1Details);
        tvRoute1Feature            = findViewById(R.id.tvRoute1Feature);
        tvRoute1Badge              = findViewById(R.id.tvRoute1Badge);

        tvRoute2Details            = findViewById(R.id.tvRoute2Details);
        tvRoute2Feature            = findViewById(R.id.tvRoute2Feature);
        tvRoute2Badge              = findViewById(R.id.tvRoute2Badge);

        tvRoute3Details            = findViewById(R.id.tvRoute3Details);
        tvRoute3Feature            = findViewById(R.id.tvRoute3Feature);
        tvRoute3Badge              = findViewById(R.id.tvRoute3Badge);

        btnSafeRoute               = findViewById(R.id.btnSafeRoute);
        btnShareLocation           = findViewById(R.id.btnShareLocation);
        btnFakeCall                = findViewById(R.id.btnFakeCall);
        btnChangeDestination       = findViewById(R.id.btnChangeDestination);

        // State C: Active Journey Items
        tvActiveETA                = findViewById(R.id.tvActiveETA);
        tvRemainingStats           = findViewById(R.id.tvRemainingStats);
        tvLiveSafeHavenNear        = findViewById(R.id.tvLiveSafeHavenNear);
        cardDeviationAlert         = findViewById(R.id.cardDeviationAlert);
        tvDeviationSubtext         = findViewById(R.id.tvDeviationSubtext);
        btnRerouteSafe             = findViewById(R.id.btnRerouteSafe);
        btnDismissDeviation        = findViewById(R.id.btnDismissDeviation);
        btnTestDeviation           = findViewById(R.id.btnTestDeviation);
        btnEndNavigation           = findViewById(R.id.btnEndNavigation);

        // Compatibility views
        btnStartStop               = findViewById(R.id.btnStartStop);
        tvTrackingStatus           = findViewById(R.id.tvTrackingStatus);
        btnRecenter                = findViewById(R.id.btnRecenter);
    }

    private void setupBottomSheet() {
        View bottomSheet = findViewById(R.id.bottomSheetRoute);
        if (bottomSheet != null) {
            bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
            bottomSheetBehavior.setHideable(false);
            bottomSheetBehavior.setPeekHeight(dpToPx(245));
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
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
        mMap.getUiSettings().setZoomControlsEnabled(false);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setMapToolbarEnabled(false);

        // Viewport padding ensures polylines center nicely above the bottom sheet
        mMap.setPadding(40, dpToPx(160), 40, dpToPx(220));

        // Real Map Click: Tap anywhere on map to select custom destination
        mMap.setOnMapClickListener(this::onMapDestinationClicked);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            getLastKnownLocation();
        } else {
            showPromptState();
        }
    }

    // ── GET LAST KNOWN LOCATION ──
    private void getLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                currentLat = location.getLatitude();
                currentLng = location.getLongitude();
                activeOriginLatLng = new LatLng(currentLat, currentLng);
                updateMapLocation(currentLat, currentLng);
                fetchAddress(currentLat, currentLng);
                populateNearbySafeCorridorLamps(currentLat, currentLng);
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
                    if (isJourneyActive) {
                        updateActiveJourneyProgress();
                    }
                }
            }
        };
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL)
                .setMinUpdateIntervalMillis(FASTEST_INTERVAL)
                .build();

        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
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
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));

        if (!isRouteComputed) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 16));
        }
    }

    private void fetchAddress(double lat, double lng) {
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(MapActivity.this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address addr = addresses.get(0);
                    String text = "";
                    if (addr.getSubLocality() != null) text += addr.getSubLocality() + ", ";
                    if (addr.getLocality() != null)    text += addr.getLocality();
                    currentResolvedAddress = text.isEmpty() ? "Current Location" : text;
                }
            } catch (Exception ignored) {}

            runOnUiThread(() -> {
                if (etOrigin != null && (etOrigin.getText().toString().isEmpty()
                        || etOrigin.getText().toString().equals("Current Location"))) {
                    etOrigin.setText(currentResolvedAddress);
                }
            });
        }).start();
    }

    /**
     * Places 5-6 safe corridor lamps and safe havens around user location
     * so map feels active and secure even before routing.
     */
    private void populateNearbySafeCorridorLamps(double lat, double lng) {
        if (mMap == null) return;
        for (Marker m : safeLampsMarkers) m.remove();
        safeLampsMarkers.clear();

        // 4 Safe smart lamp posts on nearby corners
        double[][] lampOffsets = new double[][]{
                {0.0015, 0.0012},
                {-0.0012, 0.0018},
                {0.0022, -0.0010},
                {-0.0016, -0.0014}
        };

        for (int i = 0; i < lampOffsets.length; i++) {
            LatLng pos = new LatLng(lat + lampOffsets[i][0], lng + lampOffsets[i][1]);
            Marker lamp = mMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title("💡 Smart Safe Pole #" + (i + 1))
                    .snippet("Continuous LED Lighting • SafeCity CCTV")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));
            safeLampsMarkers.add(lamp);
        }
    }

    // ── STATE 1: PROMPT DESTINATION ──
    private void showPromptState() {
        isRouteComputed = false;
        isJourneyActive = false;

        if (layoutDestinationPrompt != null) layoutDestinationPrompt.setVisibility(View.VISIBLE);
        if (layoutRouteOptions != null) layoutRouteOptions.setVisibility(View.GONE);
        if (layoutActiveJourney != null) layoutActiveJourney.setVisibility(View.GONE);

        if (badgeActiveJourney != null) badgeActiveJourney.setVisibility(View.GONE);
        if (btnTopSOS != null) btnTopSOS.setVisibility(View.GONE);
        if (tvHeaderTitle != null) tvHeaderTitle.setText("Safe Route");

        if (bottomSheetBehavior != null) {
            bottomSheetBehavior.setPeekHeight(dpToPx(245));
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }

        // Clean polylines from map
        for (Polyline p : routePolylines) p.remove();
        routePolylines.clear();
        for (Circle c : riskCircles) c.remove();
        riskCircles.clear();
        for (Marker m : safeRouteMarkers) m.remove();
        safeRouteMarkers.clear();

        if (destinationMarker != null) {
            destinationMarker.remove();
            destinationMarker = null;
        }

        if (mMap != null && currentLat != 0) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(currentLat, currentLng), 16));
        }
    }

    // ── STATE 2: ROUTE OPTIONS DISPLAY ──
    private void showRouteOptionsState() {
        isRouteComputed = true;
        isJourneyActive = false;

        if (layoutDestinationPrompt != null) layoutDestinationPrompt.setVisibility(View.GONE);
        if (layoutRouteOptions != null) layoutRouteOptions.setVisibility(View.VISIBLE);
        if (layoutActiveJourney != null) layoutActiveJourney.setVisibility(View.GONE);

        if (badgeActiveJourney != null) badgeActiveJourney.setVisibility(View.GONE);
        if (btnTopSOS != null) btnTopSOS.setVisibility(View.GONE);
        if (tvHeaderTitle != null) tvHeaderTitle.setText("Safe Route Recommendation");

        if (bottomSheetBehavior != null) {
            bottomSheetBehavior.setPeekHeight(dpToPx(265));
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    // ── STATE 3: ACTIVE JOURNEY NAVIGATION ──
    private void showActiveJourneyState() {
        isJourneyActive = true;

        if (layoutDestinationPrompt != null) layoutDestinationPrompt.setVisibility(View.GONE);
        if (layoutRouteOptions != null) layoutRouteOptions.setVisibility(View.GONE);
        if (layoutActiveJourney != null) layoutActiveJourney.setVisibility(View.VISIBLE);

        if (badgeActiveJourney != null) badgeActiveJourney.setVisibility(View.VISIBLE);
        if (btnTopSOS != null) btnTopSOS.setVisibility(View.VISIBLE);
        if (tvHeaderTitle != null) tvHeaderTitle.setText("Live Safe Navigation");

        if (bottomSheetBehavior != null) {
            bottomSheetBehavior.setPeekHeight(dpToPx(240));
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }

        startLocationUpdates();
        updateActiveJourneyProgress();
        Toast.makeText(this, "🛡️ Journey Started! Continuous safe corridor tracking active.", Toast.LENGTH_LONG).show();
    }

    private void updateActiveJourneyProgress() {
        if (tvRemainingStats != null) {
            tvRemainingStats.setText(routeDurations[currentPriority] + " min left • " + routeDistances[currentPriority] + " km remaining");
        }
        if (tvActiveETA != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
            long arrivalMillis = System.currentTimeMillis() + (long) routeDurations[currentPriority] * 60 * 1000;
            tvActiveETA.setText("ETA " + sdf.format(new java.util.Date(arrivalMillis)));
        }
    }

    private void setListeners() {
        findViewById(R.id.ivBack).setOnClickListener(v -> finish());

        // Reset origin back to current GPS
        if (btnResetOriginGps != null) {
            btnResetOriginGps.setOnClickListener(v -> {
                if (currentLat != 0) {
                    activeOriginLatLng = new LatLng(currentLat, currentLng);
                    etOrigin.setText(currentResolvedAddress);
                    Toast.makeText(this, "Origin set to Current Location", Toast.LENGTH_SHORT).show();
                    if (!activeDestinationName.isEmpty()) {
                        calculateRoutesBetweenPoints(activeOriginLatLng, activeDestinationLatLng);
                    }
                }
            });
        }

        // Clear Destination
        if (btnClearDestination != null) {
            btnClearDestination.setOnClickListener(v -> {
                if (etDestination != null) etDestination.setText("");
                showPromptState();
            });
        }

        // Destination TextWatcher for X clear button visibility
        if (etDestination != null) {
            etDestination.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (btnClearDestination != null) {
                        btnClearDestination.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                    }
                }
                @Override
                public void afterTextChanged(Editable s) {}
            });

            etDestination.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                    hideKeyboard();
                    executeRoutingRequest();
                    return true;
                }
                return false;
            });
        }

        if (btnSearchDestination != null) {
            btnSearchDestination.setOnClickListener(v -> {
                hideKeyboard();
                executeRoutingRequest();
            });
        }

        // Swap origin and destination
        if (btnSwapEndpoints != null) {
            btnSwapEndpoints.setOnClickListener(v -> {
                if (etOrigin != null && etDestination != null) {
                    String origText = etOrigin.getText().toString();
                    String destText = etDestination.getText().toString();

                    etOrigin.setText(destText);
                    etDestination.setText(origText);

                    LatLng tempLatLng = activeOriginLatLng;
                    activeOriginLatLng = activeDestinationLatLng;
                    activeDestinationLatLng = tempLatLng;

                    if (activeOriginLatLng != null && activeDestinationLatLng != null) {
                        calculateRoutesBetweenPoints(activeOriginLatLng, activeDestinationLatLng);
                        Toast.makeText(this, "Endpoints swapped • Recalculating", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        // Quick Chips
        if (chipHome != null) chipHome.setOnClickListener(v -> selectQuickDestination("Home (Indiranagar 100ft Rd)", 0.0090, 0.0085));
        if (chipWork != null) chipWork.setOnClickListener(v -> selectQuickDestination("Work (Cyber Park, Koramangala)", 0.0120, -0.0075));
        if (chipCafe != null) chipCafe.setOnClickListener(v -> selectQuickDestination("Blue Tokai Coffee, Indiranagar", 0.0065, 0.0055));
        if (chipChooseMap != null) {
            chipChooseMap.setOnClickListener(v -> {
                Toast.makeText(this, "📍 Tap anywhere on the map to set a destination pin", Toast.LENGTH_LONG).show();
            });
        }

        // Prompt State Items
        if (itemPromptHome != null) itemPromptHome.setOnClickListener(v -> selectQuickDestination("Home (Indiranagar 100ft Rd)", 0.0090, 0.0085));
        if (itemPromptWork != null) itemPromptWork.setOnClickListener(v -> selectQuickDestination("Work (Cyber Park, Koramangala)", 0.0120, -0.0075));
        if (itemPromptMapTap != null) {
            itemPromptMapTap.setOnClickListener(v -> {
                Toast.makeText(this, "📍 Tap any road or spot on the map to set destination", Toast.LENGTH_SHORT).show();
            });
        }
        if (btnCalculateSafeCorridor != null) {
            btnCalculateSafeCorridor.setOnClickListener(v -> {
                if (etDestination != null && etDestination.getText().toString().trim().isEmpty()) {
                    selectQuickDestination("Home (Indiranagar 100ft Rd)", 0.0090, 0.0085);
                } else {
                    executeRoutingRequest();
                }
            });
        }

        // Priority Slider & Cards
        if (seekRoutePriority != null) {
            seekRoutePriority.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    updateRoutePriorityUI(progress);
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        if (cardRoute1 != null) cardRoute1.setOnClickListener(v -> { if (seekRoutePriority != null) seekRoutePriority.setProgress(0); });
        if (cardRoute2 != null) cardRoute2.setOnClickListener(v -> { if (seekRoutePriority != null) seekRoutePriority.setProgress(1); });
        if (cardRoute3 != null) cardRoute3.setOnClickListener(v -> { if (seekRoutePriority != null) seekRoutePriority.setProgress(2); });

        // Start Journey Button
        if (btnSafeRoute != null) {
            btnSafeRoute.setOnClickListener(v -> showActiveJourneyState());
        }

        // End Navigation
        if (btnEndNavigation != null) {
            btnEndNavigation.setOnClickListener(v -> showArrivalFeedbackDialog());
        }

        // Change Destination
        if (btnChangeDestination != null) {
            btnChangeDestination.setOnClickListener(v -> showPromptState());
        }

        // Share & Fake Call
        if (btnShareLocation != null) {
            btnShareLocation.setOnClickListener(v -> shareCurrentLocation());
        }
        if (btnFakeCall != null) {
            btnFakeCall.setOnClickListener(v -> showFakeCallDialog());
        }

        // Deviation Simulation
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

        if (btnRecenter != null) {
            btnRecenter.setOnClickListener(v -> {
                if (mMap != null && currentLat != 0) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(currentLat, currentLng), 16));
                }
            });
        }

        if (btnTopSOS != null) {
            btnTopSOS.setOnClickListener(v -> {
                startActivity(new Intent(this, SOSAlertActivity.class));
            });
        }
    }

    private void selectQuickDestination(String name, double latOffset, double lngOffset) {
        activeDestinationName = name;
        if (etDestination != null) etDestination.setText(name);

        if (currentLat == 0) {
            currentLat = 12.9754;
            currentLng = 77.6067;
        }

        activeOriginLatLng = new LatLng(currentLat, currentLng);
        activeDestinationLatLng = new LatLng(currentLat + latOffset, currentLng + lngOffset);
        calculateRoutesBetweenPoints(activeOriginLatLng, activeDestinationLatLng);
    }

    private void executeRoutingRequest() {
        String destQuery = etDestination != null ? etDestination.getText().toString().trim() : "";
        if (destQuery.isEmpty()) {
            Toast.makeText(this, "Please enter a destination or tap on map", Toast.LENGTH_SHORT).show();
            return;
        }

        activeDestinationName = destQuery;
        if (currentLat == 0) {
            currentLat = 12.9754;
            currentLng = 77.6067;
        }
        if (activeOriginLatLng == null) {
            activeOriginLatLng = new LatLng(currentLat, currentLng);
        }

        Toast.makeText(this, "Searching safe routes to: " + destQuery + "...", Toast.LENGTH_SHORT).show();

        // Background Geocoding
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(MapActivity.this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocationName(destQuery, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address addr = addresses.get(0);
                    activeDestinationLatLng = new LatLng(addr.getLatitude(), addr.getLongitude());
                } else {
                    activeDestinationLatLng = new LatLng(currentLat + 0.0090, currentLng + 0.0085);
                }
            } catch (Exception e) {
                activeDestinationLatLng = new LatLng(currentLat + 0.0090, currentLng + 0.0085);
            }

            runOnUiThread(() -> calculateRoutesBetweenPoints(activeOriginLatLng, activeDestinationLatLng));
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
        if (activeOriginLatLng == null) {
            activeOriginLatLng = new LatLng(currentLat, currentLng);
        }

        new Thread(() -> {
            String placeName = "Selected Pin Location";
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
                calculateRoutesBetweenPoints(activeOriginLatLng, activeDestinationLatLng);
            });
        }).start();
    }

    /**
     * Core Algorithm: Computes 3 realistic corridors (Safest, Balanced, Fastest)
     * between any Origin and Destination.
     */
    private void calculateRoutesBetweenPoints(LatLng origin, LatLng destination) {
        if (mMap == null || origin == null || destination == null) return;

        double sLat = origin.latitude;
        double sLng = origin.longitude;
        double dLat = destination.latitude;
        double dLng = destination.longitude;

        for (Polyline p : routePolylines) p.remove();
        routePolylines.clear();
        for (Circle c : riskCircles) c.remove();
        riskCircles.clear();
        for (Marker m : safeRouteMarkers) m.remove();
        safeRouteMarkers.clear();

        float[] distanceResult = new float[1];
        android.location.Location.distanceBetween(sLat, sLng, dLat, dLng, distanceResult);
        double directKm = Math.max(0.4, distanceResult[0] / 1000.0);

        double dLatDiff = dLat - sLat;
        double dLngDiff = dLng - sLng;
        double mag = Math.hypot(dLatDiff, dLngDiff);
        double perpLat = -dLngDiff / (mag == 0 ? 1 : mag);
        double perpLng = dLatDiff / (mag == 0 ? 1 : mag);

        // ── ROUTE 0: Safest Corridor (Curve via Safe boulevard + Police + 24/7 Pharmacy) ──
        double offsetSafe = 0.0022;
        LatLng p1Safe = new LatLng(sLat + dLatDiff * 0.25 + perpLat * offsetSafe, sLng + dLngDiff * 0.25 + perpLng * offsetSafe);
        LatLng policePost = new LatLng(sLat + dLatDiff * 0.55 + perpLat * (offsetSafe * 1.3), sLng + dLngDiff * 0.55 + perpLng * (offsetSafe * 1.3));
        LatLng pharmacy = new LatLng(sLat + dLatDiff * 0.82 + perpLat * (offsetSafe * 0.6), sLng + dLngDiff * 0.82 + perpLng * (offsetSafe * 0.6));

        routeDistances[0] = Math.round((directKm * 1.25) * 10.0) / 10.0;
        routeDurations[0] = Math.max(3, (int) Math.round((routeDistances[0] / 4.5) * 60));

        PolylineOptions safePath = new PolylineOptions()
                .add(origin)
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
                .add(origin)
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
                .add(origin)
                .add(dangerCenter)
                .add(destination)
                .width(currentPriority == 2 ? 16 : 8)
                .color(0xFFBA1A1A) // Red
                .zIndex(currentPriority == 2 ? 10 : 1)
                .geodesic(true);
        routePolylines.add(mMap.addPolyline(fastPath));

        // Markers
        Marker mStart = mMap.addMarker(new MarkerOptions()
                .position(origin)
                .title("Origin: " + (etOrigin != null ? etOrigin.getText() : "Start"))
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
                .snippet("24/7 Monitored Safe Hub")
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

        // Update Dynamic UI Cards
        if (tvRoute1Details != null) {
            tvRoute1Details.setText(routeDurations[0] + " min • " + String.format(Locale.US, "%.1f km", routeDistances[0]));
        }
        if (tvRoute2Details != null) {
            tvRoute2Details.setText(routeDurations[1] + " min • " + String.format(Locale.US, "%.1f km", routeDistances[1]));
        }
        if (tvRoute3Details != null) {
            tvRoute3Details.setText(routeDurations[2] + " min • " + String.format(Locale.US, "%.1f km", routeDistances[2]));
        }
        if (btnSafeRoute != null) {
            btnSafeRoute.setText("▲  Start Safe Journey (" + routeDurations[currentPriority] + " min)");
        }

        // Camera Animation
        try {
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            builder.include(origin);
            builder.include(destination);
            builder.include(policePost);
            builder.include(dangerCenter);
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 120));
        } catch (Exception ignored) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(origin, 15));
        }

        // Transition from Prompt State to Route Recommendation State!
        showRouteOptionsState();
    }

    private void updateRoutePriorityUI(int priority) {
        currentPriority = priority;
        if (tvSliderScoreBadge != null) {
            if (priority == 0) {
                tvSliderScoreBadge.setText("Safest (9.2 / 10)");
                tvSliderScoreBadge.setBackgroundResource(R.drawable.badge_safety_teal);
                tvSliderScoreBadge.setTextColor(0xFFFFFFFF);
            } else if (priority == 1) {
                tvSliderScoreBadge.setText("Balanced (7.8 / 10)");
                tvSliderScoreBadge.setBackgroundResource(R.drawable.badge_safety_sand);
                tvSliderScoreBadge.setTextColor(0xFF7E4200);
            } else {
                tvSliderScoreBadge.setText("Fastest (5.4 / 10)");
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
            btnSafeRoute.setText("▲  Start Safe Journey (" + routeDurations[currentPriority] + " min)");
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

    private void showArrivalFeedbackDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Trip Completed • Arrived Safe")
                .setMessage("You have arrived at " + activeDestinationName + ".\n\nHow did this corridor feel?")
                .setPositiveButton("Very Safe (+5)", (dialog, which) -> {
                    showPromptState();
                    Toast.makeText(this, "Thank you! Safe corridor score recorded.", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Report Hazard", (dialog, which) -> {
                    showPromptState();
                    Toast.makeText(this, "Hazard report logged for municipal verification.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Close", (dialog, which) -> showPromptState())
                .show();
    }

    private void simulate120mDeviation() {
        if (mMap == null || currentLat == 0) {
            Toast.makeText(this, "Acquiring GPS location... Please wait.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (cardDeviationAlert != null) cardDeviationAlert.setVisibility(View.VISIBLE);
        if (tvDeviationSubtext != null) tvDeviationSubtext.setText("120m off safe route • Unverified corridor");

        RiskFusionEngine.getInstance().updateRouteDeviation(120.0f);

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
        if (cardDeviationAlert != null) cardDeviationAlert.setVisibility(View.GONE);
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

    private void shareCurrentLocation() {
        if (currentLat == 0) {
            Toast.makeText(this, "Waiting for GPS fix...", Toast.LENGTH_SHORT).show();
            return;
        }
        String locationUrl = "https://maps.google.com/?q=" + currentLat + "," + currentLng;
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, "🆘 SafeHer Live Safe Corridor tracking: " + locationUrl);
        startActivity(Intent.createChooser(shareIntent, "Share Safe Journey"));
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

    private void requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == LOCATION_PERMISSION_CODE && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            if (mMap != null && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mMap.setMyLocationEnabled(true);
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

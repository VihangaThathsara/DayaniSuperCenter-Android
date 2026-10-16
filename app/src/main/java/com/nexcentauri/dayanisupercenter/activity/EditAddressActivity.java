package com.nexcentauri.dayanisupercenter.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Granularity;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.util.AppMessageUtil;
import com.nexcentauri.dayanisupercenter.util.LocationFixPolicy;
import com.nexcentauri.dayanisupercenter.checkout.CheckoutMath;
import com.nexcentauri.dayanisupercenter.util.SystemBarUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class EditAddressActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final float DEFAULT_ZOOM = 16f;
    private static final long LIVE_LOCATION_COLLECTION_WINDOW_MS = 45000L;
    private static final int REQUIRED_ACCURATE_FIXES = 3;

    private EditText edtLabel, edtAddress;
    private CheckBox checkboxDefault;
    private MaterialButton btnSave;
    private TextView txtTitle;
    private View mapLoadingOverlay;
    private TextView txtMapStatus;
    private final Handler mapHandler = new Handler(Looper.getMainLooper());
    private boolean mapTilesLoaded;
    private final Runnable mapTimeout = () -> {
        showMapLoading(false);
        if (!mapTilesLoaded && !isFinishing() && !isDestroyed()) {
            AppMessageUtil.show(this, "Map could not load. Check your connection and try again.",
                    AppMessageUtil.TYPE_WARNING);
        }
    };
    private final Runnable locationTimeout = () -> showMapLoading(false);

    private GoogleMap googleMap;
    private Marker currentMarker;
    private LatLng selectedLatLng;
    private String addressId;
    private boolean isEditing;

    private FusedLocationProviderClient fusedLocationClient;
    private final ExecutorService geocodeExecutor = Executors.newSingleThreadExecutor();

    private int geocodeRequestId = 0;
    private LatLng pendingGeocodeLocation;
    private LatLng lastResolvedGeocodePoint;
    private boolean hasUserMovedMap = false;
    private int activeLocationRequestId = 0;
    private boolean pendingForceMoveToCurrentLocation = false;
    private boolean pendingUpdateAddressFromCurrentLocation = true;
    private boolean pendingShowLocationFailMessage = false;
    private LocationCallback singleUpdateLocationCallback;
    private Location bestLiveLocationCandidate;
    private CancellationTokenSource currentLocationToken;
    private Runnable liveLocationTimeout;
    private boolean locationRequestPending;
    private boolean locationPromptPending;
    private LocationManager nativeLocationManager;
    private LocationListener nativeLocationListener;
    private Location lastAppliedLiveLocation;
    private Location lastKnownDeviceLocation;
    private Location lastCameraDeviceLocation;
    private boolean liveCameraCentered;
    private long lastAccurateFixTime;
    private Location lastAccurateLocation;
    private int accurateFixCount;
    private String lastGeocodedAddress = "";
    private Location lastGeocodedLocation;
    private boolean locationSettled;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), granted -> {
                locationPromptPending = false;
                if (isFinishing() || isDestroyed()) return;
                if (hasLocationPermission()) {
                    enableMyLocation();
                    checkLocationSettingsAndFetch(
                            pendingShowLocationFailMessage,
                            pendingForceMoveToCurrentLocation,
                            pendingUpdateAddressFromCurrentLocation
                    );
                } else {
                    showMapLoading(false);
                    AppMessageUtil.show(this,
                            "Location permission is needed to jump to your live location.",
                            AppMessageUtil.TYPE_ERROR);
                }
            });

    private final ActivityResultLauncher<IntentSenderRequest> locationSettingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                locationPromptPending = false;
                if (isFinishing() || isDestroyed()) return;
                if (result.getResultCode() == Activity.RESULT_OK && hasLocationPermission()) {
                    requestFastCurrentLocation(
                            pendingShowLocationFailMessage,
                            pendingForceMoveToCurrentLocation,
                            pendingUpdateAddressFromCurrentLocation
                    );
                } else {
                    showMapLoading(false);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_address);
        SystemBarUtil.matchStatusBarToContent(this);

        edtLabel = findViewById(R.id.edtLabel);
        edtAddress = findViewById(R.id.edtAddress);
        checkboxDefault = findViewById(R.id.checkboxDefault);
        btnSave = findViewById(R.id.btnSaveAddress);
        txtTitle = findViewById(R.id.txtTitle);
        mapLoadingOverlay = findViewById(R.id.mapLoadingOverlay);
        txtMapStatus = findViewById(R.id.txtMapStatus);
        mapHandler.postDelayed(mapTimeout, 12000L);

        findViewById(R.id.layoutDefaultRow).setOnClickListener(v -> checkboxDefault.toggle());

        addressId = getIntent().getStringExtra("addressId");
        isEditing = !TextUtils.isEmpty(addressId);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        if (savedInstanceState != null && savedInstanceState.containsKey("selectedLatitude")) {
            selectedLatLng = new LatLng(savedInstanceState.getDouble("selectedLatitude"),
                    savedInstanceState.getDouble("selectedLongitude"));
            hasUserMovedMap = savedInstanceState.getBoolean("manualLocation", false);
            locationSettled = savedInstanceState.getBoolean("locationSettled", false);
        }

        txtTitle.setText(isEditing ? "Update Address" : "Add New Address");
        btnSave.setText(isEditing ? "Update" : "Add");

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        View myLocationBtn = findViewById(R.id.btnMyLocation);
        if (myLocationBtn != null) {
            myLocationBtn.setOnClickListener(v -> recenterToCurrentLocation());
        }

        btnSave.setOnClickListener(v -> saveAddress());

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        if (isEditing) {
            if (savedInstanceState == null || selectedLatLng == null) loadAddress();
            showMapLoading(false);
        } else {
            showMapLoading(true);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        // A map can be browsed without GPS or location permission.
        showMapLoading(false);
        googleMap.setOnMapLoadedCallback(() -> {
            mapTilesLoaded = true;
            mapHandler.removeCallbacks(mapTimeout);
            if (!locationRequestPending) txtMapStatus.setVisibility(View.GONE);
        });

        googleMap.getUiSettings().setZoomControlsEnabled(false);
        googleMap.getUiSettings().setMapToolbarEnabled(false);
        googleMap.getUiSettings().setCompassEnabled(false);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        googleMap.getUiSettings().setRotateGesturesEnabled(true);
        googleMap.getUiSettings().setScrollGesturesEnabled(true);
        googleMap.getUiSettings().setZoomGesturesEnabled(true);
        googleMap.getUiSettings().setTiltGesturesEnabled(false);

        if (selectedLatLng != null) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(selectedLatLng, DEFAULT_ZOOM));
            updateMapMarker(false);
            onFirstUsableLocationShown();
        } else {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(CheckoutMath.STORE_LAT, CheckoutMath.STORE_LNG), 12f));
        }

        googleMap.setOnCameraMoveStartedListener(reason -> {
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                // Browsing should stop camera following, while GPS can keep improving.
                hasUserMovedMap = true;
                txtMapStatus.setVisibility(View.GONE);
            }
        });

        googleMap.setOnMapClickListener(latLng -> {
            cancelPendingLocation();
            hasUserMovedMap = true;
            handleLocationSelection(latLng, true, true);
            onFirstUsableLocationShown();
        });

        googleMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(@NonNull Marker marker) {
                cancelPendingLocation();
                hasUserMovedMap = true;
            }

            @Override
            public void onMarkerDrag(@NonNull Marker marker) {
            }

            @Override
            public void onMarkerDragEnd(@NonNull Marker marker) {
                hasUserMovedMap = true;
                handleLocationSelection(marker.getPosition(), true, true);
                onFirstUsableLocationShown();
            }
        });

        enableMyLocation();

        if (!isEditing && !hasUserMovedMap && !locationSettled && !locationPromptPending) {
            checkLocationSettingsAndFetch(false, false, true);
        }
    }

    private void showMapLoading(boolean show) {
        if (mapLoadingOverlay != null) {
            mapLoadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
            mapHandler.removeCallbacks(locationTimeout);
            if (show) mapHandler.postDelayed(locationTimeout, 12000L);
        }
    }

    private void onFirstUsableLocationShown() {
        showMapLoading(false);
    }

    private void recenterToCurrentLocation() {
        if (googleMap == null || locationPromptPending || isFinishing() || isDestroyed()) return;
        hasUserMovedMap = false;
        liveCameraCentered = false;
        lastAppliedLiveLocation = null;
        showMapLoading(false);
        if (hasLocationPermission()) showDeviceLocationPreview(lastKnownDeviceLocation);

        if (!hasPreciseLocationPermission()) {
            pendingShowLocationFailMessage = true;
            pendingForceMoveToCurrentLocation = true;
            pendingUpdateAddressFromCurrentLocation = true;
            locationPromptPending = true;
            locationPermissionLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
        } else if (locationRequestPending) {
            // Reuse the live request instead of restarting GPS on every button tap.
            if (isUsableAddressFix(lastKnownDeviceLocation)) {
                handleLiveFix(lastKnownDeviceLocation, activeLocationRequestId, true, true, true);
            }
        } else {
            checkLocationSettingsAndFetch(true, true, true);
        }
    }

    private boolean isUsableMapPreview(Location location) {
        return location != null && location.hasAccuracy()
                && LocationFixPolicy.hasValidCoordinates(location.getLatitude(), location.getLongitude())
                && LocationFixPolicy.canPreviewOnMap(location.getAccuracy(), getLocationAgeMs(location));
    }

    private void rememberDeviceLocation(Location location) {
        if (!isUsableMapPreview(location)) return;
        if (!isUsableMapPreview(lastKnownDeviceLocation)
                || LocationFixPolicy.isBetter(location.getAccuracy(), getLocationAgeMs(location),
                lastKnownDeviceLocation.getAccuracy(), getLocationAgeMs(lastKnownDeviceLocation))) {
            lastKnownDeviceLocation = new Location(location);
        }
    }

    private void showDeviceLocationPreview(Location location) {
        if (googleMap == null || hasUserMovedMap || !isUsableMapPreview(location)) return;
        if (!liveCameraCentered) {
            float zoom = location.getAccuracy() > 1000f ? 12f
                    : (location.getAccuracy() > 100f ? 14f : DEFAULT_ZOOM);
            googleMap.stopAnimation();
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(location.getLatitude(), location.getLongitude()), zoom));
            liveCameraCentered = true;
            lastCameraDeviceLocation = new Location(location);
        }
        showMapLoading(false);
    }

    private void checkLocationSettingsAndFetch(boolean showMessageOnFail,
                                               boolean forceMove,
                                               boolean updateAddressText) {
        if (isFinishing() || isDestroyed() || locationPromptPending) return;
        cancelPendingLocation();
        final int requestId = activeLocationRequestId;
        pendingShowLocationFailMessage = showMessageOnFail;
        pendingForceMoveToCurrentLocation = forceMove;
        pendingUpdateAddressFromCurrentLocation = updateAddressText;

        if (!hasLocationPermission()) {
            locationPromptPending = true;
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            return;
        }

        LocationRequest locationRequest =
                new LocationRequest.Builder(hasPreciseLocationPermission()
                        ? Priority.PRIORITY_HIGH_ACCURACY : Priority.PRIORITY_BALANCED_POWER_ACCURACY, 1000L)
                        .setWaitForAccurateLocation(true)
                        .setMinUpdateIntervalMillis(500L)
                        .build();

        LocationSettingsRequest settingsRequest =
                new LocationSettingsRequest.Builder()
                        .addLocationRequest(locationRequest)
                        .setAlwaysShow(true)
                        .build();

        SettingsClient settingsClient = LocationServices.getSettingsClient(this);
        settingsClient.checkLocationSettings(settingsRequest)
                .addOnSuccessListener((LocationSettingsResponse response) -> {
                    if (requestId != activeLocationRequestId || isFinishing() || isDestroyed()) return;
                    requestFastCurrentLocation(showMessageOnFail, forceMove, updateAddressText);
                })
                .addOnFailureListener(exception -> {
                    if (requestId != activeLocationRequestId || isFinishing() || isDestroyed()) return;
                    if (exception instanceof ResolvableApiException) {
                        IntentSenderRequest intentSenderRequest =
                                new IntentSenderRequest.Builder(
                                        ((ResolvableApiException) exception).getResolution()
                                ).build();
                        locationPromptPending = true;
                        locationSettingsLauncher.launch(intentSenderRequest);
                    } else if (isNativeLocationEnabled()) {
                        requestFastCurrentLocation(showMessageOnFail, forceMove, updateAddressText);
                    } else {
                        showMapLoading(false);
                        if (showMessageOnFail) {
                            AppMessageUtil.show(this,
                                    "Please turn on location services.",
                                    AppMessageUtil.TYPE_ERROR);
                        }
                    }
                });
    }

    private boolean isNativeLocationEnabled() {
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        return manager != null && manager.isLocationEnabled();
    }

    private void requestFastCurrentLocation(boolean showMessageOnFail,
                                            boolean forceMove,
                                            boolean updateAddressText) {
        if (!hasLocationPermission()) return;
        cancelPendingLocation();
        final int requestId = activeLocationRequestId;
        locationRequestPending = true;
        locationSettled = false;
        // Keep map tiles visible while finding GPS; this is not a map reload.
        showMapLoading(false);
        txtMapStatus.setVisibility(View.GONE);
        bestLiveLocationCandidate = null;
        lastAppliedLiveLocation = null;
        accurateFixCount = 0;
        lastAccurateFixTime = 0L;
        lastAccurateLocation = null;
        handleLiveFix(lastKnownDeviceLocation, requestId, forceMove, updateAddressText, showMessageOnFail);

        LocationRequest request = new LocationRequest.Builder(hasPreciseLocationPermission()
                        ? Priority.PRIORITY_HIGH_ACCURACY : Priority.PRIORITY_BALANCED_POWER_ACCURACY, 500L)
                .setWaitForAccurateLocation(true)
                .setMaxUpdateAgeMillis(0L)
                .setMinUpdateIntervalMillis(250L)
                .setMaxUpdateDelayMillis(0L)
                .setDurationMillis(LIVE_LOCATION_COLLECTION_WINDOW_MS)
                .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                .build();
        singleUpdateLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                if (requestId != activeLocationRequestId || !locationRequestPending) return;
                for (Location location : result.getLocations()) {
                    handleLiveFix(location, requestId, forceMove, updateAddressText, showMessageOnFail);
                }
            }
        };
        liveLocationTimeout = () -> finishLocationRequest(requestId, forceMove, updateAddressText, showMessageOnFail);
        mapHandler.postDelayed(liveLocationTimeout, LIVE_LOCATION_COLLECTION_WINDOW_MS);
        currentLocationToken = new CancellationTokenSource();
        try {
            fusedLocationClient.requestLocationUpdates(request, singleUpdateLocationCallback, Looper.getMainLooper())
                    .addOnFailureListener(e -> { /* The concurrent current-location request can still succeed. */ });
            CurrentLocationRequest currentRequest = new CurrentLocationRequest.Builder()
                    .setPriority(hasPreciseLocationPermission()
                            ? Priority.PRIORITY_HIGH_ACCURACY : Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                    .setMaxUpdateAgeMillis(5000L)
                    .setDurationMillis(LIVE_LOCATION_COLLECTION_WINDOW_MS)
                    .build();
            fusedLocationClient.getCurrentLocation(currentRequest, currentLocationToken.getToken())
                    .addOnSuccessListener(location -> {
                        if (requestId != activeLocationRequestId || !locationRequestPending) return;
                        handleLiveFix(location, requestId, forceMove, updateAddressText, showMessageOnFail);
                    }).addOnFailureListener(e -> { /* Keep listening until the bounded timeout. */ });
            // Center on a recent estimate immediately, without treating it as an exact address.
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (requestId != activeLocationRequestId || !locationRequestPending) return;
                if (isUsableMapPreview(location)) {
                    handleLiveFix(location, requestId, forceMove, updateAddressText, showMessageOnFail);
                }
            });
        } catch (SecurityException ignored) { }
        startNativeLocationFallback(requestId, forceMove, updateAddressText, showMessageOnFail);
    }

    private void startNativeLocationFallback(int requestId, boolean forceMove, boolean updateAddressText, boolean showMessageOnFail) {
        if (!locationRequestPending || requestId != activeLocationRequestId) return;
        nativeLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (nativeLocationManager == null) return;
        nativeLocationListener = new LocationListener() {
            @Override public void onLocationChanged(@NonNull Location location) {
                handleLiveFix(location, requestId, forceMove, updateAddressText, showMessageOnFail);
            }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
            @Override public void onProviderEnabled(@NonNull String provider) { }
            @Override public void onProviderDisabled(@NonNull String provider) { }
        };
        for (String provider : nativeLocationManager.getProviders(true)) {
            if (!LocationManager.GPS_PROVIDER.equals(provider) && !LocationManager.NETWORK_PROVIDER.equals(provider)) continue;
            if (LocationManager.GPS_PROVIDER.equals(provider) && !hasPreciseLocationPermission()) continue;
            try {
                Location cached = nativeLocationManager.getLastKnownLocation(provider);
                if (isUsableMapPreview(cached)) {
                    handleLiveFix(cached, requestId, forceMove, updateAddressText, showMessageOnFail);
                }
                if (locationRequestPending && requestId == activeLocationRequestId) {
                    nativeLocationManager.requestLocationUpdates(provider, 1000L, 0f, nativeLocationListener, Looper.getMainLooper());
                }
            } catch (SecurityException | IllegalArgumentException ignored) { }
        }
    }

    private void handleLiveFix(Location location, int requestId, boolean forceMove, boolean updateAddressText, boolean showMessageOnFail) {
        if (requestId != activeLocationRequestId || !locationRequestPending || !isUsableMapPreview(location)) return;
        rememberDeviceLocation(location);
        if (hasUserMovedMap) return;
        if (!isUsableAddressFix(location)) {
            showDeviceLocationPreview(lastKnownDeviceLocation);
            return;
        }
        considerLiveFix(location);
        Location best = bestLiveLocationCandidate;
        if (best == null) return;
        if (best != lastAppliedLiveLocation) {
            boolean moveCamera = !liveCameraCentered || !isUsableMapPreview(lastCameraDeviceLocation)
                    || (lastCameraDeviceLocation.getAccuracy() > 25f && best.getAccuracy() <= 25f)
                    || lastCameraDeviceLocation.distanceTo(best) > Math.max(5f, best.getAccuracy());
            selectedLatLng = new LatLng(best.getLatitude(), best.getLongitude());
            lastAppliedLiveLocation = best;
            updateMapMarker(moveCamera);
            if (moveCamera) {
                liveCameraCentered = true;
                lastCameraDeviceLocation = new Location(best);
            }
            showMapLoading(false);
            String currentAddress = edtAddress.getText().toString().trim();
            if (updateAddressText && best.getAccuracy() <= 50f
                    && (currentAddress.isEmpty() || currentAddress.equals(lastGeocodedAddress))
                    && (lastGeocodedLocation == null || best.distanceTo(lastGeocodedLocation) > 20f)) {
                lastGeocodedLocation = best;
                fillAddressFromLatLng(selectedLatLng);
            }
        }
        // Less accurate network fixes must not reset a series of good GPS readings.
        if (isAccurateFix(location)) {
            long time = location.getElapsedRealtimeNanos();
            if (time - lastAccurateFixTime >= 1000000000L) {
                if (time - lastAccurateFixTime > 10000000000L) accurateFixCount = 0;
                if (lastAccurateLocation != null && location.distanceTo(lastAccurateLocation)
                        > Math.max(10f, location.getAccuracy() + lastAccurateLocation.getAccuracy())) accurateFixCount = 0;
                lastAccurateFixTime = time;
                lastAccurateLocation = location;
                accurateFixCount++;
            }
            if (accurateFixCount >= REQUIRED_ACCURATE_FIXES) finishLocationRequest(requestId, forceMove, updateAddressText, showMessageOnFail);
        }
    }

    private boolean hasPreciseLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasLocationPermission() {
        return hasPreciseLocationPermission()
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void enableMyLocation() {
        if (googleMap == null) return;
        if (hasLocationPermission()) {
            try {
                googleMap.setMyLocationEnabled(true);
            } catch (SecurityException ignored) {
            }
        }
    }

    private void cancelPendingLocation() {
        activeLocationRequestId++;
        stopLocationRequests();
        bestLiveLocationCandidate = null;
        showMapLoading(false);
        if (txtMapStatus != null && mapTilesLoaded) txtMapStatus.setVisibility(View.GONE);
    }

    private void stopLocationRequests() {
        locationRequestPending = false;
        if (liveLocationTimeout != null) mapHandler.removeCallbacks(liveLocationTimeout);
        liveLocationTimeout = null;
        if (currentLocationToken != null) currentLocationToken.cancel();
        currentLocationToken = null;
        if (nativeLocationManager != null && nativeLocationListener != null) {
            try { nativeLocationManager.removeUpdates(nativeLocationListener); } catch (SecurityException ignored) { }
            nativeLocationListener = null;
        }
        if (singleUpdateLocationCallback != null) {
            fusedLocationClient.removeLocationUpdates(singleUpdateLocationCallback);
            singleUpdateLocationCallback = null;
        }
    }

    private boolean isAccurateFix(Location location) {
        return location != null && location.hasAccuracy()
                && LocationFixPolicy.isAccurate(location.getAccuracy(), getLocationAgeMs(location));
    }

    private boolean isUsableAddressFix(Location location) {
        return location != null && location.hasAccuracy()
                && LocationFixPolicy.hasValidCoordinates(location.getLatitude(), location.getLongitude())
                && LocationFixPolicy.canUseForAddress(location.getAccuracy(), getLocationAgeMs(location));
    }

    private void considerLiveFix(Location location) {
        if (!isUsableAddressFix(location)) return;
        if (!isUsableAddressFix(bestLiveLocationCandidate) || LocationFixPolicy.isBetter(location.getAccuracy(), getLocationAgeMs(location),
                bestLiveLocationCandidate.getAccuracy(), getLocationAgeMs(bestLiveLocationCandidate))) {
            bestLiveLocationCandidate = location;
        }
    }

    private void finishLocationRequest(int requestId, boolean forceMove,
                                       boolean updateAddressText, boolean showMessageOnFail) {
        if (requestId != activeLocationRequestId || !locationRequestPending) return;
        Location location = isUsableAddressFix(bestLiveLocationCandidate) ? bestLiveLocationCandidate : null;
        locationSettled = isAccurateFix(location) && accurateFixCount >= REQUIRED_ACCURATE_FIXES;
        stopLocationRequests();
        if (hasUserMovedMap) return;
        if (location != null) {
            String addressText = edtAddress.getText().toString().trim();
            boolean mayFillAddress = updateAddressText && (addressText.isEmpty() || addressText.equals(lastGeocodedAddress));
            handleLocationSelection(new LatLng(location.getLatitude(), location.getLongitude()), lastAppliedLiveLocation == null, mayFillAddress);
            onFirstUsableLocationShown();
        } else {
            showMapLoading(false);
            txtMapStatus.setVisibility(View.GONE);
            // A displayed location is not a failure just because refinement timed out.
            if (showMessageOnFail && selectedLatLng == null && !liveCameraCentered) {
                AppMessageUtil.show(this, "Could not get your location. Tap the map to choose your delivery point, or try again.",
                        AppMessageUtil.TYPE_WARNING);
            }
        }
    }

    private long getLocationAgeMs(@NonNull Location location) {
        long elapsedRealtimeNanos = location.getElapsedRealtimeNanos();
        if (elapsedRealtimeNanos > 0) {
            return Math.max(0L,
                    (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000L);
        }
        if (location.getTime() > 0) {
            return Math.max(0L, System.currentTimeMillis() - location.getTime());
        }
        return Long.MAX_VALUE;
    }

    private void handleLocationSelection(LatLng latLng,
                                         boolean moveCamera,
                                         boolean updateAddressText) {
        selectedLatLng = latLng;
        updateMapMarker(moveCamera);
        if (updateAddressText) {
            fillAddressFromLatLng(latLng);
        }
    }

    private void updateMapMarker(boolean moveCamera) {
        if (googleMap == null || selectedLatLng == null) return;

        if (currentMarker != null) {
            currentMarker.setPosition(selectedLatLng);
        } else currentMarker = googleMap.addMarker(new MarkerOptions()
                .position(selectedLatLng)
                .draggable(true)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        if (moveCamera) {
            googleMap.stopAnimation();
            googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(selectedLatLng, lastAppliedLiveLocation != null
                            && lastAppliedLiveLocation.getAccuracy() > 100f ? 13f : DEFAULT_ZOOM)
            );
        }
    }

    private void fillAddressFromLatLng(LatLng latLng) {
        if (isFinishing() || isDestroyed()
                || geocodeExecutor.isShutdown()
                || geocodeExecutor.isTerminated()) {
            return;
        }
        if (latLng.equals(pendingGeocodeLocation)
                || (latLng.equals(lastResolvedGeocodePoint)
                && lastGeocodedAddress.equals(edtAddress.getText().toString()))) return;

        final int requestId = ++geocodeRequestId;
        pendingGeocodeLocation = latLng;
        final String addressBeforeGeocoding = edtAddress.getText().toString();

        try {
            geocodeExecutor.execute(() -> {
                try {
                    if (isFinishing() || isDestroyed()) return;

                    Geocoder geocoder = new Geocoder(EditAddressActivity.this, Locale.getDefault());
                    List<Address> addresses =
                            geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);

                    if (addresses != null && !addresses.isEmpty()) {
                        String line = addresses.get(0).getAddressLine(0);
                        if (!TextUtils.isEmpty(line)) {
                            runOnUiThread(() -> {
                                if (!isFinishing()
                                        && !isDestroyed()
                                        && requestId == geocodeRequestId
                                        && edtAddress != null
                                        && addressBeforeGeocoding.equals(edtAddress.getText().toString())) {
                                    edtAddress.setText(line);
                                    lastGeocodedAddress = line;
                                    lastResolvedGeocodePoint = latLng;
                                }
                            });
                        }
                    }
                } catch (IOException | IllegalArgumentException ignored) {
                } finally {
                    runOnUiThread(() -> {
                        if (requestId == geocodeRequestId) pendingGeocodeLocation = null;
                    });
                }
            });
        } catch (RejectedExecutionException ignored) {
            if (requestId == geocodeRequestId) pendingGeocodeLocation = null;
        }
    }

    private void loadAddress() {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
        if (uid == null) return;

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("addresses")
                .document(addressId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) return;

                    edtLabel.setText(snapshot.getString("label"));
                    edtAddress.setText(snapshot.getString("details"));
                    checkboxDefault.setChecked(Boolean.TRUE.equals(snapshot.getBoolean("isDefault")));

                    Double lat = snapshot.getDouble("lat");
                    Double lng = snapshot.getDouble("lng");

                    if (lat != null && lng != null) {
                        selectedLatLng = new LatLng(lat, lng);
                                    updateMapMarker(true);
                        onFirstUsableLocationShown();
                    } else {
                        showMapLoading(false);
                    }
                })
                .addOnFailureListener(e -> showMapLoading(false));
    }

    private void saveAddress() {
        String label = edtLabel.getText().toString().trim();
        String details = edtAddress.getText().toString().trim();

        if (label.isEmpty() || details.isEmpty()) {
            AppMessageUtil.show(this,
                    "Please add an address name and details.",
                    AppMessageUtil.TYPE_ERROR);
            return;
        }

        if (selectedLatLng == null) {
            AppMessageUtil.show(this,
                    "Please wait until your location is loaded or pick a place on the map.",
                    AppMessageUtil.TYPE_ERROR);
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
        if (uid == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String docId = TextUtils.isEmpty(addressId)
                ? db.collection("users").document(uid).collection("addresses").document().getId()
                : addressId;

        Map<String, Object> data = new HashMap<>();
        data.put("label", label);
        data.put("details", details);
        data.put("isDefault", checkboxDefault.isChecked());
        data.put("lat", selectedLatLng.latitude);
        data.put("lng", selectedLatLng.longitude);

        btnSave.setEnabled(false);
        if (checkboxDefault.isChecked()) {
            db.collection("users").document(uid).collection("addresses").get()
                    .addOnSuccessListener(snapshots -> {
                        com.google.firebase.firestore.WriteBatch batch = db.batch();

                        for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots) {
                            if (!doc.getId().equals(docId)
                                    && Boolean.TRUE.equals(doc.getBoolean("isDefault"))) {
                                batch.update(doc.getReference(), "isDefault", false);
                            }
                        }

                        batch.set(
                                db.collection("users")
                                        .document(uid)
                                        .collection("addresses")
                                        .document(docId),
                                data,
                                SetOptions.merge()
                        );

                        batch.commit()
                                .addOnSuccessListener(unused -> {
                                    AppMessageUtil.show(
                                            this,
                                            TextUtils.isEmpty(addressId)
                                                    ? "Address added successfully."
                                                    : "Address updated successfully.",
                                            AppMessageUtil.TYPE_SUCCESS
                                    );
                                    setResult(RESULT_OK);
                                    finish();
                                })
                                .addOnFailureListener(e ->
                                        addressSaveFailed());
                    })
                    .addOnFailureListener(e ->
                            addressSaveFailed());
            return;
        }

        db.collection("users")
                .document(uid)
                .collection("addresses")
                .document(docId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    AppMessageUtil.show(
                            this,
                            TextUtils.isEmpty(addressId)
                                    ? "Address added successfully."
                                    : "Address updated successfully.",
                            AppMessageUtil.TYPE_SUCCESS
                    );
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e ->
                        addressSaveFailed());
    }

    private void addressSaveFailed() {
        btnSave.setEnabled(true);
        AppMessageUtil.show(this, "Could not save address.", AppMessageUtil.TYPE_ERROR);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (googleMap != null && !isEditing && !hasUserMovedMap && !locationSettled
                && !locationRequestPending && !locationPromptPending && hasLocationPermission()) {
            checkLocationSettingsAndFetch(false, false, true);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        if (selectedLatLng != null) {
            outState.putDouble("selectedLatitude", selectedLatLng.latitude);
            outState.putDouble("selectedLongitude", selectedLatLng.longitude);
            outState.putBoolean("manualLocation", hasUserMovedMap);
            outState.putBoolean("locationSettled", locationSettled);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        cancelPendingLocation();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        activeLocationRequestId++;
        mapHandler.removeCallbacksAndMessages(null);
        geocodeRequestId++;
        bestLiveLocationCandidate = null;
        stopLocationRequests();

        if (!geocodeExecutor.isShutdown()) {
            geocodeExecutor.shutdownNow();
        }

        super.onDestroy();
    }
}

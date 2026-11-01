package com.example.gpstracker

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.button.MaterialButton
import java.util.Locale

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        private const val PREFERRED_INTERVAL_MS = 2000L
        private const val MIN_INTERVAL_MS = 1000L
        // Filter inaccurate GPS fixes worse than 30 metres
        private const val MAX_ACCEPTED_ACCURACY_M = 30f
        // Filter micro-jitter less than 3 metres for route/distance accumulation
        private const val MIN_ACCEPTED_MOVE_M = 3f
        private const val DEFAULT_CAMERA_ZOOM = 16.5f
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var historyStore: LocationHistoryStore

    // UI Elements
    private lateinit var tvCoordinates: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvAccuracy: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvPointCount: TextView
    private lateinit var tvTrackingStatus: TextView
    private lateinit var tvWarningBanner: TextView
    private lateinit var btnStart: MaterialButton
    private lateinit var btnStop: MaterialButton
    private lateinit var btnShare: MaterialButton
    private lateinit var btnClear: MaterialButton

    // Map and Route State
    private var googleMap: GoogleMap? = null
    private var currentMarker: Marker? = null
    private val polylines = mutableListOf<Polyline>()
    private var currentSegmentPolyline: Polyline? = null
    private val currentSegmentPoints = mutableListOf<LatLng>()

    // Tracking State
    private var isTracking = false
    private var isCallbackSubscribed = false
    private var currentSegmentId: Long = 0L
    private var lastAcceptedLocation: Location? = null
    private var latestLocation: Location? = null
    private var totalDistanceMetres = 0.0
    private var savedPointsCount = 0

    // Runtime Permission Launcher
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            when {
                fineGranted -> {
                    enableMapMyLocation()
                    startTracking()
                }
                coarseGranted -> {
                    Toast.makeText(
                        this,
                        "Approximate location granted. For precise 30m tracking, enable Fine Location.",
                        Toast.LENGTH_LONG
                    ).show()
                    enableMapMyLocation()
                    startTracking()
                }
                else -> {
                    Toast.makeText(
                        this,
                        getString(R.string.permission_denied_message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

    // Location Callback
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                handleNewLocation(location)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        historyStore = LocationHistoryStore(this)

        checkGooglePlayServices()
        checkMapsApiKey()

        // Initialize Map
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        // Restore saved points from CSV storage
        restoreHistoryData()
        updateDisplay(null)
    }

    private fun initViews() {
        tvCoordinates = findViewById(R.id.tvCoordinates)
        tvSpeed = findViewById(R.id.tvSpeed)
        tvAccuracy = findViewById(R.id.tvAccuracy)
        tvDistance = findViewById(R.id.tvDistance)
        tvPointCount = findViewById(R.id.tvPointCount)
        tvTrackingStatus = findViewById(R.id.tvTrackingStatus)
        tvWarningBanner = findViewById(R.id.tvWarningBanner)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnShare = findViewById(R.id.btnShare)
        btnClear = findViewById(R.id.btnClear)

        btnStart.setOnClickListener { onStartClicked() }
        btnStop.setOnClickListener { onStopClicked() }
        btnShare.setOnClickListener { onShareClicked() }
        btnClear.setOnClickListener { onClearClicked() }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isCompassEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = true

        enableMapMyLocation()
        redrawAllPolylinesOnMap()

        // Move camera to latest saved position if available
        latestLocation?.let { loc ->
            val latLng = LatLng(loc.latitude, loc.longitude)
            updateMapMarker(latLng)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_CAMERA_ZOOM))
        }
    }

    override fun onResume() {
        super.onResume()
        // Resume tracking updates if the user was actively tracking
        if (isTracking && !isCallbackSubscribed) {
            subscribeLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        // Keep tracking active ONLY in foreground to preserve battery and follow Android lifecycle rules
        unsubscribeLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        unsubscribeLocationUpdates()
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    private fun enableMapMyLocation() {
        if (hasLocationPermission()) {
            googleMap?.isMyLocationEnabled = true
        }
    }

    private fun onStartClicked() {
        if (!hasLocationPermission()) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        if (!LocationManagerCompat.isLocationEnabled(lm)) {
            showEnableGpsDialog()
            return
        }

        startTracking()
    }

    private fun startTracking() {
        isTracking = true
        // Assign a new segment ID for this continuous tracking session so Stop followed by Start
        // never introduces a false connecting segment
        currentSegmentId = System.currentTimeMillis()
        lastAcceptedLocation = null
        currentSegmentPolyline = null
        currentSegmentPoints.clear()

        updateTrackingStateUi(true)
        subscribeLocationUpdates()
        Toast.makeText(this, "Tracking started", Toast.LENGTH_SHORT).show()
    }

    private fun onStopClicked() {
        stopTracking()
        Toast.makeText(this, "Tracking stopped", Toast.LENGTH_SHORT).show()
    }

    private fun stopTracking() {
        isTracking = false
        unsubscribeLocationUpdates()

        // Finalize current segment to prevent false segment jumps
        lastAcceptedLocation = null
        currentSegmentPolyline = null
        currentSegmentPoints.clear()

        updateTrackingStateUi(false)
    }

    private fun onShareClicked() {
        val loc = latestLocation
        if (loc == null) {
            Toast.makeText(this, getString(R.string.no_location_to_share), Toast.LENGTH_SHORT).show()
            return
        }

        val lat = loc.latitude
        val lng = loc.longitude
        val shareText = String.format(
            Locale.US,
            "My current GPS location:\nLatitude: %.6f\nLongitude: %.6f\nSpeed: %.1f km/h\nAccuracy: ±%.1f m\nhttps://maps.google.com/?q=%.6f,%.6f",
            lat, lng, (if (loc.hasSpeed()) loc.speed * 3.6f else 0f), (if (loc.hasAccuracy()) loc.accuracy else 0f), lat, lng
        )

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, getString(R.string.share))
        startActivity(shareIntent)
    }

    private fun onClearClicked() {
        AlertDialog.Builder(this)
            .setTitle("Clear Route History")
            .setMessage("Are you sure you want to clear all saved GPS points and recorded route?")
            .setPositiveButton("Clear") { _, _ ->
                executeClear()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun executeClear() {
        historyStore.clear()
        savedPointsCount = 0
        totalDistanceMetres = 0.0
        lastAcceptedLocation = null
        currentSegmentPolyline = null
        currentSegmentPoints.clear()

        // Clear all map polylines
        for (poly in polylines) {
            poly.remove()
        }
        polylines.clear()

        currentMarker?.remove()
        currentMarker = null

        updateDisplay(latestLocation)
        Toast.makeText(this, getString(R.string.history_cleared), Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("MissingPermission")
    private fun subscribeLocationUpdates() {
        if (!hasLocationPermission() || isCallbackSubscribed) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, PREFERRED_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()

        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        isCallbackSubscribed = true
    }

    private fun unsubscribeLocationUpdates() {
        if (isCallbackSubscribed) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            isCallbackSubscribed = false
        }
    }

    private fun handleNewLocation(location: Location) {
        latestLocation = location

        // Always update live coordinate and accuracy readouts on the UI
        updateDisplay(location)
        updateMapMarker(LatLng(location.latitude, location.longitude))

        // If tracking is active, evaluate filters for route and distance calculation
        if (!isTracking) return

        // 1. Accuracy Filter: ignore GPS fixes worse than 30 metres
        if (location.hasAccuracy() && location.accuracy > MAX_ACCEPTED_ACCURACY_M) {
            return
        }

        // 2. Minimum Movement Filter: ignore jitter below 3 metres
        val prev = lastAcceptedLocation
        if (prev != null) {
            val distance = prev.distanceTo(location)
            if (distance < MIN_ACCEPTED_MOVE_M) {
                return
            }
            totalDistanceMetres += distance
        }
        lastAcceptedLocation = location

        // 3. Add point to current segment and persist to CSV
        val latLng = LatLng(location.latitude, location.longitude)
        appendPointToActiveSegment(latLng)

        val trackPoint = TrackPoint(
            lat = location.latitude,
            lng = location.longitude,
            time = location.time,
            speed = if (location.hasSpeed()) location.speed else 0f,
            accuracy = if (location.hasAccuracy()) location.accuracy else 0f,
            segmentId = currentSegmentId
        )
        historyStore.append(trackPoint)
        savedPointsCount++

        updateDisplay(location)
    }

    private fun appendPointToActiveSegment(latLng: LatLng) {
        val map = googleMap ?: return
        if (currentSegmentPolyline == null) {
            currentSegmentPoints.clear()
            currentSegmentPoints.add(latLng)
            val poly = map.addPolyline(
                PolylineOptions()
                    .addAll(currentSegmentPoints)
                    .color(Color.parseColor("#1565C0"))
                    .width(10f)
                    .geodesic(true)
            )
            currentSegmentPolyline = poly
            polylines.add(poly)
        } else {
            currentSegmentPoints.add(latLng)
            currentSegmentPolyline?.points = currentSegmentPoints
        }
    }

    private fun updateMapMarker(latLng: LatLng) {
        val map = googleMap ?: return
        if (currentMarker == null) {
            currentMarker = map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Current Location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_CAMERA_ZOOM))
        } else {
            currentMarker?.position = latLng
            map.animateCamera(CameraUpdateFactory.newLatLng(latLng))
        }
    }

    private fun updateDisplay(location: Location?) {
        if (location != null) {
            tvCoordinates.text = String.format(
                Locale.US, "Lat: %.6f   Lng: %.6f", location.latitude, location.longitude
            )
            val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0.0f
            tvSpeed.text = String.format(Locale.US, "%.1f km/h", speedKmh)

            tvAccuracy.text = if (location.hasAccuracy()) {
                String.format(Locale.US, "±%.0f m", location.accuracy)
            } else {
                "—"
            }
        } else {
            tvCoordinates.text = getString(R.string.waiting_fix)
            tvSpeed.text = "0.0 km/h"
            tvAccuracy.text = "—"
        }

        val distanceKm = totalDistanceMetres / 1000.0
        tvDistance.text = String.format(Locale.US, "%.2f km", distanceKm)
        tvPointCount.text = savedPointsCount.toString()
    }

    private fun updateTrackingStateUi(tracking: Boolean) {
        btnStart.isEnabled = !tracking
        btnStop.isEnabled = tracking

        if (tracking) {
            tvTrackingStatus.text = getString(R.string.tracking_active)
            tvTrackingStatus.setTextColor(ContextCompat.getColor(this, R.color.statusActive))
        } else {
            tvTrackingStatus.text = getString(R.string.tracking_idle)
            tvTrackingStatus.setTextColor(ContextCompat.getColor(this, R.color.statusIdle))
        }
    }

    private fun restoreHistoryData() {
        val points = historyStore.loadAll()
        savedPointsCount = points.size
        totalDistanceMetres = 0.0

        if (points.isEmpty()) return

        // Calculate total distance using Android Location.distanceTo() within continuous segments
        val grouped = points.groupBy { it.segmentId }
        for ((_, segment) in grouped) {
            for (i in 1 until segment.size) {
                val loc1 = segment[i - 1].toLocation()
                val loc2 = segment[i].toLocation()
                totalDistanceMetres += loc1.distanceTo(loc2)
            }
        }

        latestLocation = points.last().toLocation()
    }

    private fun redrawAllPolylinesOnMap() {
        val map = googleMap ?: return
        for (poly in polylines) {
            poly.remove()
        }
        polylines.clear()

        val points = historyStore.loadAll()
        if (points.isEmpty()) return

        // Draw separate polylines for each segment so no connecting line connects gaps
        val grouped = points.groupBy { it.segmentId }
        for ((_, segment) in grouped) {
            if (segment.isNotEmpty()) {
                val latLngs = segment.map { it.latLng }
                val poly = map.addPolyline(
                    PolylineOptions()
                        .addAll(latLngs)
                        .color(Color.parseColor("#1565C0"))
                        .width(10f)
                        .geodesic(true)
                )
                polylines.add(poly)
            }
        }
    }

    private fun checkGooglePlayServices() {
        val availability = GoogleApiAvailability.getInstance()
        val resultCode = availability.isGooglePlayServicesAvailable(this)
        if (resultCode != ConnectionResult.SUCCESS) {
            if (availability.isUserResolvableError(resultCode)) {
                availability.getErrorDialog(this, resultCode, 9000)?.show()
            } else {
                Toast.makeText(this, getString(R.string.play_services_missing), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkMapsApiKey() {
        // Handle unavailable or missing Maps API key gracefully
        val apiKey = BuildConfig.MAPS_API_KEY
        if (apiKey.isBlank() || apiKey == "YOUR_GOOGLE_MAPS_API_KEY_HERE") {
            tvWarningBanner.visibility = View.VISIBLE
            tvWarningBanner.text = getString(R.string.missing_api_key_banner)
        } else {
            tvWarningBanner.visibility = View.GONE
        }
    }

    private fun showEnableGpsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Location Disabled")
            .setMessage(getString(R.string.location_disabled_message))
            .setPositiveButton("Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

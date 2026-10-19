package com.example.campusexplorer

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sharedPrefs: SharedPreferences

    /* =========================================
       PERMISSION LAUNCHER
       ========================================= */
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            recordCurrentLocation()
        } else {
            Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().userAgentValue = "CampusNatureQuest_BZ_1.0"
        setContentView(R.layout.activity_main)

        // Initialize Local Storage
        sharedPrefs = getSharedPreferences("NatureQuestPrefs", Context.MODE_PRIVATE)

        // Initialize Map
        map = findViewById(R.id.mapView)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(17.0)

        // Default to Campus Coordinates
        val startPoint = GeoPoint(34.1623, -119.0433)
        map.controller.setCenter(startPoint)

        // Initialize GPS Client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Button Listeners
        findViewById<Button>(R.id.btnRecordSighting).setOnClickListener {
            checkPermissionAndRecord()
        }

        findViewById<Button>(R.id.btnCenterLocation).setOnClickListener {
            centerOnSavedObservation()
        }

        // Load previous sighting if it exists
        loadLastObservation()
    }

    /* =========================================
       LOCATION & STORAGE LOGIC
       ========================================= */
    private fun checkPermissionAndRecord() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            recordCurrentLocation()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun recordCurrentLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val lat = location.latitude
                val lon = location.longitude

                // Save coordinates locally
                sharedPrefs.edit()
                    .putFloat("last_lat", lat.toFloat())
                    .putFloat("last_lon", lon.toFloat())
                    .apply()

                placeMarkerAndCenter(lat, lon)
                Toast.makeText(this, "Sighting recorded!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Acquiring GPS fix... try again", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadLastObservation() {
        val lat = sharedPrefs.getFloat("last_lat", Float.NaN)
        val lon = sharedPrefs.getFloat("last_lon", Float.NaN)

        if (!lat.isNaN() && !lon.isNaN()) {
            placeMarkerAndCenter(lat.toDouble(), lon.toDouble())
        }
    }

    private fun centerOnSavedObservation() {
        val lat = sharedPrefs.getFloat("last_lat", Float.NaN)
        val lon = sharedPrefs.getFloat("last_lon", Float.NaN)

        if (!lat.isNaN() && !lon.isNaN()) {
            map.controller.animateTo(GeoPoint(lat.toDouble(), lon.toDouble()))
        } else {
            Toast.makeText(this, "No sightings recorded yet", Toast.LENGTH_SHORT).show()
        }
    }

    private fun placeMarkerAndCenter(lat: Double, lon: Double) {
        val point = GeoPoint(lat, lon)
        val marker = Marker(map)
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = "Saved Sighting"

        // Clear old markers and add new one
        map.overlays.clear()
        map.overlays.add(marker)
        map.invalidate()

        map.controller.animateTo(point)
    }

    /* =========================================
       LIFECYCLE MANAGEMENT
       ========================================= */
    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}
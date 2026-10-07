package com.example.wheredidipark

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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            recordObservationAtCurrentLocation()
        } else {
            Toast.makeText(this, "Location permission required to tag sightings", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // OSM User-Agent configuration
        org.osmdroid.config.Configuration.getInstance().userAgentValue = "CampusNatureQuest_BZ_1.0"

        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sharedPrefs = getSharedPreferences("NatureQuestDummy", Context.MODE_PRIVATE)

        map = findViewById(R.id.mapView)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(17.0)

        loadLastObservation()

        findViewById<Button>(R.id.btnRecordSighting).setOnClickListener {
            checkPermissionAndRecord()
        }

        findViewById<Button>(R.id.btnCenterLocation).setOnClickListener {
            centerOnSavedObservation()
        }
    }

    private fun checkPermissionAndRecord() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            recordObservationAtCurrentLocation()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun recordObservationAtCurrentLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val obsPoint = GeoPoint(location.latitude, location.longitude)

                    with(sharedPrefs.edit()) {
                        putFloat("OBS_LAT", location.latitude.toFloat())
                        putFloat("OBS_LNG", location.longitude.toFloat())
                        apply()
                    }

                    val marker = Marker(map).apply {
                        position = obsPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Sample Sighting"
                        snippet = "Lat: ${location.latitude}, Lng: ${location.longitude}"
                    }
                    map.overlays.add(marker)
                    map.controller.animateTo(obsPoint)
                    map.invalidate()

                    Toast.makeText(this, "Sighting pinned to map!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Acquiring GPS fix... try again", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun centerOnSavedObservation() {
        val lat = sharedPrefs.getFloat("OBS_LAT", 0f)
        val lng = sharedPrefs.getFloat("OBS_LNG", 0f)

        if (lat == 0f && lng == 0f) {
            Toast.makeText(this, "No sightings recorded yet", Toast.LENGTH_SHORT).show()
            return
        }

        val point = GeoPoint(lat.toDouble(), lng.toDouble())
        map.controller.animateTo(point)
    }

    private fun loadLastObservation() {
        val lat = sharedPrefs.getFloat("OBS_LAT", 0f)
        val lng = sharedPrefs.getFloat("OBS_LNG", 0f)

        if (lat != 0f && lng != 0f) {
            val point = GeoPoint(lat.toDouble(), lng.toDouble())
            val marker = Marker(map).apply {
                position = point
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Previous Sighting"
            }
            map.overlays.add(marker)
            map.controller.setCenter(point)
        } else {
            // Default center point
            map.controller.setCenter(GeoPoint(34.1706, -118.8376))
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}
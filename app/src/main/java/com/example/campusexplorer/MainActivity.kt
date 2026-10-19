package com.example.campusexplorer

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.example.campusexplorer.ui.NatureQuestScreen
import com.example.campusexplorer.ui.camera.CameraScreen
import com.example.campusexplorer.ui.map.MapScreen
import com.example.campusexplorer.ui.theme.CampusExplorerTheme
import kotlinx.coroutines.launch

private enum class Tab(
    val label: String,
    @param:DrawableRes val icon: Int
) {
    Map("Map", R.drawable.ic_map),
    Camera("Camera", R.drawable.ic_camera),
}

class MainActivity : ComponentActivity() {
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

        enableEdgeToEdge()

        setContent {
            CampusExplorerTheme {
                CampusExplorerApp()
            }
        }
    }
}

@Composable
fun CampusExplorerApp() {
    // true = Nature Quest design
    // false = original navigation layout
    val useNatureQuestUI = true

    var tab by rememberSaveable {
        mutableStateOf(Tab.Map)
    }

    val snackbar = remember {
        SnackbarHostState()
    }

    val scope = rememberCoroutineScope()

    val showMessage: (String) -> Unit = { message ->
        scope.launch {
            snackbar.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),

        snackbarHost = {
            SnackbarHost(snackbar)
        },

        bottomBar = {
            if (!useNatureQuestUI) {
                NavigationBar {
                    Tab.entries.forEach { currentTab ->
                        NavigationBarItem(
                            selected = tab == currentTab,

                            onClick = {
                                tab = currentTab
                            },

                            icon = {
                                Icon(
                                    painter = painterResource(
                                        currentTab.icon
                                    ),
                                    contentDescription = null
                                )
                            },

                            label = {
                                Text(currentTab.label)
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (useNatureQuestUI) {
            NatureQuestScreen(
                mapContent = {
                    MapScreen(
                        modifier = Modifier.fillMaxSize()
                    )
                },

                cameraContent = {
                    CameraScreen(
                        onMessage = showMessage,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            )
        } else {
            val contentModifier = Modifier.padding(innerPadding)

            when (tab) {
                Tab.Map -> {
                    MapScreen(contentModifier)
                }

                Tab.Camera -> {
                    CameraScreen(
                        onMessage = showMessage,
                        modifier = contentModifier
                    )
                }
            }
        }
    }
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
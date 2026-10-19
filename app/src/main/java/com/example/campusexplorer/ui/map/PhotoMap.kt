package com.example.campusexplorer.ui.map

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.campusexplorer.data.LocationHelper
import com.example.campusexplorer.data.NaturePhoto
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

// Where the map starts: the middle of campus.
private val CAMPUS_CENTER = GeoPoint(34.1623, -119.0433)

// OpenStreetMap view with one marker per photo and a "My Location" button.
@Composable
fun PhotoMap(
    pins: List<NaturePhoto>,

    // Called when a marker is tapped.
    onPinClick: (NaturePhoto) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Used to get the location in the background.
    val scope = rememberCoroutineScope()

    // The map, created once.
    val mapView = remember { createMapView(context) }

    // Move the map to the user's current location, or explain why we can't.
    val centerOnMe: () -> Unit = {
        scope.launch {
            val location = LocationHelper.currentLocation(context)
            if (location != null) {
                mapView.controller.animateTo(GeoPoint(location.latitude, location.longitude))
            } else {
                onMessage("Location unavailable (permission off or GPS unavailable)")
            }
        }
    }

    // Asks for location permission, then centers if it was allowed.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) centerOnMe() else onMessage("Location permission required")
    }

    // Pause and resume the map with the app, and clean it up when the screen closes.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    // Box stacks its children: map at the back, button on top.
    Box(modifier) {

        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),

            // Runs whenever the photo list changes: replace the old markers with new ones.
            update = { map ->
                map.overlays.removeAll { it is Marker }
                pins.forEach { photo ->
                    val lat = photo.latitude ?: return@forEach
                    val lon = photo.longitude ?: return@forEach
                    map.overlays.add(Marker(map).apply {
                        position = GeoPoint(lat, lon)

                        // Point the bottom of the pin at the spot.
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = photo.caption.ifBlank { "(no caption)" }

                        // Open the photo. Returning true skips osmdroid's own pop-up.
                        setOnMarkerClickListener { _, _ ->
                            onPinClick(photo)
                            true
                        }
                    })
                }

                // Redraw the map.
                map.invalidate()
            },
        )

        // Ask for permission first if we don't have it yet.
        Button(
            onClick = {
                if (LocationHelper.hasPermission(context)) {
                    centerOnMe()
                } else {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    )
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Text("My Location")
        }
    }
}

// Sets up an OpenStreetMap view centered on campus.
private fun createMapView(context: Context): MapView {

    // osmdroid needs settings loaded and a user agent before tiles can download.
    Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    Configuration.getInstance().userAgentValue = "CampusNatureQuest_BZ_1.0"

    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)

        // Pinch to zoom.
        setMultiTouchControls(true)
        controller.setZoom(17.0)
        controller.setCenter(CAMPUS_CENTER)
    }
}

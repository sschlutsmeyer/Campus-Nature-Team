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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.campusexplorer.R
import com.example.campusexplorer.data.LocationHelper
import com.example.campusexplorer.data.NaturePhoto
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

// Where the map starts: the middle of campus.
private val CAMPUS_CENTER = GeoPoint(34.1623, -119.0433)

// How far down the pin icons their tip is (0 = top, 1 = bottom). See res/drawable/ic_pin_*.xml.
private const val PIN_TIP = 23f / 24f

// OpenStreetMap view with one marker per photo and a "My Location" button.
// Your pins are turquoise and other users' pins are red.
@Composable
fun PhotoMap(
    pins: List<NaturePhoto>,

    // The signed-in user, to tell their pins apart from everyone else's.
    myUid: String?,

    // Called when a marker is tapped.
    onPinClick: (NaturePhoto) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,

    // A spot to zoom in on (from a post's "Map" button). onFocusShown is called once it's done.
    focus: GeoPoint? = null,
    onFocusShown: () -> Unit = {},
) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Used to get the location in the background.
    val scope = rememberCoroutineScope()

    // The map, created once.
    val mapView = remember { createMapView(context) }

    // Pin icons, loaded once and shared by every marker.
    val myPinIcon = remember { ContextCompat.getDrawable(context, R.drawable.ic_pin_mine) }
    val otherPinIcon = remember { ContextCompat.getDrawable(context, R.drawable.ic_pin_others) }

    // Zoom in on the requested spot. Before the map's first layout it can't animate, so jump instead.
    LaunchedEffect(focus) {
        val point = focus ?: return@LaunchedEffect
        if (mapView.isLayoutOccurred) {
            mapView.controller.animateTo(point, FOCUS_ZOOM, FOCUS_ANIMATION_MS)
        } else {
            mapView.controller.setZoom(FOCUS_ZOOM)
            mapView.controller.setCenter(point)
        }
        onFocusShown()
    }

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
    // clipToBounds stops map tiles from drawing outside the map's area.
    Box(modifier.clipToBounds()) {

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

                        // Turquoise for your pins, red for other users'. Keeps osmdroid's
                        // default icon if ours couldn't be loaded.
                        val pinIcon = if (photo.uid != null && photo.uid == myUid) myPinIcon else otherPinIcon
                        if (pinIcon != null) icon = pinIcon

                        // Point the tip of the pin at the spot.
                        setAnchor(Marker.ANCHOR_CENTER, if (pinIcon != null) PIN_TIP else Marker.ANCHOR_BOTTOM)
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

// How close to zoom in, and how long the move takes, when showing a post's pin.
private const val FOCUS_ZOOM = 18.5
private const val FOCUS_ANIMATION_MS = 800L

// Names our app to OpenStreetMap's servers. They block requests that don't identify the app.
private const val USER_AGENT = "CampusNatureQuest_BZ_1.0"

// OpenStreetMap's standard map tiles. Same as osmdroid's built-in MAPNIK source, except that
// MAPNIK replaces our user agent with "<applicationId>/<version>". Ours is still the template
// "com.example.myapplication", which OSM blocks. Changing applicationId would also mean
// re-registering the app in Firebase, so we send USER_AGENT instead.
// The new name also gives it a fresh tile cache, so blocked tiles saved earlier aren't reused.
private val OSM_TILES = XYTileSource(
    "OpenStreetMap",
    0,
    19,
    256,
    ".png",
    arrayOf("https://tile.openstreetmap.org/"),
    "© OpenStreetMap contributors",

    // At most 2 downloads at once, no bulk downloading, and send our user agent unchanged.
    TileSourcePolicy(
        2,
        TileSourcePolicy.FLAG_NO_BULK or
            TileSourcePolicy.FLAG_NO_PREVENTIVE or
            TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL,
    ),
)

// Sets up an OpenStreetMap view centered on campus.
private fun createMapView(context: Context): MapView {

    // osmdroid needs settings loaded and a user agent before tiles can download.
    Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    Configuration.getInstance().userAgentValue = USER_AGENT

    return MapView(context).apply {
        setTileSource(OSM_TILES)

        // Pinch to zoom.
        setMultiTouchControls(true)
        controller.setZoom(17.0)
        controller.setCenter(CAMPUS_CENTER)
    }
}

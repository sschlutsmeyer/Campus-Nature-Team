package com.example.campusexplorer.ui.map

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.campusexplorer.data.NaturePhoto
import com.example.campusexplorer.data.PhotoRepository
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import java.text.DateFormat

@Composable
fun MapScreen(modifier: Modifier = Modifier, onRecordSighting: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val photosFlow = remember { PhotoRepository.observePhotos() }
    val photos by photosFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val pins = photos.filter { it.latitude != null && it.longitude != null }
    var selectedPhoto by remember { mutableStateOf<NaturePhoto?>(null) }

    val map = remember(context) {
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("NatureQuestPrefs", Context.MODE_PRIVATE),
        )
        Configuration.getInstance().userAgentValue = "CampusNatureQuest_1.0"
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(17.0)
            controller.setCenter(GeoPoint(34.1623, -119.0433))
            overlays.add(CopyrightOverlay(context))
        }
    }
    val photoMarkers = remember(map) { mutableListOf<Marker>() }
    var centeredOnPhotos by remember(map) { mutableStateOf(false) }

    // Forward lifecycle events and release the map when leaving this tab.
    DisposableEffect(map, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            map.onPause()
            map.onDetach()
        }
    }

    // Refresh only photo markers, keeping the map's other overlays intact.
    LaunchedEffect(map, pins) {
        photoMarkers.forEach {
            map.overlays.remove(it)
            it.onDetach(map)
        }
        photoMarkers.clear()
        pins.forEach { photo ->
            val marker = Marker(map).apply {
                position = GeoPoint(requireNotNull(photo.latitude), requireNotNull(photo.longitude))
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = photo.caption.ifBlank { "Nature photo" }
                setOnMarkerClickListener { _, _ ->
                    selectedPhoto = photo
                    true
                }
            }
            photoMarkers.add(marker)
            map.overlays.add(marker)
        }
        // Center once, so background updates do not interrupt panning or zooming.
        if (!centeredOnPhotos && pins.isNotEmpty()) {
            val photo = pins.first()
            map.controller.setCenter(
                GeoPoint(requireNotNull(photo.latitude), requireNotNull(photo.longitude)),
            )
            centeredOnPhotos = true
        }
        map.invalidate()
    }

    selectedPhoto?.let { photo ->
        PhotoDetailsDialog(photo = photo, onDismiss = { selectedPhoto = null })
    }

    Column(modifier.fillMaxSize()) {
        AndroidView(factory = { map }, modifier = Modifier.fillMaxWidth().weight(2f))
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The camera's Save action creates a photo and its map pin together.
            Button(onClick = onRecordSighting) { Text("Record Sighting") }
            OutlinedButton(onClick = {
                val photo = pins.firstOrNull()
                if (photo == null) {
                    Toast.makeText(context, "No saved photos with a location yet", Toast.LENGTH_SHORT).show()
                } else {
                    map.controller.animateTo(
                        GeoPoint(requireNotNull(photo.latitude), requireNotNull(photo.longitude)),
                    )
                }
            }) { Text("Last Photo") }
        }
        Text(
            "Photo pins (${pins.size} of ${photos.size} photos have a location)",
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(photos, key = { it.id }) { photo ->
                PhotoPinRow(photo = photo, onClick = { selectedPhoto = photo })
            }
        }
    }
}

@Composable
private fun PhotoPinRow(photo: NaturePhoto, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClickLabel = "View photo", onClick = onClick),
        headlineContent = { Text(photo.caption.ifBlank { "(no caption)" }) },
        supportingContent = {
            val where = if (photo.latitude != null && photo.longitude != null) {
                "%.5f, %.5f".format(photo.latitude, photo.longitude)
            } else "no location"
            val whenTaken = photo.createdAt?.let {
                DateFormat.getDateTimeInstance().format(it)
            } ?: "just now"
            Text("$where · $whenTaken")
        },
    )
}

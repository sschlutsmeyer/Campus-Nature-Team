package com.example.campusexplorer.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.campusexplorer.data.NaturePhoto
import com.example.campusexplorer.data.PhotoRepository
import java.text.DateFormat

// The Map tab: a map of photo pins on top and the list of photos below.
@Composable
fun MapScreen(onMessage: (String) -> Unit, modifier: Modifier = Modifier) {

    // Live list of photos. Updates on its own when photos are added or deleted.
    val photosFlow = remember { PhotoRepository.observePhotos() }
    val photos by photosFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // Photos with a location, which can go on the map.
    val pins = photos.filter { it.latitude != null && it.longitude != null }

    // The photo that was tapped, or null if none is open.
    var selectedPhoto by remember { mutableStateOf<NaturePhoto?>(null) }

    Column(modifier.fillMaxSize()) {

        // The map, in the top half (see PhotoMap.kt). Tapping a pin opens that photo.
        PhotoMap(
            pins = pins,
            onPinClick = { selectedPhoto = it },
            onMessage = onMessage,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )

        // Bottom half: a heading and the photo list.
        Column(
            modifier = Modifier.weight(1f).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Photo pins (${pins.size} of ${photos.size} photos have a location)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            // Scrollable list with one row per photo. Tapping a row opens it.
            LazyColumn {
                items(photos, key = { it.id }) { photo ->
                    PhotoPinRow(photo = photo, onClick = { selectedPhoto = photo })
                }
            }
        }
    }

    // Show the tapped photo in a pop-up (see PhotoDetailsDialog.kt).
    selectedPhoto?.let { photo ->
        // Use the latest document so an open dialog also receives renamed captions.
        val currentPhoto = photos.firstOrNull { it.id == photo.id } ?: photo
        PhotoDetailsDialog(photo = currentPhoto, onDismiss = { selectedPhoto = null }, onMessage = onMessage)
    }
}

// One row in the photo list: caption, location, and time taken.
@Composable
private fun PhotoPinRow(photo: NaturePhoto, onClick: () -> Unit) {

    ListItem(
        headlineContent = { Text(photo.caption.ifBlank { "(no caption)" }) },
        supportingContent = {

            // Coordinates to 5 decimal places, or "no location".
            val where = if (photo.latitude != null && photo.longitude != null) {
                "%.5f, %.5f".format(photo.latitude, photo.longitude)
            } else "no location"

            // The server fills in the time shortly after saving, so show "just now" until then.
            val whenTaken = photo.createdAt?.let { DateFormat.getDateTimeInstance().format(it) } ?: "just now"
            Text("$where · $whenTaken")
        },

        // Make the whole row tappable.
        modifier = Modifier.clickable(onClickLabel = "View photo", onClick = onClick),
    )
}

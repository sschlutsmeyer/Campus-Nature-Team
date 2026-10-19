package com.example.campusexplorer.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.campusexplorer.data.NaturePhoto
import com.example.campusexplorer.data.PhotoRepository
import java.text.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    val photosFlow = remember { PhotoRepository.observePhotos() }
    val photos by photosFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val pins = photos.filter { it.latitude != null && it.longitude != null }
    var selectedPhoto by remember { mutableStateOf<NaturePhoto?>(null) }

    selectedPhoto?.let { photo ->
        PhotoDetailsDialog(
            photo = photo,
            onDismiss = { selectedPhoto = null },
        )
    }

    Column(modifier.fillMaxSize()) {
        // TODO: Replace this placeholder with an OpenStreetMap view (e.g. osmdroid or MapLibre)
        //  and draw one marker per item in `pins`.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text(
                "Map coming soon\n(OpenStreetMap)",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            modifier = Modifier.weight(1f).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Photo pins (${pins.size} of ${photos.size} photos have a location)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyColumn {
                items(photos, key = { it.id }) { photo ->
                    PhotoPinRow(
                        photo = photo,
                        onClick = { selectedPhoto = photo },
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoPinRow(photo: NaturePhoto, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(
            onClickLabel = "View photo",
            onClick = onClick,
        ),
        headlineContent = {
            Text(photo.caption.ifBlank { "(no caption)" })
        },
        supportingContent = {
            val where = if (
                photo.latitude != null && photo.longitude != null
            ) {
                "%.5f, %.5f".format(photo.latitude, photo.longitude)
            } else {
                "no location"
            }

            val whenTaken = photo.createdAt?.let {
                DateFormat.getDateTimeInstance().format(it)
            } ?: "just now"

            Text("$where · $whenTaken")
        },
    )
}

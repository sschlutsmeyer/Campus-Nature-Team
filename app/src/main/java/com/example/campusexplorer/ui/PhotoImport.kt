package com.example.campusexplorer.ui

import android.Manifest
import android.location.Location
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.campusexplorer.data.CameraRoll
import com.example.campusexplorer.data.LocalPhotoStore
import com.example.campusexplorer.data.LocationHelper
import com.example.campusexplorer.data.POST_TITLE_MAX
import com.example.campusexplorer.data.PhotoRepository
import com.example.campusexplorer.data.PickedPhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat

// Label for this file's messages in Logcat.
private const val TAG = "PhotoImport"

// Adds a photo from the phone's camera roll to the app (used by the Map and Gallery).
// content gets a function that starts it: pick a photo, then name it and choose its location.
// The location defaults to the one saved in the photo itself.
@Composable
fun PhotoImportHost(
    onMessage: (String) -> Unit,

    // Called after the photo is saved, with its id and location (null if it has none).
    onImported: (photoId: String, location: Location?) -> Unit = { _, _ -> },
    content: @Composable (startImport: () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // The picked photo being reviewed, and whether it's still being read.
    var picked by remember { mutableStateOf<PickedPhoto?>(null) }
    var reading by remember { mutableStateOf(false) }

    // Android's file chooser. Unlike the newer photo picker, it gives us the original file, location
    // included. Reads the chosen photo and its metadata in the background.
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        reading = true
        scope.launch {
            try {
                picked = CameraRoll.readPicked(context, uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't read picked photo", e)
                onMessage(e.message ?: "Couldn't open that photo.")
            } finally {
                reading = false
            }
        }
    }
    val pickPhoto = {
        pickLauncher.launch(arrayOf("image/*"))
    }

    // Without this permission Android removes a photo's location before we see it.
    // The photo can still be added if it's refused, just without its saved location.
    val mediaLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { pickPhoto() }

    content {
        if (CameraRoll.needsMediaLocationPermission(context)) {
            mediaLocationLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        } else {
            pickPhoto()
        }
    }

    if (reading) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("Opening photo…", modifier = Modifier.padding(start = 16.dp))
                }
            },
        )
    }

    picked?.let { photo ->
        ImportPhotoDialog(
            photo = photo,
            onDismiss = { picked = null },
            onSaved = { id, location ->
                picked = null
                onMessage(if (location == null) "Photo added without a location" else "Photo added!")
                onImported(id, location)
            },
            onMessage = onMessage,
        )
    }
}

// Where an imported photo's pin goes.
private enum class LocationChoice { Photo, Current, None }

// Shows the picked photo with a name box and location choices, then saves it.
@Composable
private fun ImportPhotoDialog(
    photo: PickedPhoto,
    onDismiss: () -> Unit,
    onSaved: (photoId: String, location: Location?) -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var caption by remember { mutableStateOf("") }
    var keepLocalCopy by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

    // Default to the photo's own location; otherwise where you are now.
    var choice by remember {
        mutableStateOf(if (photo.location != null) LocationChoice.Photo else LocationChoice.Current)
    }

    // Your current location, looked up once in case you choose it.
    var current by remember { mutableStateOf<Location?>(null) }
    var lookingUp by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        current = LocationHelper.currentLocation(context)
        lookingUp = false
        if (current == null && choice == LocationChoice.Current) choice = LocationChoice.None
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Add photo") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Image(
                    bitmap = photo.preview.asImageBitmap(),
                    contentDescription = "Picked photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                )

                photo.takenAt?.let {
                    Text(
                        "Taken ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it.take(POST_TITLE_MAX) },
                    label = { Text("What did you find?") },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Location", style = MaterialTheme.typography.titleSmall)
                Column(Modifier.selectableGroup()) {
                    val photoLocation = photo.location
                    LocationOption(
                        selected = choice == LocationChoice.Photo,
                        enabled = !saving && photoLocation != null,
                        label = if (photoLocation != null) {
                            "Where the photo was taken (${formatLocation(photoLocation)})"
                        } else {
                            "This photo has no saved location"
                        },
                        onSelect = { choice = LocationChoice.Photo },
                    )
                    LocationOption(
                        selected = choice == LocationChoice.Current,
                        enabled = !saving && (lookingUp || current != null),
                        label = when {
                            lookingUp -> "My current location (finding…)"
                            current != null -> "My current location (${formatLocation(checkNotNull(current))})"
                            else -> "My current location (unavailable)"
                        },
                        onSelect = { choice = LocationChoice.Current },
                    )
                    LocationOption(
                        selected = choice == LocationChoice.None,
                        enabled = !saving,
                        label = "No location (won't appear on the map)",
                        onSelect = { choice = LocationChoice.None },
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.toggleable(
                        value = keepLocalCopy,
                        enabled = !saving,
                        role = Role.Checkbox,
                        onValueChange = { keepLocalCopy = it },
                    ),
                ) {
                    Checkbox(checked = keepLocalCopy, onCheckedChange = null, enabled = !saving)
                    Text("Keep a quick-loading copy in the app", modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                // Wait for the current location before saving with it.
                enabled = !saving && !(choice == LocationChoice.Current && lookingUp),
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            val location = when (choice) {
                                LocationChoice.Photo -> photo.location
                                LocationChoice.Current -> current
                                LocationChoice.None -> null
                            }
                            val id = PhotoRepository.save(photo.jpeg, caption.trim(), location)
                            if (keepLocalCopy) {
                                try {
                                    LocalPhotoStore.save(context, id, photo.jpeg)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Couldn't save local copy", e)
                                }
                            }
                            onSaved(id, location)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e(TAG, "Import failed", e)
                            onMessage("Couldn't add photo: ${e.message}")
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { Text(if (saving) "Adding…" else "Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        },
    )
}

@Composable
private fun LocationOption(selected: Boolean, enabled: Boolean, label: String, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private fun formatLocation(location: Location) = "%.5f, %.5f".format(location.latitude, location.longitude)

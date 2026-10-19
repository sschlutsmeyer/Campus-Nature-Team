// The "package" says which folder/namespace this file belongs to.
package com.example.campusexplorer.ui.map

// "import" lines pull in code from Android, Jetpack Compose, and our own project.
// Bitmap: an image stored in memory as pixels.
import android.graphics.Bitmap
// BitmapFactory: turns downloaded JPEG bytes into a Bitmap we can show.
import android.graphics.BitmapFactory
// Log: writes messages to Logcat (Android Studio's log window) for debugging.
import android.util.Log
// Image: shows a picture on the screen.
import androidx.compose.foundation.Image
// background: fills the area behind a UI element with a color.
import androidx.compose.foundation.background
// clickable: makes a UI element respond to taps.
import androidx.compose.foundation.clickable
// Layout tools: stack things (Box), arrange vertically (Column) or horizontally (Row),
// and control size and spacing.
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
// LazyColumn: a scrollable list that only draws the rows currently on screen (fast for long lists).
import androidx.compose.foundation.lazy.LazyColumn
// items: adds one row to a LazyColumn for every item in a list.
import androidx.compose.foundation.lazy.items
// Material Design UI pieces: an "are you sure?" pop-up, button colors, a loading spinner, list rows,
// theme, background surface, a button, and text.
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
// Compose runtime tools: mark UI functions, hold state, and remember values.
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
// Alignment: where to place things inside a container (e.g. centered).
import androidx.compose.ui.Alignment
// Modifier: instructions for size, padding, background, etc.
import androidx.compose.ui.Modifier
// asImageBitmap: converts an Android Bitmap into something Compose can draw.
import androidx.compose.ui.graphics.asImageBitmap
// ContentScale: how a picture is resized to fit its space.
import androidx.compose.ui.layout.ContentScale
// TextAlign: how text lines up (left, center, right).
import androidx.compose.ui.text.style.TextAlign
// dp: "density-independent pixels", a size unit that looks the same on every screen.
import androidx.compose.ui.unit.dp
// Dialog: a pop-up window shown on top of the current screen.
import androidx.compose.ui.window.Dialog
// DialogProperties: settings for a Dialog (here, letting it fill the whole screen).
import androidx.compose.ui.window.DialogProperties
// collectAsStateWithLifecycle: turns a stream of data into UI state, pausing when the app is hidden.
import androidx.lifecycle.compose.collectAsStateWithLifecycle
// Our own data classes: a single photo, and the place photos are stored/loaded from.
import com.example.campusexplorer.data.NaturePhoto
import com.example.campusexplorer.data.PhotoRepository
// Coroutine tools for doing slow work (decoding images, deleting) off the main thread.
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
// DateFormat: turns a date into readable text like "Oct 18, 2026, 3:15 PM".
import java.text.DateFormat

// A label used in log messages so we can easily find this file's messages in Logcat.
private const val TAG = "MapScreen"

// @Composable means this function draws UI.
@Composable
// The Map tab. Shows a map placeholder on top and a list of photos underneath.
// onMessage lets us show pop-up messages. modifier adjusts our layout.
fun MapScreen(onMessage: (String) -> Unit, modifier: Modifier = Modifier) {
    // Get a live stream ("Flow") of photos from the database. "remember" makes sure we only
    // start listening once instead of on every redraw.
    val photosFlow = remember { PhotoRepository.observePhotos() }
    // Turn the stream into a list we can use. It starts empty, and the screen redraws
    // automatically whenever new photos arrive.
    val photos by photosFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    // Keep only the photos that have both a latitude and a longitude (so they can go on the map).
    val pins = photos.filter { it.latitude != null && it.longitude != null }
    // The photo the user tapped on, or null if no photo is open.
    var openPhoto by remember { mutableStateOf<NaturePhoto?>(null) }

    // Stack the map area and the list vertically, filling the screen.
    Column(modifier.fillMaxSize()) {
        // TODO: Replace this placeholder with an OpenStreetMap view (e.g. osmdroid or MapLibre)
        //  and draw one marker per item in `pins`.
        //  Tapping a marker should set `openPhoto` to show the photo, like the list rows below.
        // A Box used as a placeholder where the map will go.
        Box(
            // Center whatever is inside the box.
            contentAlignment = Alignment.Center,
            // Modifiers are applied in order:
            modifier = Modifier
                // Full screen width.
                .fillMaxWidth()
                // Take half of the available height (the list below also has weight 1f).
                .weight(1f)
                // Fill the background with a soft color from the theme.
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            // Placeholder text inside the box.
            Text(
                // The words to show ("\n" starts a new line).
                "Map coming soon\n(OpenStreetMap)",
                // Center each line of text.
                textAlign = TextAlign.Center,
                // Use the theme's large title text style.
                style = MaterialTheme.typography.titleLarge,
                // Use a text color that's easy to read on the box's background color.
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The bottom half: a heading and the list of photos.
        Column(
            // Take the other half of the height, with 8dp of space on top.
            modifier = Modifier.weight(1f).padding(top = 8.dp),
            // Put 4dp of space between items.
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // The heading above the list.
            Text(
                // ${...} inserts a value into the text, e.g. "Photo pins (3 of 5 photos have a location)".
                "Photo pins (${pins.size} of ${photos.size} photos have a location)",
                // Use the theme's medium title text style.
                style = MaterialTheme.typography.titleMedium,
                // Add 16dp of space on the left and right.
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            // A scrollable list.
            LazyColumn {
                // Add one row per photo. "key" gives each row a unique ID so the list updates smoothly.
                // Tapping a row remembers that photo in openPhoto, which opens the viewer below.
                items(photos, key = { it.id }) { PhotoPinRow(it, onClick = { openPhoto = it }) }
            }
        }
    }

    // If a photo has been tapped, show it in a full-screen viewer.
    // "?.let" only runs the code inside when openPhoto is not null.
    openPhoto?.let { photo ->
        // Closing the viewer sets openPhoto back to null, which hides it.
        PhotoViewer(photo = photo, onDismiss = { openPhoto = null }, onMessage = onMessage)
    }
}

// @Composable means this function draws UI.
@Composable
// Draws one row in the photo list. "private" means only this file can use it.
private fun PhotoPinRow(photo: NaturePhoto, onClick: () -> Unit) {
    // A ready-made list row.
    ListItem(
        // The main line: the photo's caption, or "(no caption)" if it's empty.
        headlineContent = { Text(photo.caption.ifBlank { "(no caption)" }) },
        // The smaller second line: where and when the photo was taken.
        supportingContent = {
            // If the photo has a location...
            val where = if (photo.latitude != null && photo.longitude != null) {
                // ...show the coordinates, rounded to 5 decimal places (e.g. "40.12345, -88.12345").
                "%.5f, %.5f".format(photo.latitude, photo.longitude)
            // Otherwise say there's no location.
            } else "no location"
            // If we know when it was taken, format that date nicely. "?:" means "if that was null,
            // use this instead" — the server may not have filled in the time yet, so say "just now".
            val whenTaken = photo.createdAt?.let { DateFormat.getDateTimeInstance().format(it) } ?: "just now"
            // Show both pieces of info separated by a dot.
            Text("$where · $whenTaken")
        },
        // Make the whole row tappable. "View photo" is read aloud by screen readers.
        modifier = Modifier.clickable(onClickLabel = "View photo", onClick = onClick),
    )
}

// The three things the viewer can be doing: still downloading, showing the photo, or showing an error.
private sealed interface ImageState {
    // Still downloading.
    data object Loading : ImageState
    // Downloaded successfully; holds the picture.
    data class Loaded(val bitmap: Bitmap) : ImageState
    // Something went wrong (no image found, no internet, permission denied...).
    data object Failed : ImageState
}

// @Composable means this function draws UI.
@Composable
// A full-screen pop-up that downloads and shows one photo with its caption.
private fun PhotoViewer(photo: NaturePhoto, onDismiss: () -> Unit, onMessage: (String) -> Unit) {
    // A scope for starting background tasks (deleting takes time and shouldn't freeze the screen).
    val scope = rememberCoroutineScope()
    // Whether the "Are you sure?" pop-up is showing.
    var confirmingDelete by remember { mutableStateOf(false) }
    // Whether we're currently deleting. Used to disable the Delete button.
    var deleting by remember { mutableStateOf(false) }


    // Download the image in the background. produceState starts as Loading and restarts
    // if a different photo is opened (because photo.id is passed as a "key").
    val image by produceState<ImageState>(ImageState.Loading, photo.id) {
        // "try" means: attempt this, and if anything fails jump to "catch".
        value = try {
            // Download the JPEG bytes from Firebase.
            val bytes = PhotoRepository.loadImage(photo)
            // Turn the bytes into a Bitmap on a background thread (decoding big images is slow).
            val bitmap = bytes?.let { withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(it, 0, it.size) } }
            // If we got a picture, show it; otherwise show the error message.
            if (bitmap != null) ImageState.Loaded(bitmap) else ImageState.Failed
        // If the download threw an error, we end up here.
        } catch (e: Exception) {
            // Write the error to Logcat for debugging.
            Log.e(TAG, "Couldn't load photo ${photo.id}", e)
            // Show the error message.
            ImageState.Failed
        }
    }

    // A pop-up window. Tapping outside it or pressing Back calls onDismiss.
    // usePlatformDefaultWidth = false lets it fill the whole screen instead of a small box.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // A full-screen background using the theme's surface color.
        Surface(modifier = Modifier.fillMaxSize()) {
            // Stack the photo, caption, and Close button vertically with 16dp of space around them.
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // The area for the photo: takes up all the leftover height, with its content centered.
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    // Show something different depending on the download state.
                    when (val state = image) {
                        // Still downloading: show a spinning circle.
                        ImageState.Loading -> CircularProgressIndicator()
                        // Downloaded: show the photo, shrunk to fit without cropping.
                        is ImageState.Loaded -> Image(
                            // Convert the Android Bitmap into a format Compose can draw.
                            bitmap = state.bitmap.asImageBitmap(),
                            // Description read aloud by screen readers.
                            contentDescription = photo.caption.ifBlank { "Nature photo" },
                            // Shrink the photo to fit without cropping it.
                            contentScale = ContentScale.Fit,
                            // Fill the photo area.
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Failed: tell the user.
                        ImageState.Failed -> Text("Couldn't load this photo.")
                    }
                }
                // The caption under the photo, in the theme's large title style.
                Text(photo.caption.ifBlank { "(no caption)" }, style = MaterialTheme.typography.titleLarge)
                // A row with Delete on the left and Close pushed to the right edge.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // A red flat button that asks before deleting. Disabled while a delete is running.
                    TextButton(
                        onClick = { confirmingDelete = true },
                        enabled = !deleting,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(if (deleting) "Deleting…" else "Delete") }
                    // A flat button that closes the viewer.
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }

    // "Are you sure?" pop-up, shown after tapping Delete.
    if (confirmingDelete) {
        AlertDialog(
            // Tapping outside or pressing Back just hides the pop-up.
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete photo?") },
            text = { Text("This removes the photo for everyone and can't be undone.") },
            // The "Delete" button that actually deletes.
            confirmButton = {
                TextButton(
                    onClick = {
                        // Hide the pop-up and mark that we're deleting.
                        confirmingDelete = false
                        deleting = true
                        // Start a background task so the screen doesn't freeze.
                        scope.launch {
                            try {
                                // Remove the image and its info from Firebase.
                                PhotoRepository.delete(photo)
                                // Tell the user it worked.
                                onMessage("Photo deleted")
                                // Close the viewer (the list updates on its own).
                                onDismiss()
                            // If deleting failed, we end up here.
                            } catch (e: Exception) {
                                // Write the error to Logcat for debugging.
                                Log.e(TAG, "Couldn't delete photo ${photo.id}", e)
                                // Tell the user what went wrong.
                                onMessage("Delete failed: ${e.message}")
                                // Re-enable the Delete button so they can try again.
                                deleting = false
                            }
                        }
                    },
                ) { Text("Delete") }
            },
            // The "Cancel" button just hides the pop-up.
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
        )
    }
}

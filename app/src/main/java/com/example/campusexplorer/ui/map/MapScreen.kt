// The "package" says which folder/namespace this file belongs to.
package com.example.campusexplorer.ui.map

// "import" lines pull in code from Android, Jetpack Compose, and our own project.
// background: fills the area behind a UI element with a color.
import androidx.compose.foundation.background
// clickable: makes a UI element respond to taps.
import androidx.compose.foundation.clickable
// Layout tools: stack things (Box), arrange vertically (Column), and control size and spacing.
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
// LazyColumn: a scrollable list that only draws the rows currently on screen (fast for long lists).
import androidx.compose.foundation.lazy.LazyColumn
// items: adds one row to a LazyColumn for every item in a list.
import androidx.compose.foundation.lazy.items
// Material Design UI pieces: list rows, theme, and text.
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
// Compose runtime tools: mark UI functions, hold state, and remember values.
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
// Alignment: where to place things inside a container (e.g. centered).
import androidx.compose.ui.Alignment
// Modifier: instructions for size, padding, background, etc.
import androidx.compose.ui.Modifier
// TextAlign: how text lines up (left, center, right).
import androidx.compose.ui.text.style.TextAlign
// dp: "density-independent pixels", a size unit that looks the same on every screen.
import androidx.compose.ui.unit.dp
// collectAsStateWithLifecycle: turns a stream of data into UI state, pausing when the app is hidden.
import androidx.lifecycle.compose.collectAsStateWithLifecycle
// Our own data classes: a single photo, and the place photos are stored/loaded from.
import com.example.campusexplorer.data.NaturePhoto
import com.example.campusexplorer.data.PhotoRepository
// DateFormat: turns a date into readable text like "Oct 18, 2026, 3:15 PM".
import java.text.DateFormat

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
    var selectedPhoto by remember { mutableStateOf<NaturePhoto?>(null) }

    // Stack the map area and the list vertically, filling the screen.
    Column(modifier.fillMaxSize()) {
        // TODO: Replace this placeholder with an OpenStreetMap view (e.g. osmdroid or MapLibre)
        //  and draw one marker per item in `pins`.
        //  Tapping a marker should set `selectedPhoto` to show the photo, like the list rows below.
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
                // Tapping a row remembers that photo in selectedPhoto, which opens the pop-up below.
                items(photos, key = { it.id }) { photo ->
                    PhotoPinRow(photo = photo, onClick = { selectedPhoto = photo })
                }
            }
        }
    }

    // If a photo has been tapped, show it in a pop-up (see PhotoDetailsDialog.kt).
    // "?.let" only runs the code inside when selectedPhoto is not null.
    selectedPhoto?.let { photo ->
        // Closing the pop-up sets selectedPhoto back to null, which hides it.
        PhotoDetailsDialog(photo = photo, onDismiss = { selectedPhoto = null }, onMessage = onMessage)
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

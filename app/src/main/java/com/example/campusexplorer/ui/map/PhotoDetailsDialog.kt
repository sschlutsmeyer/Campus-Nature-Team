package com.example.campusexplorer.ui.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.campusexplorer.data.NaturePhoto
import com.example.campusexplorer.data.PhotoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Label for this file's messages in Logcat.
private const val TAG = "PhotoDetailsDialog"

// Pop-up that shows one photo, with Delete, Retry, and Close buttons.
@Composable
fun PhotoDetailsDialog(photo: NaturePhoto, onDismiss: () -> Unit, onMessage: (String) -> Unit) {

    // The downloaded image, whether it's loading, and any error message.
    var bitmap by remember(photo.id) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(photo.id) { mutableStateOf(true) }
    var error by remember(photo.id) { mutableStateOf<String?>(null) }

    // Increase this to download the image again.
    var retryCount by remember(photo.id) { mutableStateOf(0) }

    // Whether the "Delete photo?" pop-up is showing, and whether a delete is running.
    var confirmingDelete by remember(photo.id) { mutableStateOf(false) }
    var deleting by remember(photo.id) { mutableStateOf(false) }

    // Used to delete in the background.
    val scope = rememberCoroutineScope()

    // Download the image. Runs again when a different photo is opened or Retry is tapped.
    LaunchedEffect(photo.id, retryCount) {
        loading = true
        error = null
        bitmap = null

        try {
            val bytes = PhotoRepository.loadImage(photo)

            // Turn the JPEG bytes into a picture, off the main thread.
            bitmap = if (bytes == null) {
                null
            } else {
                withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }

            if (bitmap == null) {
                error = "No image was found for this photo."
            }

        // The pop-up was closed mid-download. Let it stop normally.
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Could not load photo ${photo.id}", e)
            error = "Couldn't load this photo. Check your connection and try again."
        } finally {
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(photo.caption.ifBlank { "Nature photo" })
        },

        // Show a spinner, the error, or the photo.
        text = {
            Box(
                modifier = Modifier.fillMaxWidth().height(280.dp),
                contentAlignment = Alignment.Center,
            ) {
                val image = bitmap

                when {
                    loading -> CircularProgressIndicator()

                    error != null -> Text(
                        text = error.orEmpty(),
                        textAlign = TextAlign.Center,
                    )

                    image != null -> Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = photo.caption.ifBlank {
                            "Saved nature photo"
                        },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        dismissButton = {
            Row {

                // Red Delete button. Asks before deleting.
                TextButton(
                    onClick = { confirmingDelete = true },
                    enabled = !deleting,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(if (deleting) "Deleting…" else "Delete")
                }

                // Retry only shows after a failed download.
                if (error != null && !loading) {
                    TextButton(onClick = { retryCount++ }) {
                        Text("Retry")
                    }
                }
            }
        },
    )

    // "Are you sure?" pop-up for deleting.
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete photo?") },
            text = { Text("This removes the photo for everyone and can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        deleting = true
                        scope.launch {
                            try {
                                PhotoRepository.delete(photo)
                                onMessage("Photo deleted")

                                // The list updates on its own once Firestore removes the photo.
                                onDismiss()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "Could not delete photo ${photo.id}", e)
                                onMessage("Delete failed: ${e.message}")

                                // Let the user try again.
                                deleting = false
                            }
                        }
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

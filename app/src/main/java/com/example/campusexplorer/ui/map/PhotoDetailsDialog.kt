package com.example.campusexplorer.ui.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.withContext

// Displays the image belonging to the selected photo.
@Composable
fun PhotoDetailsDialog(photo: NaturePhoto, onDismiss: () -> Unit) {
    var bitmap by remember(photo.id) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(photo.id) { mutableStateOf(true) }
    var error by remember(photo.id) { mutableStateOf<String?>(null) }
    var retryCount by remember(photo.id) { mutableStateOf(0) }

    LaunchedEffect(photo.id, retryCount) {
        loading = true
        error = null
        bitmap = null

        try {
            PhotoRepository.ensureSignedIn()
            val bytes = PhotoRepository.loadImage(photo.id)

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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("PhotoDetailsDialog", "Could not load photo ${photo.id}", e)
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
            if (error != null && !loading) {
                TextButton(onClick = { retryCount++ }) {
                    Text("Retry")
                }
            }
        },
    )
}
package com.example.campusexplorer.ui.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.campusexplorer.data.LocalPhotoStore
import com.example.campusexplorer.data.NaturePhoto
import com.example.campusexplorer.data.PhotoRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Label for this file's messages in Logcat.
private const val TAG = "PhotoDetailsDialog"

// Pop-up that shows one photo, with Edit Name, Delete, Retry, and Close buttons.
@Composable
fun PhotoDetailsDialog(photo: NaturePhoto, onDismiss: () -> Unit, onMessage: (String) -> Unit) {

    // The image, whether it's loading, and any error message.
    var bitmap by remember(photo.id) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(photo.id) { mutableStateOf(true) }
    var error by remember(photo.id) { mutableStateOf<String?>(null) }

    // Increase this to download the image again.
    var retryCount by remember(photo.id) { mutableStateOf(0) }

    // Whether the "Delete photo?" pop-up is showing, and whether a delete is running.
    var confirmingDelete by remember(photo.id) { mutableStateOf(false) }
    var deleting by remember(photo.id) { mutableStateOf(false) }

    // Names are captions in Firestore. Only the owner can rename a pin.
    val canEdit = photo.uid != null && photo.uid == FirebaseAuth.getInstance().currentUser?.uid
    var displayedCaption by remember(photo.id, photo.caption) { mutableStateOf(photo.caption) }
    var editingName by remember(photo.id) { mutableStateOf(false) }
    var nameDraft by remember(photo.id) { mutableStateOf("") }
    var savingName by remember(photo.id) { mutableStateOf(false) }
    var nameError by remember(photo.id) { mutableStateOf<String?>(null) }

    // Used to save a name or delete in the background.
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Load the image. Runs again when a different photo is opened or Retry is tapped.
    LaunchedEffect(photo.id, retryCount) {
        loading = true
        error = null
        bitmap = null

        try {

            // Use the copy on this phone if there is one, otherwise download it.
            val bytes = LocalPhotoStore.load(context, photo.id) ?: PhotoRepository.loadImage(photo)

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
        onDismissRequest = { if (!savingName && !deleting) onDismiss() },
        title = {
            Text(displayedCaption.ifBlank { "Nature photo" })
        },

        // Show a spinner, the error, or the photo.
        text = {
            Column {
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
                            contentDescription = displayedCaption.ifBlank {
                                "Saved nature photo"
                            },
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (canEdit) {
                    TextButton(
                        enabled = !deleting && !savingName,
                        onClick = {
                            nameDraft = displayedCaption
                            nameError = null
                            editingName = true
                        },
                    ) { Text("Edit Name") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !savingName && !deleting) {
                Text("Close")
            }
        },
        dismissButton = {
            Row {

                // Red Delete button. Asks before deleting.
                TextButton(
                    onClick = { confirmingDelete = true },
                    enabled = !deleting && !savingName,
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

    if (editingName) {
        AlertDialog(
            onDismissRequest = { if (!savingName) editingName = false },
            title = { Text("Edit pin name") },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = {
                        nameDraft = it
                        nameError = null
                    },
                    label = { Text("Pin name") },
                    singleLine = true,
                    enabled = !savingName,
                    isError = nameError != null,
                    supportingText = {
                        Text(nameError ?: "Enter a name for this pin.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !savingName && nameDraft.isNotBlank() &&
                            nameDraft.trim() != displayedCaption,
                    onClick = {
                        val newName = nameDraft.trim()
                        savingName = true
                        nameError = null
                        scope.launch {
                            try {
                                PhotoRepository.updateCaption(photo, newName)
                                displayedCaption = newName
                                editingName = false
                                savingName = false
                                onMessage("Pin name updated")
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "Could not rename photo ${photo.id}", e)
                                nameError = "Couldn't save the name. Check your connection and try again."
                            } finally {
                                savingName = false
                            }
                        }
                    },
                ) { Text(if (savingName) "Saving…" else "Save") }
            },
            dismissButton = {
                TextButton(
                    enabled = !savingName,
                    onClick = { editingName = false },
                ) { Text("Cancel") }
            },
        )
    }

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

                                // Remove the copy on this phone too.
                                LocalPhotoStore.delete(context, photo.id)
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
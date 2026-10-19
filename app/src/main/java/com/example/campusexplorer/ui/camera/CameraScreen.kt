package com.example.campusexplorer.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.campusexplorer.data.LocationHelper
import com.example.campusexplorer.data.PhotoRepository
import com.example.campusexplorer.data.compressForUpload
import com.example.campusexplorer.data.toUprightBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Label for this file's messages in Logcat.
private const val TAG = "CameraScreen"

// The Camera tab: asks for permission, shows the camera, then the review screen.
@Composable
fun CameraScreen(onMessage: (String) -> Unit, modifier: Modifier = Modifier) {

    val context = LocalContext.current

    // Whether we can use the camera. Updating it redraws the screen.
    var hasCamera by remember { mutableStateOf(context.isGranted(Manifest.permission.CAMERA)) }

    // Shows Android's permission pop-up and records whether the camera was allowed.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> hasCamera = results[Manifest.permission.CAMERA] == true }

    // Ask for camera (required) and location (optional, used for map pins).
    val requestPermissions = {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }

    // Ask once when the screen first opens.
    LaunchedEffect(Unit) { if (!hasCamera) requestPermissions() }

    // No camera access: explain why and offer a button to ask again.
    if (!hasCamera) {

        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Camera access is needed to take nature photos.")
            Button(onClick = requestPermissions, modifier = Modifier.padding(top = 16.dp)) {
                Text("Grant camera access")
            }
        }
        return
    }

    // The photo just taken, or null while the camera is showing.
    var captured by remember { mutableStateOf<Bitmap?>(null) }

    // Local copy so Kotlin knows it can't change during the null check.
    val photo = captured

    // No photo yet: show the camera.
    if (photo == null) {

        CameraCapture(onCaptured = { captured = it }, onMessage = onMessage, modifier = modifier)

    // Photo taken: show the review screen.
    } else {

        PhotoReview(
            photo = photo,

            // Clearing the photo goes back to the camera.
            onRetake = { captured = null },

            // After saving, go back to the camera and confirm.
            onSaved = {
                captured = null
                onMessage("Photo saved!")
            },
            onMessage = onMessage,
            modifier = modifier,
        )
    }
}

// Live camera preview with a shutter button.
@Composable
private fun CameraCapture(

    // Called with the photo once it's taken.
    onCaptured: (Bitmap) -> Unit,

    // Shows a pop-up message (used for errors).
    onMessage: (String) -> Unit,

    modifier: Modifier = Modifier,
) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Camera controller, created once. It turns the camera on and off with the screen.
    val controller = remember {
        LifecycleCameraController(context).apply {

            // Only still photos, no video.
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            bindToLifecycle(lifecycleOwner)
        }
    }

    // Box stacks its children: preview at the back, button on top.
    Box(modifier.fillMaxSize()) {

        // The live camera feed (an older Android View placed inside Compose).
        AndroidView(
            factory = { PreviewView(it).apply { this.controller = controller } },
            modifier = Modifier.fillMaxSize(),
        )

        // Shutter button: a white ring around a white circle, near the bottom.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .size(76.dp)

                // The outer ring.
                .border(4.dp, Color.White, CircleShape)

                // Gap between the ring and the center.
                .padding(8.dp)

                // The filled center.
                .clip(CircleShape)
                .background(Color.White)

                // Take a photo when tapped.
                .clickable(onClickLabel = "Take photo") {
                    controller.takePicture(

                        // Run the callbacks below on the main thread.
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {

                            // Success: turn the image upright and pass it on. "use" closes the raw image.
                            override fun onCaptureSuccess(image: ImageProxy) {
                                image.use { onCaptured(it.toUprightBitmap()) }
                            }

                            // Failure: log it and tell the user.
                            override fun onError(exception: ImageCaptureException) {
                                Log.e(TAG, "Capture failed", exception)
                                onMessage("Couldn't take photo: ${exception.message}")
                            }
                        },
                    )
                },
        )
    }
}

// Shows the photo just taken with a caption box and Retake / Save buttons.
@Composable
private fun PhotoReview(
    photo: Bitmap,
    onRetake: () -> Unit,

    // Called after a successful save.
    onSaved: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {

    val context = LocalContext.current

    // Used to save in the background so the screen doesn't freeze.
    val scope = rememberCoroutineScope()

    // The caption being typed.
    var caption by remember { mutableStateOf("") }

    // True while saving. Disables the buttons and shows a spinner.
    var saving by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        // The photo, filling the leftover space without cropping.
        Image(
            bitmap = photo.asImageBitmap(),
            contentDescription = "Captured photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        // One-line caption box. Locked while saving.
        OutlinedTextField(
            value = caption,
            onValueChange = { caption = it },
            label = { Text("What did you find? (e.g. oak tree, blue jay)") },
            singleLine = true,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        )

        // Buttons side by side.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {

            OutlinedButton(onClick = onRetake, enabled = !saving) { Text("Retake") }

            Button(
                enabled = !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        try {

                            // Shrink the photo to a smaller JPEG, off the main thread.
                            val jpeg = withContext(Dispatchers.Default) { photo.compressForUpload() }

                            // Current location, or null if unavailable.
                            val location = LocationHelper.currentLocation(context)

                            // Upload the photo with its caption and location.
                            PhotoRepository.save(jpeg, caption.trim(), location)

                            // Warn that a photo without a location won't get a map pin.
                            if (location == null) onMessage("Saved without location (permission off or GPS unavailable)")
                            onSaved()
                        } catch (e: Exception) {
                            Log.e(TAG, "Save failed", e)
                            onMessage("Save failed: ${e.message}")
                        } finally {

                            // Runs whether the save worked or not.
                            saving = false
                        }
                    }
                },
            ) { Text("Save") }

            // Spinner while saving.
            if (saving) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Text("Saving…", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// True if the given permission has been granted. Lets us write context.isGranted(...).
private fun Context.isGranted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

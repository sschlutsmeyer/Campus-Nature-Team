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

private const val TAG = "CameraScreen"

@Composable
// The Camera tab. onMessage lets us show pop-up messages. modifier adjusts our layout.
fun CameraScreen(onMessage: (String) -> Unit, modifier: Modifier = Modifier) {

    // Get the Android Context (needed to check permissions and use the camera).
    val context = LocalContext.current

    // Remember whether we already have permission to use the camera. Changing it redraws the screen.
    var hasCamera by remember { mutableStateOf(context.isGranted(Manifest.permission.CAMERA)) }

    // Ask for camera (required) and location (optional, used to pin photos on the map) together.
    // This creates a "launcher" that shows Android's permission pop-up when we call it.
    val permissionLauncher = rememberLauncherForActivityResult(

        // We want to request several permissions at once.
        ActivityResultContracts.RequestMultiplePermissions()

    // When the user answers, update hasCamera based on whether CAMERA was allowed.
    ) { results -> hasCamera = results[Manifest.permission.CAMERA] == true }

    // A small function we can call (or attach to a button) to show the permission pop-up.
    val requestPermissions = {

        // Show the permission pop-up...
        permissionLauncher.launch(

            // ...asking for this list of permissions:
            arrayOf(

                // Permission to use the camera.
                Manifest.permission.CAMERA,

                // Permission for precise (GPS) location.
                Manifest.permission.ACCESS_FINE_LOCATION,

                // Permission for approximate location.
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }

    // Run once when this screen first appears, if we don't have camera access yet, ask for it.
    LaunchedEffect(Unit) { if (!hasCamera) requestPermissions() }

    // If we still don't have camera permission...
    if (!hasCamera) {

        Column(

            // Fill the screen and add 24dp of space around the edges.
            modifier = modifier.fillMaxSize().padding(24.dp),

            // Center the children vertically...
            verticalArrangement = Arrangement.Center,

            // ...and horizontally.
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Explain why we need the camera.
            Text("Camera access is needed to take nature photos.")

            // A button that asks for permission again when tapped. 16dp gap above it.
            Button(onClick = requestPermissions, modifier = Modifier.padding(top = 16.dp)) {

                // Button text.
                Text("Grant camera access")
            }
        }
        // Stop here since no camera access.
        return
    }

    // The photo the user just took, or null if they haven't taken one yet.
    var captured by remember { mutableStateOf<Bitmap?>(null) }

    // Copy it into a normal (read-only) variable so Kotlin knows it won't change while we check it.
    val photo = captured

    // No photo yet? Show the live camera.
    if (photo == null) {

        // Show the camera. When a photo is taken, store it in "captured" (which switches to the review screen).
        CameraCapture(onCaptured = { captured = it }, onMessage = onMessage, modifier = modifier)

    // We have a photo: show the review screen.
    } else {

        // The screen where the user adds a caption and saves (or retakes) the photo.
        PhotoReview(

            // The photo to show.
            photo = photo,

            // "Retake" clears the photo, which sends us back to the camera.
            onRetake = { captured = null },

            // After a successful save...
            onSaved = {

                // ...go back to the camera...
                captured = null

                // ...and tell the user it worked.
                onMessage("Photo saved!")
            },
            // Pass along the message function so the review screen can show errors.
            onMessage = onMessage,

            // Pass along the layout modifier.
            modifier = modifier,
        )
    }
}

@Composable
// Shows the live camera preview and a shutter button. "private" means only this file can use it.
private fun CameraCapture(

    // Called with the photo once it has been taken.
    onCaptured: (Bitmap) -> Unit,

    // Called to show a pop-up message (e.g. if taking the photo fails).
    onMessage: (String) -> Unit,

    // Lets the caller adjust our layout. Defaults to no changes.
    modifier: Modifier = Modifier,
) {
    // Get the Android Context (needed by the camera).
    val context = LocalContext.current

    // Get the "lifecycle owner" so the camera knows when the screen is visible.
    val lifecycleOwner = LocalLifecycleOwner.current

    // Create the camera controller once and keep it ("remember") across redraws.
    val controller = remember {

        // Make a camera controller, then configure it with "apply".
        LifecycleCameraController(context).apply {

            // We only need the camera for taking still pictures.
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)

            // Automatically turn the camera on/off when the screen is shown/hidden.
            bindToLifecycle(lifecycleOwner)
        }
    }

    // A Box stacks its children on top of each other (camera preview at the back, button in front).
    Box(modifier.fillMaxSize()) {
        // Put the old-style Android PreviewView inside our Compose UI.
        AndroidView(
            // Create the PreviewView and connect it to our camera controller so it shows the live feed.
            factory = { PreviewView(it).apply { this.controller = controller } },
            // Make the preview fill the whole screen.
            modifier = Modifier.fillMaxSize(),
        )
        // Shutter button: white ring with a white center.
        Box(
            // Modifiers are applied in order, top to bottom:
            modifier = Modifier
                // Place the button at the bottom middle of the screen.
                .align(Alignment.BottomCenter)
                // Lift it 32dp up from the bottom edge.
                .padding(bottom = 32.dp)
                // Make the button 76dp wide and tall.
                .size(76.dp)
                // Draw a 4dp-thick white circle around the outside (the ring).
                .border(4.dp, Color.White, CircleShape)
                // Leave an 8dp gap between the ring and the center.
                .padding(8.dp)
                // Cut the remaining area into a circle.
                .clip(CircleShape)
                // Fill that circle with white (the center of the button).
                .background(Color.White)
                // Make it tappable. "Take photo" is read aloud by screen readers.
                .clickable(onClickLabel = "Take photo") {
                    // Ask the camera to take a picture.
                    controller.takePicture(
                        // Run the result code on the main (UI) thread.
                        ContextCompat.getMainExecutor(context),
                        // An object with two functions: one for success and one for failure.
                        object : ImageCapture.OnImageCapturedCallback() {
                            // Called when the photo was taken successfully.
                            override fun onCaptureSuccess(image: ImageProxy) {
                                // Turn the raw image into a right-side-up Bitmap, hand it back,
                                // and then close the raw image ("use" closes it automatically).
                                image.use { onCaptured(it.toUprightBitmap()) }
                            }

                            // Called if something went wrong taking the photo.
                            override fun onError(exception: ImageCaptureException) {
                                // Write the error to Logcat for debugging.
                                Log.e(TAG, "Capture failed", exception)
                                // Tell the user what went wrong.
                                onMessage("Couldn't take photo: ${exception.message}")
                            }
                        },
                    )
                },
        )
    }
}

// @Composable means this function draws UI.
@Composable
// Shows the photo just taken, a caption box, and Retake / Save buttons.
private fun PhotoReview(
    // The photo to show.
    photo: Bitmap,
    // Called when the user taps "Retake".
    onRetake: () -> Unit,
    // Called after the photo has been saved successfully.
    onSaved: () -> Unit,
    // Called to show a pop-up message.
    onMessage: (String) -> Unit,
    // Lets the caller adjust our layout.
    modifier: Modifier = Modifier,
) {
    // Get the Android Context (needed to read the location).
    val context = LocalContext.current
    // A scope for starting background tasks (saving takes time and shouldn't freeze the screen).
    val scope = rememberCoroutineScope()
    // The caption the user types. Starts empty.
    var caption by remember { mutableStateOf("") }
    // Whether we're currently saving. Used to disable buttons and show a spinner.
    var saving by remember { mutableStateOf(false) }

    // Stack everything vertically.
    Column(
        // Fill the screen with 16dp of space around the edges.
        modifier = modifier.fillMaxSize().padding(16.dp),
        // Put 12dp of space between each item.
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Show the photo.
        Image(
            // Convert the Android Bitmap into a format Compose can draw.
            bitmap = photo.asImageBitmap(),
            // Description read aloud by screen readers.
            contentDescription = "Captured photo",
            // Shrink the photo to fit without cropping it.
            contentScale = ContentScale.Fit,
            // Take up all leftover vertical space ("weight") and the full width.
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        // A text box with an outline where the user types a caption.
        OutlinedTextField(
            // What's currently in the box.
            value = caption,
            // When the user types, save the new text (which redraws the box with it).
            onValueChange = { caption = it },
            // Hint text shown in/above the box.
            label = { Text("What did you find? (e.g. oak tree, blue jay)") },
            // Keep it to one line (no Enter for new lines).
            singleLine = true,
            // Don't allow typing while saving.
            enabled = !saving,
            // Make the box as wide as the screen.
            modifier = Modifier.fillMaxWidth(),
        )
        // A Row places its children side by side, 12dp apart, vertically centered.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // The "Retake" button (outlined style). Disabled while saving.
            OutlinedButton(onClick = onRetake, enabled = !saving) { Text("Retake") }
            // The "Save" button.
            Button(
                // Disabled while saving so the user can't tap it twice.
                enabled = !saving,
                // What happens when tapped:
                onClick = {
                    // Mark that we're saving (disables buttons, shows spinner).
                    saving = true
                    // Start a background task so the screen doesn't freeze.
                    scope.launch {
                        // "try" means: attempt this, and if anything fails jump to "catch".
                        try {
                            // Shrink the photo into a smaller JPEG file. Dispatchers.Default runs
                            // this heavy work on a background thread.
                            val jpeg = withContext(Dispatchers.Default) { photo.compressForUpload() }
                            // Get the phone's current location (may be null if unavailable).
                            val location = LocationHelper.currentLocation(context)
                            // Upload/save the photo, caption (with extra spaces trimmed), and location.
                            PhotoRepository.save(jpeg, caption.trim(), location)
                            // If we had no location, let the user know the photo won't be pinned on the map.
                            if (location == null) onMessage("Saved without location (permission off or GPS unavailable)")
                            // Tell the parent screen the save worked.
                            onSaved()
                        // If anything above threw an error, we end up here.
                        } catch (e: Exception) {
                            // Write the error to Logcat for debugging.
                            Log.e(TAG, "Save failed", e)
                            // Tell the user what went wrong.
                            onMessage("Save failed: ${e.message}")
                        // "finally" always runs, whether it worked or failed.
                        } finally {
                            // We're done saving: re-enable buttons and hide the spinner.
                            saving = false
                        }
                    }
                },
            // The text shown on the button.
            ) { Text("Save") }
            // While saving, show a loading indicator next to the buttons.
            if (saving) {
                // A small (24dp) spinning circle with a thin (2dp) line.
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                // The word "Saving…" next to the spinner, in the theme's medium body text style.
                Text("Saving…", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// A helper "extension function": it adds isGranted() to every Context, so we can write
// context.isGranted(...). It returns true if the given permission has been allowed.
private fun Context.isGranted(permission: String) =
    // Ask Android for the permission's status and compare it to "granted".
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

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
fun CameraScreen(onMessage: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasCamera by remember { mutableStateOf(context.isGranted(Manifest.permission.CAMERA)) }

    // Ask for camera (required) and location (optional, used to pin photos on the map) together.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> hasCamera = results[Manifest.permission.CAMERA] == true }
    val requestPermissions = {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }
    LaunchedEffect(Unit) { if (!hasCamera) requestPermissions() }

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

    var captured by remember { mutableStateOf<Bitmap?>(null) }
    val photo = captured
    if (photo == null) {
        CameraCapture(onCaptured = { captured = it }, onMessage = onMessage, modifier = modifier)
    } else {
        PhotoReview(
            photo = photo,
            onRetake = { captured = null },
            onSaved = {
                captured = null
                onMessage("Photo saved!")
            },
            onMessage = onMessage,
            modifier = modifier,
        )
    }
}

@Composable
private fun CameraCapture(
    onCaptured: (Bitmap) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            bindToLifecycle(lifecycleOwner)
        }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            factory = { PreviewView(it).apply { this.controller = controller } },
            modifier = Modifier.fillMaxSize(),
        )
        // Shutter button: white ring with a white center.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .size(76.dp)
                .border(4.dp, Color.White, CircleShape)
                .padding(8.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClickLabel = "Take photo") {
                    controller.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                image.use { onCaptured(it.toUprightBitmap()) }
                            }

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

@Composable
private fun PhotoReview(
    photo: Bitmap,
    onRetake: () -> Unit,
    onSaved: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var caption by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            bitmap = photo.asImageBitmap(),
            contentDescription = "Captured photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        OutlinedTextField(
            value = caption,
            onValueChange = { caption = it },
            label = { Text("What did you find? (e.g. oak tree, blue jay)") },
            singleLine = true,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onRetake, enabled = !saving) { Text("Retake") }
            Button(
                enabled = !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            val jpeg = withContext(Dispatchers.Default) { photo.compressForUpload() }
                            val location = LocationHelper.currentLocation(context)
                            PhotoRepository.save(jpeg, caption.trim(), location)
                            if (location == null) onMessage("Saved without location (permission off or GPS unavailable)")
                            onSaved()
                        } catch (e: Exception) {
                            Log.e(TAG, "Save failed", e)
                            onMessage("Save failed: ${e.message}")
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { Text("Save") }
            if (saving) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Text("Saving…", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun Context.isGranted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

package com.example.campusexplorer.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.scale
import com.example.campusexplorer.data.LocalPhotoStore
import com.example.campusexplorer.data.NaturePhoto
import com.example.campusexplorer.data.PhotoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

// Decode smaller images off the main thread instead of keeping every full-sized photo in memory,
// and limit how many download at once while scrolling.
private object PhotoImages {
    val downloads = Semaphore(2)
    val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
}

// A photo's image, loaded in the background from this phone's copy or from Firebase.
// It's shrunk so its longest side is at most maxSize pixels. Shows a spinner while loading and a
// Retry button if loading fails. Used by the feed and the new-post photo picker.
@Composable
fun PhotoImage(
    photo: NaturePhoto,
    modifier: Modifier = Modifier,
    maxSize: Int = 768,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = photo.caption.ifBlank { "Nature photo" },
    background: Color = LightGreen,
) {
    val context = LocalContext.current.applicationContext
    val cacheKey = "${photo.uid}/${photo.id}/${photo.storagePath}@$maxSize"
    var bitmap by remember(cacheKey) { mutableStateOf(PhotoImages.cache.get(cacheKey)) }
    var failed by remember(cacheKey) { mutableStateOf(false) }
    var retry by remember(cacheKey) { mutableIntStateOf(0) }

    LaunchedEffect(cacheKey, retry) {
        if (bitmap != null) return@LaunchedEffect
        failed = false
        try {
            bitmap = PhotoImages.downloads.withPermit {
                withContext(Dispatchers.IO) {
                    PhotoImages.cache.get(cacheKey) ?: run {
                        val bytes = try {
                            LocalPhotoStore.load(context, photo.id)
                        } catch (_: java.io.IOException) {
                            null // A missing local copy can still be downloaded.
                        } ?: PhotoRepository.loadImage(photo) ?: error("Photo image is missing")
                        decodeScaled(bytes, maxSize).also { PhotoImages.cache.put(cacheKey, it) }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PhotoImage", "Could not load photo ${photo.id}", e)
            failed = true
        }
    }

    Box(modifier.background(background), contentAlignment = Alignment.Center) {
        val image = bitmap
        when {
            image != null -> Image(
                image.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
            failed -> TextButton(onClick = { retry++ }) {
                Text("Retry photo", color = Turquoise, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            else -> CircularProgressIndicator(color = Turquoise, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
    }
}

// Decodes JPEG bytes into a bitmap whose longest side is at most maxSize pixels.
private fun decodeScaled(bytes: ByteArray, maxSize: Int): Bitmap {

    // Read only the size first, then decode at the smallest power-of-two scale that's still big enough.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSize) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options))

    // Then scale the rest of the way down.
    val scale = minOf(1f, maxSize.toFloat() / maxOf(decoded.width, decoded.height))
    return if (scale < 1f) {
        decoded.scale(maxOf(1, (decoded.width * scale).toInt()), maxOf(1, (decoded.height * scale).toInt()))
    } else decoded
}

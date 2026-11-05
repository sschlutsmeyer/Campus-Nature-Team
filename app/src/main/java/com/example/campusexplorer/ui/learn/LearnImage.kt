package com.example.campusexplorer.ui.learn

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.campusexplorer.data.readBytesLimited
import com.example.campusexplorer.ui.LightGreen
import com.example.campusexplorer.ui.Turquoise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private object LearnImages {
    val downloads = Semaphore(3)
    val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
}

@Composable
internal fun LearnImage(url: String?, description: String, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf(url?.let { LearnImages.cache.get(it) }) }
    var failed by remember(url) { mutableStateOf(false) }
    var retry by remember(url) { mutableIntStateOf(0) }

    LaunchedEffect(url, retry) {
        if (url == null || bitmap != null) return@LaunchedEffect
        failed = false
        try {
            bitmap = LearnImages.downloads.withPermit {
                withContext(Dispatchers.IO) {
                    LearnImages.cache.get(url) ?: run {
                        require(url.startsWith("https://"))
                        val connection = URL(url).openConnection() as HttpURLConnection
                        val bytes = try {
                            connection.connectTimeout = 10_000
                            connection.readTimeout = 15_000
                            if (connection.responseCode !in 200..299) throw IOException("Photo download failed.")
                            connection.inputStream.use { it.readBytesLimited(6 * 1024 * 1024) }
                        } finally {
                            connection.disconnect()
                        }
                        currentCoroutineContext().ensureActive()
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Unreadable photo.")
                        var sample = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
                        val options = BitmapFactory.Options().apply { inSampleSize = sample }
                        val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                            ?: throw IOException("Unreadable photo.")
                        currentCoroutineContext().ensureActive()
                        LearnImages.cache.put(url, image)
                        image
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("LearnImage", "Species photo unavailable", e)
            failed = true
        }
    }

    Box(modifier.background(LightGreen), contentAlignment = Alignment.Center) {
        val image = bitmap
        when {
            image != null -> Image(image.asImageBitmap(), contentDescription = description,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            url == null -> Text("🌿")
            failed -> TextButton(onClick = { retry++ }) { Text("Retry", color = Turquoise) }
            else -> CircularProgressIndicator(color = Turquoise, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        }
    }
}

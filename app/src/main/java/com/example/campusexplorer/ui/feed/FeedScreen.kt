package com.example.campusexplorer.ui.feed

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.format.DateUtils
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.campusexplorer.data.LocalPhotoStore
import com.example.campusexplorer.data.NaturePhoto
import com.example.campusexplorer.data.PhotoRepository
import com.example.campusexplorer.ui.LeafGreen
import com.example.campusexplorer.ui.LightGreen
import com.example.campusexplorer.ui.Turquoise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private val FeedText = Color(0xFF294C39)
private val FeedMuted = Color(0xFF65756C)

// Connects the screen to the app's existing saved photos. No social data is written here.
@Composable
fun FeedScreen(modifier: Modifier = Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var photos by remember { mutableStateOf<List<NaturePhoto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(lifecycle, retry) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            loading = true
            failed = false
            try {
                PhotoRepository.observePhotos(failOnError = true).collect {
                    photos = it
                    loading = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("FeedScreen", "Could not load feed", e)
                failed = true
                loading = false
            }
        }
    }

    Column(modifier.fillMaxSize().background(Color.White)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Feed", color = FeedText, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Discoveries around campus", color = Turquoise, fontSize = 14.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }

        when {
            failed -> FeedStatus(
                message = "Couldn't load the feed. Check your connection and try again.",
                modifier = Modifier.weight(1f),
                onRetry = { retry++ },
            )
            loading -> FeedStatus(modifier = Modifier.weight(1f))
            photos.isEmpty() -> FeedStatus(
                message = "No discoveries yet.\nSave a photo with the camera to add the first one.",
                modifier = Modifier.weight(1f),
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
            ) {
                items(photos, key = { it.id }) { photo ->
                    FeedPostCard(photo = photo)
                    HorizontalDivider(color = LightGreen, thickness = 2.dp,
                        modifier = Modifier.padding(vertical = 14.dp))
                }
            }
        }
    }
}

// Reusable post layout. A teammate can pass callbacks when likes/comments are ready.
// Null callbacks leave the controls disabled; tapping them never changes fake counters.
@Composable
fun FeedPostCard(
    photo: NaturePhoto,
    modifier: Modifier = Modifier,
    onLike: ((NaturePhoto) -> Unit)? = null,
    onComment: ((NaturePhoto) -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(40.dp).background(LightGreen, CircleShape),
                contentAlignment = Alignment.Center) {
                Icon(LeafIcon, contentDescription = null, tint = LeafGreen, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("User", color = FeedText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                val timestamp = photo.createdAt?.let {
                    DateUtils.getRelativeTimeSpanString(
                        it.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                    ).toString()
                } ?: "Date unavailable"
                Text(timestamp, color = FeedMuted, fontSize = 12.sp)
            }
        }

        FeedPhoto(photo)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeedAction("Like", HeartIcon, enabled = onLike != null,
                onClick = { onLike?.invoke(photo) })
            FeedAction("Comment", CommentIcon, enabled = onComment != null,
                onClick = { onComment?.invoke(photo) })
        }
        if (photo.caption.isNotBlank()) {
            Text(photo.caption, color = FeedText, fontSize = 15.sp, lineHeight = 21.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
        }
    }
}

@Composable
private fun FeedAction(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 4.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = Turquoise, disabledContentColor = FeedMuted),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 14.sp)
    }
}

@Composable
private fun FeedStatus(modifier: Modifier, message: String? = null, onRetry: (() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (message == null) {
            CircularProgressIndicator(color = Turquoise, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(12.dp))
            Text("Loading discoveries…", color = FeedMuted)
        } else {
            Text(message, color = FeedText, textAlign = TextAlign.Center)
            if (onRetry != null) {
                TextButton(onClick = onRetry) { Text("Try again", color = Turquoise) }
            }
        }
    }
}

// Decode smaller images off the main thread instead of keeping every full-sized photo in memory.
private object FeedImages {
    val downloads = Semaphore(2)
    val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
}

@Composable
private fun FeedPhoto(photo: NaturePhoto) {
    val context = LocalContext.current.applicationContext
    val cacheKey = "${photo.uid}/${photo.id}/${photo.storagePath}"
    var bitmap by remember(cacheKey) { mutableStateOf(FeedImages.cache.get(cacheKey)) }
    var failed by remember(cacheKey) { mutableStateOf(false) }
    var retry by remember(cacheKey) { mutableIntStateOf(0) }

    LaunchedEffect(cacheKey, retry) {
        if (bitmap != null) return@LaunchedEffect
        failed = false
        try {
            bitmap = FeedImages.downloads.withPermit {
                withContext(Dispatchers.IO) {
                    FeedImages.cache.get(cacheKey) ?: run {
                        val bytes = try {
                            LocalPhotoStore.load(context, photo.id)
                        } catch (_: java.io.IOException) {
                            null // A missing local copy can still be downloaded.
                        } ?: PhotoRepository.loadImage(photo) ?: error("Photo image is missing")
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        var sample = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 768) sample *= 2
                        val options = BitmapFactory.Options().apply { inSampleSize = sample }
                        val decoded = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options))
                        val scale = minOf(1f, 768f / maxOf(decoded.width, decoded.height))
                        val image = if (scale < 1f) {
                            Bitmap.createScaledBitmap(decoded, maxOf(1, (decoded.width * scale).toInt()),
                                maxOf(1, (decoded.height * scale).toInt()), true)
                        } else decoded
                        FeedImages.cache.put(cacheKey, image)
                        image
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("FeedScreen", "Could not load photo ${photo.id}", e)
            failed = true
        }
    }

    Box(Modifier.fillMaxWidth().aspectRatio(1f).background(LightGreen), contentAlignment = Alignment.Center) {
        val image = bitmap
        when {
            image != null -> Image(image.asImageBitmap(),
                contentDescription = photo.caption.ifBlank { "Nature discovery" },
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Photo unavailable", color = FeedMuted)
                TextButton(onClick = { retry++ }) { Text("Retry photo", color = Turquoise) }
            }
            else -> CircularProgressIndicator(color = Turquoise, modifier = Modifier.size(28.dp))
        }
    }
}

// Local vectors keep the screen independent of an additional icon library.
private val HeartIcon = ImageVector.Builder("FeedLike", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f) {
        moveTo(12f, 21f)
        curveTo(9f, 18f, 3f, 13f, 3f, 7.5f)
        curveTo(3f, 2.5f, 9f, 1.5f, 12f, 6f)
        curveTo(15f, 1.5f, 21f, 2.5f, 21f, 7.5f)
        curveTo(21f, 13f, 15f, 18f, 12f, 21f)
        close()
    }
}.build()

private val CommentIcon = ImageVector.Builder("FeedComment", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f) {
        moveTo(21f, 11f)
        curveTo(21f, 16f, 17f, 19f, 12f, 19f)
        lineTo(4f, 22f)
        lineTo(5f, 16f)
        curveTo(3f, 14f, 3f, 13f, 3f, 11f)
        curveTo(3f, 6f, 7f, 3f, 12f, 3f)
        curveTo(17f, 3f, 21f, 6f, 21f, 11f)
        close()
    }
}.build()

private val LeafIcon = ImageVector.Builder("FeedLeaf", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 21f)
        curveTo(3f, 19f, 2f, 10f, 4f, 5f)
        curveTo(11f, 6f, 14f, 12f, 12f, 21f)
        close()
        moveTo(14f, 18f)
        curveTo(12f, 9f, 15f, 4f, 22f, 2f)
        curveTo(23f, 10f, 21f, 17f, 14f, 18f)
        close()
    }
}.build()

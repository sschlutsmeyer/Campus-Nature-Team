package com.example.campusexplorer.ui.gallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import com.example.campusexplorer.ui.map.PhotoDetailsDialog
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

@Composable
fun GalleryScreen(onMessage: (String) -> Unit, modifier: Modifier = Modifier) {
    val auth = remember { FirebaseAuth.getInstance() }
    var uid by remember { mutableStateOf(auth.currentUser?.uid) }
    var signInError by remember { mutableStateOf(false) }
    var signInRetry by remember { mutableIntStateOf(0) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { uid = it.currentUser?.uid }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    LaunchedEffect(signInRetry) {
        signInError = false
        try {
            PhotoRepository.ensureSignedIn()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("GalleryScreen", "Gallery sign-in failed", e)
            signInError = true
        }
    }

    // Recreate gallery state when accounts change so the previous user's photos disappear.
    val currentUid = uid
    if (currentUid != null) {
        key(currentUid) { UserGallery(currentUid, onMessage, modifier) }
    } else {
        Column(modifier.fillMaxSize().padding(14.dp)) {
            GalleryHeader(null)
            GalleryStatus(
                modifier = Modifier.weight(1f),
                message = if (signInError) "Couldn't open your photos. Please try again." else null,
                onRetry = { signInRetry++ },
            )
        }
    }
}

@Composable
private fun UserGallery(uid: String, onMessage: (String) -> Unit, modifier: Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var photos by remember { mutableStateOf<List<NaturePhoto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(uid, retry, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            loading = true
            failed = false
            try {
                PhotoRepository.observeOwnPhotos(uid).collect {
                    photos = it
                    loading = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("GalleryScreen", "Gallery listener failed", e)
                failed = true
                loading = false
            }
        }
    }

    val selectedPhoto = photos.firstOrNull { it.id == selectedId }
    if (selectedPhoto != null && !failed) {
        PhotoDetailsDialog(selectedPhoto, onDismiss = { selectedId = null }, onMessage = onMessage)
    }
    LaunchedEffect(photos, loading) {
        if (!loading && photos.none { it.id == selectedId }) selectedId = null
    }

    Column(modifier.fillMaxSize().background(Color.White).padding(12.dp)) {
        GalleryHeader(if (loading || failed) null else photos.size)
        when {
            failed -> GalleryStatus(
                Modifier.weight(1f), "Couldn't load your photos. Check your connection and try again.",
                onRetry = { retry++ },
            )
            loading -> GalleryStatus(Modifier.weight(1f))
            photos.isEmpty() -> Column(
                Modifier.fillMaxWidth().weight(1f).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Your discoveries start here", color = LeafGreen, fontWeight = FontWeight.SemiBold,
                    fontSize = 19.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text("Take and save a photo with the camera. Your photos will appear here.",
                    color = Color.DarkGray, textAlign = TextAlign.Center)
            }
            else -> {
                // Group by local calendar date, with a separate heading for undated photos.
                val groups = photos.groupBy { it.createdAt?.let(::dayKey) }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    groups.forEach { (day, dailyPhotos) ->
                        item(key = "day:$day", span = { GridItemSpan(maxLineSpan) }) {
                            DateHeading(dailyPhotos.first().createdAt)
                        }
                        items(dailyPhotos, key = { "photo:${it.id}" }) { photo ->
                            PhotoThumbnail(photo, onClick = { selectedId = photo.id })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryHeader(count: Int?) {
    Text("My Photos", color = Color(0xFF294C39), fontSize = 30.sp, fontWeight = FontWeight.Bold)
    if (count != null) {
        Text("$count ${if (count == 1) "photo" else "photos"}", color = Color.DarkGray, fontSize = 13.sp)
    }
    Text("Your nature discoveries", color = Turquoise, fontSize = 14.sp,
        modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
}

@Composable
private fun GalleryStatus(modifier: Modifier, message: String? = null, onRetry: () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        if (message == null) {
            CircularProgressIndicator(color = Turquoise, modifier = Modifier.size(30.dp))
            Text("Loading your photos…", color = Color.DarkGray, modifier = Modifier.padding(top = 12.dp))
        } else {
            Text(message, color = Color.DarkGray, textAlign = TextAlign.Center)
            TextButton(onClick = onRetry) { Text("Try again", color = Turquoise) }
        }
    }
}

private fun dayKey(date: Date): String = Calendar.getInstance().apply { time = date }.let {
    "${it.get(Calendar.YEAR)}-${it.get(Calendar.DAY_OF_YEAR)}"
}

@Composable
private fun DateHeading(date: Date?) {
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val title = when (date?.let(::dayKey)) {
        null -> "Date unavailable"
        dayKey(today.time) -> "Today"
        dayKey(yesterday.time) -> "Yesterday"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(date)
    }
    Column(Modifier.padding(top = 10.dp, bottom = 8.dp)) {
        Text(title, color = LeafGreen, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        if (date != null && (title == "Today" || title == "Yesterday")) {
            Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(date),
                color = Color.DarkGray, fontSize = 11.sp)
        }
    }
}

// Limit both decoded bitmap memory and simultaneous downloads while scrolling.
private object GalleryThumbnails {
    val downloads = Semaphore(3)
    val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
}

@Composable
private fun PhotoThumbnail(photo: NaturePhoto, onClick: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val cacheKey = "${photo.uid}/${photo.id}/${photo.storagePath}"
    var bitmap by remember(cacheKey) { mutableStateOf(GalleryThumbnails.cache.get(cacheKey)) }
    var failed by remember(cacheKey) { mutableStateOf(false) }
    var retry by remember(cacheKey) { mutableIntStateOf(0) }
    LaunchedEffect(cacheKey, retry) {
        if (bitmap != null) return@LaunchedEffect
        failed = false
        try {
            bitmap = GalleryThumbnails.downloads.withPermit {
                withContext(Dispatchers.IO) {
                    GalleryThumbnails.cache.get(cacheKey) ?: run {
                        val bytes = try { LocalPhotoStore.load(context, photo.id) } catch (_: java.io.IOException) { null }
                            ?: PhotoRepository.loadImage(photo)
                            ?: error("Photo image is missing")
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        var sample = 1
                        while ((bounds.outWidth / (sample * 2) >= 256 && bounds.outHeight / (sample * 2) >= 256) ||
                            maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 768) {
                            sample *= 2
                        }
                        val options = BitmapFactory.Options().apply { inSampleSize = sample }
                        val decoded = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options))
                        // Extreme panoramas also need a bound on their longest edge.
                        val scale = minOf(1f, 768f / maxOf(decoded.width, decoded.height))
                        val thumbnail = if (scale < 1f) Bitmap.createScaledBitmap(decoded,
                            maxOf(1, (decoded.width * scale).toInt()), maxOf(1, (decoded.height * scale).toInt()), true)
                        else decoded
                        GalleryThumbnails.cache.put(cacheKey, thumbnail)
                        thumbnail
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("GalleryScreen", "Thumbnail failed: ${photo.id}", e)
            failed = true
        }
    }
    Box(Modifier.fillMaxWidth().aspectRatio(1f).background(LightGreen)
        .clickable(onClickLabel = "View ${photo.caption.ifBlank { "photo" }}", onClick = onClick),
        contentAlignment = Alignment.Center) {
        val image = bitmap
        when {
            image != null -> Image(image.asImageBitmap(), contentDescription = photo.caption.ifBlank { "Nature photo" },
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            failed -> TextButton(onClick = { retry++ }) {
                Text("Retry photo", color = Turquoise, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
            else -> CircularProgressIndicator(color = Turquoise, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        }
    }
}
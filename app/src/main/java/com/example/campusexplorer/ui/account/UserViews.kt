package com.example.campusexplorer.ui.account

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.scale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.campusexplorer.data.AccountRepository
import com.example.campusexplorer.data.UserProfile
import com.example.campusexplorer.ui.LeafGreen
import com.example.campusexplorer.ui.LightGreen
import com.example.campusexplorer.ui.Turquoise
import com.example.campusexplorer.ui.feed.CloseIcon
import com.example.campusexplorer.ui.feed.FeedMuted
import com.example.campusexplorer.ui.feed.FeedText
import com.example.campusexplorer.ui.feed.LeafIcon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

// Small pieces for showing users: names, profile pictures, and rows in lists of people.

// A user's profile, live, or null if they don't have one (or while it loads).
// Shows the last known profile right away.
@Composable
fun rememberUserProfile(uid: String?): UserProfile? {
    val flow = remember(uid) { uid?.let { AccountRepository.profile(it) } ?: flowOf(null) }
    val profile by flow.collectAsStateWithLifecycle(uid?.let { AccountRepository.cachedProfile(it) })
    return profile
}

// The name to show for a user: their username, or a made-up one for older posts from before accounts.
@Composable
fun rememberDisplayName(uid: String?): String = rememberUserProfile(uid)?.username ?: fallbackName(uid)

fun fallbackName(uid: String?): String =
    if (uid == null) "Unknown explorer" else "Explorer ${uid.take(4).uppercase()}"

// Profile pictures are small, so keep a few in memory and download a few at a time.
private object Avatars {
    val downloads = Semaphore(3)
    val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
}

// A round profile picture. Without a picture: the username's first letter, or a leaf for guests and
// older posters. Pass profile when it's already loaded to skip looking it up again.
@Composable
fun UserAvatar(
    uid: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    profile: UserProfile? = rememberUserProfile(uid),
) {
    val path = profile?.photoPath
    var bitmap by remember(path) { mutableStateOf(path?.let { Avatars.cache.get(it) }) }

    LaunchedEffect(path) {
        if (path == null || bitmap != null) return@LaunchedEffect
        bitmap = try {
            Avatars.downloads.withPermit {
                withContext(Dispatchers.IO) {
                    Avatars.cache.get(path) ?: decodeAvatar(AccountRepository.loadAvatar(path)).also {
                        Avatars.cache.put(path, it)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("UserAvatar", "Couldn't load profile picture $path", e)
            null
        }
    }

    Box(
        modifier.size(size).clip(CircleShape).background(LightGreen),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        when {
            image != null -> Image(
                image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            profile != null -> Text(
                profile.username.take(1).uppercase(),
                color = Turquoise,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp,
            )
            else -> Icon(LeafIcon, contentDescription = null, tint = LeafGreen, modifier = Modifier.size(size * 0.6f))
        }
    }
}

private fun decodeAvatar(bytes: ByteArray): Bitmap {
    val decoded = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "Not an image" }
    val scale = minOf(1f, 256f / maxOf(decoded.width, decoded.height))
    return if (scale < 1f) {
        decoded.scale(maxOf(1, (decoded.width * scale).toInt()), maxOf(1, (decoded.height * scale).toInt()))
    } else decoded
}

// One person in a list: picture, name, and a short line under it (their bio unless supporting is given).
// trailing is for buttons like Accept or Unblock.
@Composable
fun UserRow(
    uid: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val profile = rememberUserProfile(uid)
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "View profile", onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(uid, 44.dp, profile = profile)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                profile?.username ?: fallbackName(uid),
                color = FeedText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val line = supporting ?: profile?.bio?.takeIf { it.isNotBlank() }
            if (line != null) {
                Text(line, color = FeedMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

// A pop-up list of people, like followers or friends. Tapping someone opens their profile.
@Composable
fun UserListDialog(
    title: String,
    uids: List<String>,
    emptyText: String,
    onDismiss: () -> Unit,
    onOpenUser: (String) -> Unit,
    trailing: (@Composable (uid: String) -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, color = FeedText, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(CloseIcon, contentDescription = "Close", tint = FeedMuted)
                    }
                }
                HorizontalDivider(color = LightGreen)
                if (uids.isEmpty()) {
                    Text(
                        emptyText,
                        color = FeedMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                    )
                } else {
                    // Alphabetical by the names we know so far.
                    val sorted = uids.sortedBy { (AccountRepository.cachedProfile(it)?.username ?: it).lowercase() }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(vertical = 6.dp),
                    ) {
                        items(sorted, key = { it }) { uid ->
                            UserRow(
                                uid = uid,
                                onClick = {
                                    onDismiss()
                                    onOpenUser(uid)
                                },
                                trailing = trailing?.let { { it(uid) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

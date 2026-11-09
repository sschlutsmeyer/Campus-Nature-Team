package com.example.campusexplorer.ui.map

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.campusexplorer.data.NaturePhoto
import com.example.campusexplorer.data.PhotoRepository
import com.example.campusexplorer.data.PostRepository
import com.example.campusexplorer.ui.CampusRed
import com.example.campusexplorer.ui.PhotoImportHost
import com.example.campusexplorer.ui.Turquoise
import com.example.campusexplorer.ui.feed.authorLabel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.text.DateFormat

// Label for this file's messages in Logcat.
private const val TAG = "MapScreen"

// Letting go of the pin list's header faster than this snaps it open or closed in that direction.
private val FLING_SPEED = 400.dp

// The Map tab: a map of photo pins on top and the list of those photos below.
// It starts out showing only your own pins. The "All users' pins" switch adds other users' pins,
// but only ones they've posted to the feed: a pin nobody posted is never shown to anyone else.
@Composable
fun MapScreen(
    // Whether other users' posted pins are showing. Kept by the caller so it lasts the whole session.
    showAllPins: Boolean,
    onShowAllPinsChange: (Boolean) -> Unit,

    // Whether the list of pins under the map is showing (false = minimized, bigger map).
    // Kept by the caller so it stays the way you left it.
    pinListOpen: Boolean,
    onPinListOpenChange: (Boolean) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,

    // Opens a photo's post in the feed, or starts a post with your photo. Null hides the button.
    onViewPost: ((postId: String) -> Unit)? = null,
    onCreatePost: ((NaturePhoto) -> Unit)? = null,

    // A pin to zoom in on (from a post's "Map" button). Cleared with onFocusHandled.
    focusPhotoId: String? = null,
    onFocusHandled: () -> Unit = {},
) {

    // Who's signed in. Signs in anonymously if needed.
    val uid by remember { PhotoRepository.observeUserId() }
        .collectAsStateWithLifecycle(FirebaseAuth.getInstance().currentUser?.uid)

    // Your photos. Updates on its own when photos are added, renamed, or deleted.
    val myPhotos by remember(uid) {
        val id = uid
        if (id == null) {
            flowOf(emptyList<NaturePhoto>())
        } else {
            PhotoRepository.observeOwnPhotos(id).catch { e ->
                Log.w(TAG, "Couldn't load your pins", e)
                emit(emptyList())
            }
        }
    }.collectAsStateWithLifecycle(emptyList())

    // Everyone's posts. Other users' photos are private, so their pins come from their posts.
    val posts by remember {
        PostRepository.observePosts().catch { e ->
            Log.w(TAG, "Couldn't load posts for the map", e)
            emit(emptyList())
        }
    }.collectAsStateWithLifecycle(emptyList())

    // Which of your photos are posted, and other users' posted photos (only when switched on).
    val myPostedIds = posts.filter { it.uid == uid }.mapTo(HashSet()) { it.id }
    val otherPhotos = if (showAllPins) {
        posts.filter { it.uid != uid && it.hasLocation }.mapNotNull { it.photo }
    } else emptyList()

    // Everything in the list (your photos without a location too), newest first.
    val listed = (myPhotos + otherPhotos).sortedByDescending { it.createdAt?.time ?: Long.MAX_VALUE }

    // Photos with a location, which can go on the map.
    val pins = listed.filter { it.latitude != null && it.longitude != null }

    // The photo that was tapped, or null if none is open.
    var selectedPhoto by remember { mutableStateOf<NaturePhoto?>(null) }

    // A spot for the map to zoom in on.
    var focusPoint by remember { mutableStateOf<GeoPoint?>(null) }

    // Zoom to the pin from a post's "Map" button once it's on the map.
    LaunchedEffect(focusPhotoId, pins) {
        val target = focusPhotoId ?: return@LaunchedEffect
        val pin = pins.firstOrNull { it.id == target }
        if (pin == null) {

            // The pins may still be loading. If they update, this starts over.
            delay(5_000)
            onMessage("Couldn't find that pin on the map.")
        } else {
            focusPoint = GeoPoint(checkNotNull(pin.latitude), checkNotNull(pin.longitude))
        }
        onFocusHandled()
    }

    // Kept here (not in the list) so the scroll position survives hiding and showing the list.
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // How open the pin list is: 0 = hidden, 1 = open. Follows your finger while you drag the header.
    val openness = remember { Animatable(if (pinListOpen) 1f else 0f) }
    LaunchedEffect(pinListOpen) { openness.animateTo(if (pinListOpen) 1f else 0f) }

    BoxWithConstraints(modifier.fillMaxSize()) {

        // When open, the header and list fill the bottom half, like before. The map gets the rest.
        var headerHeightPx by remember { mutableIntStateOf(0) }
        val openListHeightPx = (constraints.maxHeight / 2f - headerHeightPx).coerceAtLeast(0f)

        Column(Modifier.fillMaxSize()) {

            // The map (see PhotoMap.kt), with the switch floating over it. Grows when the list is hidden.
            Box(Modifier.fillMaxWidth().weight(1f)) {
                PhotoMap(
                    pins = pins,
                    myUid = uid,
                    onPinClick = { selectedPhoto = it },
                    onMessage = onMessage,
                    focus = focusPoint,
                    onFocusShown = { focusPoint = null },
                    modifier = Modifier.fillMaxSize(),
                )
                AllPinsSwitch(
                    checked = showAllPins,
                    onCheckedChange = onShowAllPinsChange,
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                )

                // Add a photo from the camera roll as a pin, then zoom to it.
                val addPhotoModifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                PhotoImportHost(
                    onMessage = onMessage,
                    onImported = { _, location ->
                        if (location != null) focusPoint = GeoPoint(location.latitude, location.longitude)
                    },
                ) { startImport ->
                    Button(onClick = startImport, modifier = addPhotoModifier) { Text("Add Photo") }
                }
            }

            // Header with the pin counts and an arrow. Tap it to hide or show the list,
            // or drag it down to hide the list and pull it back up to show it.
            val myPinCount = pins.count { it.uid == uid }
            PinListHeader(
                title = if (showAllPins) "All pins" else "Your pins",
                subtitle = if (showAllPins) {
                    "$myPinCount yours · ${pins.size - myPinCount} from other users"
                } else {
                    "$myPinCount of ${myPhotos.size} photos have a location"
                },
                openness = openness.value,
                onToggle = { onPinListOpenChange(!pinListOpen) },
                modifier = Modifier
                    .onSizeChanged { headerHeightPx = it.height }
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            if (openListHeightPx > 0f) {
                                scope.launch {
                                    openness.snapTo((openness.value - delta / openListHeightPx).coerceIn(0f, 1f))
                                }
                            }
                        },

                        // Let go: a quick flick decides, otherwise whichever it's closer to.
                        onDragStopped = { velocity ->
                            val flingSpeed = with(density) { FLING_SPEED.toPx() }
                            val open = when {
                                velocity < -flingSpeed -> true
                                velocity > flingSpeed -> false
                                else -> openness.value > 0.5f
                            }
                            if (open == pinListOpen) {
                                openness.animateTo(if (open) 1f else 0f)
                            } else {
                                onPinListOpenChange(open)
                            }
                        },
                    ),
            )

            // Scrollable list with one row per photo. Tapping a row opens it.
            if (openness.value > 0f) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { (openListHeightPx * openness.value).toDp() }),
                ) {
                    if (listed.isEmpty()) {
                        item {
                            Text(
                                if (showAllPins) "No pins yet. Photos show up here once they're posted to the feed."
                                else "No pins yet. Take a photo with the Camera, or tap Add Photo, to drop your first pin.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                    items(listed, key = { it.id }) { photo ->
                        val mine = photo.uid != null && photo.uid == uid
                        PhotoPinRow(
                            photo = photo,
                            owner = authorLabel(photo.uid, uid),
                            mine = mine,
                            posted = !mine || photo.id in myPostedIds,
                            onClick = { selectedPhoto = photo },
                        )
                    }
                }
            }
        }
    }

    // Show the tapped photo in a pop-up (see PhotoDetailsDialog.kt).
    selectedPhoto?.let { photo ->
        // Use the latest version so an open pop-up also shows renamed captions.
        val currentPhoto = listed.firstOrNull { it.id == photo.id } ?: photo
        PhotoDetailsDialog(
            photo = currentPhoto,
            onDismiss = { selectedPhoto = null },
            onMessage = onMessage,
            onViewPost = onViewPost,
            onCreatePost = onCreatePost,
        )
    }
}

// The bar between the map and the pin list: a grab handle, the pin counts, and an arrow.
// The arrow points down while the list is open (tap to hide it) and up while it's hidden
// (tap to show it). It turns as the list is dragged.
@Composable
private fun PinListHeader(
    title: String,
    subtitle: String,

    // How open the list is, from 0 (hidden) to 1 (open).
    openness: Float,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val open = openness > 0.5f
    Surface(modifier.fillMaxWidth(), shadowElevation = 6.dp) {
        Column(
            Modifier
                .clickable(onClickLabel = if (open) "Hide the list of pins" else "Show the list of pins", onClick = onToggle)
                .semantics { stateDescription = if (open) "List showing" else "List hidden" }
                .padding(start = 16.dp, end = 10.dp, top = 6.dp, bottom = 8.dp),
        ) {

            // Grab handle, like a bottom sheet's, to hint that the header can be dragged.
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    ChevronUpIcon,
                    contentDescription = null,
                    tint = Turquoise,
                    modifier = Modifier.size(34.dp).rotate(180f * openness),
                )
            }
        }
    }
}

// Material "expand_less" (Apache License 2.0): an arrow pointing up.
private val ChevronUpIcon = ImageVector.Builder("ChevronUp", 24.dp, 24.dp, 24f, 24f)
    .addPath(addPathNodes("M12 8l-6 6 1.41 1.41L12 10.83l4.59 4.58L18 14z"), fill = SolidColor(Color.Black))
    .build()

// The "All users' pins" switch over the map. Off shows only your pins.
@Composable
private fun AllPinsSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.95f),
        shadowElevation = 4.dp,
    ) {
        Row(
            Modifier
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(start = 14.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("All users' pins", color = Color(0xFF294C39), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(8.dp))

            // onCheckedChange is null because the whole pill handles taps.
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedTrackColor = Turquoise),
            )
        }
    }
}

// One row in the photo list: caption, who took it, location, and time taken.
// The dot matches the pin color. Your pins say whether other users can see them.
@Composable
private fun PhotoPinRow(photo: NaturePhoto, owner: String, mine: Boolean, posted: Boolean, onClick: () -> Unit) {

    ListItem(
        leadingContent = {
            Box(Modifier.size(12.dp).background(if (mine) Turquoise else CampusRed, CircleShape))
        },
        headlineContent = { Text(photo.caption.ifBlank { "(no caption)" }) },
        supportingContent = {

            // Coordinates to 5 decimal places, or "no location".
            val where = if (photo.latitude != null && photo.longitude != null) {
                "%.5f, %.5f".format(photo.latitude, photo.longitude)
            } else "no location"

            // The server fills in the time shortly after saving, so show "just now" until then.
            val whenTaken = photo.createdAt?.let { DateFormat.getDateTimeInstance().format(it) } ?: "just now"
            Text("$owner · $where · $whenTaken")
        },
        trailingContent = if (mine) {
            {
                Text(
                    if (posted) "Posted" else "Private",
                    color = if (posted) Turquoise else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        } else null,

        // Make the whole row tappable.
        modifier = Modifier.clickable(onClickLabel = "View photo", onClick = onClick),
    )
}

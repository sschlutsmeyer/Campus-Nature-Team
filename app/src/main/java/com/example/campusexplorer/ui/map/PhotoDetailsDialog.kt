package com.example.campusexplorer.ui.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campusexplorer.data.FeedPost
import com.example.campusexplorer.data.LocalPhotoStore
import com.example.campusexplorer.data.NaturePhoto
import com.example.campusexplorer.data.POST_TITLE_MAX
import com.example.campusexplorer.data.PhotoRepository
import com.example.campusexplorer.data.PostRepository
import com.example.campusexplorer.ui.CampusRed
import com.example.campusexplorer.ui.LightGreen
import com.example.campusexplorer.ui.LocalNavigator
import com.example.campusexplorer.ui.Turquoise
import com.example.campusexplorer.ui.account.UserAvatar
import com.example.campusexplorer.ui.account.rememberDisplayName
import com.example.campusexplorer.ui.feed.CloseIcon
import com.example.campusexplorer.ui.feed.userMessage
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Label for this file's messages in Logcat.
private const val TAG = "PhotoDetailsDialog"

// Bottom sheet for a map pin: photo, post actions, rename, delete, and retry.
// Only the photo's owner can rename or delete it.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailsDialog(
    photo: NaturePhoto,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,

    // Opens this photo's post in the feed. Null hides the "View post" button.
    onViewPost: ((postId: String) -> Unit)? = null,

    // Starts a feed post with this photo (yours, not posted yet). Null hides the "Post to feed" button.
    onCreatePost: ((NaturePhoto) -> Unit)? = null,
) {

    // The image, whether it's loading, and any error message.
    var bitmap by remember(photo.id) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(photo.id) { mutableStateOf(true) }
    var error by remember(photo.id) { mutableStateOf<String?>(null) }

    // Increase this to download the image again.
    var retryCount by remember(photo.id) { mutableStateOf(0) }

    // Whether the "Delete photo?" pop-up is showing, and whether a delete is running.
    var confirmingDelete by remember(photo.id) { mutableStateOf(false) }
    var deleting by remember(photo.id) { mutableStateOf(false) }

    // Names are captions in Firestore. Only the owner can rename or delete a pin.
    val myUid = FirebaseAuth.getInstance().currentUser?.uid
    val isOwner = photo.uid != null && photo.uid == myUid
    var displayedCaption by remember(photo.id, photo.caption) { mutableStateOf(photo.caption) }
    var editingName by remember(photo.id) { mutableStateOf(false) }
    var nameDraft by remember(photo.id) { mutableStateOf("") }
    var savingName by remember(photo.id) { mutableStateOf(false) }
    var nameError by remember(photo.id) { mutableStateOf<String?>(null) }

    // This photo's feed post, if it has one (a photo post has the same id as its photo).
    // postChecked stays false if posts can't be read, which hides both post buttons.
    var post by remember(photo.id) { mutableStateOf<FeedPost?>(null) }
    var postChecked by remember(photo.id) { mutableStateOf(false) }

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

    // Keep track of whether this photo has a post, so the right button shows.
    LaunchedEffect(photo.id) {
        try {
            PostRepository.observePost(photo.id).collect {
                post = it
                postChecked = true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't check whether photo ${photo.id} is posted", e)
            postChecked = false
        }
    }

    val busy = savingName || deleting
    val currentBusy by rememberUpdatedState(busy)
    var closing by remember(photo.id) { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        // Keep the sheet open until a save/delete operation has finished.
        confirmValueChange = { value -> value != SheetValue.Hidden || !currentBusy },
    )
    val pinText = Color(0xFF294C39)
    val mutedText = Color(0xFF65756C)
    val actionsEnabled = !busy && !closing

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(0.9f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color.White,
        contentColor = pinText,
        scrimColor = Color.Black.copy(alpha = 0.4f),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Fixed header, matching the Learn species details sheet.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    displayedCaption.ifBlank { "Nature photo" },
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    color = pinText,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    enabled = actionsEnabled,
                    onClick = {
                        closing = true
                        scope.launch {
                            try {
                                sheetState.hide()
                                if (!sheetState.isVisible) onDismiss()
                            } finally {
                                closing = false
                            }
                        }
                    },
                    modifier = Modifier.size(40.dp)
                        .background(Color(0xFFF0F2F0), CircleShape),
                ) {
                    Icon(CloseIcon, contentDescription = "Close pin details", tint = Color.DarkGray)
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Keep the incoming profile link for other users' pins.
                val authorUid = photo.uid
                if (!isOwner && authorUid != null) {
                    val nav = LocalNavigator.current
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = actionsEnabled, onClickLabel = "View profile") {
                                onDismiss()
                                nav.openUser(authorUid)
                            }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UserAvatar(authorUid, 26.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Posted by ${rememberDisplayName(authorUid)}",
                            color = mutedText, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("View profile", color = Turquoise, fontSize = 12.sp)
                    }
                }

                val visibility = when {
                    !isOwner || !postChecked -> null
                    post != null -> "Posted to the feed: everyone can see this pin."
                    else -> "Private: only you can see this pin until you post it."
                }
                if (visibility != null) {
                    Text(visibility, color = mutedText, fontSize = 12.sp, lineHeight = 18.sp)
                }

                val image = bitmap
                if (!loading && error == null && image != null) {
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = displayedCaption.ifBlank { "Saved nature photo" },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth()
                            .aspectRatio(image.width.toFloat() / image.height.coerceAtLeast(1))
                            .clip(RoundedCornerShape(16.dp)),
                    )
                } else {
                    Box(
                        Modifier.fillMaxWidth().height(240.dp)
                            .clip(RoundedCornerShape(16.dp)).background(LightGreen),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (loading) {
                            CircularProgressIndicator(color = Turquoise)
                        } else {
                            Text(error ?: "No image was found for this photo.",
                                color = mutedText, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp))
                        }
                    }
                }

                if (error != null && !loading) {
                    OutlinedButton(
                        enabled = actionsEnabled,
                        onClick = { retryCount++ },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Retry photo", color = Turquoise) }
                }

                val currentPost = post
                if (currentPost != null && onViewPost != null) {
                    Button(
                        enabled = actionsEnabled,
                        onClick = { onDismiss(); onViewPost(currentPost.id) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Turquoise,
                            contentColor = Color.White),
                    ) { Text("View post") }
                } else if (postChecked && currentPost == null && isOwner && onCreatePost != null) {
                    Button(
                        enabled = actionsEnabled,
                        onClick = { onDismiss(); onCreatePost(photo.copy(caption = displayedCaption)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Turquoise,
                            contentColor = Color.White),
                    ) { Text("Post to feed") }
                }

                if (isOwner) {
                    OutlinedButton(
                        enabled = actionsEnabled,
                        onClick = {
                            nameDraft = displayedCaption
                            nameError = null
                            editingName = true
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Edit name", color = Turquoise) }
                    TextButton(
                        enabled = actionsEnabled,
                        onClick = { confirmingDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = CampusRed),
                    ) { Text(if (deleting) "Deleting…" else "Delete photo") }
                }
            }
        }
    }

    if (editingName) {
        AlertDialog(
            onDismissRequest = { if (!savingName) editingName = false },
            title = { Text("Edit pin name") },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = {
                        nameDraft = it.take(POST_TITLE_MAX)
                        nameError = null
                    },
                    label = { Text("Pin name") },
                    singleLine = true,
                    enabled = !savingName,
                    isError = nameError != null,
                    supportingText = {
                        Text(
                            nameError ?: if (post != null) {
                                "This also renames its feed post."
                            } else {
                                "Enter a name for this pin."
                            }
                        )
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
                                onMessage("Pin name updated")
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "Could not rename photo ${photo.id}", e)
                                nameError = userMessage(e, "Couldn't save the name.")
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
            text = {
                Text(
                    if (post != null) {
                        "This removes the photo and its feed post (with its likes and comments) " +
                                "for everyone, and can't be undone."
                    } else {
                        "This removes the photo for good and can't be undone."
                    }
                )
            },
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
                                onMessage(userMessage(e, "Delete failed."))

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

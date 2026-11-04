package com.example.campusexplorer.ui.feed

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.campusexplorer.data.FeedPost
import com.example.campusexplorer.data.NaturePhoto
import com.example.campusexplorer.data.POST_DESCRIPTION_MAX
import com.example.campusexplorer.data.POST_TITLE_MAX
import com.example.campusexplorer.data.PhotoRepository
import com.example.campusexplorer.data.PostRepository
import com.example.campusexplorer.ui.CampusRed
import com.example.campusexplorer.ui.LightGreen
import com.example.campusexplorer.ui.PhotoImage
import com.example.campusexplorer.ui.Turquoise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// Label for this file's messages in Logcat.
private const val TAG = "PostEditor"

// The two pages of the new-post pop-up.
private enum class ComposeStep { ChoosePhoto, Write }

// Pop-up for making a new post. First choose one of your photos (or a text-only post), then add a
// title and description. A photo post's title starts as the photo's pin name. Changing the title
// renames the pin too, so the map, gallery, and feed always show the same name.
@Composable
fun CreatePostDialog(
    myUid: String,

    // Photos that already have a post. Each photo can be posted once, so these can't be chosen.
    postedPhotoIds: Set<String>,
    onDismiss: () -> Unit,

    // Called with the new post's id after it's published.
    onPosted: (postId: String) -> Unit,

    // A photo to start with (from a pin's "Post to feed" button), skipping the photo choice.
    initialPhotoId: String? = null,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()

    // Your photos, or null while they load.
    var photos by remember { mutableStateOf<List<NaturePhoto>?>(null) }
    var photosFailed by remember { mutableStateOf(false) }
    var photosRetry by remember { mutableIntStateOf(0) }

    // What's been chosen and typed so far. Saved so it survives the phone rotating.
    var step by rememberSaveable { mutableStateOf(ComposeStep.ChoosePhoto) }
    var photoId by rememberSaveable { mutableStateOf<String?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }

    // True once you type your own title, so choosing a different photo doesn't replace it.
    var titleEdited by rememberSaveable { mutableStateOf(false) }
    var initialPhotoHandled by rememberSaveable { mutableStateOf(initialPhotoId == null) }

    var posting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(myUid, photosRetry, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            photosFailed = false
            try {
                PhotoRepository.observeOwnPhotos(myUid).collect { photos = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't load photos for a new post", e)
                photosFailed = true
            }
        }
    }

    val selectedPhoto = photos?.firstOrNull { it.id == photoId }

    // Picks a photo (or null for text-only) and moves on to writing.
    // The title follows the photo's name until you type your own.
    fun choose(photo: NaturePhoto?) {
        photoId = photo?.id
        if (!titleEdited) title = photo?.caption.orEmpty().trim().take(POST_TITLE_MAX)
        error = null
        step = ComposeStep.Write
    }

    LaunchedEffect(photos) {
        val list = photos ?: return@LaunchedEffect

        // Start with the photo from "Post to feed", once your photos have loaded.
        if (!initialPhotoHandled) {
            initialPhotoHandled = true
            list.firstOrNull { it.id == initialPhotoId && it.id !in postedPhotoIds }?.let { choose(it) }
        }

        // The chosen photo was deleted (maybe on another screen). Go back to choosing.
        if (photoId != null && list.none { it.id == photoId }) {
            photoId = null
            step = ComposeStep.ChoosePhoto
        }
    }

    // Back goes from writing to choosing, then closes.
    val goBack: () -> Unit = {
        when {
            posting -> Unit
            step == ComposeStep.Write -> {
                step = ComposeStep.ChoosePhoto
                error = null
            }
            else -> onDismiss()
        }
    }

    val publish: () -> Unit = {
        posting = true
        error = null
        scope.launch {
            try {
                onPosted(PostRepository.createPost(title, description, selectedPhoto))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't create post", e)
                error = userMessage(e, "Couldn't publish your post.")
                posting = false
            }
        }
    }

    Dialog(
        onDismissRequest = goBack,

        // Tapping outside shouldn't throw away what you've written.
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.9f),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            contentColor = FeedText,
        ) {
            Column(Modifier.fillMaxSize()) {
                EditorTopBar(
                    onBack = if (step == ComposeStep.Write) goBack else null,
                    onClose = onDismiss,
                    enabled = !posting,
                )
                HorizontalDivider(color = LightGreen, thickness = 2.dp)

                when (step) {
                    ComposeStep.ChoosePhoto -> PhotoChooser(
                        photos = photos,
                        failed = photosFailed,
                        postedPhotoIds = postedPhotoIds,
                        onRetry = { photosRetry++ },
                        onChoose = { choose(it) },
                        modifier = Modifier.weight(1f),
                    )
                    ComposeStep.Write -> PostForm(
                        photo = selectedPhoto,
                        photoLoading = photoId != null && selectedPhoto == null,
                        title = title,
                        onTitleChange = {
                            title = it

                            // Clearing the title lets the next photo you choose fill it in again.
                            titleEdited = it.isNotBlank()
                        },
                        description = description,
                        onDescriptionChange = { description = it },
                        posting = posting,
                        error = error,
                        onChangePhoto = goBack,
                        onPost = publish,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// Pop-up for changing your post's title and description.
@Composable
fun EditPostDialog(post: FeedPost, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var title by rememberSaveable(post.id) { mutableStateOf(post.title) }
    var description by rememberSaveable(post.id) { mutableStateOf(post.description) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val changed = title.trim() != post.title || description.trim() != post.description

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Edit post") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(POST_TITLE_MAX) },
                    label = { Text("Title") },
                    singleLine = true,
                    enabled = !saving,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next,
                    ),
                    supportingText = {
                        val note = if (post.photoId != null && title.trim() != post.title) {
                            "Your photo's pin will be renamed too."
                        } else null
                        FieldNote(note, title.length, POST_TITLE_MAX)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(POST_DESCRIPTION_MAX) },
                    label = { Text("Description") },
                    minLines = 3,
                    maxLines = 8,
                    enabled = !saving,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    supportingText = { FieldNote(null, description.length, POST_DESCRIPTION_MAX) },
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && changed && title.isNotBlank(),
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            PostRepository.updatePost(post, title, description)
                            onSaved()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Couldn't update post ${post.id}", e)
                            error = userMessage(e, "Couldn't save your changes.")
                            saving = false
                        }
                    }
                },
            ) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// "New post" heading with a back arrow (while writing) and a close button.
@Composable
private fun EditorTopBar(onBack: (() -> Unit)?, onClose: () -> Unit, enabled: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Same width either way, so the heading stays centered.
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (onBack != null) {
                IconButton(onClick = onBack, enabled = enabled) {
                    Icon(BackArrowIcon, contentDescription = "Back to choosing a photo", tint = Turquoise)
                }
            }
        }
        Text(
            "New post",
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = FeedText,
        )
        IconButton(onClick = onClose, enabled = enabled) {
            Icon(CloseIcon, contentDescription = "Close without posting", tint = FeedMuted)
        }
    }
}

// Page 1: a text-only option and a grid of your photos.
@Composable
private fun PhotoChooser(
    photos: List<NaturePhoto>?,
    failed: Boolean,
    postedPhotoIds: Set<String>,
    onRetry: () -> Unit,

    // Called with the chosen photo, or null for a text-only post.
    onChoose: (NaturePhoto?) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(LightGreen)
                .clickable { onChoose(null) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(PencilIcon, contentDescription = null, tint = Turquoise, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Text-only post", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = FeedText)
                Text("Share a tip, question, or sighting without a photo.", fontSize = 13.sp, color = FeedMuted)
            }
        }

        Text(
            "Or share a photo from your gallery",
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = FeedText,
            modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
        )

        when {
            failed -> ChooserMessage("Couldn't load your photos.", Modifier.weight(1f), onRetry)
            photos == null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Turquoise, modifier = Modifier.size(30.dp))
            }
            photos.isEmpty() -> ChooserMessage(
                "You haven't saved any photos yet. Take one with the Camera, then come back to post it.",
                Modifier.weight(1f),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(photos, key = { it.id }) { photo ->
                    ChooserTile(photo, posted = photo.id in postedPhotoIds, onClick = { onChoose(photo) })
                }
            }
        }
    }
}

// One photo in the chooser, with its name underneath. Already-posted photos are dimmed and can't be chosen.
@Composable
private fun ChooserTile(photo: NaturePhoto, posted: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = !posted, onClickLabel = "Post this photo", onClick = onClick)
            .semantics { if (posted) stateDescription = "Already posted" },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))) {
            PhotoImage(photo, Modifier.fillMaxSize(), maxSize = 384)
            if (posted) {
                Box(
                    Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Posted", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
        Text(
            photo.caption.ifBlank { "(no name)" },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp,
            color = if (posted) FeedMuted else FeedText,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
        )
    }
}

@Composable
private fun ChooserMessage(message: String, modifier: Modifier, onRetry: (() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = FeedMuted, textAlign = TextAlign.Center)
        if (onRetry != null) {
            TextButton(onClick = onRetry) { Text("Try again", color = Turquoise) }
        }
    }
}

// Page 2: the chosen photo (or a text-only note), title, description, and the Post button.
@Composable
private fun PostForm(
    photo: NaturePhoto?,

    // A photo was chosen but your photos are still loading (for example, after rotating the phone).
    photoLoading: Boolean,
    title: String,
    onTitleChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    posting: Boolean,
    error: String?,
    onChangePhoto: () -> Unit,
    onPost: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            photo != null -> PhotoImage(
                photo,
                Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(14.dp)),
                maxSize = 1024,
            )
            photoLoading -> Box(
                Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(14.dp)).background(LightGreen),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = Turquoise, modifier = Modifier.size(28.dp)) }
            else -> Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(LightGreen).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PencilIcon, contentDescription = null, tint = Turquoise, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Text-only post", fontWeight = FontWeight.SemiBold, color = FeedText)
            }
        }
        TextButton(onClick = onChangePhoto, enabled = !posting) {
            Text(if (photo != null || photoLoading) "Change photo" else "Add a photo instead", color = Turquoise)
        }

        // Explain that the title and the pin name are linked.
        val note = when {
            photo == null -> null
            title.isBlank() -> "Starts as your pin's name. Changing it renames the pin too."
            title.trim() != photo.caption.trim() ->
                "Your pin “${photo.caption.ifBlank { "(no name)" }}” will be renamed to “${title.trim()}”."
            else -> "Your pin's name. Changing it renames the pin too."
        }
        OutlinedTextField(
            value = title,
            onValueChange = { onTitleChange(it.take(POST_TITLE_MAX)) },
            label = { Text("Title") },
            singleLine = true,
            enabled = !posting,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
            ),
            supportingText = { FieldNote(note, title.length, POST_TITLE_MAX) },
            colors = feedFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = description,
            onValueChange = { onDescriptionChange(it.take(POST_DESCRIPTION_MAX)) },
            label = { Text("Description (optional)") },
            placeholder = { Text("What did you find? Where? Anything others should know?") },
            minLines = 4,
            maxLines = 10,
            enabled = !posting,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            supportingText = { FieldNote(null, description.length, POST_DESCRIPTION_MAX) },
            colors = feedFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        if (error != null) Text(error, color = CampusRed, fontSize = 14.sp)

        Button(
            onClick = onPost,
            enabled = !posting && !photoLoading && title.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Turquoise, contentColor = Color.White),
        ) {
            if (posting) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Posting…")
            } else {
                Text("Post", fontSize = 16.sp)
            }
        }
    }
}

// Under a text box: an optional note on the left and a "12/100" character count on the right.
@Composable
private fun FieldNote(note: String?, length: Int, max: Int) {
    Row(Modifier.fillMaxWidth()) {
        Text(note.orEmpty(), modifier = Modifier.weight(1f).padding(end = 8.dp))
        Text("$length/$max")
    }
}

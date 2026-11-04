package com.example.campusexplorer.ui.feed

import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.campusexplorer.data.COMMENT_MAX
import com.example.campusexplorer.data.FeedPost
import com.example.campusexplorer.data.PhotoRepository
import com.example.campusexplorer.data.PostComment
import com.example.campusexplorer.data.PostRepository
import com.example.campusexplorer.data.Reaction
import com.example.campusexplorer.ui.CampusRed
import com.example.campusexplorer.ui.LeafGreen
import com.example.campusexplorer.ui.LightGreen
import com.example.campusexplorer.ui.PhotoImage
import com.example.campusexplorer.ui.Turquoise
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Label for this file's messages in Logcat.
private const val TAG = "FeedScreen"

// Lets the set of opened posts survive the phone rotating.
private val StringSetSaver = Saver<Set<String>, ArrayList<String>>(
    save = { ArrayList(it) },
    restore = { it.toSet() },
)

// The Feed: everyone's posts, newest first. Only things posted here (with the Post button)
// appear in the feed; saving a photo with the camera keeps it private until you post it.
@Composable
fun FeedScreen(
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,

    // A post to scroll to and open (from a pin's "View post" button). Cleared with onFocusHandled.
    focusPostId: String? = null,
    onFocusHandled: () -> Unit = {},

    // A photo to start a new post with (from a pin's "Post to feed" button). Cleared with onComposeHandled.
    composePhotoId: String? = null,
    onComposeHandled: () -> Unit = {},

    // Shows a post's photo on the map. Null hides the Map button.
    onViewOnMap: ((FeedPost) -> Unit)? = null,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val myUid by remember { PhotoRepository.observeUserId() }
        .collectAsStateWithLifecycle(FirebaseAuth.getInstance().currentUser?.uid)

    var posts by remember { mutableStateOf<List<FeedPost>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }

    val listState = rememberLazyListState()

    // Posts showing their full description and comments ("Show more").
    var expandedIds by rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(emptySet<String>()) }

    // The new-post pop-up, and the photo it starts with (null to start by choosing).
    var composing by rememberSaveable { mutableStateOf(false) }
    var composeWithPhotoId by rememberSaveable { mutableStateOf<String?>(null) }

    // A just-published post to scroll to once it shows up.
    var scrollToPostId by rememberSaveable { mutableStateOf<String?>(null) }

    // Your post being edited or deleted, if any.
    var editingPost by remember { mutableStateOf<FeedPost?>(null) }
    var deletingPost by remember { mutableStateOf<FeedPost?>(null) }
    var deleteInProgress by remember { mutableStateOf(false) }

    LaunchedEffect(lifecycle, retry) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {

            // Keep showing the posts we have while reconnecting after the app was in the background.
            loading = posts.isEmpty()
            loadError = null
            try {
                PostRepository.observePosts().collect {
                    posts = it
                    loading = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not load feed", e)
                loadError = userMessage(e, "Couldn't load the feed.")
                loading = false
            }
        }
    }

    // Open the post from a pin's "View post" button once the feed has loaded.
    LaunchedEffect(focusPostId, posts, loading, loadError) {
        val target = focusPostId ?: return@LaunchedEffect
        if (loading || loadError != null) return@LaunchedEffect
        val index = posts.indexOfFirst { it.id == target }
        if (index < 0) {

            // The list may still be catching up with the server. If it updates, this starts over.
            delay(4_000)
            onMessage("That post is no longer in the feed.")
        } else {
            expandedIds = expandedIds + target
            listState.scrollToItem(index)
        }
        onFocusHandled()
    }

    // Open the new-post pop-up for a pin's "Post to feed" button.
    LaunchedEffect(composePhotoId) {
        val photoId = composePhotoId ?: return@LaunchedEffect
        composeWithPhotoId = photoId
        composing = true
        onComposeHandled()
    }

    // Show a just-published post.
    LaunchedEffect(scrollToPostId, posts) {
        val target = scrollToPostId ?: return@LaunchedEffect
        val index = posts.indexOfFirst { it.id == target }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            scrollToPostId = null
        }
    }

    // Like, dislike, or undo. The feed updates right away; a failure puts it back and says why.
    fun react(post: FeedPost, reaction: Reaction) {
        scope.launch {
            try {
                PostRepository.setReaction(post, reaction)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't save reaction to ${post.id}", e)
                onMessage(userMessage(e, "Couldn't save your reaction."))
            }
        }
    }

    Column(modifier.fillMaxSize().background(Color.White)) {
        FeedHeader(
            canPost = myUid != null,
            onPost = {
                composeWithPhotoId = null
                composing = true
            },
        )

        when {
            loadError != null -> FeedStatus(
                message = loadError,
                modifier = Modifier.weight(1f),
                onRetry = { retry++ },
            )
            loading -> FeedStatus(modifier = Modifier.weight(1f))
            posts.isEmpty() -> FeedStatus(
                message = "No posts yet.\nTap Post to share a photo from your gallery or write a text post.",
                modifier = Modifier.weight(1f),
            )
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
            ) {
                items(posts, key = { it.id }) { post ->
                    FeedPostCard(
                        post = post,
                        myUid = myUid,
                        expanded = post.id in expandedIds,
                        onExpandedChange = { open ->
                            expandedIds = if (open) expandedIds + post.id else expandedIds - post.id
                        },
                        onReact = { react(post, reaction = it) },
                        onBookmark = {
                            scope.launch {
                                try {
                                    PostRepository.toggleBookmark(post)
                                } catch (e: Exception) {
                                    onMessage(userMessage(e, "Could not update bookmark"))
                                }
                            }
                        },
                        onMessage = onMessage,
                        onEdit = { editingPost = post },
                        onDelete = { deletingPost = post },
                        onViewOnMap = if (onViewOnMap != null && post.hasLocation) {
                            { onViewOnMap(post) }
                        } else null,
                    )
                    HorizontalDivider(
                        color = LightGreen,
                        thickness = 2.dp,
                        modifier = Modifier.padding(vertical = 14.dp),
                    )
                }
            }
        }
    }

    val uid = myUid
    if (composing && uid != null) {
        val postedPhotoIds = remember(posts) { posts.mapNotNullTo(HashSet()) { it.photoId } }
        CreatePostDialog(
            myUid = uid,
            postedPhotoIds = postedPhotoIds,
            initialPhotoId = composeWithPhotoId,
            onDismiss = {
                composing = false
                composeWithPhotoId = null
            },
            onPosted = { postId ->
                composing = false
                composeWithPhotoId = null
                scrollToPostId = postId
                onMessage("Posted to the feed!")
            },
        )
    }

    editingPost?.let { post ->
        EditPostDialog(
            post = post,
            onDismiss = { editingPost = null },
            onSaved = {
                editingPost = null
                onMessage("Post updated")
            },
        )
    }

    deletingPost?.let { post ->
        AlertDialog(
            onDismissRequest = { if (!deleteInProgress) deletingPost = null },
            title = { Text("Delete post?") },
            text = {
                Text(
                    if (post.photoId != null) {
                        "This removes the post and its likes and comments for everyone. Your photo " +
                            "stays in your gallery and on your map, but other users won't see it."
                    } else {
                        "This removes the post and its likes and comments for everyone."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !deleteInProgress,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        deleteInProgress = true
                        scope.launch {
                            try {
                                PostRepository.deletePost(post)
                                expandedIds = expandedIds - post.id
                                onMessage("Post deleted")
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(TAG, "Couldn't delete post ${post.id}", e)
                                onMessage(userMessage(e, "Couldn't delete the post."))
                            } finally {
                                deleteInProgress = false
                                deletingPost = null
                            }
                        }
                    },
                ) { Text(if (deleteInProgress) "Deleting…" else "Delete") }
            },
            dismissButton = {
                TextButton(enabled = !deleteInProgress, onClick = { deletingPost = null }) { Text("Cancel") }
            },
        )
    }
}

// "Feed" heading with the Post button.
@Composable
private fun FeedHeader(canPost: Boolean, onPost: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Feed", color = FeedText, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Discoveries around campus", color = Turquoise, fontSize = 14.sp)
        }
        Button(
            onClick = onPost,
            enabled = canPost,
            colors = ButtonDefaults.buttonColors(containerColor = Turquoise, contentColor = Color.White),
            contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
        ) {
            Icon(PlusIcon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("Post", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// One post: author, title, photo, reactions, and a short preview of the description that trails off.
// "Show more" opens the full description and the comments; "Show less" closes them again.
@Composable
fun FeedPostCard(
    post: FeedPost,
    myUid: String?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onReact: (Reaction) -> Unit,
    onBookmark: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,

    // Shown in a menu on your own posts. Null hides the option.
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,

    // Null hides the Map button (text posts and photos without a location).
    onViewOnMap: (() -> Unit)? = null,
) {
    val isBookmarked = myUid != null && myUid in post.bookmarkedBy
    val isMine = myUid != null && post.uid == myUid
    val myReaction = when {
        myUid == null -> Reaction.NONE
        myUid in post.likedBy -> Reaction.LIKE
        myUid in post.dislikedBy -> Reaction.DISLIKE
        else -> Reaction.NONE
    }

    // Set by the Comment button so the comment box gets the keyboard once it appears.
    var focusCommentBox by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth().animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Avatar(40.dp)
            Column(Modifier.weight(1f)) {
                Text(authorLabel(post.uid, myUid), color = FeedText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(relativeTime(post.createdAt), color = FeedMuted, fontSize = 12.sp)
            }
            if (isMine && (onEdit != null || onDelete != null)) PostMenu(onEdit, onDelete)
        }

        Text(
            post.title,
            color = FeedText,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        post.photo?.let { photo ->
            PhotoImage(
                photo,
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                contentDescription = post.title,
            )
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FeedAction(
                icon = ThumbIcon,
                text = post.likedBy.size.toString(),
                description = "Like",
                tint = if (myReaction == Reaction.LIKE) Turquoise else FeedMuted,
                selected = myReaction == Reaction.LIKE,
                enabled = myUid != null,
                onClick = { onReact(if (myReaction == Reaction.LIKE) Reaction.NONE else Reaction.LIKE) },
            )
            FeedAction(
                icon = ThumbIcon,
                iconRotation = 180f,
                text = post.dislikedBy.size.toString(),
                description = "Dislike",
                tint = if (myReaction == Reaction.DISLIKE) CampusRed else FeedMuted,
                selected = myReaction == Reaction.DISLIKE,
                enabled = myUid != null,
                onClick = { onReact(if (myReaction == Reaction.DISLIKE) Reaction.NONE else Reaction.DISLIKE) },
            )
            FeedAction(
                icon = CommentIcon,
                text = post.commentCount.toString(),
                description = "Comment",
                onClick = {
                    onExpandedChange(true)
                    focusCommentBox = true
                },
            )
            FeedAction(
                icon = if (isBookmarked) BookmarkFilledIcon else BookmarkOutlineIcon,
                text = "",
                description = "Bookmark",
                tint = if (isBookmarked) Turquoise else FeedMuted,
                selected = isBookmarked,
                enabled = true,
                onClick = onBookmark
            )
            Spacer(Modifier.weight(1f))
            if (onViewOnMap != null) {
                FeedAction(icon = PinIcon, text = "Map", description = "View on map", tint = Turquoise, onClick = onViewOnMap)
            }
        }

        if (post.description.isNotBlank()) {
            Text(
                post.description,
                color = FeedText,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        if (expanded) {
            PostComments(
                post = post,
                myUid = myUid,
                focusCommentBox = focusCommentBox,
                onCommentBoxFocused = { focusCommentBox = false },
                onMessage = onMessage,
            )
        }

        TextButton(
            onClick = { onExpandedChange(!expanded) },
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            Text(if (expanded) "Show less" else "Show more", color = Turquoise, fontWeight = FontWeight.SemiBold)
        }
    }
}

// The comments under an opened post, with a box for adding your own.
@Composable
private fun PostComments(
    post: FeedPost,
    myUid: String?,
    focusCommentBox: Boolean,
    onCommentBoxFocused: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var comments by remember(post.id) { mutableStateOf<List<PostComment>>(emptyList()) }
    var loading by remember(post.id) { mutableStateOf(true) }
    var failed by remember(post.id) { mutableStateOf(false) }
    var retry by remember(post.id) { mutableIntStateOf(0) }
    var draft by rememberSaveable(post.id) { mutableStateOf("") }
    var confirmingDelete by remember(post.id) { mutableStateOf<PostComment?>(null) }
    val focusRequester = remember { FocusRequester() }

    // Comments are only loaded while the post is open.
    LaunchedEffect(post.id, retry, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            failed = false
            try {
                PostRepository.observeComments(post.id).collect {
                    comments = it
                    loading = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't load comments for ${post.id}", e)
                failed = true
                loading = false
            }
        }
    }

    LaunchedEffect(focusCommentBox) {
        if (!focusCommentBox) return@LaunchedEffect

        // Wait a frame so the comment box is on screen before focusing it.
        withFrameNanos { }
        try {
            focusRequester.requestFocus()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Comment box wasn't ready for focus", e)
        }
        onCommentBoxFocused()
    }

    val canSend = myUid != null && draft.isNotBlank()
    val send: () -> Unit = {
        val text = draft.trim()
        if (canSend) {

            // Clear the box right away. The comment shows up before the server confirms it.
            draft = ""
            scope.launch {
                try {
                    PostRepository.addComment(post, text)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Couldn't add comment to ${post.id}", e)

                    // Give the text back so it can be sent again.
                    if (draft.isEmpty()) draft = text
                    onMessage(userMessage(e, "Couldn't post your comment."))
                }
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        HorizontalDivider(color = LightGreen)
        Text(
            if (comments.isEmpty()) "Comments" else "Comments (${comments.size})",
            color = FeedText,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
        )

        when {
            failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Couldn't load comments.", color = FeedMuted, fontSize = 14.sp)
                TextButton(onClick = { retry++ }) { Text("Try again", color = Turquoise) }
            }
            loading -> CircularProgressIndicator(
                color = Turquoise,
                strokeWidth = 2.dp,
                modifier = Modifier.padding(vertical = 8.dp).size(20.dp),
            )
            comments.isEmpty() -> Text(
                "No comments yet. Start the conversation!",
                color = FeedMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            else -> comments.forEach { comment ->
                CommentRow(
                    comment = comment,
                    myUid = myUid,

                    // You can delete your own comments, and any comment on your own post.
                    canDelete = myUid != null && (comment.uid == myUid || post.uid == myUid),
                    onDelete = { confirmingDelete = comment },
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(COMMENT_MAX) },
                placeholder = { Text("Add a comment…") },
                enabled = myUid != null,
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { send() }),
                colors = feedFieldColors(),
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
            )
            IconButton(onClick = send, enabled = canSend) {
                Icon(
                    SendIcon,
                    contentDescription = "Send comment",
                    tint = if (canSend) Turquoise else FeedMuted.copy(alpha = 0.5f),
                )
            }
        }
    }

    confirmingDelete?.let { comment ->
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = { Text("Delete comment?") },
            text = {
                val preview = if (comment.text.length > 80) comment.text.take(80) + "…" else comment.text
                Text("“$preview” will be removed for everyone.")
            },
            confirmButton = {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        confirmingDelete = null
                        scope.launch {
                            try {
                                PostRepository.deleteComment(post, comment)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(TAG, "Couldn't delete comment ${comment.id}", e)
                                onMessage(userMessage(e, "Couldn't delete the comment."))
                            }
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

// One comment: who wrote it, when, and what it says.
@Composable
private fun CommentRow(comment: PostComment, myUid: String?, canDelete: Boolean, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Avatar(28.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(authorLabel(comment.uid, myUid), color = FeedText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(" · ${relativeTime(comment.createdAt)}", color = FeedMuted, fontSize = 12.sp)
            }
            Text(comment.text, color = FeedText, fontSize = 14.sp, lineHeight = 19.sp)
        }
        if (canDelete) {
            TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 6.dp)) {
                Text("Delete", fontSize = 12.sp, color = FeedMuted)
            }
        }
    }
}

// The ⋮ menu on your own posts.
@Composable
private fun PostMenu(onEdit: (() -> Unit)?, onDelete: (() -> Unit)?) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(MoreIcon, contentDescription = "Post options", tint = FeedMuted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (onEdit != null) {
                DropdownMenuItem(
                    text = { Text("Edit post") },
                    onClick = {
                        open = false
                        onEdit()
                    },
                )
            }
            if (onDelete != null) {
                DropdownMenuItem(
                    text = { Text("Delete post", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        open = false
                        onDelete()
                    },
                )
            }
        }
    }
}

// An icon with a count or short label, like "👍 3".
@Composable
private fun FeedAction(
    icon: ImageVector,
    text: String,
    description: String,
    onClick: () -> Unit,
    tint: Color = FeedMuted,

    // For like and dislike: whether it's on. Read out by screen readers.
    selected: Boolean? = null,
    enabled: Boolean = true,
    iconRotation: Float = 0f,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 6.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = tint,
            disabledContentColor = FeedMuted.copy(alpha = 0.5f),
        ),
        modifier = Modifier.semantics { if (selected != null) this.selected = selected },
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp).rotate(iconRotation))
        Spacer(Modifier.width(5.dp))
        Text(text, fontSize = 14.sp)
    }
}

@Composable
private fun Avatar(size: Dp) {
    Box(Modifier.size(size).background(LightGreen, CircleShape), contentAlignment = Alignment.Center) {
        Icon(LeafIcon, contentDescription = null, tint = LeafGreen, modifier = Modifier.size(size * 0.6f))
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
            Text("Loading posts…", color = FeedMuted)
        } else {
            Text(message, color = FeedText, textAlign = TextAlign.Center)
            if (onRetry != null) {
                TextButton(onClick = onRetry) { Text("Try again", color = Turquoise) }
            }
        }
    }
}

package com.example.campusexplorer.ui.account

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.campusexplorer.data.AccountRepository
import com.example.campusexplorer.data.BIO_MAX
import com.example.campusexplorer.data.CameraRoll
import com.example.campusexplorer.data.FeedPost
import com.example.campusexplorer.data.PostRepository
import com.example.campusexplorer.data.SocialRepository
import com.example.campusexplorer.data.UserProfile
import com.example.campusexplorer.data.toAvatarJpeg
import com.example.campusexplorer.ui.CampusRed
import com.example.campusexplorer.ui.LightGreen
import com.example.campusexplorer.ui.LocalNavigator
import com.example.campusexplorer.ui.LocalSession
import com.example.campusexplorer.ui.LocalSocial
import com.example.campusexplorer.ui.PhotoImage
import com.example.campusexplorer.ui.Turquoise
import com.example.campusexplorer.ui.feed.CloseIcon
import com.example.campusexplorer.ui.feed.FeedMuted
import com.example.campusexplorer.ui.feed.FeedText
import com.example.campusexplorer.ui.feed.MoreIcon
import com.example.campusexplorer.ui.feed.PencilIcon
import com.example.campusexplorer.ui.feed.feedFieldColors
import com.example.campusexplorer.ui.feed.relativeTime
import com.example.campusexplorer.ui.withAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

// Label for this file's messages in Logcat.
private const val TAG = "ProfileScreen"

// A user's profile: picture, name, bio, counts, and posts. On your own profile you can edit your
// details, answer friend requests, find people, and log out. On someone else's you can follow them,
// add them as a friend, message them (once you're friends), or block them.
@Composable
fun ProfileScreen(
    uid: String,
    onMessage: (String) -> Unit,

    // Opens a post in the feed (from the grid of posts).
    onOpenPost: (FeedPost) -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = LocalSession.current
    val social = LocalSocial.current
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val isMe = uid == session.uid
    val liveProfile = rememberUserProfile(uid)
    val profile = if (isMe) session.profile ?: liveProfile else liveProfile
    val name = profile?.username ?: fallbackName(uid)

    val followers by remember(uid) { SocialRepository.observeFollowers(uid) }.collectAsStateWithLifecycle(emptySet())
    val following by remember(uid) { SocialRepository.observeFollowing(uid) }.collectAsStateWithLifecycle(emptySet())
    val friends by remember(uid) { SocialRepository.observeFriends(uid) }.collectAsStateWithLifecycle(emptySet())
    val posts by remember(uid) {
        PostRepository.observeUserPosts(uid).catch { e ->
            Log.w(TAG, "Couldn't load posts for $uid", e)
            emit(emptyList())
        }
    }.collectAsStateWithLifecycle<List<FeedPost>?>(null)

    // Your connections with this user.
    val iFollow = uid in social.following
    val followsMe = uid in social.followers
    val isFriend = uid in social.friends
    val requestSent = social.hasRequestTo(uid)
    val requestReceived = social.hasRequestFrom(uid)
    val blockedByMe = uid in social.blockedByMe
    val blockedMe = uid in social.blockedMe

    // True while a follow, friend, or block change is saving.
    var working by remember { mutableStateOf(false) }
    fun act(failure: String, success: String? = null, action: suspend () -> Unit) {
        if (working) return
        working = true
        scope.launch {
            try {
                action()
                success?.let(onMessage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, failure, e)
                onMessage(AccountRepository.errorMessage(e, failure))
            } finally {
                working = false
            }
        }
    }

    // Pop-ups.
    var shownList by rememberSaveable { mutableStateOf<PeopleList?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var findingPeople by rememberSaveable { mutableStateOf(false) }
    var changingPassword by rememberSaveable { mutableStateOf(false) }
    var photoOptions by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf<Confirm?>(null) }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            act("Couldn't change your profile picture.", "Profile picture updated") {
                val picked = CameraRoll.readPicked(context, uri)
                val jpeg = withContext(Dispatchers.Default) { picked.preview.toAvatarJpeg() }
                AccountRepository.setProfilePhoto(jpeg)
            }
        }
    }

    // Hide posts between users who've blocked each other.
    val hidePosts = !isMe && (blockedByMe || blockedMe)

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize().background(Color.White),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ProfileHeader(
                uid = uid,
                profile = profile,
                isMe = isMe,
                followsMe = followsMe && !isMe,
                onAvatarClick = if (isMe) ({ photoOptions = true }) else null,
                onAddBio = { editing = true },
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)) {
                Stat("Posts", posts?.size, onClick = null, modifier = Modifier.weight(1f))
                Stat("Followers", followers.size, onClick = { shownList = PeopleList.Followers }, modifier = Modifier.weight(1f))
                Stat("Following", following.size, onClick = { shownList = PeopleList.Following }, modifier = Modifier.weight(1f))
                Stat("Friends", friends.size, onClick = { shownList = PeopleList.Friends }, modifier = Modifier.weight(1f))
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    isMe -> MyActions(
                        blockedCount = social.blockedByMe.size,
                        onEdit = { editing = true },
                        onFindPeople = { findingPeople = true },
                        onChangePassword = { changingPassword = true },
                        onBlocked = { shownList = PeopleList.Blocked },
                        onLogOut = { confirming = Confirm.LogOut },
                    )

                    blockedByMe -> Notice("You've blocked $name. You won't see their posts, and they can't follow, friend, or message you.") {
                        OutlinedButton(onClick = { act("Couldn't unblock $name.", "Unblocked $name") { SocialRepository.unblock(uid) } }, enabled = !working) {
                            Text("Unblock", color = Turquoise)
                        }
                    }

                    blockedMe -> Notice("You can't follow, friend, or message $name.")

                    // Older posters from before accounts can't be followed or friended.
                    profile == null -> Unit

                    else -> {
                        if (requestReceived) {
                            Notice("$name wants to be friends.") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        onClick = { act("Couldn't decline.") { SocialRepository.declineFriendRequest(uid) } },
                                        enabled = !working,
                                    ) { Text("Decline", color = FeedMuted) }
                                    Button(
                                        onClick = { act("Couldn't accept.", "You and $name are now friends") { SocialRepository.acceptFriendRequest(uid) } },
                                        enabled = !working,
                                        colors = ButtonDefaults.buttonColors(containerColor = Turquoise),
                                    ) { Text("Accept") }
                                }
                            }
                        }
                        OtherActions(
                            name = name,
                            iFollow = iFollow,
                            followsMe = followsMe,
                            isFriend = isFriend,
                            requestSent = requestSent,
                            requestReceived = requestReceived,
                            enabled = !working,
                            onFollow = {
                                nav.withAccount(session, "follow people") {
                                    act("Couldn't follow $name.") { SocialRepository.follow(uid) }
                                }
                            },
                            onUnfollow = { act("Couldn't unfollow $name.") { SocialRepository.unfollow(uid) } },
                            onAddFriend = {
                                nav.withAccount(session, "add friends") {
                                    act("Couldn't send a friend request.", "Friend request sent to $name") {
                                        SocialRepository.sendFriendRequest(uid)
                                    }
                                }
                            },
                            onCancelRequest = { act("Couldn't cancel the request.") { SocialRepository.cancelFriendRequest(uid) } },
                            onUnfriend = { confirming = Confirm.Unfriend },
                            onMessage = { nav.openChat(uid) },
                            onRemoveFollower = { act("Couldn't remove $name.", "$name no longer follows you") { SocialRepository.removeFollower(uid) } },
                            onBlock = { nav.withAccount(session, "block people") { confirming = Confirm.Block } },
                        )
                    }
                }
            }
        }

        // Your friend requests: ones to answer, then ones you've sent.
        if (isMe && social.incomingRequests.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 14.dp)) {
                    SectionTitle("Friend requests (${social.incomingRequests.size})")
                    social.incomingRequests.forEach { request ->
                        UserRow(
                            uid = request.from,
                            onClick = { nav.openUser(request.from) },
                            supporting = "Asked ${relativeTime(request.createdAt).lowercase()}",
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = { act("Couldn't decline.") { SocialRepository.declineFriendRequest(request.from) } },
                                        enabled = !working,
                                    ) { Text("Decline", color = FeedMuted) }
                                    Button(
                                        onClick = { act("Couldn't accept.", "You're now friends") { SocialRepository.acceptFriendRequest(request.from) } },
                                        enabled = !working,
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Turquoise),
                                    ) { Text("Accept") }
                                }
                            },
                        )
                    }
                }
            }
        }
        if (isMe && social.outgoingRequests.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 10.dp)) {
                    SectionTitle("Requests you sent")
                    social.outgoingRequests.forEach { request ->
                        UserRow(
                            uid = request.to,
                            onClick = { nav.openUser(request.to) },
                            supporting = "Waiting for them to accept",
                            trailing = {
                                TextButton(
                                    onClick = { act("Couldn't cancel the request.") { SocialRepository.cancelFriendRequest(request.to) } },
                                    enabled = !working,
                                ) { Text("Cancel", color = FeedMuted) }
                            },
                        )
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 16.dp)) {
                HorizontalDivider(color = LightGreen, thickness = 2.dp)
                val count = posts?.size
                Text(
                    when {
                        hidePosts -> "Posts are hidden"
                        count == null -> "Posts"
                        count == 0 -> if (isMe) "No posts yet. Share a find from the Feed!" else "No posts yet"
                        else -> "Posts"
                    },
                    color = FeedText,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        if (!hidePosts) {
            items(posts.orEmpty(), key = { it.id }) { post ->
                PostTile(post, onClick = { onOpenPost(post) })
            }
        }

        if (isMe) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                TextButton(onClick = { confirming = Confirm.LogOut }, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Log out", color = CampusRed)
                }
            }
        }
    }

    // Pop-ups

    shownList?.let { list ->
        val uids = when (list) {
            PeopleList.Followers -> followers
            PeopleList.Following -> following
            PeopleList.Friends -> friends
            PeopleList.Blocked -> social.blockedByMe
        }
        UserListDialog(
            title = list.title,
            uids = uids.toList(),
            emptyText = when (list) {
                PeopleList.Followers -> if (isMe) "No followers yet." else "$name has no followers yet."
                PeopleList.Following -> if (isMe) "You're not following anyone yet. Tap a name on a post to follow them." else "$name isn't following anyone yet."
                PeopleList.Friends -> if (isMe) "No friends yet. Use Find people to add some." else "$name hasn't added friends yet."
                PeopleList.Blocked -> "You haven't blocked anyone."
            },
            onDismiss = { shownList = null },
            onOpenUser = nav.openUser,
            trailing = when {
                isMe && list == PeopleList.Blocked -> { blocked ->
                    TextButton(onClick = { act("Couldn't unblock.", "Unblocked") { SocialRepository.unblock(blocked) } }, enabled = !working) {
                        Text("Unblock", color = Turquoise)
                    }
                }
                isMe && list == PeopleList.Followers -> { follower ->
                    TextButton(onClick = { act("Couldn't remove that follower.") { SocialRepository.removeFollower(follower) } }, enabled = !working) {
                        Text("Remove", color = FeedMuted)
                    }
                }
                else -> null
            },
        )
    }

    if (editing && profile != null && isMe) {
        EditProfileDialog(
            profile = profile,
            onDismiss = { editing = false },
            onSaved = {
                editing = false
                onMessage("Profile updated")
            },
        )
    }

    if (findingPeople) {
        FindPeopleDialog(onDismiss = { findingPeople = false }, onOpenUser = nav.openUser)
    }

    if (changingPassword) {
        ChangePasswordDialog(
            onDismiss = { changingPassword = false },
            onChanged = {
                changingPassword = false
                onMessage("Password changed")
            },
        )
    }

    if (photoOptions) {
        AlertDialog(
            onDismissRequest = { photoOptions = false },
            title = { Text("Profile picture") },
            text = { Text("Choose a photo from your phone, or remove your current picture.") },
            confirmButton = {
                TextButton(onClick = {
                    photoOptions = false
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Choose photo") }
            },
            dismissButton = {
                Row {
                    if (profile?.photoPath != null) {
                        TextButton(onClick = {
                            photoOptions = false
                            act("Couldn't remove your profile picture.", "Profile picture removed") {
                                AccountRepository.removeProfilePhoto()
                            }
                        }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                    }
                    TextButton(onClick = { photoOptions = false }) { Text("Cancel") }
                }
            },
        )
    }

    confirming?.let { confirm ->
        val (title, text, button) = when (confirm) {
            Confirm.LogOut -> Triple(
                "Log out?",
                "You'll be a guest until you log in again with your username and password.",
                "Log out",
            )
            Confirm.Block -> Triple(
                "Block $name?",
                "They won't be able to follow you, add you as a friend, message you, or comment on your " +
                    "posts, and you won't see their posts or comments. This also unfollows and unfriends them. " +
                    "They aren't told, and you can unblock them later.",
                "Block",
            )
            Confirm.Unfriend -> Triple(
                "Unfriend $name?",
                "You won't be able to message each other until you're friends again. Your chat history stays.",
                "Unfriend",
            )
        }
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = null
                        when (confirm) {
                            Confirm.LogOut -> act("Couldn't log out.") { AccountRepository.signOut() }
                            Confirm.Block -> act("Couldn't block $name.", "Blocked $name") { SocialRepository.block(uid) }
                            Confirm.Unfriend -> act("Couldn't unfriend $name.", "Unfriended $name") { SocialRepository.unfriend(uid) }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(button) }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("Cancel") } },
        )
    }
}

private enum class PeopleList(val title: String) {
    Followers("Followers"),
    Following("Following"),
    Friends("Friends"),
    Blocked("Blocked accounts"),
}

private enum class Confirm { LogOut, Block, Unfriend }

@Composable
private fun ProfileHeader(
    uid: String,
    profile: UserProfile?,
    isMe: Boolean,
    followsMe: Boolean,
    onAvatarClick: (() -> Unit)?,
    onAddBio: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                UserAvatar(
                    uid = uid,
                    size = 84.dp,
                    profile = profile,
                    modifier = if (onAvatarClick != null) {
                        Modifier.clickable(onClickLabel = "Change profile picture", onClick = onAvatarClick)
                    } else Modifier,
                )
                if (onAvatarClick != null) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Turquoise)
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(PencilIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    profile?.username ?: fallbackName(uid),
                    color = FeedText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val joined = profile?.createdAt?.let { SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(it) }
                Text(
                    when {
                        profile == null -> "Posted before accounts were added"
                        joined != null -> "Joined $joined"
                        else -> "New explorer"
                    },
                    color = FeedMuted,
                    fontSize = 13.sp,
                )
                if (followsMe) {
                    Text(
                        "Follows you",
                        color = Turquoise,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .background(LightGreen, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        val bio = profile?.bio.orEmpty()
        when {
            bio.isNotBlank() -> Text(bio, color = FeedText, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 12.dp))
            isMe -> Text(
                "Add a bio so people know what you love to find.",
                color = FeedMuted,
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 12.dp).clickable(onClick = onAddBio),
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: Int?, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = onClick != null, onClickLabel = "Show $label") { onClick?.invoke() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value?.toString() ?: "–", color = FeedText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = FeedMuted, fontSize = 13.sp)
    }
}

@Composable
private fun MyActions(
    blockedCount: Int,
    onEdit: () -> Unit,
    onFindPeople: () -> Unit,
    onChangePassword: () -> Unit,
    onBlocked: () -> Unit,
    onLogOut: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit profile", color = Turquoise) }
        Button(
            onClick = onFindPeople,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = Turquoise),
        ) { Text("Find people") }
        var menuOpen by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(MoreIcon, contentDescription = "More account options", tint = FeedMuted)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Change password") }, onClick = { menuOpen = false; onChangePassword() })
                DropdownMenuItem(
                    text = { Text(if (blockedCount > 0) "Blocked accounts ($blockedCount)" else "Blocked accounts") },
                    onClick = { menuOpen = false; onBlocked() },
                )
                DropdownMenuItem(
                    text = { Text("Log out", color = MaterialTheme.colorScheme.error) },
                    onClick = { menuOpen = false; onLogOut() },
                )
            }
        }
    }
}

@Composable
private fun OtherActions(
    name: String,
    iFollow: Boolean,
    followsMe: Boolean,
    isFriend: Boolean,
    requestSent: Boolean,
    requestReceived: Boolean,
    enabled: Boolean,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
    onAddFriend: () -> Unit,
    onCancelRequest: () -> Unit,
    onUnfriend: () -> Unit,
    onMessage: () -> Unit,
    onRemoveFollower: () -> Unit,
    onBlock: () -> Unit,
) {
    val compact = PaddingValues(horizontal = 8.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (iFollow) {
            OutlinedButton(onClick = onUnfollow, enabled = enabled, contentPadding = compact, modifier = Modifier.weight(1f)) {
                Text("Following", color = Turquoise, maxLines = 1)
            }
        } else {
            Button(
                onClick = onFollow,
                enabled = enabled,
                contentPadding = compact,
                colors = ButtonDefaults.buttonColors(containerColor = Turquoise),
                modifier = Modifier.weight(1f),
            ) { Text(if (followsMe) "Follow back" else "Follow", maxLines = 1) }
        }

        when {
            isFriend -> OutlinedButton(onClick = onUnfriend, enabled = enabled, contentPadding = compact, modifier = Modifier.weight(1f)) {
                Text("Friends ✓", color = Turquoise, maxLines = 1)
            }
            // Answered in the box above the buttons.
            requestReceived -> Unit
            requestSent -> OutlinedButton(onClick = onCancelRequest, enabled = enabled, contentPadding = compact, modifier = Modifier.weight(1f)) {
                Text("Requested", color = FeedMuted, maxLines = 1)
            }
            else -> OutlinedButton(onClick = onAddFriend, enabled = enabled, contentPadding = compact, modifier = Modifier.weight(1f)) {
                Text("Add friend", color = Turquoise, maxLines = 1)
            }
        }

        if (isFriend) {
            Button(
                onClick = onMessage,
                contentPadding = compact,
                colors = ButtonDefaults.buttonColors(containerColor = Turquoise),
                modifier = Modifier.weight(1f),
            ) { Text("Message", maxLines = 1) }
        }

        var menuOpen by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(MoreIcon, contentDescription = "More options for $name", tint = FeedMuted)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (requestSent) {
                    DropdownMenuItem(text = { Text("Cancel friend request") }, onClick = { menuOpen = false; onCancelRequest() })
                }
                if (isFriend) {
                    DropdownMenuItem(text = { Text("Unfriend") }, onClick = { menuOpen = false; onUnfriend() })
                }
                if (followsMe) {
                    DropdownMenuItem(text = { Text("Remove follower") }, onClick = { menuOpen = false; onRemoveFollower() })
                }
                DropdownMenuItem(
                    text = { Text("Block $name", color = MaterialTheme.colorScheme.error) },
                    onClick = { menuOpen = false; onBlock() },
                )
            }
        }
    }
}

// A short message in a soft box, with an optional button under it.
@Composable
private fun Notice(text: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(LightGreen, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, color = FeedText, fontSize = 14.sp, lineHeight = 19.sp)
        action?.invoke()
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = FeedText,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

// One post in the grid: its photo, or its title for a text post.
@Composable
private fun PostTile(post: FeedPost, onClick: () -> Unit) {
    Box(Modifier.aspectRatio(1f).clickable(onClickLabel = "Open post", onClick = onClick)) {
        val photo = post.photo
        if (photo != null) {
            PhotoImage(photo, Modifier.fillMaxSize(), maxSize = 384, contentDescription = post.title)
        } else {
            Box(Modifier.fillMaxSize().background(LightGreen).padding(8.dp), contentAlignment = Alignment.Center) {
                Text(
                    post.title,
                    color = FeedText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EditProfileDialog(profile: UserProfile, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf(profile.username) }
    var bio by rememberSaveable { mutableStateOf(profile.bio) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val check = rememberNameCheck(username, enabled = true, unchangedFrom = profile.username)
    val changed = username.trim() != profile.username || bio.trim() != profile.bio

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Edit profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                UsernameField(
                    value = username,
                    onValueChange = {
                        username = it
                        error = null
                    },
                    check = check,
                    enabled = !saving,
                    imeAction = ImeAction.Next,
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = {
                        bio = it.take(BIO_MAX)
                        error = null
                    },
                    label = { Text("Bio") },
                    placeholder = { Text("What do you like to find?") },
                    enabled = !saving,
                    maxLines = 4,
                    supportingText = { Text("${bio.length}/$BIO_MAX") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = feedFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && changed && check != NameCheck.Taken && check !is NameCheck.Problem,
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            AccountRepository.updateProfile(username, bio)
                            onSaved()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Couldn't update profile", e)
                            error = AccountRepository.errorMessage(e, "Couldn't save your profile.")
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } },
    )
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        error = when {
            current.isEmpty() -> "Enter your current password."
            new != confirm -> "New passwords don't match."
            else -> AccountRepository.passwordProblem(new)
        }
        if (error != null || saving) return
        saving = true
        scope.launch {
            try {
                AccountRepository.changePassword(current, new)
                onChanged()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't change password", e)
                error = AccountRepository.errorMessage(e, "Couldn't change your password.").let {
                    if (it == "Incorrect password.") "Your current password is incorrect." else it
                }
            } finally {
                saving = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Change password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PasswordField(current, { current = it; error = null }, "Current password", enabled = !saving, imeAction = ImeAction.Next)
                PasswordField(new, { new = it; error = null }, "New password", enabled = !saving, imeAction = ImeAction.Next, supporting = "At least 6 characters.")
                PasswordField(confirm, { confirm = it; error = null }, "Confirm new password", enabled = !saving, imeAction = ImeAction.Done, onDone = ::save)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp) }
            }
        },
        confirmButton = { TextButton(onClick = ::save, enabled = !saving) { Text(if (saving) "Saving…" else "Change") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } },
    )
}

// Search for people by username.
@Composable
private fun FindPeopleDialog(onDismiss: () -> Unit, onOpenUser: (String) -> Unit) {
    val session = LocalSession.current
    val social = LocalSocial.current
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<UserProfile>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val text = query.trim()
        failed = false
        if (text.isEmpty()) {
            results = null
            return@LaunchedEffect
        }
        delay(300)
        searching = true
        results = try {
            AccountRepository.searchUsers(text).filter { it.uid != session.uid && it.uid !in social.hidden }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "User search failed", e)
            failed = true
            emptyList()
        }
        searching = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
            Column(Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 560.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Find people", color = FeedText, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(CloseIcon, contentDescription = "Close", tint = FeedMuted) }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.replace(" ", "") },
                    placeholder = { Text("Search by username") },
                    leadingIcon = { Text("🔍") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                    colors = feedFieldColors(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                val found = results
                when {
                    searching && found == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Turquoise, modifier = Modifier.size(26.dp))
                    }
                    found == null -> HintText("Type the start of a username.")
                    failed -> HintText("Couldn't search right now. Check your connection.")
                    found.isEmpty() -> HintText("No one found with a username starting with “${query.trim()}”.")
                    else -> LazyColumn(Modifier.padding(bottom = 8.dp)) {
                        items(found, key = { it.uid }) { person ->
                            UserRow(
                                uid = person.uid,
                                onClick = {
                                    onDismiss()
                                    onOpenUser(person.uid)
                                },
                                supporting = when {
                                    person.uid in social.friends -> "Friend"
                                    person.uid in social.following -> "Following"
                                    person.uid in social.followers -> "Follows you"
                                    else -> null
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text,
        color = FeedMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
    )
}

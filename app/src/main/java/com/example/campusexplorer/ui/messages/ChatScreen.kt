package com.example.campusexplorer.ui.messages

import android.content.ClipData
import android.text.format.DateFormat
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.campusexplorer.data.AccountRepository
import com.example.campusexplorer.data.ChatMessage
import com.example.campusexplorer.data.ChatRepository
import com.example.campusexplorer.data.MESSAGE_MAX
import com.example.campusexplorer.data.SocialRepository
import com.example.campusexplorer.ui.Feature
import com.example.campusexplorer.ui.LightGreen
import com.example.campusexplorer.ui.LocalNavigator
import com.example.campusexplorer.ui.LocalSession
import com.example.campusexplorer.ui.LocalSocial
import com.example.campusexplorer.ui.Turquoise
import com.example.campusexplorer.ui.account.GuestNotice
import com.example.campusexplorer.ui.account.UserAvatar
import com.example.campusexplorer.ui.account.fallbackName
import com.example.campusexplorer.ui.account.rememberUserProfile
import com.example.campusexplorer.ui.feed.BackArrowIcon
import com.example.campusexplorer.ui.feed.FeedMuted
import com.example.campusexplorer.ui.feed.FeedText
import com.example.campusexplorer.ui.feed.SendIcon
import com.example.campusexplorer.ui.feed.feedFieldColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

// Label for this file's messages in Logcat.
private const val TAG = "ChatScreen"

// Messages closer together than this, from the same person, are grouped (one time label for the group).
private const val GROUP_GAP_MS = 5 * 60 * 1000L

// The other person's bubbles.
private val TheirBubble = Color(0xFFEDF1EE)

// Your direct messages with a friend: their name and picture at the top, the messages in bubbles
// (yours on the right), and a box to write in at the bottom. Long-press a message to copy it, or to
// unsend one of yours. Shows "Seen" once they've read your latest message.
@Composable
fun ChatScreen(otherUid: String, onMessage: (String) -> Unit, modifier: Modifier = Modifier) {
    val session = LocalSession.current
    val social = LocalSocial.current
    val nav = LocalNavigator.current
    val me = session.uid

    if (!session.hasAccount || me == null) {
        GuestNotice(
            title = "Messages",
            message = "Log in or sign up to message your friends.",
            onLogIn = { nav.open(Feature.Account) },
            modifier = modifier,
        )
        return
    }

    val profile = rememberUserProfile(otherUid)
    val name = profile?.username ?: fallbackName(otherUid)
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }

    // Messages load only while the screen is showing.
    LaunchedEffect(me, otherUid, retry, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            failed = false
            try {
                ChatRepository.observeMessages(me, otherUid).collect {
                    messages = it
                    loading = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't load messages", e)
                failed = true
                loading = false
            }
        }
    }

    val chat by remember(me, otherUid) {
        ChatRepository.observeChat(me, otherUid).catch { emit(null) }
    }.collectAsStateWithLifecycle(null)

    // Mark the chat read when it opens and whenever a message arrives while it's on screen.
    val lifecycleState by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val onScreen = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(chat?.unread, chat?.lastMessageAt, onScreen) {
        if (onScreen && chat?.unread == true) {
            try {
                ChatRepository.markRead(otherUid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't mark chat read", e)
            }
        }
    }

    val isFriend = otherUid in social.friends
    val blocked = otherUid in social.hidden
    var draft by rememberSaveable(otherUid) { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Jump to the newest message when you send one, or when one arrives while you're near the bottom.
    val newest = messages.lastOrNull()
    LaunchedEffect(newest?.id) {
        if (newest != null && (newest.from == me || listState.firstVisibleItemIndex <= 2)) {
            listState.animateScrollToItem(0)
        }
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty()) return
        draft = ""
        scope.launch {
            try {
                ChatRepository.send(otherUid, text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't send message", e)

                // Give the text back so it can be sent again.
                if (draft.isEmpty()) draft = text
                onMessage(AccountRepository.errorMessage(e, "Couldn't send your message."))
            }
        }
    }

    var unsending by remember { mutableStateOf<ChatMessage?>(null) }

    Column(modifier.fillMaxSize().background(Color.White)) {

        // Who you're talking to. Tap their name for their profile.
        Row(
            Modifier.fillMaxWidth().padding(start = 2.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = nav.back) {
                Icon(BackArrowIcon, contentDescription = "Back", tint = Turquoise)
            }
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = "View profile") { nav.openUser(otherUid) }
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(otherUid, 40.dp, profile = profile)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(name, color = FeedText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            blocked -> "Unavailable"
                            isFriend -> "Friends · tap for profile"
                            else -> "Not friends"
                        },
                        color = FeedMuted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        HorizontalDivider(color = LightGreen, thickness = 2.dp)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> CircularProgressIndicator(color = Turquoise, modifier = Modifier.align(Alignment.Center))
                failed -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn't load messages.", color = FeedText)
                    TextButton(onClick = { retry++ }) { Text("Try again", color = Turquoise) }
                }
                messages.isEmpty() -> Column(
                    Modifier.align(Alignment.Center).padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    UserAvatar(otherUid, 84.dp, profile = profile)
                    Spacer(Modifier.height(12.dp))
                    Text(name, color = FeedText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (isFriend) "Say hi to $name! Only the two of you can see these messages."
                        else "No messages yet.",
                        color = FeedMuted,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> MessageList(
                    messages = messages,
                    me = me,
                    otherUid = otherUid,
                    theirLastRead = chat?.theirLastRead,
                    listState = listState,
                    onUnsend = { unsending = it },
                )
            }
        }

        if (isFriend && !blocked) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(MESSAGE_MAX) },
                    placeholder = { Text("Message $name…") },
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    supportingText = if (draft.length > MESSAGE_MAX - 100) {
                        { Text("${draft.length}/$MESSAGE_MAX") }
                    } else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = feedFieldColors(),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                FilledIconButton(
                    onClick = ::send,
                    enabled = draft.isNotBlank(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Turquoise,
                        contentColor = Color.White,
                        disabledContainerColor = LightGreen,
                        disabledContentColor = FeedMuted,
                    ),
                    modifier = Modifier.padding(bottom = 4.dp).size(48.dp),
                ) {
                    Icon(SendIcon, contentDescription = "Send")
                }
            }
        } else {
            NotFriendsBar(otherUid, name, blocked = blocked, hasProfile = profile != null, onMessage = onMessage)
        }
    }

    unsending?.let { message ->
        AlertDialog(
            onDismissRequest = { unsending = null },
            title = { Text("Unsend message?") },
            text = { Text("It will be removed for both of you.") },
            confirmButton = {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        unsending = null
                        scope.launch {
                            try {
                                ChatRepository.unsend(otherUid, message)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(TAG, "Couldn't unsend", e)
                                onMessage(AccountRepository.errorMessage(e, "Couldn't unsend the message."))
                            }
                        }
                    },
                ) { Text("Unsend") }
            },
            dismissButton = { TextButton(onClick = { unsending = null }) { Text("Cancel") } },
        )
    }
}

// Shown instead of the message box when you aren't (or are no longer) friends.
@Composable
private fun NotFriendsBar(otherUid: String, name: String, blocked: Boolean, hasProfile: Boolean, onMessage: (String) -> Unit) {
    val social = LocalSocial.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().background(LightGreen).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (blocked) "You can't message $name." else "You can only message friends. Add $name as a friend to keep chatting.",
            color = FeedText,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        if (!blocked && hasProfile) {
            val received = social.hasRequestFrom(otherUid)
            val sent = social.hasRequestTo(otherUid)
            Button(
                enabled = !sent && !working,
                colors = ButtonDefaults.buttonColors(containerColor = Turquoise, contentColor = Color.White),
                onClick = {
                    working = true
                    scope.launch {
                        try {
                            if (received) SocialRepository.acceptFriendRequest(otherUid) else SocialRepository.sendFriendRequest(otherUid)
                            onMessage(if (received) "You and $name are now friends" else "Friend request sent to $name")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Friend request failed", e)
                            onMessage(AccountRepository.errorMessage(e, "Couldn't send a friend request."))
                        } finally {
                            working = false
                        }
                    }
                },
            ) {
                Text(
                    when {
                        received -> "Accept friend request"
                        sent -> "Friend request sent"
                        else -> "Add friend"
                    }
                )
            }
        }
    }
}

// Something in the message list: a message, or the date above a day's messages.
private sealed interface ChatItem {
    val key: String

    data class Day(val label: String, override val key: String) : ChatItem

    data class Bubble(
        val message: ChatMessage,

        // Where it sits in a run of messages from the same person, for the bubble's corners and time label.
        val firstInGroup: Boolean,
        val lastInGroup: Boolean,
    ) : ChatItem {
        override val key: String get() = message.id
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    me: String,
    otherUid: String,
    theirLastRead: Date?,
    listState: LazyListState,
    onUnsend: (ChatMessage) -> Unit,
) {
    val context = LocalContext.current
    val items = remember(messages) { chatItems(context, messages) }

    // "Sending…", "Sent", or "Seen" under your latest message, if the chat ends with yours.
    val latest = messages.last()
    val status = if (latest.from != me) null else when {
        latest.sending -> "Sending…"
        theirLastRead != null && latest.createdAt != null && !theirLastRead.before(latest.createdAt) -> "Seen"
        else -> "Sent"
    }

    // Newest at the bottom, and the list starts there.
    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        items(items, key = { it.key }) { item ->
            when (item) {
                is ChatItem.Day -> Text(
                    item.label,
                    color = FeedMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )
                is ChatItem.Bubble -> MessageBubble(
                    item = item,
                    mine = item.message.from == me,
                    otherUid = otherUid,
                    status = if (item.message.id == latest.id) status else null,
                    onUnsend = { onUnsend(item.message) },
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    item: ChatItem.Bubble,
    mine: Boolean,
    otherUid: String,
    status: String?,
    onUnsend: () -> Unit,
) {
    val message = item.message
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }

    val big = 18.dp
    val small = 5.dp
    val shape = if (mine) {
        RoundedCornerShape(
            topStart = big,
            topEnd = if (item.firstInGroup) big else small,
            bottomEnd = if (item.lastInGroup) big else small,
            bottomStart = big,
        )
    } else {
        RoundedCornerShape(
            topStart = if (item.firstInGroup) big else small,
            topEnd = big,
            bottomEnd = big,
            bottomStart = if (item.lastInGroup) big else small,
        )
    }

    Column(
        Modifier.fillMaxWidth().padding(top = if (item.firstInGroup) 8.dp else 2.dp),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {

            // Their picture next to the last bubble of each of their groups.
            if (!mine) {
                if (item.lastInGroup) UserAvatar(otherUid, 28.dp) else Spacer(Modifier.width(28.dp))
                Spacer(Modifier.width(6.dp))
            }
            Box {
                Text(
                    message.text,
                    color = if (mine) Color.White else FeedText,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .widthIn(max = 260.dp)

                        // A message still sending is a little faded.
                        .alpha(if (message.sending) 0.75f else 1f)
                        .clip(shape)
                        .background(if (mine) Turquoise else TheirBubble)
                        .combinedClickable(onLongClickLabel = "Message options", onClick = {}, onLongClick = { menuOpen = true })
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Copy") },
                        onClick = {
                            menuOpen = false
                            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Message", message.text))) }
                        },
                    )
                    if (mine && !message.sending) {
                        DropdownMenuItem(
                            text = { Text("Unsend", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuOpen = false
                                onUnsend()
                            },
                        )
                    }
                }
            }
        }
        if (item.lastInGroup || status != null) {
            val time = message.createdAt?.let { DateFormat.getTimeFormat(context).format(it) }
            Text(
                listOfNotNull(time, status).joinToString(" · "),
                color = FeedMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = if (mine) 0.dp else 40.dp, end = 4.dp, top = 2.dp),
            )
        }
    }
}

// Messages with date labels between days, grouped into runs, newest first (for the bottom-up list).
private fun chatItems(context: android.content.Context, messages: List<ChatMessage>): List<ChatItem> {
    val items = ArrayList<ChatItem>(messages.size + 8)
    messages.forEachIndexed { index, message ->
        val previous = messages.getOrNull(index - 1)
        val next = messages.getOrNull(index + 1)
        if (previous == null || !sameDay(previous.createdAt, message.createdAt)) {
            items += ChatItem.Day(dayLabel(context, message.createdAt), key = "day:${message.id}")
        }
        items += ChatItem.Bubble(
            message = message,
            firstInGroup = previous == null || !sameGroup(previous, message),
            lastInGroup = next == null || !sameGroup(message, next),
        )
    }
    return items.asReversed()
}

private fun sameGroup(a: ChatMessage, b: ChatMessage): Boolean {
    val gap = (b.createdAt?.time ?: 0L) - (a.createdAt?.time ?: 0L)
    return a.from == b.from && gap in 0..GROUP_GAP_MS && sameDay(a.createdAt, b.createdAt)
}

private fun sameDay(a: Date?, b: Date?): Boolean {
    if (a == null || b == null) return true
    val ca = Calendar.getInstance().apply { time = a }
    val cb = Calendar.getInstance().apply { time = b }
    return daysBetween(ca, cb) == 0
}

// "Today", "Yesterday", "Monday", or a date.
private fun dayLabel(context: android.content.Context, date: Date?): String {
    if (date == null) return "Today"
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { time = date }
    val days = daysBetween(then, now)
    return when {
        days == 0 -> "Today"
        days == 1 -> "Yesterday"
        days in 2..6 -> DateFormat.format("EEEE", date).toString()
        then.get(Calendar.YEAR) == now.get(Calendar.YEAR) -> DateFormat.format("EEEE, MMMM d", date).toString()
        else -> DateFormat.getMediumDateFormat(context).format(date)
    }
}

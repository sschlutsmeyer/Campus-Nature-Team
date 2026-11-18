package com.example.campusexplorer.ui.messages

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campusexplorer.data.ChatSummary
import com.example.campusexplorer.ui.CampusRed
import com.example.campusexplorer.ui.Feature
import com.example.campusexplorer.ui.LightGreen
import com.example.campusexplorer.ui.LocalChats
import com.example.campusexplorer.ui.LocalNavigator
import com.example.campusexplorer.ui.LocalSession
import com.example.campusexplorer.ui.LocalSocial
import com.example.campusexplorer.ui.Turquoise
import com.example.campusexplorer.ui.account.GuestNotice
import com.example.campusexplorer.ui.account.UserAvatar
import com.example.campusexplorer.ui.account.UserListDialog
import com.example.campusexplorer.ui.account.fallbackName
import com.example.campusexplorer.ui.account.rememberUserProfile
import com.example.campusexplorer.ui.feed.FeedMuted
import com.example.campusexplorer.ui.feed.FeedText
import com.example.campusexplorer.ui.feed.PlusIcon
import java.util.Calendar
import java.util.Date

@Composable
fun MessagesScreen(
    modifier: Modifier = Modifier
) {
    val session = LocalSession.current
    val social = LocalSocial.current
    val nav = LocalNavigator.current
    val myUid = session.uid

    if (!session.hasAccount || myUid == null) {
        GuestNotice(
            title = "Messages",
            message =
                "Log in or sign up to message your friends. " +
                        "Once someone accepts your friend request, you can chat here.",
            onLogIn = { nav.open(Feature.Account) },
            modifier = modifier
        )
        return
    }

    var picking by rememberSaveable {
        mutableStateOf(false)
    }

    val chats = LocalChats.current.filter {
        it.otherUid !in social.hidden && it.lastMessageAt != null
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 12.dp,
                    top = 14.dp,
                    bottom = 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Messages",
                    color = FeedText,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Chat with your friends",
                    color = Turquoise,
                    fontSize = 14.sp
                )
            }

            Button(
                onClick = { picking = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Turquoise,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(
                    start = 14.dp,
                    end = 18.dp
                )
            ) {
                Icon(
                    imageVector = PlusIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(Modifier.width(6.dp))

                Text(
                    text = "New",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        HorizontalDivider(
            color = LightGreen,
            thickness = 2.dp
        )

        if (chats.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "💬",
                    fontSize = 44.sp
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "No messages yet",
                    color = FeedText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = if (social.friends.isEmpty()) {
                        "You can message friends. " +
                                "Add friends from their profile " +
                                "(tap their name on a post), " +
                                "or open the Friends screen."
                    } else {
                        val count = social.friends.size
                        val word = if (count == 1) "friend" else "friends"
                        "Tap New to message one of your $count $word."
                    },
                    color = FeedMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (social.friends.isEmpty()) {
                            nav.open(Feature.Friends)
                        } else {
                            picking = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Turquoise,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        if (social.friends.isEmpty()) {
                            "Find friends"
                        } else {
                            "Message a friend"
                        }
                    )
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(
                    items = chats,
                    key = { it.id }
                ) { chat ->
                    ChatRow(
                        chat = chat,
                        myUid = myUid,
                        onClick = {
                            nav.openChat(chat.otherUid)
                        }
                    )
                }
            }
        }
    }

    if (picking) {
        UserListDialog(
            title = "New message",
            uids = social.friends.filter {
                it !in social.hidden
            },
            emptyText =
                "You can message friends. " +
                        "Add friends from their profile, " +
                        "or open the Friends screen.",
            onDismiss = { picking = false },
            onOpenUser = nav.openChat
        )
    }
}

@Composable
private fun ChatRow(
    chat: ChatSummary,
    myUid: String,
    onClick: () -> Unit
) {
    val profile = rememberUserProfile(chat.otherUid)
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = "Open chat",
                onClick = onClick
            )
            .padding(
                horizontal = 16.dp,
                vertical = 10.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            UserAvatar(
                uid = chat.otherUid,
                size = 52.dp,
                profile = profile
            )

            if (chat.unread) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(CampusRed)
                        .border(
                            width = 2.dp,
                            color = Color.White,
                            shape = CircleShape
                        )
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = profile?.username
                        ?: fallbackName(chat.otherUid),
                    color = FeedText,
                    fontSize = 16.sp,
                    fontWeight = if (chat.unread) {
                        FontWeight.Bold
                    } else {
                        FontWeight.SemiBold
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = chatListTime(
                        context,
                        chat.lastMessageAt
                    ),
                    color = if (chat.unread) {
                        Turquoise
                    } else {
                        FeedMuted
                    },
                    fontSize = 12.sp
                )
            }

            Text(
                text = when {
                    chat.lastMessage.isEmpty() -> "Message unsent"
                    chat.lastSender == myUid -> "You: ${chat.lastMessage}"
                    else -> chat.lastMessage
                },
                color = if (chat.unread) {
                    FeedText
                } else {
                    FeedMuted
                },
                fontWeight = if (chat.unread) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                },
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal fun chatListTime(
    context: android.content.Context,
    date: Date?
): String {
    if (date == null) return ""

    val now = Calendar.getInstance()

    val then = Calendar.getInstance().apply {
        time = date
    }

    val days = daysBetween(then, now)

    return when {
        days == 0 ->
            DateFormat.getTimeFormat(context).format(date)

        days == 1 -> "Yesterday"

        days in 2..6 ->
            DateFormat.format("EEE", date).toString()

        then.get(Calendar.YEAR) == now.get(Calendar.YEAR) ->
            DateFormat.format("MMM d", date).toString()

        else ->
            DateFormat.format("MMM d, yyyy", date).toString()
    }
}

internal fun daysBetween(
    a: Calendar,
    b: Calendar
): Int {
    fun startOfDay(c: Calendar): Long =
        (c.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    return Math.round(
        (startOfDay(b) - startOfDay(a)) /
                (24.0 * 60 * 60 * 1000)
    ).toInt()
}
package com.example.campusexplorer.ui.feed

import android.text.format.DateUtils
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.example.campusexplorer.ui.Turquoise
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.Date

// Shared by the feed, its pop-ups, and the map.

internal val FeedText = Color(0xFF294C39)
internal val FeedMuted = Color(0xFF65756C)

// How a post's or comment's author is shown. Accounts are anonymous for now, so other users get a
// short name made from their id. It stays the same for a user every time.
internal fun authorLabel(uid: String?, myUid: String?): String = when {
    uid == null -> "Unknown explorer"
    uid == myUid -> "You"
    else -> "Explorer ${uid.take(4).uppercase()}"
}

// "Just now", "5 minutes ago", "Yesterday", and so on.
internal fun relativeTime(date: Date?): String {
    if (date == null) return "Just now"
    val now = System.currentTimeMillis()
    if (now - date.time < DateUtils.MINUTE_IN_MILLIS) return "Just now"
    return DateUtils.getRelativeTimeSpanString(date.time, now, DateUtils.MINUTE_IN_MILLIS).toString()
}

// A short message for the user about a failed save or load.
// Our own checks (like "Give your post a title.") already have a readable message.
internal fun userMessage(e: Throwable, fallback: String): String = when {
    e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
        "$fallback Permission denied. (Have the latest Firestore rules been published?)"
    e is IllegalArgumentException || e is IllegalStateException -> e.message ?: fallback
    else -> "$fallback Check your connection and try again."
}

// Text box colors for the feed's white pages, so text stays readable when the phone is in dark mode.
@Composable
internal fun feedFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = FeedText,
    unfocusedTextColor = FeedText,
    disabledTextColor = FeedMuted,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    disabledContainerColor = Color.White,
    cursorColor = Turquoise,
    focusedBorderColor = Turquoise,
    unfocusedBorderColor = FeedMuted.copy(alpha = 0.5f),
    disabledBorderColor = FeedMuted.copy(alpha = 0.3f),
    focusedLabelColor = Turquoise,
    unfocusedLabelColor = FeedMuted,
    disabledLabelColor = FeedMuted,
    focusedPlaceholderColor = FeedMuted,
    unfocusedPlaceholderColor = FeedMuted,
    disabledPlaceholderColor = FeedMuted,
    focusedSupportingTextColor = FeedMuted,
    unfocusedSupportingTextColor = FeedMuted,
    disabledSupportingTextColor = FeedMuted,
)

// Icons, drawn here so the app doesn't need an extra icon library.
// Paths marked "Material" are from Google's Material Icons (Apache License 2.0).
private fun icon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes(pathData), fill = SolidColor(Color.Black))
        .build()

// Material "thumb_up". Rotate it 180 degrees for thumbs down.
internal val ThumbIcon = icon(
    "Thumb",
    "M1 21h4V9H1v12zm22-11c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L14.17 1 " +
        "7.59 7.59C7.22 7.95 7 8.45 7 9v10c0 1.1.9 2 2 2h9c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23" +
        ".14-.47.14-.73v-2z",
)

// Material "more_vert".
internal val MoreIcon = icon(
    "More",
    "M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z" +
        "m0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z",
)

// Material "send".
internal val SendIcon = icon("Send", "M2.01 21L23 12 2.01 3 2 10l15 2-15 2z")

// Material "place".
internal val PinIcon = icon(
    "Pin",
    "M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12" +
        "-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z",
)

// Material "add".
internal val PlusIcon = icon("Plus", "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")

// Material "close".
internal val CloseIcon = icon(
    "Close",
    "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z",
)

// Material "arrow_back".
internal val BackArrowIcon = icon("BackArrow", "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z")

// Material "edit".
internal val PencilIcon = icon(
    "Pencil",
    "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34" +
        "c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z",
)

internal val CommentIcon = ImageVector.Builder("FeedComment", 24.dp, 24.dp, 24f, 24f).apply {
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

internal val LeafIcon = ImageVector.Builder("FeedLeaf", 24.dp, 24.dp, 24f, 24f).apply {
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

internal val BookmarkOutlineIcon = icon(
    name = "BookmarkOutline",
    pathData = "M17 3H7c-1.1 0-2 .9-2 2v16l7-3 7 3V5c0-1.1-.9-2-2-2zm0 15l-5-2.18L7 18V5h10v13z"
)

internal val BookmarkFilledIcon = icon(
    name = "BookmarkFilled",
    pathData = "M17 3H7c-1.1 0-1.99.9-1.99 2L5 21l7-3 7 3V5c0-1.1-.9-2-2-2z"
)

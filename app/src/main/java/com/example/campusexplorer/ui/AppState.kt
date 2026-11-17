package com.example.campusexplorer.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.campusexplorer.data.AccountRepository
import com.example.campusexplorer.data.ChatSummary
import com.example.campusexplorer.data.MySocial
import com.example.campusexplorer.data.Session

// App-wide state that many screens need. MainActivity provides the live values once, at the top of
// the app, so screens can read them with LocalSession.current and so on.

// Who's using the app: a guest, or an account and its profile.
val LocalSession = compositionLocalOf { AccountRepository.session.value }

// Your follows, friends, friend requests, and blocks. Empty for guests.
val LocalSocial = compositionLocalOf { MySocial() }

// Your chats, newest first. Empty for guests.
val LocalChats = compositionLocalOf { emptyList<ChatSummary>() }

// Opens screens from anywhere in the app.
val LocalNavigator = staticCompositionLocalOf { AppNavigator.None }

// A place the back and forward buttons can return to.
sealed interface Destination {
    data object Home : Destination

    // A screen from a home square.
    data class Screen(val feature: Feature) : Destination

    // Someone's profile (yours opens the Profile square's screen instead).
    data class Profile(val uid: String) : Destination

    // Your direct messages with a friend.
    data class Chat(val uid: String) : Destination
}

// Destinations are kept in the history as text, so the history survives the phone rotating.
internal fun Destination.key(): String = when (this) {
    Destination.Home -> "Home"
    is Destination.Screen -> feature.name
    is Destination.Profile -> "user:$uid"
    is Destination.Chat -> "chat:$uid"
}

internal fun destinationOf(key: String): Destination = when {
    key.startsWith("user:") -> Destination.Profile(key.removePrefix("user:"))
    key.startsWith("chat:") -> Destination.Chat(key.removePrefix("chat:"))
    else -> Feature.entries.firstOrNull { it.name == key }?.let { Destination.Screen(it) } ?: Destination.Home
}

class AppNavigator(
    val open: (Feature) -> Unit,

    // Someone's profile. Your own opens the Profile screen.
    val openUser: (uid: String) -> Unit,

    // Your direct messages with a friend.
    val openChat: (uid: String) -> Unit,
    val back: () -> Unit,

    // Tells a guest they need an account to do something, and offers to log in or sign up.
    // reason finishes "Log in to …", like "like posts".
    val requireAccount: (reason: String) -> Unit,
) {
    companion object {
        val None = AppNavigator(open = {}, openUser = {}, openChat = {}, back = {}, requireAccount = {})
    }
}

// Runs action if you have an account; otherwise asks you to log in or sign up first.
inline fun AppNavigator.withAccount(session: Session, reason: String, action: () -> Unit) {
    if (session.hasAccount) action() else requireAccount(reason)
}

package com.example.campusexplorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.campusexplorer.data.AccountRepository
import com.example.campusexplorer.data.ChatRepository
import com.example.campusexplorer.data.ChatSummary
import com.example.campusexplorer.data.FeedPost
import com.example.campusexplorer.data.MySocial
import com.example.campusexplorer.data.NaturePhoto
import com.example.campusexplorer.data.SocialRepository
import com.example.campusexplorer.ui.Destination
import com.example.campusexplorer.ui.Feature
import com.example.campusexplorer.ui.LocalChats
import com.example.campusexplorer.ui.LocalNavigator
import com.example.campusexplorer.ui.LocalSession
import com.example.campusexplorer.ui.LocalSocial
import com.example.campusexplorer.ui.NatureQuestScreen
import com.example.campusexplorer.ui.account.AccountScreen
import com.example.campusexplorer.ui.account.ProfileScreen
import com.example.campusexplorer.ui.camera.CameraScreen
import com.example.campusexplorer.ui.feed.FeedScreen
import com.example.campusexplorer.ui.gallery.GalleryScreen
import com.example.campusexplorer.ui.map.MapScreen
import com.example.campusexplorer.ui.learn.LearnScreen
import com.example.campusexplorer.ui.messages.ChatScreen
import com.example.campusexplorer.ui.messages.MessagesScreen
import com.example.campusexplorer.ui.theme.CampusExplorerTheme
import com.example.campusexplorer.ui.withAccount
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch


// Switches between the Nature Quest design (true) and the plain tab bar layout (false).
private const val USE_NATURE_QUEST_UI = true

// The tabs in the bottom bar, each with a label and an icon from res/drawable.
private enum class Tab(val label: String, @param:DrawableRes val icon: Int) {
    Map("Map", R.drawable.ic_map),
    Camera("Camera", R.drawable.ic_camera),
}

// The app's only screen. Everything is drawn by CampusExplorerApp below.
class MainActivity : ComponentActivity() {

    // Runs once when the app opens.
    override fun onCreate(savedInstanceState: Bundle?) {

        // Let Android do its own setup first.
        super.onCreate(savedInstanceState)

        // Draw behind the status and navigation bars.
        enableEdgeToEdge()

        // Set the UI, wrapped in our theme.
        setContent {

            // Apply our colors and fonts.
            CampusExplorerTheme {

                // Draw the app.
                CampusExplorerApp()
            }
        }
    }
}

// The whole app: a bottom tab bar and the selected tab's screen.
@Composable
fun CampusExplorerApp() {

    // The selected tab. rememberSaveable keeps it when the phone rotates.
    var tab by rememberSaveable { mutableStateOf(Tab.Map) }

    // Controls the pop-up messages (snackbars) at the bottom of the screen.
    val snackbar = remember { SnackbarHostState() }

    // Used to show snackbars, which run in the background.
    val scope = rememberCoroutineScope()

    // Shows a pop-up message. Passed to each screen.
    val showMessage: (String) -> Unit = { scope.launch { snackbar.showSnackbar(it) } }

    // Account messages that can come from anywhere, like "You've been logged out".
    LaunchedEffect(Unit) {
        AccountRepository.notices.collect { snackbar.showSnackbar(it) }
    }

    // Shared between screens for the whole session (and kept when the phone rotates).
    // Whether the map shows other users' posted pins. Off (only your own pins) when the app opens.
    var showAllPins by rememberSaveable { mutableStateOf(false) }

    // Whether the list of pins under the map is showing, or minimized for a bigger map.
    var mapPinListOpen by rememberSaveable { mutableStateOf(true) }

    // A post for the Feed to scroll to and open, from a pin's "View post" button.
    var feedFocusPostId by rememberSaveable { mutableStateOf<String?>(null) }

    // A photo for the Feed's new-post pop-up to start with, from a pin's "Post to feed" button.
    var feedComposePhotoId by rememberSaveable { mutableStateOf<String?>(null) }

    // A pin for the Map to zoom in on, from a post's "Map" button.
    var mapFocusPhotoId by rememberSaveable { mutableStateOf<String?>(null) }

    // Who's using the app (a guest until they log in), and, with an account, their follows,
    // friends, blocks, and chats. Every screen can read these (see ui/AppState.kt).
    val session by AccountRepository.session.collectAsStateWithLifecycle()
    val accountUid = session.uid?.takeIf { session.hasAccount }
    val social by remember(accountUid) {
        if (accountUid == null) flowOf(MySocial()) else SocialRepository.observeMySocial(accountUid)
    }.collectAsStateWithLifecycle(MySocial())
    val chats by remember(accountUid) {
        if (accountUid == null) flowOf(emptyList()) else ChatRepository.observeChats(accountUid).catch { emit(emptyList()) }
    }.collectAsStateWithLifecycle(emptyList<ChatSummary>())

    CompositionLocalProvider(
        LocalSession provides session,
        LocalSocial provides social,
        LocalChats provides chats,
    ) {

        // Standard page layout with slots for a bottom bar and snackbars.
        Scaffold(

            // Fill the whole screen.
            modifier = Modifier.fillMaxSize(),

            // Where pop-up messages appear.
            snackbarHost = { SnackbarHost(snackbar) },

            // The plain tab bar. The Nature Quest design draws its own instead.
            bottomBar = {

                if (!USE_NATURE_QUEST_UI) {
                    NavigationBar {

                        // One button per tab.
                        Tab.entries.forEach {

                            NavigationBarItem(

                                // Highlight the selected tab.
                                selected = tab == it,

                                // Switch to this tab when tapped.
                                onClick = { tab = it },

                                // Tab icon and label.
                                icon = { Icon(painterResource(it.icon), contentDescription = null) },
                                label = { Text(it.label) },
                            )
                        }
                    }
                }
            },
        )

        // innerPadding is the space the bars take up, so content isn't hidden behind them.
        { innerPadding ->

            // Nature Quest design: a home screen of features plus back/forward/home navigation
            // (see ui/HomeScreen.kt and ui/MapCameraUI.kt).
            if (USE_NATURE_QUEST_UI) {

                NatureQuestScreen { destination ->
                    val nav = LocalNavigator.current

                    // Links between screens: a photo's post in the Feed, a new post with a photo,
                    // and a post's pin on the Map.
                    val viewPost: (String) -> Unit = { postId ->
                        feedFocusPostId = postId
                        nav.open(Feature.Feed)
                    }
                    val createPost: (NaturePhoto) -> Unit = { photo ->

                        // Guests can save pins but need an account to post them.
                        nav.withAccount(session, "post to the feed") {
                            feedComposePhotoId = photo.id
                            nav.open(Feature.Feed)
                        }
                    }
                    val viewOnMap: (FeedPost) -> Unit = { post ->

                        // Someone else's pin only shows when the map shows everyone's pins.
                        if (post.uid != session.uid) showAllPins = true
                        mapFocusPhotoId = post.photoId
                        nav.open(Feature.Map)
                    }

                    // The screens opened from inside other screens: profiles and chats.
                    val feature = when (destination) {
                        is Destination.Profile -> {
                            ProfileScreen(
                                uid = destination.uid,
                                onMessage = showMessage,
                                onOpenPost = { viewPost(it.id) },
                                modifier = Modifier.fillMaxSize(),
                            )
                            return@NatureQuestScreen
                        }
                        is Destination.Chat -> {
                            ChatScreen(otherUid = destination.uid, onMessage = showMessage, modifier = Modifier.fillMaxSize())
                            return@NatureQuestScreen
                        }
                        is Destination.Screen -> destination.feature
                        Destination.Home -> return@NatureQuestScreen
                    }

                    // The screen for each feature.
                    when (feature) {
                        Feature.Account -> AccountScreen(
                            onMessage = showMessage,
                            onOpenPost = { viewPost(it.id) },
                            modifier = Modifier.fillMaxSize(),
                        )
                        Feature.Messages -> MessagesScreen(modifier = Modifier.fillMaxSize())
                        Feature.Map -> MapScreen(
                            showAllPins = showAllPins,
                            onShowAllPinsChange = { showAllPins = it },
                            pinListOpen = mapPinListOpen,
                            onPinListOpenChange = { mapPinListOpen = it },
                            onMessage = showMessage,
                            modifier = Modifier.fillMaxSize(),
                            onViewPost = viewPost,
                            onCreatePost = createPost,
                            focusPhotoId = mapFocusPhotoId,
                            onFocusHandled = { mapFocusPhotoId = null },
                        )
                        Feature.Camera -> CameraScreen(
                            onMessage = showMessage,
                            modifier = Modifier.fillMaxSize(),

                            // "Save & Post" saves the photo, then opens the new-post pop-up with it.
                            // Guests only get "Save", since posting needs an account.
                            onPostToFeed = if (session.hasAccount) {
                                { photoId ->
                                    feedComposePhotoId = photoId
                                    nav.open(Feature.Feed)
                                }
                            } else null,
                        )
                        Feature.Gallery -> GalleryScreen(
                            onMessage = showMessage,
                            modifier = Modifier.fillMaxSize(),
                            onViewPost = viewPost,
                            onCreatePost = createPost,
                        )
                        Feature.Feed -> FeedScreen(
                            onMessage = showMessage,
                            modifier = Modifier.fillMaxSize(),
                            focusPostId = feedFocusPostId,
                            onFocusHandled = { feedFocusPostId = null },
                            composePhotoId = feedComposePhotoId,
                            onComposeHandled = { feedComposePhotoId = null },
                            onViewOnMap = viewOnMap,
                        )
                        Feature.Learn -> LearnScreen(
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Plain layout: show the screen for the selected tab (see MapScreen.kt and CameraScreen.kt).
            } else {

                val contentModifier = Modifier.padding(innerPadding)
                when (tab) {
                    Tab.Map -> MapScreen(
                        showAllPins = showAllPins,
                        onShowAllPinsChange = { showAllPins = it },
                        pinListOpen = mapPinListOpen,
                        onPinListOpenChange = { mapPinListOpen = it },
                        onMessage = showMessage,
                        modifier = contentModifier,
                    )
                    Tab.Camera -> CameraScreen(onMessage = showMessage, modifier = contentModifier)
                }
            }
        }
    }
}

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
import com.example.campusexplorer.ui.friends.FriendScreen
import com.example.campusexplorer.ui.gallery.GalleryScreen
import com.example.campusexplorer.ui.learn.LearnScreen
import com.example.campusexplorer.ui.map.MapScreen
import com.example.campusexplorer.ui.messages.ChatScreen
import com.example.campusexplorer.ui.messages.MessagesScreen
import com.example.campusexplorer.ui.theme.CampusExplorerTheme
import com.example.campusexplorer.ui.withAccount
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private const val USE_NATURE_QUEST_UI = true

private enum class Tab(
    val label: String,
    @param:DrawableRes val icon: Int
) {
    Map("Map", R.drawable.ic_map),
    Camera("Camera", R.drawable.ic_camera)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CampusExplorerTheme {
                CampusExplorerApp()
            }
        }
    }
}

@Composable
fun CampusExplorerApp() {
    var tab by rememberSaveable {
        mutableStateOf(Tab.Map)
    }

    val snackbar = remember {
        SnackbarHostState()
    }

    val scope = rememberCoroutineScope()

    val showMessage: (String) -> Unit = { message ->
        scope.launch {
            snackbar.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        AccountRepository.notices.collect {
            snackbar.showSnackbar(it)
        }
    }

    var showAllPins by rememberSaveable {
        mutableStateOf(false)
    }

    var mapPinListOpen by rememberSaveable {
        mutableStateOf(true)
    }

    var feedFocusPostId by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var feedComposePhotoId by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var mapFocusPhotoId by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    val session by AccountRepository.session.collectAsStateWithLifecycle()

    val accountUid = session.uid?.takeIf {
        session.hasAccount
    }

    val social by remember(accountUid) {
        if (accountUid == null) {
            flowOf(MySocial())
        } else {
            SocialRepository.observeMySocial(accountUid)
        }
    }.collectAsStateWithLifecycle(MySocial())

    val chats by remember(accountUid) {
        if (accountUid == null) {
            flowOf(emptyList<ChatSummary>())
        } else {
            ChatRepository.observeChats(accountUid).catch {
                emit(emptyList())
            }
        }
    }.collectAsStateWithLifecycle(emptyList<ChatSummary>())

    CompositionLocalProvider(
        LocalSession provides session,
        LocalSocial provides social,
        LocalChats provides chats
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = {
                SnackbarHost(snackbar)
            },
            bottomBar = {
                if (!USE_NATURE_QUEST_UI) {
                    NavigationBar {
                        Tab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = {
                                    Icon(
                                        painterResource(item.icon),
                                        contentDescription = null
                                    )
                                },
                                label = {
                                    Text(item.label)
                                }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            if (USE_NATURE_QUEST_UI) {
                NatureQuestScreen { destination ->
                    val nav = LocalNavigator.current

                    val viewPost: (String) -> Unit = { postId ->
                        feedFocusPostId = postId
                        nav.open(Feature.Feed)
                    }

                    val createPost: (NaturePhoto) -> Unit = { photo ->
                        nav.withAccount(session, "post to the feed") {
                            feedComposePhotoId = photo.id
                            nav.open(Feature.Feed)
                        }
                    }

                    val viewOnMap: (FeedPost) -> Unit = { post ->
                        if (post.uid != session.uid) {
                            showAllPins = true
                        }

                        mapFocusPhotoId = post.photoId
                        nav.open(Feature.Map)
                    }

                    val feature = when (destination) {
                        is Destination.Profile -> {
                            ProfileScreen(
                                uid = destination.uid,
                                onMessage = showMessage,
                                onOpenPost = { viewPost(it.id) },
                                modifier = Modifier.fillMaxSize()
                            )

                            return@NatureQuestScreen
                        }

                        is Destination.Chat -> {
                            ChatScreen(
                                otherUid = destination.uid,
                                onMessage = showMessage,
                                modifier = Modifier.fillMaxSize()
                            )

                            return@NatureQuestScreen
                        }

                        is Destination.Screen -> destination.feature
                        Destination.Home -> return@NatureQuestScreen
                    }

                    when (feature) {
                        Feature.Account -> AccountScreen(
                            onMessage = showMessage,
                            onOpenPost = { viewPost(it.id) },
                            modifier = Modifier.fillMaxSize()
                        )

                        Feature.Friends -> FriendScreen(
                            onMessage = showMessage,
                            modifier = Modifier.fillMaxSize()
                        )

                        Feature.Messages -> MessagesScreen(
                            modifier = Modifier.fillMaxSize()
                        )

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
                            onFocusHandled = { mapFocusPhotoId = null }
                        )

                        Feature.Camera -> CameraScreen(
                            onMessage = showMessage,
                            modifier = Modifier.fillMaxSize(),
                            onPostToFeed = if (session.hasAccount) {
                                { photoId ->
                                    feedComposePhotoId = photoId
                                    nav.open(Feature.Feed)
                                }
                            } else {
                                null
                            }
                        )

                        Feature.Gallery -> GalleryScreen(
                            onMessage = showMessage,
                            modifier = Modifier.fillMaxSize(),
                            onViewPost = viewPost,
                            onCreatePost = createPost
                        )

                        Feature.Feed -> FeedScreen(
                            onMessage = showMessage,
                            modifier = Modifier.fillMaxSize(),
                            focusPostId = feedFocusPostId,
                            onFocusHandled = { feedFocusPostId = null },
                            composePhotoId = feedComposePhotoId,
                            onComposeHandled = { feedComposePhotoId = null },
                            onViewOnMap = viewOnMap
                        )

                        Feature.Learn -> LearnScreen(
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                val contentModifier = Modifier.padding(innerPadding)

                when (tab) {
                    Tab.Map -> MapScreen(
                        showAllPins = showAllPins,
                        onShowAllPinsChange = { showAllPins = it },
                        pinListOpen = mapPinListOpen,
                        onPinListOpenChange = { mapPinListOpen = it },
                        onMessage = showMessage,
                        modifier = contentModifier
                    )

                    Tab.Camera -> CameraScreen(
                        onMessage = showMessage,
                        modifier = contentModifier
                    )
                }
            }
        }
    }
}
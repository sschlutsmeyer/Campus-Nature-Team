package com.example.campusexplorer.ui.friends

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.campusexplorer.data.*
import com.example.campusexplorer.ui.*
import com.example.campusexplorer.ui.account.GuestNotice
import com.example.campusexplorer.ui.account.UserRow
import com.example.campusexplorer.ui.account.rememberUserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
fun FriendScreen(
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val session = LocalSession.current
    val social = LocalSocial.current
    val nav = LocalNavigator.current
    val me = session.uid

    if (!session.hasAccount || me == null) {
        GuestNotice(
            title = "Friends",
            message = "Log in to find people, add friends, and send messages.",
            onLogIn = { nav.open(Feature.Account) },
            modifier = modifier
        )
        return
    }

    val scope = rememberCoroutineScope()

    var busy by remember(me) {
        mutableStateOf(false)
    }

    fun action(
        success: String,
        block: suspend () -> Unit
    ) {
        if (busy) return
        busy = true

        scope.launch {
            try {
                block()
                onMessage(success)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onMessage(
                    AccountRepository.errorMessage(
                        e,
                        "Couldn't complete that action. Try again."
                    )
                )
            } finally {
                busy = false
            }
        }
    }

    var tab by rememberSaveable(me) {
        mutableStateOf("Friends")
    }

    var query by rememberSaveable(me) {
        mutableStateOf("")
    }

    var results by remember(me) {
        mutableStateOf<List<UserProfile>>(emptyList())
    }

    var searching by remember {
        mutableStateOf(false)
    }

    var searchError by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(query, me, tab) {
        results = emptyList()
        searchError = null
        searching = false

        if (tab == "Search" && query.isNotBlank()) {
            searching = true

            try {
                delay(300)
                results = AccountRepository.searchUsers(query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                searchError =
                    "Search couldn't load. Edit your search to try again."
            } finally {
                searching = false
            }
        }
    }

    val notes by remember(me) {
        FriendDetailsRepository.notes(me).catch {
            onMessage("Couldn't load friend notes.")
            emit(emptyMap())
        }
    }.collectAsStateWithLifecycle(emptyMap())

    val sharedArea by remember(me) {
        FriendDetailsRepository.area(me).catch {
            onMessage("Couldn't load area settings.")
            emit("")
        }
    }.collectAsStateWithLifecycle("")

    var areaInput by rememberSaveable(me) {
        mutableStateOf("")
    }

    LaunchedEffect(sharedArea) {
        areaInput = sharedArea
    }

    val nearby by remember(sharedArea, me) {
        if (sharedArea.isBlank()) {
            flowOf(emptyList<String>())
        } else {
            FriendDetailsRepository.peopleIn(sharedArea).catch {
                onMessage("Couldn't load people in this area.")
                emit(emptyList())
            }
        }
    }.collectAsStateWithLifecycle(emptyList())

    var noteUid by rememberSaveable(me) {
        mutableStateOf<String?>(null)
    }

    var draft by rememberSaveable(me) {
        mutableStateOf("")
    }

    var blockUid by rememberSaveable(me) {
        mutableStateOf<String?>(null)
    }

    val ids = when (tab) {
        "Requests" ->
            social.incomingRequests.map { it.from } +
                    social.outgoingRequests.map { it.to }

        "Search" -> results.map { it.uid }
        "Nearby" -> nearby
        "Blocked" -> social.blockedByMe.toList()
        else -> social.friends.toList()
    }.distinct().filter {
        it != me && (tab == "Blocked" || it !in social.hidden)
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        Text(
            text = "Friends",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "Friends",
                "Requests",
                "Search",
                "Nearby",
                "Blocked"
            ).forEach { name ->
                FilterChip(
                    selected = tab == name,
                    onClick = { tab = name },
                    label = {
                        Text(
                            if (name == "Requests") {
                                val count = social.incomingRequests.count {
                                    it.from !in social.hidden
                                }
                                "Requests ($count)"
                            } else {
                                name
                            }
                        )
                    }
                )
            }
        }

        if (tab == "Friends" || tab == "Search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(20) },
                singleLine = true,
                label = {
                    Text(
                        if (tab == "Search") {
                            "Search username (starts with)"
                        } else {
                            "Filter friends by username"
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (tab == "Nearby") {
            Text(
                "Find people who opt in with the same city or campus name. " +
                        "Your area is visible to signed-in users. " +
                        "No GPS is shared. Up to 100 people are shown."
            )

            OutlinedTextField(
                value = areaInput,
                onValueChange = { areaInput = it.take(80) },
                label = {
                    Text("City or campus, e.g. CSU Channel Islands")
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row {
                TextButton(
                    enabled = !busy && areaInput.trim().length >= 2,
                    onClick = {
                        action("Area saved") {
                            FriendDetailsRepository.shareArea(areaInput)
                        }
                    }
                ) {
                    Text("Share area & find people")
                }

                if (sharedArea.isNotBlank()) {
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            action("Area sharing stopped") {
                                FriendDetailsRepository.stopSharing()
                            }
                        }
                    ) {
                        Text("Stop sharing")
                    }
                }
            }
        }

        if (searching) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        searchError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (ids.isEmpty() && !searching && searchError == null) {
                item {
                    Text(
                        when (tab) {
                            "Search" -> {
                                if (query.isBlank()) {
                                    "Enter a username to find people."
                                } else {
                                    "No matching people."
                                }
                            }

                            "Requests" -> "No pending friend requests."

                            "Nearby" -> {
                                if (sharedArea.isBlank()) {
                                    "Share an area to join discovery."
                                } else {
                                    "No other people sharing this area yet."
                                }
                            }

                            "Blocked" -> "No blocked people."

                            else ->
                                "No friends yet. Search for someone and send a request."
                        }
                    )
                }
            }

            items(ids, key = { it }) { uid ->
                val profile = rememberUserProfile(uid)

                val matches =
                    tab != "Friends" ||
                            query.isBlank() ||
                            profile?.username?.contains(
                                query,
                                ignoreCase = true
                            ) == true

                if (matches) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(8.dp)) {
                            UserRow(
                                uid = uid,
                                onClick = { nav.openUser(uid) }
                            )

                            if (tab != "Blocked") {
                                notes[uid]?.let {
                                    Text(
                                        text = "Private note: $it",
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }

                            Row(
                                Modifier.horizontalScroll(
                                    rememberScrollState()
                                )
                            ) {
                                if (tab == "Blocked") {
                                    TextButton(
                                        enabled = !busy,
                                        onClick = {
                                            action("Unblocked") {
                                                SocialRepository.unblock(uid)
                                            }
                                        }
                                    ) {
                                        Text("Unblock")
                                    }
                                } else {
                                    when {
                                        uid in social.friends -> {
                                            TextButton(
                                                onClick = {
                                                    nav.openChat(uid)
                                                }
                                            ) {
                                                Text("Message")
                                            }

                                            TextButton(
                                                onClick = {
                                                    noteUid = uid
                                                    draft = notes[uid].orEmpty()
                                                }
                                            ) {
                                                Text("Friend note")
                                            }

                                            TextButton(
                                                enabled = !busy,
                                                onClick = {
                                                    action("Friend removed") {
                                                        SocialRepository.unfriend(uid)
                                                    }
                                                }
                                            ) {
                                                Text("Remove friend")
                                            }
                                        }

                                        social.hasRequestFrom(uid) -> {
                                            TextButton(
                                                enabled = !busy,
                                                onClick = {
                                                    action("Friend added") {
                                                        SocialRepository
                                                            .acceptFriendRequest(uid)
                                                    }
                                                }
                                            ) {
                                                Text("Accept")
                                            }

                                            TextButton(
                                                enabled = !busy,
                                                onClick = {
                                                    action("Request declined") {
                                                        SocialRepository
                                                            .declineFriendRequest(uid)
                                                    }
                                                }
                                            ) {
                                                Text("Decline")
                                            }
                                        }

                                        social.hasRequestTo(uid) -> {
                                            TextButton(
                                                enabled = !busy,
                                                onClick = {
                                                    action("Request canceled") {
                                                        SocialRepository
                                                            .cancelFriendRequest(uid)
                                                    }
                                                }
                                            ) {
                                                Text("Cancel request")
                                            }
                                        }

                                        else -> {
                                            TextButton(
                                                enabled = !busy,
                                                onClick = {
                                                    action("Friend request sent") {
                                                        SocialRepository
                                                            .sendFriendRequest(uid)
                                                    }
                                                }
                                            ) {
                                                Text("Add friend")
                                            }
                                        }
                                    }

                                    TextButton(
                                        enabled = !busy,
                                        onClick = { blockUid = uid }
                                    ) {
                                        Text("Block")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    noteUid?.let { uid ->
        AlertDialog(
            onDismissRequest = {
                if (!busy) noteUid = null
            },
            title = {
                Text("Private friend note")
            },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(1000) },
                    label = {
                        Text("Only you can see this")
                    },
                    supportingText = {
                        Text("${draft.length}/1000 • Clear to delete")
                    }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        action("Note saved") {
                            FriendDetailsRepository.saveNote(uid, draft)
                            noteUid = null
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { noteUid = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    blockUid?.let { uid ->
        AlertDialog(
            onDismissRequest = {
                blockUid = null
            },
            title = {
                Text("Block this person?")
            },
            text = {
                Text(
                    "This removes your friendship and pending requests " +
                            "and prevents new messages. " +
                            "You can unblock them in Blocked."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        action("Person blocked") {
                            SocialRepository.block(uid)
                            blockUid = null
                        }
                    }
                ) {
                    Text("Block")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { blockUid = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
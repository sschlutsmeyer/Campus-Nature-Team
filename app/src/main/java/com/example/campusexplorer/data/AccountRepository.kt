package com.example.campusexplorer.data

import android.util.Log
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

// Limits for accounts. firebase/firestore.rules checks the same username and bio limits,
// and Firebase itself requires passwords of at least 6 characters.
const val USERNAME_MIN = 3
const val USERNAME_MAX = 20
const val BIO_MAX = 160
const val PASSWORD_MIN = 6

// A user's public profile.
data class UserProfile(
    val uid: String,

    // Shown as typed. Usernames are unique ignoring upper and lower case.
    val username: String,
    val bio: String,

    // Where the profile picture is in Firebase Storage, or null for none.
    val photoPath: String?,
    val createdAt: Date?,
)

// Who's using the app right now.
data class Session(

    // The signed-in user's id. Null only for a moment while a guest session starts.
    val uid: String?,

    // True for guests. Everyone is a guest (a Firebase anonymous user) until they sign up or log in.
    val isGuest: Boolean,

    // The account's profile. Null for guests, while it loads, and for an account that hasn't picked
    // a username yet (if sign-up stopped partway).
    val profile: UserProfile?,

    // Whether the profile has loaded, so a missing one means the account still needs a username.
    val profileChecked: Boolean,
) {
    val isLoggedIn: Boolean get() = uid != null && !isGuest

    // Logged in with a username: can post, comment, follow, add friends, and send messages.
    val hasAccount: Boolean get() = isLoggedIn && profile != null

    // Logged in, but sign-up stopped before a username was saved.
    val needsUsername: Boolean get() = isLoggedIn && profileChecked && profile == null
}

// Sign-up, log-in, profiles, and usernames.
//
// Accounts use Firebase email/password sign-in, but people only ever see a username and password.
// Each account gets a made-up sign-in email when it signs up. Logging in looks the username up in
// /usernames to find that email, so usernames can change without changing how you log in.
//
// Where things are stored:
//  - Firestore users/{uid}:          username, usernameLower, bio, photoPath, createdAt
//  - Firestore usernames/{lowercase}: uid, email (one per username, so two accounts can't share one)
//  - Storage avatars/{uid}/{time}.jpg: profile pictures
object AccountRepository {

    // Label for this file's messages in Logcat.
    private const val TAG = "AccountRepository"

    // Made-up sign-in emails end with this. example.com is reserved, so these never reach a real inbox.
    private const val LOGIN_EMAIL_DOMAIN = "users.nature-network.example.com"

    // Largest profile picture we'll download (2 MB). They're saved much smaller than that.
    private const val MAX_AVATAR_BYTES = 2L * 1024 * 1024

    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()
    private val users get() = db.collection("users")
    private val usernames get() = db.collection("usernames")
    private val photos get() = db.collection("photos")
    private val photoTransfers get() = db.collection("photoTransfers")
    private val storage get() = FirebaseStorage.getInstance().reference

    // Keeps shared listeners running while any screen uses them.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Profiles seen so far, so names and pictures show right away when they come back on screen.
    // (Declared before session, which reads it when it starts.)
    private val knownProfiles = ConcurrentHashMap<String, UserProfile>()
    private val profileFlows = ConcurrentHashMap<String, SharedFlow<UserProfile?>>()

    // Bumped after signing up, logging in, or out, so the session updates even if Firebase is slow to say so.
    private val authChanges = MutableStateFlow(0)

    private data class AuthState(val uid: String?, val isGuest: Boolean)

    private fun currentAuthState() = auth.currentUser.let { AuthState(it?.uid, it == null || it.isAnonymous) }

    // The signed-in user, live. Starts a guest session whenever nobody is signed in (at first launch
    // and after logging out). The token listener also hears about a guest turning into an account.
    private fun observeAuthState(): Flow<AuthState> = callbackFlow {
        val listener = FirebaseAuth.IdTokenListener { firebaseAuth ->
            trySend(currentAuthState())
            if (firebaseAuth.currentUser == null) launch { startGuestSession() }
        }
        auth.addIdTokenListener(listener)
        awaitClose { auth.removeIdTokenListener(listener) }
    }

    private suspend fun startGuestSession() {
        try {
            PhotoRepository.ensureSignedIn()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't start a guest session", e)
        }
    }

    // Messages about your account that can come up on any screen, like being logged out because the
    // account stopped working. MainActivity shows them.
    private val noticeEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val notices: SharedFlow<String> = noticeEvents

    // Who's using the app, live.
    @OptIn(ExperimentalCoroutinesApi::class)
    val session: StateFlow<Session> = combine(observeAuthState(), authChanges) { _, _ -> currentAuthState() }
        .distinctUntilChanged()
        .flatMapLatest { state ->
            val uid = state.uid
            when {
                uid == null -> flowOf(Session(uid = null, isGuest = true, profile = null, profileChecked = false))
                state.isGuest -> flowOf(Session(uid, isGuest = true, profile = null, profileChecked = true))
                else -> observeOwnProfile(uid)
                    .map { (profile, checked) -> Session(uid, isGuest = false, profile = profile, profileChecked = checked) }
                    .onStart { scope.launch { checkSavedLogin(uid) } }
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), initialSession())

    // A saved login stops working if the account is deleted or disabled, or its password is changed on
    // another phone. Then nothing loads or saves, so log out to a guest session and say why. (Being
    // offline isn't a reason: the login is checked again the next time the app opens.)
    private suspend fun checkSavedLogin(uid: String) {
        val user = auth.currentUser?.takeIf { it.uid == uid && !it.isAnonymous } ?: return
        try {
            user.getIdToken(false).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!isLoginRevoked(e)) return
            Log.w(TAG, "Saved login no longer works; logging out", e)
            auth.signOut()
            authChanges.value++
            noticeEvents.tryEmit("You've been logged out. Please log in again.")
            startGuestSession()
        }
    }

    private fun isLoginRevoked(e: Exception): Boolean {
        if (e is FirebaseAuthInvalidUserException) return true
        val reason = (e as? FirebaseAuthException)?.errorCode.orEmpty() + " " + e.message.orEmpty()
        return listOf("INVALID_REFRESH_TOKEN", "USER_NOT_FOUND", "USER_DISABLED", "TOKEN_EXPIRED", "INVALID_USER_TOKEN")
            .any { it in reason }
    }

    private fun initialSession(): Session {
        val state = currentAuthState()
        val profile = state.uid?.let { knownProfiles[it] }
        return Session(state.uid, state.isGuest, profile, profileChecked = state.isGuest || profile != null)
    }

    // Your own profile and whether it has loaded. A profile that isn't in the phone's cache while
    // offline counts as "not loaded yet", not "missing", so the app doesn't ask for a username by mistake.
    private fun observeOwnProfile(uid: String): Flow<Pair<UserProfile?, Boolean>> = callbackFlow {
        knownProfiles[uid]?.let { trySend(it to true) }
        val registration = users.document(uid).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Couldn't load your profile", error)
                trySend(knownProfiles[uid] to true)
                return@addSnapshotListener
            }
            if (snapshot == null || (!snapshot.exists() && snapshot.metadata.isFromCache)) return@addSnapshotListener
            val profile = toProfile(snapshot)
            if (profile != null) knownProfiles[uid] = profile
            trySend(profile to true)
        }
        awaitClose { registration.remove() }
    }.distinctUntilChanged()

    // Anyone's profile, live, or null if they don't have one (guests and older anonymous posters).
    // Screens showing the same user share one listener.
    fun profile(uid: String): Flow<UserProfile?> = profileFlows.getOrPut(uid) {
        callbackFlow {
            val registration = users.document(uid).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.takeIf { it.exists() }?.let(::toProfile))
            }
            awaitClose { registration.remove() }
        }
            .onEach { profile -> if (profile != null) knownProfiles[uid] = profile }
            .catch { e ->
                Log.w(TAG, "Couldn't load profile $uid", e)
                emit(knownProfiles[uid])
            }
            .shareIn(scope, SharingStarted.WhileSubscribed(30_000), replay = 1)
    }

    // The last profile seen for a user, if any, to show while the live one loads.
    fun cachedProfile(uid: String): UserProfile? = knownProfiles[uid]

    // Why a username can't be used, or null if it's fine. Doesn't check whether it's taken.
    fun usernameProblem(name: String): String? = when {
        name.length < USERNAME_MIN -> "Usernames need at least $USERNAME_MIN characters."
        name.length > USERNAME_MAX -> "Usernames can be up to $USERNAME_MAX characters."
        !USERNAME_PATTERN.matches(name) -> "Use only letters, numbers, underscores (_), and periods (.)."
        else -> null
    }

    private val USERNAME_PATTERN = Regex("[A-Za-z0-9_.]+")

    fun passwordProblem(password: String): String? =
        if (password.length < PASSWORD_MIN) "Passwords need at least $PASSWORD_MIN characters." else null

    // Whether nobody else has the username (yours counts as available). Checks the server, not the cache.
    suspend fun isUsernameAvailable(name: String): Boolean {
        PhotoRepository.ensureSignedIn()
        val entry = usernames.document(name.trim().lowercase()).get(Source.SERVER).await()
        return !entry.exists() || entry.getString("uid") == auth.currentUser?.uid
    }

    // Signing up, logging in, and logging out switch the app to the new account (or guest) as soon as
    // Firebase signs in, which closes the screen that started them. So they run in the app-wide scope
    // to finish anyway, and report success with a notice rather than through that screen.
    private suspend fun <T> runInApp(block: suspend () -> T): T = scope.async { block() }.await()

    // Turns the guest into an account with this username and password. The account keeps the guest's
    // id, so pins saved as a guest stay. If saving the username fails after the account is made,
    // the session reports needsUsername and the app asks for one again (see finishSignUp).
    suspend fun signUp(username: String, password: String): Unit = runInApp {
        val name = username.trim()
        usernameProblem(name)?.let { throw IllegalArgumentException(it) }
        passwordProblem(password)?.let { throw IllegalArgumentException(it) }
        PhotoRepository.ensureSignedIn()
        val user = checkNotNull(auth.currentUser) { "Couldn't start a session. Check your connection." }
        check(user.isAnonymous) { "You're already logged in." }
        require(isUsernameAvailable(name)) { "“$name” is already taken. Try another username." }

        user.linkWithCredential(EmailAuthProvider.getCredential(loginEmailFor(user.uid), password)).await()

        // Get a fresh sign-in token so the database sees an account, not a guest, before saving the profile.
        user.getIdToken(true).await()
        authChanges.value++
        try {
            createProfile(name)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            noticeEvents.tryEmit(errorMessage(e, "Your account was made, but its username couldn't be saved.") + " Choose a username to finish.")
            throw e
        }
        noticeEvents.tryEmit("Welcome to Nature Network, $name!")
    }

    // For an account that doesn't have a username yet (if sign-up stopped partway).
    suspend fun finishSignUp(username: String): Unit = runInApp {
        createProfile(username)
        noticeEvents.tryEmit("Welcome to Nature Network, ${username.trim()}!")
    }

    // Saves the profile and claims the username, both at once.
    private suspend fun createProfile(username: String) {
        val name = username.trim()
        usernameProblem(name)?.let { throw IllegalArgumentException(it) }
        val user = checkNotNull(auth.currentUser?.takeIf { !it.isAnonymous }) { "Log in first." }
        val lower = name.lowercase()
        try {
            db.batch()
                .set(usernames.document(lower), mapOf("uid" to user.uid, "email" to (user.email ?: loginEmailFor(user.uid))))
                .set(
                    users.document(user.uid),
                    mapOf(
                        "username" to name,
                        "usernameLower" to lower,
                        "bio" to "",
                        "photoPath" to null,
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                )
                .commit()
                .await()
        } catch (e: FirebaseFirestoreException) {

            // Someone else claimed the name first. (Taking another user's name is refused by the rules.)
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED && !isUsernameAvailable(name)) {
                throw IllegalArgumentException("“$name” was just taken. Try another username.")
            }
            throw e
        }
    }

    // Logs in to an existing account. A guest can bring their pins along (bringGuestPins): they're
    // moved to the account after it logs in.
    suspend fun logIn(username: String, password: String, bringGuestPins: Boolean): Unit = runInApp {
        val name = username.trim()
        require(name.isNotEmpty()) { "Enter your username." }
        require(password.isNotEmpty()) { "Enter your password." }
        PhotoRepository.ensureSignedIn()

        val entry = usernames.document(name.lowercase()).get(Source.SERVER).await()
        require(entry.exists()) { "No account has the username “$name”." }
        val accountUid = checkNotNull(entry.getString("uid")) { "That account is missing its details." }
        val email = checkNotNull(entry.getString("email")) { "That account is missing its details." }

        val current = auth.currentUser
        if (current?.uid == accountUid) return@runInApp
        val guestUid = current?.takeIf { it.isAnonymous }?.uid

        // While still the guest, list the pins the account may take (the rules check this list).
        val pinIds = if (guestUid != null && bringGuestPins) {
            photos.whereEqualTo("uid", guestUid).get().await().documents.map { it.id }.take(500)
        } else emptyList()
        if (guestUid != null && pinIds.isNotEmpty()) {
            photoTransfers.document(guestUid).set(
                mapOf("to" to accountUid, "photoIds" to pinIds, "createdAt" to FieldValue.serverTimestamp())
            ).await()
        }

        try {
            auth.signInWithEmailAndPassword(email, password).await()
        } catch (e: Exception) {
            if (guestUid != null && pinIds.isNotEmpty()) {
                try {
                    photoTransfers.document(guestUid).delete().await()
                } catch (cleanup: Exception) {
                    Log.w(TAG, "Couldn't cancel the pin transfer", cleanup)
                }
            }
            throw e
        }
        authChanges.value++

        val moved = try {
            claimGuestPins()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't move guest pins", e)
            if (pinIds.isNotEmpty()) {
                noticeEvents.tryEmit("Logged in, but your guest pins couldn't be moved to this account.")
                return@runInApp
            }
            0
        }
        noticeEvents.tryEmit(
            if (moved > 0) "Logged in. Moved $moved ${if (moved == 1) "pin" else "pins"} to your account."
            else "Logged in. Welcome back!"
        )
    }

    // Moves guest pins waiting for the account that just logged in (listed in /photoTransfers). That
    // includes any from an earlier log-in that was cut off, for example by the app closing. Retries
    // briefly, because the database can take a moment to notice the new account.
    private suspend fun claimGuestPins(): Int {
        val me = auth.currentUser?.uid ?: return 0
        repeat(3) { attempt ->
            try {
                val transfers = photoTransfers.whereEqualTo("to", me).get(Source.SERVER).await().documents
                var moved = 0
                for (transfer in transfers) {
                    val ids = (transfer.get("photoIds") as? List<*>).orEmpty().filterIsInstance<String>()
                    moved += coroutineScope {
                        ids.map { id ->
                            async {
                                try {
                                    photos.document(id).update("uid", me).await()
                                    true
                                } catch (e: FirebaseFirestoreException) {

                                    // A pin deleted in the meantime is fine to skip.
                                    if (e.code != FirebaseFirestoreException.Code.NOT_FOUND) throw e
                                    false
                                }
                            }
                        }.awaitAll().count { it }
                    }
                    transfer.reference.delete().await()
                }
                return moved
            } catch (e: FirebaseFirestoreException) {
                if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED || attempt == 2) throw e
                delay(800L * (attempt + 1))
            }
        }
        return 0
    }

    // Logs out and starts a new guest session. The account stays; log in again with its username.
    suspend fun signOut(): Unit = runInApp {
        auth.signOut()
        authChanges.value++
        noticeEvents.tryEmit("Logged out. You're exploring as a guest now.")
        startGuestSession()
    }

    // Changes your username and bio. A new username is claimed, and the old one freed, at the same time.
    suspend fun updateProfile(username: String, bio: String) {
        val name = username.trim()
        val cleanBio = bio.trim()
        usernameProblem(name)?.let { throw IllegalArgumentException(it) }
        require(cleanBio.length <= BIO_MAX) { "Bios can be up to $BIO_MAX characters." }
        val user = checkNotNull(auth.currentUser?.takeIf { !it.isAnonymous }) { "Log in first." }
        val current = users.document(user.uid).get().await()
        val oldLower = checkNotNull(current.getString("usernameLower")) { "Your profile is missing." }
        val newLower = name.lowercase()

        val batch = db.batch()
        if (newLower != oldLower) {
            require(isUsernameAvailable(name)) { "“$name” is already taken. Try another username." }
            batch.set(usernames.document(newLower), mapOf("uid" to user.uid, "email" to (user.email ?: loginEmailFor(user.uid))))
            batch.delete(usernames.document(oldLower))
        }
        batch.update(users.document(user.uid), mapOf("username" to name, "usernameLower" to newLower, "bio" to cleanBio))
        batch.commit().await()
    }

    // Uploads a new profile picture (a small square JPEG) and removes the old one.
    suspend fun setProfilePhoto(jpeg: ByteArray) {
        val uid = checkNotNull(auth.currentUser?.takeIf { !it.isAnonymous }?.uid) { "Log in first." }
        val oldPath = users.document(uid).get().await().getString("photoPath")
        val path = "avatars/$uid/${System.currentTimeMillis()}.jpg"
        storage.child(path).putBytes(jpeg, storageMetadata { contentType = "image/jpeg" }).await()
        users.document(uid).update("photoPath", path).await()
        oldPath?.let { deleteAvatarFile(it) }
    }

    suspend fun removeProfilePhoto() {
        val uid = checkNotNull(auth.currentUser?.takeIf { !it.isAnonymous }?.uid) { "Log in first." }
        val oldPath = users.document(uid).get().await().getString("photoPath") ?: return
        users.document(uid).update("photoPath", null).await()
        deleteAvatarFile(oldPath)
    }

    // The picture is already unlinked from the profile, so a failure here only leaves an unused file.
    private suspend fun deleteAvatarFile(path: String) {
        try {
            storage.child(path).delete().await()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't delete old profile picture $path", e)
        }
    }

    // A profile picture's JPEG bytes.
    suspend fun loadAvatar(path: String): ByteArray {
        PhotoRepository.ensureSignedIn()
        return storage.child(path).getBytes(MAX_AVATAR_BYTES).await()
    }

    // Checks the current password, then sets a new one.
    suspend fun changePassword(currentPassword: String, newPassword: String) {
        passwordProblem(newPassword)?.let { throw IllegalArgumentException(it) }
        val user = checkNotNull(auth.currentUser?.takeIf { !it.isAnonymous }) { "Log in first." }
        val email = checkNotNull(user.email) { "This account can't change its password." }
        user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword)).await()
        user.updatePassword(newPassword).await()
    }

    // Users whose username starts with the search text (ignoring case), up to 20.
    suspend fun searchUsers(text: String): List<UserProfile> {
        val prefix = text.trim().lowercase()
        if (prefix.isEmpty()) return emptyList()
        PhotoRepository.ensureSignedIn()
        return users.orderBy("usernameLower")
            .startAt(prefix)
            .endAt(prefix + "")
            .limit(20)
            .get()
            .await()
            .documents
            .mapNotNull(::toProfile)
            .onEach { knownProfiles[it.uid] = it }
    }

    // A readable message for a failed sign-up, log-in, or profile change.
    fun errorMessage(e: Throwable, fallback: String): String = when {
        e is IllegalArgumentException || e is IllegalStateException -> e.message ?: fallback
        e is FirebaseAuthWeakPasswordException -> "That password is too weak. Use at least $PASSWORD_MIN characters."
        e is FirebaseAuthInvalidCredentialsException -> "Incorrect password."
        e is FirebaseAuthUserCollisionException -> "That account already exists. Try logging in instead."
        e is FirebaseAuthInvalidUserException -> "That account has been disabled or deleted."
        e is FirebaseTooManyRequestsException -> "Too many tries. Wait a minute, then try again."
        e is FirebaseNetworkException -> "No connection. Check your internet and try again."
        e is FirebaseAuthException && (e.errorCode == "ERROR_OPERATION_NOT_ALLOWED" ||
            e.message.orEmpty().contains("CONFIGURATION_NOT_FOUND")) ->
            "Accounts aren't turned on for this Firebase project yet. In the Firebase console, enable " +
                "Authentication > Sign-in method > Email/Password."
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "$fallback Permission denied. (Have the latest Firestore rules been published?)"
        else -> "$fallback Check your connection and try again."
    }

    private fun loginEmailFor(uid: String) = "${uid.lowercase()}@$LOGIN_EMAIL_DOMAIN"

    internal fun toProfile(doc: DocumentSnapshot): UserProfile? {
        val username = doc.getString("username") ?: return null
        return UserProfile(
            uid = doc.id,
            username = username,
            bio = doc.getString("bio").orEmpty(),
            photoPath = doc.getString("photoPath"),
            createdAt = doc.getTimestamp("createdAt", ServerTimestampBehavior.ESTIMATE)?.toDate(),
        )
    }
}

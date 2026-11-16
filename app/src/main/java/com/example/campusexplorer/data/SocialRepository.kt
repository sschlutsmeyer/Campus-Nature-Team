package com.example.campusexplorer.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import java.util.Date

// A friend request from one user to another.
data class FriendRequest(val from: String, val to: String, val createdAt: Date?)

// Your connections with other users. Empty for guests.
data class MySocial(
    val following: Set<String> = emptySet(),
    val followers: Set<String> = emptySet(),
    val friends: Set<String> = emptySet(),

    // Friend requests sent to you and by you, newest first.
    val incomingRequests: List<FriendRequest> = emptyList(),
    val outgoingRequests: List<FriendRequest> = emptyList(),
    val blockedByMe: Set<String> = emptySet(),
    val blockedMe: Set<String> = emptySet(),
) {

    // Users whose posts, comments, and pins you don't see: ones you blocked and ones who blocked you.
    val hidden: Set<String> get() = blockedByMe + blockedMe

    fun hasRequestFrom(uid: String) = incomingRequests.any { it.from == uid }
    fun hasRequestTo(uid: String) = outgoingRequests.any { it.to == uid }
}

// Follows, friends, friend requests, and blocks.
//
// Where things are stored (ids made of two user ids joined by "_"):
//  - Firestore follows/{follower}_{followed}:     follower, followed, createdAt
//  - Firestore friendRequests/{from}_{to}:        from, to, createdAt
//  - Firestore friendships/{pair id}:             users (both ids, smaller first), createdAt
//  - Firestore blocks/{blocker}_{blocked}:        blocker, blocked, createdAt
object SocialRepository {

    // Label for this file's messages in Logcat.
    private const val TAG = "SocialRepository"

    private val db get() = FirebaseFirestore.getInstance()
    private val follows get() = db.collection("follows")
    private val friendRequests get() = db.collection("friendRequests")
    private val friendships get() = db.collection("friendships")
    private val blocks get() = db.collection("blocks")

    // Two users' shared id: both ids, smaller first. Friendships and chats use it.
    fun pairId(a: String, b: String) = if (a < b) "${a}_$b" else "${b}_$a"

    // All of your connections, live. Parts that can't be loaded (for example, before the latest
    // rules are published) count as empty.
    fun observeMySocial(me: String): Flow<MySocial> {
        val people = combine(observeFollowing(me), observeFollowers(me), observeFriends(me)) { following, followers, friends ->
            Triple(following, followers, friends)
        }
        val requests = combine(
            observeRequests(friendRequests.whereEqualTo("to", me)),
            observeRequests(friendRequests.whereEqualTo("from", me)),
        ) { incoming, outgoing -> incoming to outgoing }
        val blocking = combine(
            observeUids(blocks.whereEqualTo("blocker", me), "blocked"),
            observeUids(blocks.whereEqualTo("blocked", me), "blocker"),
        ) { byMe, ofMe -> byMe to ofMe }
        return combine(people, requests, blocking) { (following, followers, friends), (incoming, outgoing), (byMe, ofMe) ->
            MySocial(following, followers, friends, incoming, outgoing, byMe, ofMe)
        }.distinctUntilChanged()
    }

    // Anyone's follows and friends, live. Used on profiles.
    fun observeFollowing(uid: String) = observeUids(follows.whereEqualTo("follower", uid), "followed")
    fun observeFollowers(uid: String) = observeUids(follows.whereEqualTo("followed", uid), "follower")
    fun observeFriends(uid: String): Flow<Set<String>> = observeQuery(friendships.whereArrayContains("users", uid), emptySet()) { docs ->
        docs.flatMap { (it.get("users") as? List<*>).orEmpty().filterIsInstance<String>() }.filterTo(HashSet()) { it != uid }
    }

    suspend fun follow(uid: String) {
        val me = myUid()
        follows.document("${me}_$uid")
            .set(mapOf("follower" to me, "followed" to uid, "createdAt" to FieldValue.serverTimestamp()))
            .await()
    }

    suspend fun unfollow(uid: String) {
        follows.document("${myUid()}_$uid").delete().await()
    }

    // Stops someone from following you.
    suspend fun removeFollower(uid: String) {
        follows.document("${uid}_${myUid()}").delete().await()
    }

    // Asks someone to be friends. If they already asked you, this accepts their request instead.
    suspend fun sendFriendRequest(uid: String) {
        val me = myUid()
        if (friendRequests.document("${uid}_$me").get().await().exists()) {
            acceptFriendRequest(uid)
            return
        }
        friendRequests.document("${me}_$uid")
            .set(mapOf("from" to me, "to" to uid, "createdAt" to FieldValue.serverTimestamp()))
            .await()
    }

    suspend fun cancelFriendRequest(uid: String) {
        friendRequests.document("${myUid()}_$uid").delete().await()
    }

    // Becomes friends with someone who asked, and clears the request (and yours to them, if any).
    suspend fun acceptFriendRequest(from: String) {
        val me = myUid()
        val pair = pairId(me, from)
        db.batch()
            .set(friendships.document(pair), mapOf("users" to pair.split("_"), "createdAt" to FieldValue.serverTimestamp()))
            .delete(friendRequests.document("${from}_$me"))
            .delete(friendRequests.document("${me}_$from"))
            .commit()
            .await()
    }

    suspend fun declineFriendRequest(from: String) {
        friendRequests.document("${from}_${myUid()}").delete().await()
    }

    suspend fun unfriend(uid: String) {
        friendships.document(pairId(myUid(), uid)).delete().await()
    }

    // Blocks someone: also unfollows both ways, cancels friend requests both ways, and unfriends.
    // Neither of you can follow, friend, or message the other until you unblock them.
    suspend fun block(uid: String) {
        val me = myUid()
        db.batch()
            .set(blocks.document("${me}_$uid"), mapOf("blocker" to me, "blocked" to uid, "createdAt" to FieldValue.serverTimestamp()))
            .delete(follows.document("${me}_$uid"))
            .delete(follows.document("${uid}_$me"))
            .delete(friendRequests.document("${me}_$uid"))
            .delete(friendRequests.document("${uid}_$me"))
            .delete(friendships.document(pairId(me, uid)))
            .commit()
            .await()
    }

    suspend fun unblock(uid: String) {
        blocks.document("${myUid()}_$uid").delete().await()
    }

    private fun myUid(): String =
        checkNotNull(FirebaseAuth.getInstance().currentUser?.takeIf { !it.isAnonymous }?.uid) { "Log in first." }

    // One field of each matching document, as a set of user ids.
    private fun observeUids(query: Query, field: String): Flow<Set<String>> =
        observeQuery(query, emptySet()) { docs -> docs.mapNotNullTo(HashSet()) { it.getString(field) } }

    private fun observeRequests(query: Query): Flow<List<FriendRequest>> = observeQuery(query, emptyList()) { docs ->
        docs.mapNotNull { doc ->
            FriendRequest(
                from = doc.getString("from") ?: return@mapNotNull null,
                to = doc.getString("to") ?: return@mapNotNull null,
                createdAt = doc.getTimestamp("createdAt", ServerTimestampBehavior.ESTIMATE)?.toDate(),
            )
        }.sortedByDescending { it.createdAt?.time ?: Long.MAX_VALUE }
    }

    // A query's results, live. Errors are logged and count as no results.
    private fun <T> observeQuery(query: Query, empty: T, read: (List<DocumentSnapshot>) -> T): Flow<T> = callbackFlow {
        PhotoRepository.ensureSignedIn()
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(read(snapshot?.documents.orEmpty()))
        }
        awaitClose { registration.remove() }
    }.catch { e ->
        Log.w(TAG, "Couldn't load connections", e)
        emit(empty)
    }
}

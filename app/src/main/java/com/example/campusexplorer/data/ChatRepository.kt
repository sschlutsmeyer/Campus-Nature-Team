package com.example.campusexplorer.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date

// Longest message. firebase/firestore.rules checks the same limit.
const val MESSAGE_MAX = 1000

// One conversation in your list of chats.
data class ChatSummary(
    val id: String,
    val otherUid: String,

    // The latest message, who sent it, and when. Empty for a chat whose messages were all unsent.
    val lastMessage: String,
    val lastSender: String?,
    val lastMessageAt: Date?,

    // When you and the other user last opened the chat.
    val myLastRead: Date?,
    val theirLastRead: Date?,
) {

    // A message from them that you haven't seen yet.
    val unread: Boolean
        get() = lastSender == otherUid && lastMessageAt != null &&
            (myLastRead == null || lastMessageAt.after(myLastRead))
}

// One direct message. sending is true until the server has it.
data class ChatMessage(val id: String, val from: String, val text: String, val createdAt: Date?, val sending: Boolean)

// Direct messages between friends.
//
// Where things are stored:
//  - Firestore chats/{pair id}:                 users, lastMessage, lastSender, lastMessageAt,
//                                               lastRead (a map of user id to when they last read it)
//  - Firestore chats/{pair id}/messages/{id}:   from, text, createdAt
// A chat's id is the two users' pair id (see SocialRepository.pairId), the same as their friendship's.
object ChatRepository {

    // Label for this file's messages in Logcat.
    private const val TAG = "ChatRepository"

    // How much of the latest message the chat list keeps.
    private const val PREVIEW_MAX = 200

    // How many of the newest messages a chat loads.
    private const val MESSAGE_PAGE = 500

    private val db get() = FirebaseFirestore.getInstance()
    private val chats get() = db.collection("chats")
    private fun chatRef(me: String, other: String) = chats.document(SocialRepository.pairId(me, other))

    // Your chats, newest message first.
    fun observeChats(me: String): Flow<List<ChatSummary>> = callbackFlow {
        PhotoRepository.ensureSignedIn()
        val registration = chats.whereArrayContains("users", me).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Chat list listener failed", error)
                close(error)
                return@addSnapshotListener
            }
            val list = snapshot?.documents.orEmpty()
                .mapNotNull { toSummary(it, me) }
                .sortedByDescending { it.lastMessageAt?.time ?: 0L }
            trySend(list)
        }
        awaitClose { registration.remove() }
    }

    // One chat, or null before its first message.
    fun observeChat(me: String, other: String): Flow<ChatSummary?> = callbackFlow {
        PhotoRepository.ensureSignedIn()
        val registration = chatRef(me, other).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Chat listener failed", error)
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.takeIf { it.exists() }?.let { toSummary(it, me) })
        }
        awaitClose { registration.remove() }
    }

    // A chat's newest messages, oldest first. A message you just sent shows right away, marked as sending.
    fun observeMessages(me: String, other: String): Flow<List<ChatMessage>> = callbackFlow {
        PhotoRepository.ensureSignedIn()
        val registration = chatRef(me, other).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(MESSAGE_PAGE.toLong())
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Message listener failed", error)
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    ChatMessage(
                        id = doc.id,
                        from = doc.getString("from") ?: return@mapNotNull null,
                        text = doc.getString("text").orEmpty(),
                        createdAt = doc.getTimestamp("createdAt", ServerTimestampBehavior.ESTIMATE)?.toDate(),
                        sending = doc.metadata.hasPendingWrites(),
                    )
                }.sortedBy { it.createdAt?.time ?: Long.MAX_VALUE }
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    // Sends a message to a friend. Starts the chat if this is the first message.
    suspend fun send(other: String, text: String) {
        val me = myUid()
        val clean = text.trim()
        require(clean.isNotEmpty()) { "Write a message first." }
        require(clean.length <= MESSAGE_MAX) { "Messages can be up to $MESSAGE_MAX characters." }
        val chat = chatRef(me, other)
        db.batch()
            .set(
                chat.collection("messages").document(),
                mapOf("from" to me, "text" to clean, "createdAt" to FieldValue.serverTimestamp()),
            )
            .set(
                chat,
                mapOf(
                    "users" to chat.id.split("_"),
                    "lastMessage" to clean.take(PREVIEW_MAX),
                    "lastSender" to me,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "lastRead" to mapOf(me to FieldValue.serverTimestamp()),
                ),
                SetOptions.merge(),
            )
            .commit()
            .await()
    }

    // Records that you've read the chat up to now.
    suspend fun markRead(other: String) {
        val me = myUid()
        chatRef(me, other).update("lastRead.$me", FieldValue.serverTimestamp()).await()
    }

    // Deletes one of your messages for both of you. The chat list then shows the message before it.
    suspend fun unsend(other: String, message: ChatMessage) {
        val me = myUid()
        check(message.from == me) { "You can only unsend your own messages." }
        val chat = chatRef(me, other)
        chat.collection("messages").document(message.id).delete().await()

        val latest = chat.collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
        chat.update(
            mapOf(
                "lastMessage" to latest?.getString("text").orEmpty().take(PREVIEW_MAX),
                "lastSender" to latest?.getString("from"),
                "lastMessageAt" to latest?.getTimestamp("createdAt"),
            )
        ).await()
    }

    private fun myUid(): String =
        checkNotNull(FirebaseAuth.getInstance().currentUser?.takeIf { !it.isAnonymous }?.uid) { "Log in first." }

    private fun toSummary(doc: DocumentSnapshot, me: String): ChatSummary? {
        val users = (doc.get("users") as? List<*>).orEmpty().filterIsInstance<String>()
        val other = users.firstOrNull { it != me } ?: return null
        return ChatSummary(
            id = doc.id,
            otherUid = other,
            lastMessage = doc.getString("lastMessage").orEmpty(),
            lastSender = doc.getString("lastSender"),
            lastMessageAt = doc.getTimestamp("lastMessageAt", ServerTimestampBehavior.ESTIMATE)?.toDate(),
            myLastRead = readTime(doc, me),
            theirLastRead = readTime(doc, other),
        )
    }

    private fun readTime(doc: DocumentSnapshot, uid: String): Date? =
        (doc.get("lastRead.$uid", ServerTimestampBehavior.ESTIMATE) as? Timestamp)?.toDate()
}

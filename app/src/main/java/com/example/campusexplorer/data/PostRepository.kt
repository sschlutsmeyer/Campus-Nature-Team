package com.example.campusexplorer.data

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date

// Longest allowed post title, description, and comment. firebase/firestore.rules checks the same limits.
// Pin names share the title limit, because a photo post's title and its pin name are kept the same.
const val POST_TITLE_MAX = 100
const val POST_DESCRIPTION_MAX = 2000
const val COMMENT_MAX = 500

// One post in the feed. A photo post shares one of its author's photos; a text post has no photo.
data class FeedPost(
    val id: String,

    // Who posted it.
    val uid: String,
    val title: String,
    val description: String,
    val createdAt: Date?,

    // The shared photo's id, or null for a text-only post. A photo post's id is the same as its
    // photo's id, so each photo can be posted only once and a map pin can find its post directly.
    val photoId: String?,

    // Copied from the photo when it's posted. Photos are private to their owner, so other users
    // show the image and the map pin from these instead of reading the photo itself.
    val storagePath: String?,
    val latitude: Double?,
    val longitude: Double?,
    val photoTakenAt: Date?,

    // The ids of the users who liked and disliked it. A user can be in at most one.
    val bookmarkedBy: List<String> = emptyList(),
    val likedBy: Set<String>,
    val dislikedBy: Set<String>,
    val commentCount: Int,
) {

    // The shared photo in the form the image loaders and the map use, or null for a text post.
    val photo: NaturePhoto?
        get() = photoId?.let {
            NaturePhoto(
                id = it,
                caption = title,
                latitude = latitude,
                longitude = longitude,
                createdAt = photoTakenAt ?: createdAt,
                uid = uid,
                storagePath = storagePath,
            )
        }

    // True if the post's photo can be shown on the map.
    val hasLocation: Boolean get() = photoId != null && latitude != null && longitude != null
}

// One comment on a post.
data class PostComment(val id: String, val uid: String, val text: String, val createdAt: Date?)

// What a user thinks of a post. Like and dislike can't both be on. NONE clears both.
enum class Reaction { LIKE, DISLIKE, NONE }

// Every post, reaction, and comment read and write goes through here.
//
// Where things are stored:
//  - Firestore posts/{id}:                  title, description, uid, createdAt, the photo's id,
//                                           image path, and location, likedBy, dislikedBy, commentCount
//  - Firestore posts/{id}/comments/{id}:    uid, text, createdAt
object PostRepository {

    // Label for this file's messages in Logcat.
    private const val TAG = "PostRepository"

    // Shortcuts to the Firebase collections we use.
    private val db get() = FirebaseFirestore.getInstance()
    private val posts get() = db.collection("posts")
    private val photos get() = db.collection("photos")
    private fun comments(postId: String) = posts.document(postId).collection("comments")

    // Live list of every post, newest first. Ends with an error if the posts can't be read.
    // Your own changes (a like, a new post) show up right away, before the server confirms them.
    fun observePosts(): Flow<List<FeedPost>> = callbackFlow {
        PhotoRepository.ensureSignedIn()
        val registration = posts
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Post listener failed", error)
                    close(error)
                    return@addSnapshotListener
                }

                // Sort here too: a brand-new post's time is only an estimate until the server sets it.
                val list = snapshot?.documents.orEmpty().mapNotNull(::toPost)
                    .sortedByDescending { it.createdAt?.time ?: Long.MAX_VALUE }
                trySend(list)
            }

        // Stop listening when nobody needs the list anymore.
        awaitClose { registration.remove() }
    }

    fun observeBookmarkedPosts(): Flow<List<FeedPost>> = callbackFlow {
        val uid = PhotoRepository.ensureSignedIn()
        val registration = posts
            .whereArrayContains("bookmarkedBy", uid) // <--- THIS IS THE MAGIC FILTER
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                // ... same listener logic as your observePosts() function ...
            }
        // ...
    }

    // Live view of one post, or null if it doesn't exist (or was deleted).
    fun observePost(postId: String): Flow<FeedPost?> = callbackFlow {
        PhotoRepository.ensureSignedIn()
        val registration = posts.document(postId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Listener for post $postId failed", error)
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.takeIf { it.exists() }?.let(::toPost))
        }
        awaitClose { registration.remove() }
    }

    // Publishes a post and returns its id. If a photo is given, it's shared with the post and its
    // pin is renamed to the post's title (if different), so the pin and post always match.
    suspend fun createPost(title: String, description: String, photo: NaturePhoto?): String {
        val uid = PhotoRepository.ensureSignedIn()
        val cleanTitle = cleanTitle(title)
        val cleanDescription = cleanDescription(description)

        // Text-only post: a new id and nothing else to update.
        if (photo == null) {
            val postRef = posts.document()
            postRef.set(newPostData(uid, cleanTitle, cleanDescription, photo = null)).await()
            return postRef.id
        }

        check(photo.uid == uid) { "You can only post your own photos." }
        val postRef = posts.document(photo.id)
        val photoRef = photos.document(photo.id)

        // A transaction so the post and the rename happen together, and two taps can't post twice.
        db.runTransaction { transaction ->
            check(!transaction.get(postRef).exists()) { "This photo has already been posted." }
            val current = transaction.get(photoRef)
            check(current.exists()) { "This photo was deleted." }

            transaction.set(postRef, newPostData(uid, cleanTitle, cleanDescription, photo))
            if (current.getString("caption") != cleanTitle) {
                transaction.update(photoRef, "caption", cleanTitle)
            }
        }.await()
        return postRef.id
    }

    // Changes your post's title and description. A new title also renames the shared photo's pin.
    suspend fun updatePost(post: FeedPost, title: String, description: String) {
        val uid = PhotoRepository.ensureSignedIn()
        check(post.uid == uid) { "You can only edit your own posts." }
        val newTitle = cleanTitle(title)
        val changes = mapOf("title" to newTitle, "description" to cleanDescription(description))
        val postRef = posts.document(post.id)

        // No pin to rename: just update the post.
        val photoId = post.photoId
        if (photoId == null || newTitle == post.title) {
            postRef.update(changes).await()
            return
        }

        val photoRef = photos.document(photoId)
        db.runTransaction { transaction ->
            val photo = transaction.get(photoRef)
            transaction.update(postRef, changes)
            if (photo.exists()) transaction.update(photoRef, "caption", newTitle)
        }.await()
    }

    // Deletes your post with its comments. A shared photo stays in your gallery and on your map,
    // but other users stop seeing it.
    suspend fun deletePost(post: FeedPost) {
        val uid = PhotoRepository.ensureSignedIn()
        check(post.uid == uid) { "You can only delete your own posts." }
        deletePostAndComments(post.id)
    }

    // Likes, dislikes, or clears your reaction to a post. Liking removes a dislike and vice versa.
    suspend fun setReaction(post: FeedPost, reaction: Reaction) {
        val uid = PhotoRepository.ensureSignedIn()
        val (likes, dislikes) = when (reaction) {
            Reaction.LIKE -> FieldValue.arrayUnion(uid) to FieldValue.arrayRemove(uid)
            Reaction.DISLIKE -> FieldValue.arrayRemove(uid) to FieldValue.arrayUnion(uid)
            Reaction.NONE -> FieldValue.arrayRemove(uid) to FieldValue.arrayRemove(uid)
        }
        posts.document(post.id).update("likedBy", likes, "dislikedBy", dislikes).await()
    }

    // Bookmarks or unbookmarks a post for the signed-in user.
    suspend fun toggleBookmark(post: FeedPost) {
        val uid = PhotoRepository.ensureSignedIn()
        val isBookmarked = uid in post.bookmarkedBy
        val update = if (isBookmarked) {
            FieldValue.arrayRemove(uid)
        } else {
            FieldValue.arrayUnion(uid)
        }
        posts.document(post.id).update("bookmarkedBy", update).await()
    }

    // Live list of a post's comments, oldest first.
    fun observeComments(postId: String): Flow<List<PostComment>> = callbackFlow {
        PhotoRepository.ensureSignedIn()
        val registration = comments(postId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Comment listener for post $postId failed", error)
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    PostComment(
                        id = doc.id,
                        uid = doc.getString("uid") ?: return@mapNotNull null,
                        text = doc.getString("text").orEmpty(),
                        createdAt = doc.getTimestamp("createdAt", ServerTimestampBehavior.ESTIMATE)?.toDate(),
                    )
                }.sortedBy { it.createdAt?.time ?: Long.MAX_VALUE }
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    // Adds your comment to a post and bumps its comment count, both at once.
    suspend fun addComment(post: FeedPost, text: String) {
        val uid = PhotoRepository.ensureSignedIn()
        val clean = text.trim()
        require(clean.isNotEmpty()) { "Write a comment first." }
        require(clean.length <= COMMENT_MAX) { "Comments can be up to $COMMENT_MAX characters." }
        db.batch()
            .set(
                comments(post.id).document(),
                mapOf("uid" to uid, "text" to clean, "createdAt" to FieldValue.serverTimestamp()),
            )
            .update(posts.document(post.id), "commentCount", FieldValue.increment(1))
            .commit()
            .await()
    }

    // Deletes a comment. Allowed for whoever wrote it and for the post's author.
    suspend fun deleteComment(post: FeedPost, comment: PostComment) {
        val uid = PhotoRepository.ensureSignedIn()
        check(comment.uid == uid || post.uid == uid) { "You can only delete your own comments." }
        db.batch()
            .delete(comments(post.id).document(comment.id))
            .update(posts.document(post.id), "commentCount", FieldValue.increment(-1))
            .commit()
            .await()
    }

    // The ref of a photo's post (a photo post's id is its photo's id). Used to rename pins.
    internal fun postRefForPhoto(photoId: String) = posts.document(photoId)

    // Whether a post exists. "Permission denied" counts as no post, so photos can still be renamed
    // and deleted in a Firebase project whose rules don't allow posts yet (it can't have any).
    internal suspend fun postExists(postId: String): Boolean = try {
        posts.document(postId).get().await().exists()
    } catch (e: FirebaseFirestoreException) {
        if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw e
        Log.w(TAG, "Can't read posts. Are the latest Firestore rules published?", e)
        false
    }

    // Deletes a post and its comments. Comments go first, while the post still exists, because the
    // rules check the post's author before letting them delete other people's comments.
    internal suspend fun deletePostAndComments(postId: String) {
        while (true) {
            val page = comments(postId).limit(400).get().await()
            if (page.isEmpty) break
            val batch = db.batch()
            page.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
        posts.document(postId).delete().await()
    }

    // A new post's fields. Every field is always written (null when unused) so the rules can check them.
    private fun newPostData(uid: String, title: String, description: String, photo: NaturePhoto?) = mapOf(
        "uid" to uid,
        "title" to title,
        "description" to description,
        "createdAt" to FieldValue.serverTimestamp(),
        "photoId" to photo?.id,
        "storagePath" to photo?.storagePath,
        "latitude" to photo?.latitude,
        "longitude" to photo?.longitude,
        "photoTakenAt" to photo?.createdAt,
        "likedBy" to emptyList<String>(),
        "dislikedBy" to emptyList<String>(),
        "commentCount" to 0,
    )

    private fun cleanTitle(title: String) = title.trim().also {
        require(it.isNotEmpty()) { "Give your post a title." }
        require(it.length <= POST_TITLE_MAX) { "Titles can be up to $POST_TITLE_MAX characters." }
    }

    private fun cleanDescription(description: String) = description.trim().also {
        require(it.length <= POST_DESCRIPTION_MAX) {
            "Descriptions can be up to $POST_DESCRIPTION_MAX characters."
        }
    }

    // Turns a Firestore document into a post, or null if it's missing its author.
    private fun toPost(doc: DocumentSnapshot): FeedPost? {
        val uid = doc.getString("uid") ?: return null
        return FeedPost(
            id = doc.id,
            uid = uid,
            title = doc.getString("title").orEmpty(),
            description = doc.getString("description").orEmpty(),

            // A brand-new post's time is set by the server. Until then, use the phone's estimate.
            createdAt = doc.getTimestamp("createdAt", ServerTimestampBehavior.ESTIMATE)?.toDate(),
            photoId = doc.getString("photoId"),
            storagePath = doc.getString("storagePath"),
            latitude = doc.getDouble("latitude"),
            longitude = doc.getDouble("longitude"),
            photoTakenAt = doc.getTimestamp("photoTakenAt")?.toDate(),
            likedBy = doc.stringSet("likedBy"),
            dislikedBy = doc.stringSet("dislikedBy"),
            bookmarkedBy = doc.stringSet("bookmarkedBy").toList(),
            commentCount = (doc.getLong("commentCount") ?: 0L).toInt().coerceAtLeast(0),
        )
    }

    // A list-of-strings field as a set, or empty if it's missing.
    private fun DocumentSnapshot.stringSet(field: String): Set<String> =
        (get(field) as? List<*>).orEmpty().filterIsInstance<String>().toSet()
}

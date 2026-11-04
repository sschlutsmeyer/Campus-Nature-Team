package com.example.campusexplorer.data

import android.location.Location
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.Date

// Info about one photo. The image itself is downloaded separately with loadImage().
data class NaturePhoto(
    val id: String,
    val caption: String,
    val latitude: Double?,
    val longitude: Double?,
    val createdAt: Date?,

    // Who took it.
    val uid: String?,

    // Where the JPEG is in Firebase Storage. Null for older photos stored in Firestore.
    val storagePath: String?,
)

// Every photo read and write goes through here.
//
// Where things are stored:
//  - Firestore photos/{id}:           caption, latitude, longitude, createdAt, uid, storagePath
//  - Storage photos/{uid}/{id}.jpg:   the image
//  - Firestore photoImages/{id}:      image bytes for older photos saved before we used Storage
//
// Photos are private: only their owner can read them. Posting a photo to the feed (see
// PostRepository) copies what other users need to see it (title, image path, location) into the post.
object PhotoRepository {

    // Label for this file's messages in Logcat.
    private const val TAG = "PhotoRepository"

    // Largest image we'll download (10 MB).
    private const val MAX_DOWNLOAD_BYTES = 10L * 1024 * 1024

    // Shortcuts to the Firebase databases and folders we use.
    private val db get() = FirebaseFirestore.getInstance()
    private val storage get() = FirebaseStorage.getInstance().reference
    private val photos get() = db.collection("photos")
    private val legacyImages get() = db.collection("photoImages")

    // Only one sign-in at a time. Several screens and listeners start at once, and two sign-ins
    // running together could create two different anonymous users.
    private val signInLock = Mutex()

    // Signs in anonymously if needed and returns the user's id. Firebase rules require sign-in.
    suspend fun ensureSignedIn(): String {
        val auth = FirebaseAuth.getInstance()
        auth.currentUser?.let { return it.uid }
        return signInLock.withLock {
            auth.currentUser?.uid ?: checkNotNull(auth.signInAnonymously().await().user).uid
        }
    }

    // The signed-in user's id, live. Signs in anonymously first if needed. Sends null if signed out.
    fun observeUserId(): Flow<String?> = callbackFlow {
        try {
            ensureSignedIn()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Sign-in failed; reads may be denied by security rules", e)
        }

        // Called right away with the current user, then again whenever it changes.
        val auth = FirebaseAuth.getInstance()
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    // Uploads the image, saves its info, and returns the new photo's id.
    suspend fun save(jpeg: ByteArray, caption: String, location: Location?): String {
        val uid = ensureSignedIn()

        // Make a new photo id and decide where its image goes.
        val photoRef = photos.document()
        val storagePath = "photos/$uid/${photoRef.id}.jpg"

        // Upload the image first so a photo never shows up without one.
        try {
            storage.child(storagePath)
                .putBytes(jpeg, storageMetadata { contentType = "image/jpeg" })
                .await()
        } catch (e: StorageException) {

            // "Not found" on upload means Storage isn't set up for this Firebase project.
            if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw e
            throw IllegalStateException(
                "Storage bucket not found. Is Storage enabled in the Firebase console?", e
            )
        }

        // Save the photo's info. The server fills in the time.
        photoRef.set(
            mapOf(
                "caption" to caption,
                "latitude" to location?.latitude,
                "longitude" to location?.longitude,
                "createdAt" to FieldValue.serverTimestamp(),
                "uid" to uid,
                "storagePath" to storagePath,
            )
        ).await()
        return photoRef.id
    }

    // Live list of one user's photos, newest first. Used by the gallery, the map, and the post picker.
    // Photos are private, so this is the only way to list them. Sorting locally avoids a composite index.
    fun observeOwnPhotos(uid: String): Flow<List<NaturePhoto>> = callbackFlow {
        require(uid.isNotBlank())

        // Keep an existing pin visible with its last confirmed name during an edit.
        // A new photo still waits for its first confirmed save before appearing.
        var confirmedPhotos = emptyMap<String, NaturePhoto>()
        val registration = photos.whereEqualTo("uid", uid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val updatedPhotos = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    if (doc.metadata.hasPendingWrites()) {
                        return@mapNotNull confirmedPhotos[doc.id]
                    }
                    if (doc.getString("uid") != uid) return@mapNotNull null
                    NaturePhoto(
                        id = doc.id,
                        caption = doc.getString("caption").orEmpty(),
                        latitude = doc.getDouble("latitude"),
                        longitude = doc.getDouble("longitude"),
                        createdAt = doc.getTimestamp("createdAt")?.toDate(),
                        uid = uid,
                        storagePath = doc.getString("storagePath"),
                    )
                }.sortedWith(compareByDescending<NaturePhoto> { it.createdAt?.time ?: Long.MIN_VALUE }
                    .thenBy { it.id })
                confirmedPhotos = updatedPhotos.associateBy { it.id }
                trySend(updatedPhotos)
            }
        awaitClose { registration.remove() }
    }

    // Rename the existing pin without changing its image, location, owner, or time.
    // A posted photo's post title is its pin name, so its post is renamed along with it.
    suspend fun updateCaption(photo: NaturePhoto, caption: String) {
        val name = caption.trim()
        require(name.isNotEmpty()) { "Enter a pin name." }
        require(name.length <= POST_TITLE_MAX) { "Pin names can be up to $POST_TITLE_MAX characters." }
        val uid = ensureSignedIn()
        check(photo.uid == uid) { "You can only edit pins you created." }
        val photoRef = photos.document(photo.id)
        if (PostRepository.postExists(photo.id)) {
            db.batch()
                .update(photoRef, "caption", name)
                .update(PostRepository.postRefForPhoto(photo.id), "title", name)
                .commit()
                .await()
        } else {
            photoRef.update("caption", name).await()
        }
    }

    // Downloads a photo's JPEG bytes, or returns null if the image is missing.
    suspend fun loadImage(photo: NaturePhoto): ByteArray? {
        ensureSignedIn()

        // Older photos keep their image in Firestore instead of Storage.
        val path = photo.storagePath
            ?: return legacyImages.document(photo.id).get().await().getBlob("data")?.toBytes()
        return try {
            storage.child(path).getBytes(MAX_DOWNLOAD_BYTES).await()
        } catch (e: StorageException) {
            if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw e
            Log.w(TAG, "Image for photo ${photo.id} is missing from Storage at $path")
            null
        }
    }

    // Deletes a photo's info and image, and its feed post if it has one. An already-missing image is fine.
    suspend fun delete(photo: NaturePhoto) {
        ensureSignedIn()

        // Remove the post (and its comments) first, so the feed never shows a post whose photo is gone.
        if (PostRepository.postExists(photo.id)) PostRepository.deletePostAndComments(photo.id)

        // Delete the info next so the pin disappears. If the image delete then fails, we're left
        // with an unused file in Storage instead of a pin with no photo.
        photos.document(photo.id).delete().await()

        // Older photos keep their image in Firestore instead of Storage.
        val path = photo.storagePath
        if (path == null) {
            legacyImages.document(photo.id).delete().await()
            return
        }

        // The photo is already gone from the app, so a failure here is only logged.
        try {
            storage.child(path).delete().await()
        } catch (e: StorageException) {
            if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) {
                Log.w(TAG, "Deleted photo ${photo.id} but not its image at $path", e)
            }
        }
    }
}

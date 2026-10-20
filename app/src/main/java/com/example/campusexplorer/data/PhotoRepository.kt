package com.example.campusexplorer.data

import android.location.Location
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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

    // Signs in anonymously if needed and returns the user's id. Firebase rules require sign-in.
    suspend fun ensureSignedIn(): String {
        val auth = FirebaseAuth.getInstance()
        auth.currentUser?.let { return it.uid }
        return checkNotNull(auth.signInAnonymously().await().user).uid
    }

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

    // Live list of all photos, newest first. Sends a new list whenever anything changes.
    fun observePhotos(): Flow<List<NaturePhoto>> = callbackFlow {
        try {
            ensureSignedIn()
        } catch (e: Exception) {
            Log.w(TAG, "Sign-in failed; reads may be denied by security rules", e)
        }

        // Keep an existing pin visible with its last confirmed name during an edit.
        // A new photo still waits for its first confirmed save before appearing.
        var confirmedPhotos = emptyMap<String, NaturePhoto>()

        // Include acknowledgements even when only pending-write metadata changes.
        val registration = photos
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Photo listener failed", error)
                    return@addSnapshotListener
                }

                val updatedPhotos = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    if (doc.metadata.hasPendingWrites()) {
                        return@mapNotNull confirmedPhotos[doc.id]
                    }
                    NaturePhoto(
                        id = doc.id,
                        caption = doc.getString("caption").orEmpty(),
                        latitude = doc.getDouble("latitude"),
                        longitude = doc.getDouble("longitude"),
                        createdAt = doc.getTimestamp("createdAt")?.toDate(),
                        uid = doc.getString("uid"),
                        storagePath = doc.getString("storagePath"),
                    )
                }
                confirmedPhotos = updatedPhotos.associateBy { it.id }
                trySend(updatedPhotos)
            }

        // Stop listening when nobody needs the list anymore.
        awaitClose { registration.remove() }
    }

    // Rename the existing pin without changing its image, location, owner, or time.
    suspend fun updateCaption(photo: NaturePhoto, caption: String) {
        val name = caption.trim()
        require(name.isNotEmpty()) { "Enter a pin name." }
        val uid = ensureSignedIn()
        check(photo.uid == uid) { "You can only edit pins you created." }
        photos.document(photo.id).update("caption", name).await()
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

    // Deletes a photo's info and image. An already-missing image is fine.
    suspend fun delete(photo: NaturePhoto) {
        ensureSignedIn()

        // Delete the info first so the pin disappears. If the image delete then fails, we're left
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

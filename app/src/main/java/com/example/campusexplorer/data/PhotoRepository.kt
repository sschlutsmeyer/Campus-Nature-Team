package com.example.campusexplorer.data

import android.location.Location
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date

/** Metadata for one photo. The image bytes live separately (see [PhotoRepository.loadImage]). */
data class NaturePhoto(
    val id: String,
    val caption: String,
    val latitude: Double?,
    val longitude: Double?,
    val createdAt: Date?,
    val uid: String?,
    /** Path of the JPEG in Firebase Storage, or null for older photos stored in Firestore. */
    val storagePath: String?,
)

/**
 * All photo reads/writes go through here.
 *
 * Layout:
 *  - photos/{id}                    caption, latitude, longitude, createdAt, uid, storagePath
 *  - Storage: photos/{uid}/{id}.jpg the JPEG itself
 *  - photoImages/{id}               legacy: JPEG bytes in Firestore, only read for photos saved
 *                                   before the move to Firebase Storage
 */
object PhotoRepository {
    private const val TAG = "PhotoRepository"
    private const val MAX_DOWNLOAD_BYTES = 10L * 1024 * 1024
    private val db get() = FirebaseFirestore.getInstance()
    private val storage get() = FirebaseStorage.getInstance().reference
    private val photos get() = db.collection("photos")
    private val legacyImages get() = db.collection("photoImages")

    /** Signs in anonymously if needed so security rules can require a signed-in user. */
    suspend fun ensureSignedIn(): String {
        val auth = FirebaseAuth.getInstance()
        auth.currentUser?.let { return it.uid }
        return checkNotNull(auth.signInAnonymously().await().user).uid
    }

    /** Uploads a photo to Firebase Storage, saves its metadata, and returns its id. */
    suspend fun save(jpeg: ByteArray, caption: String, location: Location?): String {
        val uid = ensureSignedIn()
        val photoRef = photos.document()
        val storagePath = "photos/$uid/${photoRef.id}.jpg"
        // Upload the image first so a photo never shows up in the list without its image.
        try {
            storage.child(storagePath)
                .putBytes(jpeg, storageMetadata { contentType = "image/jpeg" })
                .await()
        } catch (e: StorageException) {
            // On upload, "object not found" means the bucket itself is missing.
            if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw e
            throw IllegalStateException(
                "Storage bucket not found. Is Storage enabled in the Firebase console?", e
            )
        }
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

    /** Live list of photo metadata, newest first. Use this for map pins. */
    fun observePhotos(): Flow<List<NaturePhoto>> = callbackFlow {
        try {
            ensureSignedIn()
        } catch (e: Exception) {
            Log.w(TAG, "Sign-in failed; reads may be denied by security rules", e)
        }
        val registration = photos
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Photo listener failed", error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().map { doc ->
                    NaturePhoto(
                        id = doc.id,
                        caption = doc.getString("caption").orEmpty(),
                        latitude = doc.getDouble("latitude"),
                        longitude = doc.getDouble("longitude"),
                        createdAt = doc.getTimestamp("createdAt")?.toDate(),
                        uid = doc.getString("uid"),
                        storagePath = doc.getString("storagePath"),
                    )
                })
            }
        awaitClose { registration.remove() }
    }

    /** Downloads the JPEG bytes for one photo, or null if missing. */
    suspend fun loadImage(photo: NaturePhoto): ByteArray? {
        ensureSignedIn()
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

    /** Deletes a photo's image and metadata. A missing image is ignored. */
    suspend fun delete(photo: NaturePhoto) {
        ensureSignedIn()
        // Delete the image first so a failure leaves the photo listed (and retryable) instead of
        // leaving an orphaned file in Storage.
        val path = photo.storagePath
        if (path != null) {
            try {
                storage.child(path).delete().await()
            } catch (e: StorageException) {
                if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw e
            }
        } else {
            legacyImages.document(photo.id).delete().await()
        }
        photos.document(photo.id).delete().await()
    }
}

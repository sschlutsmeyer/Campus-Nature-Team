package com.example.campusexplorer.data

import android.location.Location
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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
)

/**
 * All photo reads/writes go through here, so swapping Firestore image storage for
 * Firebase Storage later only means changing this file.
 *
 * Layout:
 *  - photos/{id}       caption, latitude, longitude, createdAt, uid  (small; the map lists these)
 *  - photoImages/{id}  data: JPEG bytes, kept under Firestore's 1 MiB document limit
 */
object PhotoRepository {
    private const val TAG = "PhotoRepository"
    private val db get() = FirebaseFirestore.getInstance()
    private val photos get() = db.collection("photos")
    private val images get() = db.collection("photoImages")

    /** Signs in anonymously if needed so security rules can require a signed-in user. */
    suspend fun ensureSignedIn(): String {
        val auth = FirebaseAuth.getInstance()
        auth.currentUser?.let { return it.uid }
        return checkNotNull(auth.signInAnonymously().await().user).uid
    }

    /** Saves a photo and returns its id. [jpeg] must be under ~1 MB (see compressForUpload). */
    suspend fun save(jpeg: ByteArray, caption: String, location: Location?): String {
        val uid = ensureSignedIn()
        val photoRef = photos.document()
        db.batch()
            .set(
                photoRef, mapOf(
                    "caption" to caption,
                    "latitude" to location?.latitude,
                    "longitude" to location?.longitude,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "uid" to uid,
                )
            )
            .set(images.document(photoRef.id), mapOf("data" to Blob.fromBytes(jpeg)))
            .commit()
            .await()
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
                    )
                })
            }
        awaitClose { registration.remove() }
    }

    /** Downloads the JPEG bytes for one photo, or null if missing. */
    suspend fun loadImage(photoId: String): ByteArray? =
        images.document(photoId).get().await().getBlob("data")?.toBytes()
}

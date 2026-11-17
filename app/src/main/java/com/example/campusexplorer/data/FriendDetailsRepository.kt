package com.example.campusexplorer.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Locale

object FriendDetailsRepository {

    private val db
        get() = FirebaseFirestore.getInstance()

    private fun me(): String =
        checkNotNull(
            FirebaseAuth.getInstance()
                .currentUser
                ?.takeIf { !it.isAnonymous }
                ?.uid
        ) {
            "Log in first."
        }

    fun notes(uid: String) = callbackFlow<Map<String, String>> {
        val listener = db.collection("users")
            .document(uid)
            .collection("friendNotes")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else {
                    trySend(
                        snapshot?.documents.orEmpty().associate {
                            it.id to it.getString("text").orEmpty()
                        }
                    )
                }
            }

        awaitClose {
            listener.remove()
        }
    }

    suspend fun saveNote(friend: String, text: String) {
        require(text.length <= 1000) {
            "Notes can be up to 1,000 characters."
        }

        val ref = db.collection("users")
            .document(me())
            .collection("friendNotes")
            .document(friend)

        if (text.isBlank()) {
            ref.delete().await()
        } else {
            ref.set(
                mapOf(
                    "text" to text.trim(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    fun area(uid: String) = callbackFlow<String> {
        val listener = db.collection("peopleAreas")
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else {
                    trySend(snapshot?.getString("area").orEmpty())
                }
            }

        awaitClose {
            listener.remove()
        }
    }

    suspend fun shareArea(area: String) {
        val clean = area.trim()
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.ROOT)

        require(clean.length in 2..80) {
            "Enter a city or campus area (2–80 characters)."
        }

        db.collection("peopleAreas")
            .document(me())
            .set(mapOf("area" to clean))
            .await()
    }

    suspend fun stopSharing() {
        db.collection("peopleAreas")
            .document(me())
            .delete()
            .await()
    }

    fun peopleIn(area: String) = callbackFlow<List<String>> {
        val listener = db.collection("peopleAreas")
            .whereEqualTo("area", area)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else {
                    trySend(
                        snapshot?.documents.orEmpty().map { it.id }
                    )
                }
            }

        awaitClose {
            listener.remove()
        }
    }
}
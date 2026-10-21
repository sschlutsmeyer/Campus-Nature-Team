package com.example.campusexplorer.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Keeps copies of photos on this phone so they open without downloading.
// Files live in the app's private storage (photos/{id}.jpg) and are removed if the app is uninstalled.
object LocalPhotoStore {

    // The file for one photo's local copy.
    private fun file(context: Context, photoId: String) =
        File(File(context.filesDir, "photos"), "$photoId.jpg")

    // Saves a copy of the photo's JPEG bytes.
    suspend fun save(context: Context, photoId: String, jpeg: ByteArray) = withContext(Dispatchers.IO) {
        val file = file(context, photoId)
        file.parentFile?.mkdirs()
        file.writeBytes(jpeg)
    }

    // The saved copy's bytes, or null if this phone doesn't have one.
    suspend fun load(context: Context, photoId: String): ByteArray? = withContext(Dispatchers.IO) {
        file(context, photoId).takeIf { it.exists() }?.readBytes()
    }

    // Removes the saved copy, if there is one.
    suspend fun delete(context: Context, photoId: String) = withContext(Dispatchers.IO) {
        file(context, photoId).delete()
    }
}

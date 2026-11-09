package com.example.campusexplorer.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

// A photo chosen from the phone's camera roll, ready to add to the app.
class PickedPhoto(

    // Upright, shrunk JPEG, ready for PhotoRepository.save().
    val jpeg: ByteArray,

    // Small copy for showing on screen.
    val preview: Bitmap,

    // Where the photo was taken, from its metadata (EXIF). Null if it has none.
    val location: Location?,

    // When the photo was taken, from its metadata. Null if it has none.
    val takenAt: Date?,
)

// Saves photos to, and reads photos from, the phone's camera roll (its shared Pictures folder).
object CameraRoll {

    // Label for this file's messages in Logcat.
    private const val TAG = "CameraRoll"

    // Our photos go in Pictures/Nature Quest.
    private const val ALBUM = "Nature Quest"

    // Largest picked image we'll read (40 MB). Bigger files are almost certainly not photos.
    private const val MAX_PICKED_BYTES = 40L * 1024 * 1024

    // Android 9 and older need storage permission to add to the camera roll. Newer versions don't.
    fun needsWritePermission(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED

    // Android 10+ hides a photo's location from apps without this permission. Ask before picking.
    fun needsMediaLocationPermission(context: Context) =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) !=
            PackageManager.PERMISSION_GRANTED

    // Adds a JPEG to the camera roll, tagged with where it was taken if known. Returns its new address.
    suspend fun save(context: Context, jpeg: ByteArray, location: Location?): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bytes = if (location != null) withLocation(context, jpeg, location) else jpeg
        val now = System.currentTimeMillis()
        val name = "NatureQuest_$now.jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, now)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")

                // Hidden from other apps until it's fully written.
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
                folder.mkdirs()
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATA, File(folder, name).absolutePath)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val uri = checkNotNull(resolver.insert(collection, values)) { "Couldn't add to the camera roll" }
        try {
            checkNotNull(resolver.openOutputStream(uri)) { "Couldn't write to the camera roll" }
                .use { it.write(bytes) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }
        } catch (e: Exception) {

            // Don't leave an empty photo behind.
            resolver.delete(uri, null, null)
            throw e
        }
        uri
    }

    // Reads a photo the user picked: its image (upright and shrunk), location, and date taken.
    suspend fun readPicked(context: Context, uri: Uri): PickedPhoto = withContext(Dispatchers.IO) {
        val raw = readOriginal(context, uri)

        // Metadata: location, date, and which way up the camera was held.
        val exif = try {
            ExifInterface(ByteArrayInputStream(raw))
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read photo metadata", e)
            null
        }
        val location = exif?.latLong?.takeUnless { it[0] == 0.0 && it[1] == 0.0 }?.let { (lat, lng) ->
            Location("exif").apply {
                latitude = lat
                longitude = lng
            }
        }
        val takenAt = exif?.let { parseExifDate(it) }
        val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ?: ExifInterface.ORIENTATION_NORMAL

        // Decode at a reduced size (compressForUpload shrinks it to 2048 anyway), then turn it upright.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "That file isn't a photo the app can read." }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2048) sample *= 2
        val decoded = checkNotNull(
            BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ) { "That file isn't a photo the app can read." }
        val upright = decoded.applyOrientation(orientation)

        val jpeg = upright.compressForUpload()
        val previewScale = minOf(1f, 1024f / max(upright.width, upright.height))
        val preview = if (previewScale < 1f) {
            upright.scale(max(1, (upright.width * previewScale).toInt()), max(1, (upright.height * previewScale).toInt()))
        } else upright

        PickedPhoto(jpeg = jpeg, preview = preview, location = location, takenAt = takenAt)
    }

    // The picked file's bytes. Asks for the unredacted original first, which keeps its location.
    private fun readOriginal(context: Context, uri: Uri): ByteArray {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !needsMediaLocationPermission(context)) {
            try {
                resolver.openInputStream(MediaStore.setRequireOriginal(uri))?.use { return it.readLimited() }
            } catch (e: Exception) {

                // Some pickers don't support originals. The redacted copy still works, just without location.
                Log.i(TAG, "Original not available for $uri; reading it without location", e)
            }
        }
        return checkNotNull(resolver.openInputStream(uri)) { "Couldn't open that photo." }.use { it.readLimited() }
    }

    private fun java.io.InputStream.readLimited(): ByteArray {
        val bytes = readBytes()
        require(bytes.size <= MAX_PICKED_BYTES) { "That photo is too large." }
        return bytes
    }

    // Copies the JPEG with GPS tags added, so the camera roll copy remembers where it was taken.
    private fun withLocation(context: Context, jpeg: ByteArray, location: Location): ByteArray {
        val temp = File.createTempFile("camera_roll", ".jpg", context.cacheDir)
        return try {
            temp.writeBytes(jpeg)
            ExifInterface(temp.absolutePath).apply {
                setLatLong(location.latitude, location.longitude)
                setAttribute(
                    ExifInterface.TAG_DATETIME_ORIGINAL,
                    SimpleDateFormat(EXIF_DATE_FORMAT, Locale.US).format(Date()),
                )
                saveAttributes()
            }
            temp.readBytes()
        } catch (e: Exception) {

            // The photo matters more than its tags.
            Log.w(TAG, "Couldn't add location to the camera roll copy", e)
            jpeg
        } finally {
            temp.delete()
        }
    }

    private const val EXIF_DATE_FORMAT = "yyyy:MM:dd HH:mm:ss"

    private fun parseExifDate(exif: ExifInterface): Date? {
        val text = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            ?: return null
        return try {
            SimpleDateFormat(EXIF_DATE_FORMAT, Locale.US).parse(text)
        } catch (_: ParseException) {
            null
        }
    }

    // Rotates/flips a bitmap the way its EXIF orientation says, so it shows upright.
    private fun Bitmap.applyOrientation(orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return this
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }
}

package com.example.campusexplorer.data

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Converts a captured frame to a Bitmap rotated the way the user was holding the phone. */
fun ImageProxy.toUprightBitmap(): Bitmap {
    val bitmap = toBitmap()
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return bitmap
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/** Shrinks and JPEG-compresses a photo to keep uploads and downloads quick. */
@SuppressLint("UseKtx")
fun Bitmap.compressForUpload(maxDimension: Int = 2048, maxBytes: Int = 2_000_000): ByteArray {
    val scale = maxDimension.toFloat() / max(width, height)
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
    } else this

    var quality = 85
    while (true) {
        val bytes = ByteArrayOutputStream().use {
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, it)
            it.toByteArray()
        }
        if (bytes.size <= maxBytes || quality <= 40) return bytes
        quality -= 10
    }
}

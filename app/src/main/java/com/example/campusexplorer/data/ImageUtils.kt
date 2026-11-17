package com.example.campusexplorer.data

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import kotlin.math.max

// Turns a camera image into a Bitmap, rotated to match how the phone was held.
fun ImageProxy.toUprightBitmap(): Bitmap {
    val bitmap = toBitmap()
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return bitmap
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

// Crops the middle square out of a photo and shrinks it, for a profile picture.
fun Bitmap.toAvatarJpeg(size: Int = 512): ByteArray {
    val side = minOf(width, height)
    val square = Bitmap.createBitmap(this, (width - side) / 2, (height - side) / 2, side, side)
    val scaled = if (side > size) Bitmap.createScaledBitmap(square, size, size, true) else square
    return ByteArrayOutputStream().use {
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, it)
        it.toByteArray()
    }
}

// Shrinks a photo and turns it into a JPEG so uploads and downloads are quick.
@SuppressLint("UseKtx")
fun Bitmap.compressForUpload(maxDimension: Int = 2048, maxBytes: Int = 2_000_000): ByteArray {

    // Scale down so the longest side is at most maxDimension pixels.
    val scale = maxDimension.toFloat() / max(width, height)
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
    } else this

    // Lower the JPEG quality until the file is small enough (but not below 40).
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

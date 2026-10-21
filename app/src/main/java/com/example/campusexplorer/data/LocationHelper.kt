package com.example.campusexplorer.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

// Gets the phone's location for tagging photos.
object LocationHelper {

    // True if either precise or approximate location is allowed.
    fun hasPermission(context: Context) = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ).any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    // The current location, or null if permission is off or the location can't be found.
    @SuppressLint("MissingPermission") // checked by hasPermission()
    suspend fun currentLocation(context: Context): Location? {
        if (!hasPermission(context)) return null
        return try {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .await()
        } catch (e: Exception) {
            Log.w("LocationHelper", "Could not get location", e)
            null
        }
    }
}

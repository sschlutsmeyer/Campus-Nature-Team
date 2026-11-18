package com.example.campusexplorer.ui.learn

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campusexplorer.data.INaturalistRepository
import com.example.campusexplorer.data.LearnSpecies
import com.example.campusexplorer.ui.LightGreen
import com.example.campusexplorer.ui.Turquoise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/** Keep the displayed photo and its credit together, including alternate photos. */
@Composable
internal fun SpeciesPhoto(species: LearnSpecies, imageModifier: Modifier, showCredit: Boolean = true) {
    var photo by remember(species.id, species.photo) { mutableStateOf(species.photo) }
    var loading by remember(species.id, species.photo) { mutableStateOf(species.photo == null) }
    var failed by remember(species.id, species.photo) { mutableStateOf(false) }
    var retry by remember(species.id) { mutableIntStateOf(0) }

    LaunchedEffect(species.id, species.photo, retry) {
        if (species.photo != null) {
            photo = species.photo
            loading = false
            return@LaunchedEffect
        }
        loading = true
        failed = false
        try {
            photo = INaturalistRepository.resolvePhoto(species, refresh = retry > 0)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("SpeciesPhoto", "Alternate photo lookup failed for ${species.id}", e)
            failed = true
        } finally {
            if (currentCoroutineContext().isActive) loading = false
        }
    }

    Column {
        val resolved = photo
        when {
            loading -> Box(imageModifier.background(LightGreen), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Turquoise, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            }
            failed -> Box(imageModifier.background(LightGreen), contentAlignment = Alignment.Center) {
                TextButton(onClick = { retry++ }) { Text("Retry", color = Turquoise, fontSize = 11.sp) }
            }
            else -> LearnImage(if (showCredit) resolved?.imageUrl else resolved?.thumbnailUrl,
                species.name, imageModifier)
        }
        if (showCredit && !loading && !failed) {
            if (resolved != null) PhotoCredit(resolved)
            else Text("No suitable photo found.", color = Color.DarkGray, fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

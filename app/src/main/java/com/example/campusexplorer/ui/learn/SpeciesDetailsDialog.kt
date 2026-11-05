package com.example.campusexplorer.ui.learn

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campusexplorer.data.INaturalistRepository
import com.example.campusexplorer.data.LearnSpecies
import com.example.campusexplorer.ui.LeafGreen
import com.example.campusexplorer.ui.Turquoise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.text.NumberFormat

@Composable
internal fun SpeciesDetailsDialog(id: Long, initial: LearnSpecies?, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    var species by remember(id) { mutableStateOf(initial) }
    var loading by remember(id) { mutableStateOf(true) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var retry by remember(id) { mutableIntStateOf(0) }

    LaunchedEffect(id, retry) {
        loading = true
        error = null
        try {
            species = INaturalistRepository.details(id, refresh = retry > 0)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Couldn't load species information."
        } finally {
            if (currentCoroutineContext().isActive) loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(species?.name ?: "Species details", color = LearnText) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                val detail = species
                if (detail != null) {
                    SpeciesPhoto(detail,
                        Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(12.dp)))
                    Text(detail.scientificName, color = LearnText, fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(top = 8.dp))
                    Text(detail.groupName, color = LeafGreen, modifier = Modifier.padding(vertical = 6.dp))
                    Text("${NumberFormat.getIntegerInstance().format(detail.observationsCount)} observations on iNaturalist",
                        color = Color.DarkGray, fontSize = 12.sp)
                    if (detail.classification.isNotEmpty()) {
                        Text("Classification", color = LearnText, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp))
                        detail.classification.forEach { Text(it, color = Color.DarkGray, fontSize = 12.sp) }
                    }
                    if (!loading) SpeciesQuickFacts(detail)
                }
                if (loading) CircularProgressIndicator(color = Turquoise,
                    modifier = Modifier.padding(top = 12.dp).size(24.dp))
                error?.let {
                    Text(it, color = LearnText, modifier = Modifier.padding(top = 12.dp))
                    TextButton(onClick = { retry++ }) { Text("Try again", color = Turquoise) }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { runCatching { uriHandler.openUri("https://www.inaturalist.org/taxa/$id") } }) {
                Text("iNaturalist", color = Turquoise)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Turquoise) } },
    )
}

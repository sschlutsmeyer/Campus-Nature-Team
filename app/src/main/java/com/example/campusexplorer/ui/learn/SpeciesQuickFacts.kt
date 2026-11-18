package com.example.campusexplorer.ui.learn

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campusexplorer.data.INaturalistRepository
import com.example.campusexplorer.data.LearnFacts
import com.example.campusexplorer.data.LearnSpecies
import com.example.campusexplorer.ui.Turquoise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

@Composable
internal fun SpeciesQuickFacts(species: LearnSpecies) {
    val uriHandler = LocalUriHandler.current
    var facts by remember(species.id) { mutableStateOf<LearnFacts?>(null) }
    var loading by remember(species.id) { mutableStateOf(true) }
    var failed by remember(species.id) { mutableStateOf(false) }
    var retry by remember(species.id) { mutableIntStateOf(0) }

    LaunchedEffect(species.id, species.descriptionHtml, species.wikipediaUrl, retry) {
        loading = true
        failed = false
        try {
            facts = INaturalistRepository.facts(species)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("SpeciesQuickFacts", "Species summary unavailable", e)
            failed = true
        } finally {
            if (currentCoroutineContext().isActive) loading = false
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        when {
            loading -> CircularProgressIndicator(color = Turquoise,
                modifier = Modifier.padding(top = 8.dp).size(18.dp), strokeWidth = 2.dp)
            failed -> {
                Text("Facts couldn't be loaded.", color = Color.DarkGray, fontSize = 12.sp)
                TextButton(onClick = { retry++ }, contentPadding = PaddingValues(0.dp)) {
                    Text("Retry facts", color = Turquoise, fontSize = 12.sp)
                }
            }
            facts?.sentences.isNullOrEmpty() -> Text("No short facts are available for this species yet.",
                color = Color.DarkGray, fontSize = 12.sp)
            else -> {
                val loaded = facts
                if (loaded != null) {
                    loaded.sentences.forEach { sentence ->
                        Row(Modifier.padding(top = 6.dp)) {
                            Text("•", color = LearnText, modifier = Modifier.padding(end = 6.dp))
                            Text(sentence, color = LearnText, fontSize = 13.sp, lineHeight = 19.sp)
                        }
                    }
                    TextButton(onClick = { runCatching { uriHandler.openUri(loaded.sourceUrl) } },
                        contentPadding = PaddingValues(0.dp)) {
                        Text("Source: Wikipedia", color = Turquoise, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

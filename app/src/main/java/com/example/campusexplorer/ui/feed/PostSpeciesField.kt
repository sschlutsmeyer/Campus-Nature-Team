package com.example.campusexplorer.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campusexplorer.data.*
import com.example.campusexplorer.ui.LightGreen
import com.example.campusexplorer.ui.Turquoise
import com.example.campusexplorer.ui.learn.SpeciesPhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

// Only primitive fields go into Android's saved state, so selections survive rotation.
internal val PostIdentificationStateSaver = listSaver<MutableState<PostIdentification?>, Any>(
    save = { state ->
        state.value?.let {
            listOf(it.taxonId, it.commonName, it.scientificName, it.category, it.confidence.name)
        } ?: listOf(false)
    },
    restore = { fields ->
        mutableStateOf<PostIdentification?>(
            if (fields.size != 5) null else PostIdentification.fromFields(
                mapOf("taxonId" to fields[0], "commonName" to fields[1],
                    "scientificName" to fields[2], "confidence" to fields[4]),
                fields[3] as? String ?: "Other",
            ),
        )
    },
)

@Composable
internal fun PostSpeciesFields(
    identification: PostIdentification?,
    onIdentificationChange: (PostIdentification?) -> Unit,
    onPendingSearchChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<LearnSpecies>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }

    // Keep posting disabled until typed search text is selected or explicitly cleared.
    LaunchedEffect(query) { onPendingSearchChange(query.isNotBlank()) }
    DisposableEffect(Unit) { onDispose { onPendingSearchChange(false) } }
    LaunchedEffect(query, retry) {
        results = emptyList()
        error = null
        loading = false
        if (query.trim().length < 2) return@LaunchedEffect
        loading = true
        try {
            delay(500)
            val response = INaturalistRepository.search(
                query = query.trim(), region = LearnRegion.ALL, group = LearnGroup.ALL,
                refresh = retry > 0,
            )
            if (currentCoroutineContext().isActive) results = response.species.take(8)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Couldn't search species. Check your connection and try again."
        } finally {
            if (currentCoroutineContext().isActive) loading = false
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("What species do you think you found?", color = FeedText,
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("Optional — search by common or scientific name.", color = FeedMuted, fontSize = 12.sp)
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it.take(200)
                results = emptyList()
                error = null
                loading = query.trim().length >= 2
                onPendingSearchChange(query.isNotBlank())
            },
            label = { Text(if (identification == null) "Search species" else "Search for a different species") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = feedFieldColors(),
            trailingIcon = {
                if (query.isNotEmpty()) TextButton(enabled = enabled, onClick = {
                    query = ""
                    results = emptyList()
                    onPendingSearchChange(false)
                }) { Text("Clear", color = Turquoise) }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        )

        when {
            query.isBlank() -> Unit
            query.trim().length < 2 -> Text("Enter at least 2 characters.", color = FeedMuted, fontSize = 12.sp)
            loading -> Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Turquoise, strokeWidth = 2.dp)
                Text("Searching species…", color = FeedMuted, fontSize = 12.sp)
            }
            error != null -> {
                Text(error.orEmpty(), color = FeedMuted, fontSize = 12.sp)
                TextButton(enabled = enabled, onClick = { retry++ }) { Text("Try again", color = Turquoise) }
            }
            results.isEmpty() -> Text("No matching species. Try another name or clear the search to skip.",
                color = FeedMuted, fontSize = 12.sp)
            else -> {
                Text("Select a species to tag your post", color = FeedMuted, fontSize = 12.sp)
                results.forEach { species ->
                    Surface(
                        onClick = {
                            onIdentificationChange(PostIdentification.fromSpecies(species))
                            query = ""
                            results = emptyList()
                            onPendingSearchChange(false)
                            focus.clearFocus()
                        },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                    ) {
                        Row(Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            SpeciesPhoto(
                                species = species,
                                imageModifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
                                showCredit = false,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(species.name, color = FeedText, fontWeight = FontWeight.SemiBold)
                                Text(species.scientificName, color = FeedMuted,
                                    fontStyle = FontStyle.Italic, fontSize = 12.sp)
                                Text(PostIdentification.fromSpecies(species).category,
                                    color = Turquoise, fontSize = 12.sp)
                            }
                            Text("+", color = Turquoise, fontSize = 22.sp)
                        }
                    }
                    HorizontalDivider(color = LightGreen)
                }
                Text("Type more of the name to narrow the results.", color = FeedMuted, fontSize = 12.sp)
            }
        }

        if (identification != null) {
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                color = LightGreen, border = BorderStroke(1.dp, Turquoise)) {
                Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(identification.commonName, color = FeedText, fontWeight = FontWeight.SemiBold)
                        Text(identification.scientificName, color = FeedMuted,
                            fontSize = 12.sp, fontStyle = FontStyle.Italic)
                        Text("Feed category: ${identification.category}", color = Turquoise, fontSize = 12.sp)
                    }
                    IconButton(enabled = enabled, onClick = { onIdentificationChange(null) }) {
                        Icon(CloseIcon, contentDescription = "Remove species identification", tint = FeedMuted)
                    }
                }
            }
            Text("Your confidence: ${identification.confidence.label}",
                color = FeedText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Slider(
                value = identification.confidence.ordinal.toFloat(),
                onValueChange = { value ->
                    onIdentificationChange(identification.copy(
                        confidence = IdentificationConfidence.entries[value.roundToInt().coerceIn(0, 2)],
                    ))
                },
                valueRange = 0f..2f,
                steps = 1,
                enabled = enabled,
                colors = SliderDefaults.colors(thumbColor = Turquoise, activeTrackColor = Turquoise),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Not certain", color = FeedMuted, fontSize = 12.sp)
                Text("Certain", color = FeedMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
internal fun PostIdentificationSummary(identification: PostIdentification) {
    val confidence = identification.confidence.label.replaceFirstChar { it.lowercaseChar() }
    Text(
        text = "Species: ${identification.commonName} · Confidence: $confidence",
        color = FeedText,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
    )
}

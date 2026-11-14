package com.example.campusexplorer.ui.learn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campusexplorer.data.INaturalistRepository
import com.example.campusexplorer.data.LearnGroup
import com.example.campusexplorer.data.LearnPlace
import com.example.campusexplorer.data.LearnSort
import com.example.campusexplorer.ui.LightGreen
import com.example.campusexplorer.ui.Turquoise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LearnFilters(
    sort: LearnSort,
    placeId: Long?,
    placeName: String,
    group: LearnGroup,
    onDismiss: () -> Unit,
    onApply: (LearnSort, Long?, String, LearnGroup) -> Unit,
) {
    val focus = LocalFocusManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draftSort by rememberSaveable { mutableStateOf(sort) }
    var draftPlaceId by rememberSaveable { mutableStateOf(placeId) }
    var draftPlaceName by rememberSaveable { mutableStateOf(placeName) }
    var draftGroup by rememberSaveable { mutableStateOf(group) }
    var locationQuery by rememberSaveable { mutableStateOf("") }
    var locations by remember { mutableStateOf<List<LearnPlace>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(locationQuery, retry) {
        locations = emptyList()
        searchError = null
        searching = false
        if (locationQuery.trim().length < 2) return@LaunchedEffect
        searching = true
        try {
            delay(500)
            val result = INaturalistRepository.searchPlaces(locationQuery, refresh = retry > 0)
            // An older search must not replace results for the current text.
            if (currentCoroutineContext().isActive) locations = result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            searchError = "Couldn't search locations. Check your connection and try again."
        } finally {
            if (currentCoroutineContext().isActive) searching = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(0.92f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color.White,
        contentColor = LearnText,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                "Filter species",
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp),
                color = LearnText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilterHeading("Sort by")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LearnSort.entries.forEach { choice ->
                        ChoiceChip(
                            label = if (choice == LearnSort.AZ) "A–Z" else "Z–A",
                            selected = draftSort == choice,
                            onClick = { draftSort = choice },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                FilterHeading("Location")
                Surface(
                    onClick = {
                        draftPlaceId = null
                        draftPlaceName = ""
                        locationQuery = ""
                        locations = emptyList()
                        focus.clearFocus()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = if (draftPlaceId == null) LightGreen else Color.White,
                    border = BorderStroke(1.dp, if (draftPlaceId == null) Turquoise else Color.LightGray),
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Worldwide", color = LearnText, fontWeight = FontWeight.SemiBold)
                            Text("Explore species everywhere", color = Color.DarkGray, fontSize = 12.sp)
                        }
                        RadioButton(
                            selected = draftPlaceId == null,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = Turquoise),
                        )
                    }
                }

                if (draftPlaceId != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = LightGreen,
                        border = BorderStroke(1.dp, Turquoise),
                    ) {
                        Row(
                            Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Selected location", color = LearnText, fontSize = 11.sp)
                                Text(draftPlaceName, color = LearnText, fontWeight = FontWeight.SemiBold)
                            }
                            TextButton(onClick = {
                                draftPlaceId = null
                                draftPlaceName = ""
                                locationQuery = ""
                                locations = emptyList()
                            }) { Text("Clear", color = Turquoise) }
                        }
                    }
                }

                OutlinedTextField(
                    value = locationQuery,
                    onValueChange = {
                        locationQuery = it
                        // Clear stale choices immediately, before the next effect starts.
                        locations = emptyList()
                        searchError = null
                        searching = it.trim().length >= 2
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search location") },
                    placeholder = { Text("City, county, state, or park") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        if (locationQuery.isNotEmpty()) {
                            TextButton(onClick = { locationQuery = ""; locations = emptyList() }) {
                                Text("Clear", color = Turquoise)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Turquoise,
                        cursorColor = Turquoise,
                        focusedLabelColor = Turquoise,
                        focusedTextColor = LearnText,
                        unfocusedTextColor = LearnText,
                    ),
                )

                when {
                    locationQuery.isBlank() -> Unit
                    locationQuery.trim().length < 2 -> {
                        Text("Enter at least 2 characters.", color = Color.DarkGray, fontSize = 12.sp)
                    }
                    searching -> {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp), color = Turquoise, strokeWidth = 2.dp,
                            )
                            Text("Searching locations…", color = Color.DarkGray, fontSize = 12.sp)
                        }
                    }
                    searchError != null -> {
                        Text(searchError.orEmpty(), color = Color.DarkGray, fontSize = 12.sp)
                        TextButton(onClick = { retry++ }) { Text("Try again", color = Turquoise) }
                    }
                    locations.isEmpty() -> {
                        Text("No matching locations. Try a different place name.",
                            color = Color.DarkGray, fontSize = 12.sp)
                    }
                    else -> {
                        Text("Choose a location", color = Color.DarkGray, fontSize = 12.sp)
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                            items(locations, key = { it.id }) { place ->
                                Surface(
                                    onClick = {
                                        draftPlaceId = place.id
                                        draftPlaceName = place.displayName
                                        locationQuery = ""
                                        locations = emptyList()
                                        focus.clearFocus()
                                    },
                                    color = Color.White,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(place.displayName, modifier = Modifier.weight(1f),
                                            color = LearnText, fontSize = 14.sp)
                                        Text("›", color = Turquoise, fontSize = 24.sp,
                                            modifier = Modifier.padding(start = 8.dp))
                                    }
                                }
                                HorizontalDivider(color = LightGreen)
                            }
                        }
                    }
                }

                FilterHeading("Species group")
                LearnGroup.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { choice ->
                            ChoiceChip(
                                label = choice.label,
                                selected = draftGroup == choice,
                                onClick = { draftGroup = choice },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            HorizontalDivider(color = LightGreen)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    draftSort = LearnSort.AZ
                    draftPlaceId = null
                    draftPlaceName = ""
                    draftGroup = LearnGroup.ALL
                    locationQuery = ""
                    locations = emptyList()
                    focus.clearFocus()
                }) { Text("Reset", color = Turquoise) }
                Button(
                    onClick = {
                        focus.clearFocus()
                        onApply(draftSort, draftPlaceId, draftPlaceName, draftGroup)
                    },
                    // A typed place must be selected from results or cleared first.
                    enabled = locationQuery.isBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Turquoise),
                ) { Text("Apply") }
            }
        }
    }
}

@Composable
private fun FilterHeading(label: String) {
    Text(label, modifier = Modifier.padding(top = 4.dp), color = LearnText,
        fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Box(Modifier.fillMaxWidth().padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
                Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        },
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (selected) Turquoise else Color.LightGray),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.White,
            labelColor = LearnText,
            selectedContainerColor = LightGreen,
            selectedLabelColor = LearnText,
        ),
    )
}

package com.example.campusexplorer.ui.learn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.campusexplorer.data.LearnGroup
import com.example.campusexplorer.data.LearnRegion
import com.example.campusexplorer.data.LearnSampleData
import com.example.campusexplorer.data.LearnSort
import com.example.campusexplorer.data.LearnSpecies
import com.example.campusexplorer.ui.LeafGreen
import com.example.campusexplorer.ui.LightGreen
import com.example.campusexplorer.ui.Turquoise
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.Collator

private val LearnText = Color(0xFF294C39)

@Composable
fun LearnScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val focus = LocalFocusManager.current

    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(true) }

    var sort by rememberSaveable {
        mutableStateOf(LearnSort.AZ)
    }

    var region by rememberSaveable {
        mutableStateOf(LearnRegion.ALL)
    }

    var group by rememberSaveable {
        mutableStateOf(LearnGroup.ALL)
    }

    var showFilters by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    var daily by remember {
        mutableStateOf(LearnSampleData.dailySpecies(context))
    }

    // Check for a new day while this screen is visible.
    LaunchedEffect(lifecycle, context) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                daily = LearnSampleData.dailySpecies(context)
                delay(60_000)
            }
        }
    }

    val collator = remember { Collator.getInstance() }

    // Search and filter the sample entries, then sort the results.
    val visibleSpecies = remember(query, sort, region, group) {
        val search = query.trim()

        val matching = LearnSampleData.species.filter {
            (region == LearnRegion.ALL || region in it.sampleRegions) &&
                    (group == LearnGroup.ALL || group == it.group) &&
                    (
                            it.name.contains(search, ignoreCase = true) ||
                                    it.scientificName.contains(search, ignoreCase = true)
                            )
        }.sortedWith(
            Comparator { a, b ->
                collator.compare(a.name, b.name)
            }
        )

        if (sort == LearnSort.ZA) {
            matching.reversed()
        } else {
            matching
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(12.dp)
    ) {
        Text(
            text = "Learn",
            color = LearnText,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Explore the natural world",
            color = Turquoise,
            fontSize = 14.sp
        )

        Text(
            text = "Sample species",
            color = Color.Gray,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Hide the daily species immediately when typing begins.
            if (query.isEmpty()) {
                item(key = "daily") {
                    DailySpeciesCard(
                        species = daily,
                        expanded = expanded,
                        onToggle = { expanded = !expanded },
                        onOpen = {
                            focus.clearFocus()
                            selectedId = daily.id
                        }
                    )
                }
            }

            item(key = "heading") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (query.isEmpty()) {
                            "All species"
                        } else {
                            "Search results"
                        },
                        modifier = Modifier.weight(1f),
                        color = LearnText,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    TextButton(
                        onClick = {
                            focus.clearFocus()
                            showFilters = true
                        }
                    ) {
                        Text("Filters", color = Turquoise)
                    }
                }

                Text(
                    text = "${sort.label} · ${region.label} · ${group.label}",
                    color = Color.DarkGray,
                    fontSize = 12.sp
                )
            }

            if (visibleSpecies.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "No sample species match. Try another search or reset the filters.",
                        color = LearnText,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }

            items(
                items = visibleSpecies,
                key = { it.id }
            ) { species ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "View ${species.name}"
                        ) {
                            focus.clearFocus()
                            selectedId = species.id
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                LightGreen,
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = species.illustration,
                            fontSize = 28.sp
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = species.name,
                            color = LearnText,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = species.scientificName,
                            color = Color.DarkGray,
                            fontSize = 12.sp,
                            fontStyle = FontStyle.Italic
                        )
                    }

                    Text(
                        text = "›",
                        color = Turquoise,
                        fontSize = 24.sp
                    )
                }

                HorizontalDivider(color = LightGreen)
            }
        }

        // Pinned below the list. The shared app layout handles the keyboard.
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search species...") },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            trailingIcon = {
                if (query.isNotEmpty()) {
                    TextButton(onClick = { query = "" }) {
                        Text("Clear", color = Turquoise)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = { focus.clearFocus() }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = LearnText,
                unfocusedTextColor = LearnText,
                focusedBorderColor = Turquoise,
                unfocusedBorderColor = Turquoise,
                cursorColor = Turquoise,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )
    }

    if (showFilters) {
        LearnFilters(
            sort = sort,
            region = region,
            group = group,
            onDismiss = { showFilters = false },
            onApply = { newSort, newRegion, newGroup ->
                sort = newSort
                region = newRegion
                group = newGroup
                showFilters = false
            }
        )
    }

    // Temporary details dialog for the sample entries.
    LearnSampleData.species
        .firstOrNull { it.id == selectedId }
        ?.let { species ->
            AlertDialog(
                onDismissRequest = { selectedId = null },
                containerColor = Color.White,
                title = {
                    Text(species.name, color = LearnText)
                },
                text = {
                    Column {
                        Text(
                            species.illustration,
                            fontSize = 64.sp
                        )

                        Text(
                            species.scientificName,
                            color = LearnText,
                            fontStyle = FontStyle.Italic
                        )

                        Text(
                            species.group.label,
                            color = LeafGreen,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        Text(
                            text = "This is a sample entry. Species photos and information will be added when iNaturalist is connected.",
                            color = Color.DarkGray
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedId = null }) {
                        Text("Close", color = Turquoise)
                    }
                }
            )
        }
}

@Composable
private fun DailySpeciesCard(
    species: LearnSpecies,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = LightGreen
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (expanded) {
                        "Minimize daily species"
                    } else {
                        "Expand daily species"
                    },
                    onClick = onToggle
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Species of the Day",
                modifier = Modifier.weight(1f),
                color = LeafGreen,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (expanded) "⌃" else "⌄",
                color = Turquoise,
                fontSize = 24.sp
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "View ${species.name}",
                        onClick = onOpen
                    )
                    .padding(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(105.dp)
                        .background(
                            Color.White,
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = species.illustration,
                        fontSize = 64.sp
                    )
                }

                Text(
                    text = species.name,
                    color = LearnText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Text(
                    text = species.scientificName,
                    color = LearnText,
                    fontStyle = FontStyle.Italic,
                    fontSize = 13.sp
                )

                Text(
                    text = "Discover something new each day.",
                    color = LeafGreen,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LearnFilters(
    sort: LearnSort,
    region: LearnRegion,
    group: LearnGroup,
    onDismiss: () -> Unit,
    onApply: (LearnSort, LearnRegion, LearnGroup) -> Unit
) {
    // Changes stay here until Apply is pressed.
    var draftSort by rememberSaveable { mutableStateOf(sort) }
    var draftRegion by rememberSaveable { mutableStateOf(region) }
    var draftGroup by rememberSaveable { mutableStateOf(group) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Filter species",
                color = LearnText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            FilterMenu(
                title = "Sort by",
                choices = LearnSort.entries.map { it.label },
                selected = draftSort.ordinal
            ) {
                draftSort = LearnSort.entries[it]
            }

            FilterMenu(
                title = "Region",
                choices = LearnRegion.entries.map { it.label },
                selected = draftRegion.ordinal
            ) {
                draftRegion = LearnRegion.entries[it]
            }

            FilterMenu(
                title = "Species group",
                choices = LearnGroup.entries.map { it.label },
                selected = draftGroup.ordinal
            ) {
                draftGroup = LearnGroup.entries[it]
            }

            Text(
                text = "Region filters use sample tags for now.",
                color = Color.DarkGray,
                fontSize = 12.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = {
                        draftSort = LearnSort.AZ
                        draftRegion = LearnRegion.ALL
                        draftGroup = LearnGroup.ALL
                    }
                ) {
                    Text("Reset", color = Turquoise)
                }

                Button(
                    onClick = {
                        onApply(draftSort, draftRegion, draftGroup)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Turquoise
                    )
                ) {
                    Text("Apply", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun FilterMenu(
    title: String,
    choices: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    var open by remember { mutableStateOf(false) }

    Column {
        Text(
            text = title,
            color = LearnText,
            fontWeight = FontWeight.SemiBold
        )

        Box {
            OutlinedButton(
                onClick = { open = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(choices[selected], color = Turquoise)
            }

            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false }
            ) {
                choices.forEachIndexed { index, label ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSelect(index)
                            open = false
                        }
                    )
                }
            }
        }
    }
}
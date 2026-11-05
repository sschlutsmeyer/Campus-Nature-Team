package com.example.campusexplorer.ui.learn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.campusexplorer.data.*
import com.example.campusexplorer.ui.LeafGreen
import com.example.campusexplorer.ui.LightGreen
import com.example.campusexplorer.ui.Turquoise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.NumberFormat

internal val LearnText = Color(0xFF294C39)

@Composable
fun LearnScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(true) }
    var sort by rememberSaveable { mutableStateOf(LearnSort.AZ) }
    var region by rememberSaveable { mutableStateOf(LearnRegion.ALL) }
    var group by rememberSaveable { mutableStateOf(LearnGroup.ALL) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var rows by remember { mutableStateOf<List<LearnSpecies>>(emptyList()) }
    var total by remember { mutableLongStateOf(0) }
    var page by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var moreJob by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableIntStateOf(0) }

    var day by remember { mutableStateOf(INaturalistRepository.todayKey()) }
    var daily by remember { mutableStateOf<LearnSpecies?>(null) }
    var dailyLoading by remember { mutableStateOf(true) }
    var dailyError by remember { mutableStateOf<String?>(null) }
    var dailyRetry by remember { mutableIntStateOf(0) }

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                day = INaturalistRepository.todayKey()
                delay(30_000)
            }
        }
    }
    LaunchedEffect(day, dailyRetry) {
        daily = null
        dailyLoading = true
        dailyError = null
        try {
            daily = INaturalistRepository.dailySpecies(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            dailyError = e.message ?: "Couldn't load today's species."
        } finally {
            if (currentCoroutineContext().isActive) dailyLoading = false
        }
    }

    // Cancelling the effect prevents older searches from replacing newer results.
    LaunchedEffect(query, region, group, retry) {
        moreJob?.cancel()
        generation++
        loadingMore = false
        loading = true
        error = null
        rows = emptyList()
        total = 0
        page = 0
        hasMore = false
        listState.scrollToItem(0)
        try {
            if (query.isNotBlank()) delay(500)
            val result = INaturalistRepository.search(query, region, group)
            rows = result.species
            total = result.total
            hasMore = result.hasMore
            page = 1
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Couldn't load species. Check your connection and try again."
        } finally {
            if (currentCoroutineContext().isActive) loading = false
        }
    }

    fun loadMore() {
        if (loading || loadingMore || !hasMore) return
        val requestGeneration = generation
        val nextPage = page + 1
        val search = query
        val requestRegion = region
        val requestGroup = group
        loadingMore = true
        error = null
        moreJob = scope.launch {
            try {
                val result = INaturalistRepository.search(search, requestRegion, requestGroup, nextPage)
                if (generation == requestGeneration) {
                    rows = (rows + result.species).distinctBy { it.id }
                    total = result.total
                    hasMore = result.hasMore
                    page = nextPage
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == requestGeneration) error = e.message ?: "Couldn't load more species."
            } finally {
                if (generation == requestGeneration) loadingMore = false
            }
        }
    }

    val sorted = remember(rows, sort) { sortLearnSpecies(rows, sort) }
    val formattedTotal = NumberFormat.getIntegerInstance().format(total)
    Column(modifier.fillMaxSize().background(Color.White).padding(12.dp)) {
        Text("Learn", color = LearnText, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Explore the natural world", color = Turquoise, fontSize = 14.sp)
        Text("Species data from iNaturalist", color = Color.DarkGray, fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))

        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (query.isEmpty()) {
                item(key = "daily") {
                    DailySpeciesCard(daily, expanded, dailyLoading, dailyError,
                        onToggle = { expanded = !expanded },
                        onRetry = { dailyRetry++ },
                        onOpen = { focus.clearFocus(); selectedId = it.id })
                }
            }
            item(key = "heading") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (query.isEmpty()) "Browse species" else "Search results",
                        modifier = Modifier.weight(1f), color = LearnText,
                        fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { focus.clearFocus(); showFilters = true }) {
                        Text("Filters", color = Turquoise)
                    }
                }
                Text("${sort.label} · ${region.label} · ${group.label}", color = Color.DarkGray, fontSize = 12.sp)
                if (!loading) {
                    Text("${rows.size} loaded · $formattedTotal matches", color = Color.DarkGray, fontSize = 11.sp)
                }
                Text("Alphabetical order applies to loaded species. Load more or search to narrow the list.",
                    color = Color.DarkGray, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                if (region != LearnRegion.ALL || group != LearnGroup.ALL) {
                    Text(if (region == LearnRegion.CAMPUS) "Recorded within 5 km of CSU Channel Islands."
                    else "Species with verifiable iNaturalist observations matching these filters.",
                        color = LeafGreen, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (loading) {
                item(key = "loading") { LoadingSpecies() }
            } else if (rows.isEmpty() && error == null) {
                item(key = "empty") {
                    Text("No species found. Try another name or reset the filters.", color = LearnText,
                        modifier = Modifier.padding(vertical = 20.dp))
                }
            }
            items(sorted, key = { "species:${it.id}" }) { species ->
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button,
                    onClickLabel = "View ${species.name}") {
                    focus.clearFocus()
                    selectedId = species.id
                }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SpeciesPhoto(species, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)), showCredit = false)
                    Column(Modifier.weight(1f)) {
                        Text(species.name, color = LearnText, fontWeight = FontWeight.SemiBold)
                        Text(species.scientificName, color = Color.DarkGray,
                            fontSize = 12.sp, fontStyle = FontStyle.Italic)
                    }
                    Text("›", color = Turquoise, fontSize = 24.sp)
                }
                HorizontalDivider(color = LightGreen)
            }
            if (error != null) {
                item(key = "error") {
                    Text(error.orEmpty(), color = LearnText)
                    TextButton(onClick = { if (page == 0) retry++ else loadMore() }) {
                        Text("Try again", color = Turquoise)
                    }
                }
            }
            if (loadingMore) {
                item(key = "loadingMore") { LoadingSpecies() }
            } else if (!loading && hasMore && error == null) {
                item(key = "more") {
                    OutlinedButton(onClick = { loadMore() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Load more species", color = Turquoise)
                    }
                }
            } else if (!loading && error == null && total > rows.size && !hasMore) {
                item(key = "limit") { Text("Narrow your search or filters to explore more matches.",
                    fontSize = 12.sp, color = Color.DarkGray) }
            }
        }

        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search species...") }, singleLine = true, shape = RoundedCornerShape(24.dp),
            trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) {
                Text("Clear", color = Turquoise)
            } }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = LearnText, unfocusedTextColor = LearnText,
                focusedBorderColor = Turquoise, unfocusedBorderColor = Turquoise, cursorColor = Turquoise,
                focusedContainerColor = Color.White, unfocusedContainerColor = Color.White))
    }
    if (showFilters) {
        LearnFilters(sort, region, group, onDismiss = { showFilters = false },
            onApply = { newSort, newRegion, newGroup ->
                sort = newSort
                region = newRegion
                group = newGroup
                showFilters = false
            })
    }
    selectedId?.let { id ->
        val initial = rows.firstOrNull { it.id == id } ?: daily?.takeIf { it.id == id }
        SpeciesDetailsDialog(id, initial, onDismiss = { selectedId = null })
    }
}

@Composable
private fun LoadingSpecies() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Turquoise, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun DailySpeciesCard(species: LearnSpecies?, expanded: Boolean, loading: Boolean, error: String?,
                             onToggle: () -> Unit, onRetry: () -> Unit, onOpen: (LearnSpecies) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = LightGreen), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button,
            onClickLabel = if (expanded) "Minimize daily species" else "Expand daily species",
            onClick = onToggle).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Species of the Day", Modifier.weight(1f), color = LeafGreen, fontWeight = FontWeight.Bold)
            Text(if (expanded) "⌃" else "⌄", color = Turquoise, fontSize = 24.sp)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                when {
                    loading -> LoadingSpecies()
                    error != null -> {
                        Text(error, color = LearnText)
                        TextButton(onClick = onRetry) { Text("Try again", color = Turquoise) }
                    }
                    species != null -> {
                        Column(Modifier.fillMaxWidth().clickable(role = Role.Button,
                            onClickLabel = "View ${species.name}", onClick = { onOpen(species) })) {
                            SpeciesPhoto(species,
                                Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(12.dp)))
                            Text(species.name, color = LearnText, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp))
                            Text(species.scientificName, color = LearnText, fontStyle = FontStyle.Italic, fontSize = 13.sp)
                        }
                        SpeciesQuickFacts(species)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PhotoCredit(photo: LearnPhoto) {
    val uriHandler = LocalUriHandler.current
    var showCredit by remember(photo.id) { mutableStateOf(false) }
    TextButton(onClick = { showCredit = true }, contentPadding = PaddingValues(0.dp)) {
        Text("Photo credit", color = Turquoise, fontSize = 11.sp)
    }
    if (showCredit) {
        AlertDialog(
            onDismissRequest = { showCredit = false }, containerColor = Color.White,
            title = { Text("Photo credit", color = LearnText) },
            text = { Column {
                Text(photo.attribution, color = LearnText)
                Text("License: ${photo.license}", color = Color.DarkGray, modifier = Modifier.padding(top = 8.dp))
                Text("Display: cropped to fit.", color = Color.DarkGray, modifier = Modifier.padding(top = 8.dp))
            } },
            dismissButton = { TextButton(onClick = { runCatching { uriHandler.openUri(photo.pageUrl) } }) {
                Text("View source", color = Turquoise)
            } },
            confirmButton = { TextButton(onClick = { showCredit = false }) { Text("Close", color = Turquoise) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LearnFilters(sort: LearnSort, region: LearnRegion, group: LearnGroup, onDismiss: () -> Unit,
                         onApply: (LearnSort, LearnRegion, LearnGroup) -> Unit) {
    var draftSort by rememberSaveable { mutableStateOf(sort) }
    var draftRegion by rememberSaveable { mutableStateOf(region) }
    var draftGroup by rememberSaveable { mutableStateOf(group) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
            .padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Filter species", color = LearnText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            FilterMenu("Sort by", LearnSort.entries.map { it.label }, draftSort.ordinal) { draftSort = LearnSort.entries[it] }
            FilterMenu("Region", LearnRegion.entries.map { it.label }, draftRegion.ordinal) { draftRegion = LearnRegion.entries[it] }
            FilterMenu("Species group", LearnGroup.entries.map { it.label }, draftGroup.ordinal) { draftGroup = LearnGroup.entries[it] }
            Text("A–Z and Z–A sort the species loaded so far. Region and group filters use iNaturalist observation records.",
                color = Color.DarkGray, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { draftSort = LearnSort.AZ; draftRegion = LearnRegion.ALL; draftGroup = LearnGroup.ALL }) {
                    Text("Reset", color = Turquoise)
                }
                Button(onClick = { onApply(draftSort, draftRegion, draftGroup) },
                    colors = ButtonDefaults.buttonColors(containerColor = Turquoise)) { Text("Apply", color = Color.White) }
            }
        }
    }
}

@Composable
private fun FilterMenu(title: String, choices: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(title, color = LearnText, fontWeight = FontWeight.SemiBold)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(choices[selected], color = Turquoise) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                choices.forEachIndexed { index, label ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(index); open = false })
                }
            }
        }
    }
}

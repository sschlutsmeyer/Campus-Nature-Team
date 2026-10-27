package com.example.campusexplorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Every feature reachable from the home screen.
// To add a new feature: add an entry here, then show its screen in MainActivity.
enum class Feature(
    val title: String,
    val emoji: String,
    val subtitle: String,
    val keywords: List<String> = emptyList()
) {
    Map("Map", "🗺️", "Explore nature", listOf("pins", "location", "campus")),
    Camera("Camera", "📷", "Discover your next nature find", listOf("photo", "picture", "cam")),
    Feed("COMING SOON Feed", "📰", "See what others have found", listOf("posts", "social")),
    Friends("COMING SOON Friends", "👥", "Explore with your friends", listOf("people", "social")),
    Gallery("COMING SOON Gallery", "🖼️", "Browse your nature photos", listOf("photos", "pictures")),
    Learn("COMING SOON Learn", "📚", "Learn about local plants and wildlife", listOf("plants", "animals", "wildlife")),
}

// The home screen: logo, welcome, a search bar, and a scrollable 2-column grid of features.
@Composable
fun HomeScreen(
    onFeatureSelected: (Feature) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    // Features whose title or keywords contain the search text.
    val features = Feature.entries.filter { feature ->
        val search = query.trim()
        search.isEmpty() ||
            feature.title.contains(search, ignoreCase = true) ||
            feature.keywords.any { it.contains(search, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        QuestHeader()

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Welcome, [Insert User]",
            fontFamily = FontFamily.Serif,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = LeafGreen,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search features") },
            leadingIcon = { Text("🔍") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    TextButton(onClick = { query = "" }) {
                        Text("Clear", color = Turquoise)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Turquoise,
                unfocusedBorderColor = Turquoise.copy(alpha = 0.5f),
                cursorColor = Turquoise,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (features.isEmpty()) {
            Text(
                text = "No features match \"${query.trim()}\"",
                modifier = Modifier.padding(top = 24.dp),
                color = CampusRed,
                textAlign = TextAlign.Center
            )
        } else {
            // Only the grid scrolls; the header and search bar stay put.
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(features, key = { it.name }) { feature ->
                    FeatureTile(
                        feature = feature,
                        onClick = {
                            focusManager.clearFocus()
                            onFeatureSelected(feature)
                        }
                    )
                }
            }
        }
    }
}

// One square in the home grid.
@Composable
private fun FeatureTile(
    feature: Feature,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .background(LightGreen)
            .border(
                width = 3.dp,
                color = Turquoise,
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = feature.emoji,
            fontSize = 44.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = feature.title,
            fontFamily = FontFamily.Serif,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Turquoise,
            textAlign = TextAlign.Center
        )
    }
}

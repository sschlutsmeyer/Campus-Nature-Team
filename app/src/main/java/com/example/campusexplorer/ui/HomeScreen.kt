package com.example.campusexplorer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Feature(
    val title: String,
    val emoji: String,
    val subtitle: String,
    val keywords: List<String> = emptyList()
) {
    Account(
        title = "Log In / Sign Up",
        emoji = "🔑",
        subtitle = "Your profile, friends, and followers",
        keywords = listOf(
            "login", "log in", "sign in", "sign up", "account",
            "profile", "view profile", "friends", "followers",
            "following", "username", "password", "log out", "sign out"
        )
    ),

    Map(
        title = "Map",
        emoji = "🗺️",
        subtitle = "Explore nature",
        keywords = listOf("pins", "location", "campus")
    ),

    Camera(
        title = "Camera",
        emoji = "📷",
        subtitle = "Discover your next nature find",
        keywords = listOf("photo", "picture", "cam")
    ),

    Feed(
        title = "Feed",
        emoji = "📰",
        subtitle = "See what others have found",
        keywords = listOf("posts", "social")
    ),

    Friends(
        title = "Friends",
        emoji = "👥",
        subtitle = "Find people and keep in touch",
        keywords = listOf(
            "friends", "people", "nearby", "requests", "notes", "block"
        )
    ),

    Messages(
        title = "Messages",
        emoji = "💬",
        subtitle = "Chat with your friends",
        keywords = listOf(
            "chat", "dm", "direct messages", "inbox", "friends", "text"
        )
    ),

    Gallery(
        title = "Gallery",
        emoji = "🖼️",
        subtitle = "Browse your nature photos",
        keywords = listOf("photos", "pictures")
    ),

    Learn(
        title = "Learn",
        emoji = "📚",
        subtitle = "Discover plants and wildlife",
        keywords = listOf(
            "species", "plants", "animals", "wildlife", "learn"
        )
    )
}

@Composable
fun HomeScreen(
    onFeatureSelected: (Feature) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable {
        mutableStateOf("")
    }

    val focusManager = LocalFocusManager.current
    val search = query.trim()

    val session = LocalSession.current
    val social = LocalSocial.current
    val chats = LocalChats.current

    fun titleOf(feature: Feature) = when {
        feature != Feature.Account -> feature.title
        session.needsUsername -> "Finish Sign Up"
        session.isLoggedIn -> "View Profile"
        else -> feature.title.replace("/ ", "/\n")
    }

    fun emojiOf(feature: Feature) =
        if (feature == Feature.Account && session.isLoggedIn) {
            "👤"
        } else {
            feature.emoji
        }

    fun badgeOf(feature: Feature) = when (feature) {
        Feature.Messages -> chats.count {
            it.unread && it.otherUid !in social.hidden
        }

        Feature.Friends -> social.incomingRequests.count {
            it.from !in social.hidden
        }

        else -> 0
    }

    val features = Feature.entries.filter { feature ->
        search.isEmpty() ||
                titleOf(feature)
                    .replace('\n', ' ')
                    .contains(search, ignoreCase = true) ||
                feature.keywords.any { keyword ->
                    keyword.contains(search, ignoreCase = true)
                }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        NatureNetworkHeader()

        Spacer(Modifier.height(12.dp))

        Text(
            text = session.profile?.username?.let {
                "Welcome, $it!"
            } ?: "Welcome, Guest!",
            modifier = Modifier.fillMaxWidth(),
            fontFamily = FontFamily.Serif,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = LeafGreen,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("Search features")
            },
            leadingIcon = {
                Text("🔍")
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    TextButton(onClick = { query = "" }) {
                        Text(
                            text = "Clear",
                            color = Turquoise
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    focusManager.clearFocus()
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Turquoise,
                unfocusedBorderColor = Turquoise.copy(alpha = 0.5f),
                cursorColor = Turquoise,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )

        Spacer(Modifier.height(16.dp))

        if (features.isEmpty()) {
            Text(
                text = "No features match \"$search\"",
                modifier = Modifier.padding(top = 24.dp),
                color = CampusRed,
                textAlign = TextAlign.Center
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(
                    items = features,
                    key = { it.name }
                ) { feature ->
                    FeatureTile(
                        title = titleOf(feature),
                        emoji = emojiOf(feature),
                        badge = badgeOf(feature),
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

@Composable
private fun NatureNetworkHeader() {
    val outerShape = RoundedCornerShape(24.dp)
    val innerShape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(outerShape)
            .background(Color.White)
            .border(
                width = 3.dp,
                color = Turquoise,
                shape = outerShape
            )
            .padding(7.dp)
            .border(
                width = 2.dp,
                color = CampusRed,
                shape = innerShape
            ),
        contentAlignment = Alignment.Center
    ) {
        HeaderVines(
            modifier = Modifier.matchParentSize()
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 38.dp,
                    vertical = 24.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Nature Network",
                modifier = Modifier.fillMaxWidth(),
                fontFamily = FontFamily.Serif,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Turquoise,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "CSU Channel Islands",
                modifier = Modifier.fillMaxWidth(),
                fontFamily = FontFamily.Serif,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = CampusRed,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HeaderVines(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val inset = 19.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = size.height - top
        val leafLength = 12.dp.toPx()
        val leafWidth = 5.dp.toPx()

        for (side in 0..1) {
            val direction = if (side == 0) 1f else -1f

            val baseX = if (side == 0) {
                inset
            } else {
                size.width - inset
            }

            val stem = Path().apply {
                moveTo(baseX, bottom)

                cubicTo(
                    baseX - direction * 8.dp.toPx(),
                    size.height * 0.65f,
                    baseX + direction * 8.dp.toPx(),
                    size.height * 0.35f,
                    baseX,
                    top
                )
            }

            drawPath(
                path = stem,
                color = Turquoise,
                style = Stroke(width = 2.dp.toPx())
            )

            for (index in 1..5) {
                val y = top + (bottom - top) * index / 6f

                val leafDirection = if (index % 2 == 0) {
                    direction
                } else {
                    -direction
                }

                val start = Offset(
                    x = baseX,
                    y = y
                )

                val tip = Offset(
                    x = baseX + leafDirection * leafLength,
                    y = y - leafLength
                )

                val leaf = Path().apply {
                    moveTo(start.x, start.y)

                    quadraticTo(
                        start.x + leafDirection * leafWidth,
                        tip.y,
                        tip.x,
                        tip.y
                    )

                    quadraticTo(
                        tip.x,
                        start.y,
                        start.x,
                        start.y
                    )

                    close()
                }

                drawPath(
                    path = leaf,
                    color = LeafGreen
                )

                if (index % 2 == 0) {
                    drawCircle(
                        color = CampusRed,
                        radius = 2.5.dp.toPx(),
                        center = Offset(
                            x = baseX - leafDirection * 5.dp.toPx(),
                            y = y + 3.dp.toPx()
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureTile(
    title: String,
    emoji: String,
    badge: Int,
    onClick: () -> Unit
) {
    val tileShape = RoundedCornerShape(24.dp)

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(tileShape)
                .background(LightGreen)
                .border(
                    width = 3.dp,
                    color = Turquoise,
                    shape = tileShape
                )
                .clickable(onClick = onClick)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = emoji,
                fontSize = 44.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = title,
                fontFamily = FontFamily.Serif,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Turquoise,
                textAlign = TextAlign.Center
            )
        }

        if (badge > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .defaultMinSize(
                        minWidth = 28.dp,
                        minHeight = 28.dp
                    )
                    .clip(CircleShape)
                    .background(CampusRed)
                    .padding(horizontal = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (badge > 99) {
                        "99+"
                    } else {
                        badge.toString()
                    },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
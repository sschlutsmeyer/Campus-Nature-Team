package com.example.campusexplorer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val Turquoise = Color(0xFF008C95)
internal val CampusRed = Color(0xFFC6323A)
internal val LeafGreen = Color(0xFF427A46)
internal val Cream = Color(0xFFF7F8F2)
internal val LightGreen = Color(0xFFE8F3EC)

// Navigation icons require no additional icon dependencies.
private val BackIcon: ImageVector = ImageVector.Builder(
    name = "Back",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2.2f
    ) {
        moveTo(15f, 5f)
        lineTo(8f, 12f)
        lineTo(15f, 19f)
    }
}.build()

private val ForwardIcon: ImageVector = ImageVector.Builder(
    name = "Forward",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2.2f
    ) {
        moveTo(9f, 5f)
        lineTo(16f, 12f)
        lineTo(9f, 19f)
    }
}.build()

private val HomeIcon: ImageVector = ImageVector.Builder(
    name = "Home",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f
    ) {
        moveTo(3f, 11f)
        lineTo(12f, 3f)
        lineTo(21f, 11f)

        moveTo(5f, 9.5f)
        lineTo(5f, 21f)
        lineTo(19f, 21f)
        lineTo(19f, 9.5f)

        moveTo(10f, 21f)
        lineTo(10f, 15f)
        lineTo(14f, 15f)
        lineTo(14f, 21f)
    }
}.build()

private const val HOME = "Home"

@Composable
fun NatureQuestScreen(
    featureContent: @Composable (Feature) -> Unit = {
        FeaturePlaceholder(it)
    }
) {
    // Replace the whole list when navigating so Compose observes changes.
    var history by rememberSaveable {
        mutableStateOf(listOf(HOME))
    }

    var position by rememberSaveable {
        mutableIntStateOf(0)
    }

    val current = history[position]
    val canGoBack = position > 0
    val canGoForward = position < history.lastIndex

    fun open(screen: String) {
        if (screen == current) return

        // Remove forward history before opening a new screen.
        history = history.take(position + 1) + screen
        position = history.lastIndex
    }

    BackHandler(enabled = canGoBack) {
        position--
    }

    Scaffold(
        containerColor = Cream,
        bottomBar = {
            NavigationBar(
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                onBack = { position-- },
                onForward = { position++ },
                onHome = { open(HOME) }
            )
        }
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)

        if (current == HOME) {
            HomeScreen(
                onFeatureSelected = { feature ->
                    open(feature.name)
                },
                modifier = contentModifier
            )
        } else {
            val feature = Feature.valueOf(current)

            Column(
                modifier = contentModifier
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Branding appears on Home; feature screens use their own titles.
                NatureFrame(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    featureContent(feature)
                }

                Spacer(
                    modifier = Modifier.height(16.dp)
                )
            }
        }
    }
}

// Back and forward on the left, home in the center.
@Composable
private fun NavigationBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    horizontal = 20.dp,
                    vertical = 10.dp
                )
        ) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    enabled = canGoBack
                ) {
                    Icon(
                        imageVector = BackIcon,
                        contentDescription = "Back",
                        tint = if (canGoBack) {
                            Turquoise
                        } else {
                            Turquoise.copy(alpha = 0.3f)
                        }
                    )
                }

                IconButton(
                    onClick = onForward,
                    enabled = canGoForward
                ) {
                    Icon(
                        imageVector = ForwardIcon,
                        contentDescription = "Forward",
                        tint = if (canGoForward) {
                            Turquoise
                        } else {
                            Turquoise.copy(alpha = 0.3f)
                        }
                    )
                }
            }

            FilledIconButton(
                onClick = onHome,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(52.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Turquoise,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = HomeIcon,
                    contentDescription = "Home",
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

// Shown for features without an implemented screen.
@Composable
fun FeaturePlaceholder(feature: Feature) {
    ViewPlaceholder(
        title = "${feature.emoji} ${feature.title}",
        subtitle = "${feature.subtitle}\n\nComing soon!",
        background = Color(0xFFE4EEE2),
        foreground = Color(0xFF294C39)
    )
}

@Composable
internal fun QuestHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(LightGreen)
            .border(
                width = 3.dp,
                color = Turquoise,
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .border(
                    width = 1.5.dp,
                    color = CampusRed,
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(
                    horizontal = 24.dp,
                    vertical = 18.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "NATURE QUEST",
                fontFamily = FontFamily.Serif,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = Turquoise,
                textAlign = TextAlign.Center
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(2.dp)
                        .background(CampusRed)
                )

                Text(
                    text = "✦",
                    color = LeafGreen,
                    fontSize = 18.sp
                )

                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(2.dp)
                        .background(CampusRed)
                )
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = "CSU Channel Islands",
                fontFamily = FontFamily.Serif,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
                color = CampusRed,
                textAlign = TextAlign.Center
            )
        }

        VineDecoration(
            modifier = Modifier.matchParentSize()
        )
    }
}

// Decorative frame around feature content.
@Composable
private fun NatureFrame(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Cream)
            .border(
                width = 5.dp,
                color = Turquoise,
                shape = RoundedCornerShape(28.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
        ) {
            content()

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        width = 3.dp,
                        color = CampusRed,
                        shape = RoundedCornerShape(18.dp)
                    )
            )
        }

        VineDecoration(
            modifier = Modifier.matchParentSize()
        )
    }
}

@Composable
private fun VineDecoration(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val inset = 10.dp.toPx()
        val wave = 3.dp.toPx()
        val leafWidth = 7.dp.toPx()
        val leafHeight = 4.dp.toPx()
        val step = 36.dp.toPx()
        val top = 24.dp.toPx()
        val bottom = size.height - top

        if (bottom > top) {
            for (rightSide in listOf(false, true)) {
                val edgeX = if (rightSide) {
                    size.width - inset
                } else {
                    inset
                }

                val direction = if (rightSide) -1f else 1f

                val stem = Path().apply {
                    moveTo(edgeX, top)

                    var currentY = top

                    while (currentY < bottom) {
                        val nextY = (currentY + step)
                            .coerceAtMost(bottom)

                        quadraticTo(
                            edgeX + direction * wave,
                            (currentY + nextY) / 2f,
                            edgeX,
                            nextY
                        )

                        currentY = nextY
                    }
                }

                drawPath(
                    path = stem,
                    color = LeafGreen,
                    style = Stroke(
                        width = 2.dp.toPx()
                    )
                )

                var leafY = top + step / 2f
                var leafIndex = 0

                while (leafY < bottom) {
                    val leafX = if (leafIndex % 2 == 0) {
                        edgeX
                    } else {
                        edgeX - leafWidth
                    }

                    drawOval(
                        color = LeafGreen,
                        topLeft = Offset(leafX, leafY),
                        size = Size(
                            leafWidth,
                            leafHeight
                        )
                    )

                    leafY += step / 2f
                    leafIndex++
                }
            }
        }
    }
}

@Composable
private fun ViewPlaceholder(
    title: String,
    subtitle: String,
    background: Color,
    foreground: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            fontFamily = FontFamily.Serif,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = foreground,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = subtitle,
            fontSize = 14.sp,
            color = foreground,
            textAlign = TextAlign.Center
        )
    }
}
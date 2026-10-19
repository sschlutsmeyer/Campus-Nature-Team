package com.example.campusexplorer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

private val Turquoise = Color(0xFF008C95)
private val CampusRed = Color(0xFFC6323A)
private val LeafGreen = Color(0xFF427A46)
private val Cream = Color(0xFFF7F8F2)
private val LightGreen = Color(0xFFE8F3EC)

// Map icon: no extra icon dependency needed.
private val MapTabIcon: ImageVector = ImageVector.Builder(
    name = "Map",
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
        moveTo(3f, 6f)
        lineTo(9f, 3f)
        lineTo(15f, 6f)
        lineTo(21f, 3f)
        lineTo(21f, 18f)
        lineTo(15f, 21f)
        lineTo(9f, 18f)
        lineTo(3f, 21f)
        close()

        moveTo(9f, 3f)
        lineTo(9f, 18f)

        moveTo(15f, 6f)
        lineTo(15f, 21f)
    }
}.build()

// Camera icon: no extra icon dependency needed.
private val CameraTabIcon: ImageVector = ImageVector.Builder(
    name = "Camera",
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
        moveTo(3f, 7f)
        lineTo(7f, 7f)
        lineTo(9f, 4f)
        lineTo(15f, 4f)
        lineTo(17f, 7f)
        lineTo(21f, 7f)
        lineTo(21f, 20f)
        lineTo(3f, 20f)
        close()

        moveTo(16f, 13f)
        curveTo(16f, 15.21f, 14.21f, 17f, 12f, 17f)
        curveTo(9.79f, 17f, 8f, 15.21f, 8f, 13f)
        curveTo(8f, 10.79f, 9.79f, 9f, 12f, 9f)
        curveTo(14.21f, 9f, 16f, 10.79f, 16f, 13f)
        close()
    }
}.build()

@Composable
fun NatureQuestScreen(
    mapContent: @Composable () -> Unit = {
        ViewPlaceholder(
            title = "Campus Map",
            subtitle = "Explore nature around CSUCI",
            background = Color(0xFFE4EEE2),
            foreground = Color(0xFF294C39)
        )
    },
    cameraContent: @Composable () -> Unit = {
        ViewPlaceholder(
            title = "Nature Camera",
            subtitle = "Discover your next nature find",
            background = Color(0xFF202C2B),
            foreground = Color.White
        )
    }
) {
    var selectedTab by rememberSaveable {
        mutableStateOf("Map")
    }

    Scaffold(
        containerColor = Cream,
        bottomBar = {
            Surface(
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(
                            horizontal = 20.dp,
                            vertical = 12.dp
                        ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TabButton(
                        title = "Map",
                        icon = MapTabIcon,
                        selected = selectedTab == "Map",
                        onClick = { selectedTab = "Map" },
                        modifier = Modifier.weight(1f)
                    )

                    TabButton(
                        title = "Camera",
                        icon = CameraTabIcon,
                        selected = selectedTab == "Camera",
                        onClick = { selectedTab = "Camera" },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            QuestHeader()

            Spacer(modifier = Modifier.height(20.dp))

            // Expands to fill all remaining space.
            NatureFrame(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (selectedTab == "Map") {
                    mapContent()
                } else {
                    cameraContent()
                }
            }
        }
    }
}

@Composable
private fun QuestHeader() {
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

            Spacer(modifier = Modifier.height(8.dp))

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

            Spacer(modifier = Modifier.height(8.dp))

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

@Composable
private fun TabButton(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(
            horizontal = 12.dp,
            vertical = 12.dp
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Turquoise else Cream,
            contentColor = if (selected) Color.White else Turquoise
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = title,
            fontFamily = FontFamily.Serif,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

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
        // Your teammates' map or camera appears here.
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
                    style = Stroke(width = 2.dp.toPx())
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
                        size = Size(leafWidth, leafHeight)
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

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            fontSize = 14.sp,
            color = foreground,
            textAlign = TextAlign.Center
        )
    }
}
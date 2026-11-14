package com.example.campusexplorer.ui.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campusexplorer.data.INaturalistRepository
import com.example.campusexplorer.data.LearnSpecies
import com.example.campusexplorer.ui.Turquoise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpeciesDetailsDialog(
    id: Long,
    initial: LearnSpecies?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    val scope = rememberCoroutineScope()

    var species by remember(id) {
        mutableStateOf(initial)
    }

    var loading by remember(id) {
        mutableStateOf(true)
    }

    var error by remember(id) {
        mutableStateOf<String?>(null)
    }

    var retry by remember(id) {
        mutableIntStateOf(0)
    }

    var closing by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(id, retry) {
        loading = true
        error = null

        try {
            species = INaturalistRepository.details(
                id,
                refresh = retry > 0
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Couldn't load species information."
        } finally {
            if (currentCoroutineContext().isActive) {
                loading = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(0.9f),
        shape = RoundedCornerShape(
            topStart = 28.dp,
            topEnd = 28.dp
        ),
        containerColor = Color.White,
        contentColor = LearnText,
        scrimColor = Color.Black.copy(alpha = 0.4f)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Title stays visible while the content scrolls.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = species?.name ?: "Species details",
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    color = LearnText,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    enabled = !closing,
                    onClick = {
                        closing = true

                        scope.launch {
                            try {
                                sheetState.hide()

                                if (!sheetState.isVisible) {
                                    onDismiss()
                                }
                            } finally {
                                closing = false
                            }
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            Color(0xFFF0F2F0),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = SpeciesCloseIcon,
                        contentDescription = "Close species details",
                        tint = Color.DarkGray
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
            ) {
                val detail = species

                if (detail != null) {
                    // Facts appear before the photo.
                    SpeciesQuickFacts(detail)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Keeps alternate-photo lookup and matching photo credit.
                    SpeciesPhoto(
                        species = detail,
                        imageModifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )

                    Text(
                        text = detail.scientificName,
                        color = LearnText,
                        fontStyle = FontStyle.Italic,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (loading) {
                    CircularProgressIndicator(
                        color = Turquoise,
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .size(24.dp)
                    )
                }

                error?.let { message ->
                    Text(
                        text = message,
                        color = LearnText,
                        modifier = Modifier.padding(top = 12.dp)
                    )

                    TextButton(onClick = { retry++ }) {
                        Text(
                            text = "Try again",
                            color = Turquoise
                        )
                    }
                }
            }
        }
    }
}

// Close icon without adding another dependency.
private val SpeciesCloseIcon = ImageVector.Builder(
    name = "SpeciesClose",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f
    ) {
        moveTo(6f, 6f)
        lineTo(18f, 18f)

        moveTo(18f, 6f)
        lineTo(6f, 18f)
    }
}.build()
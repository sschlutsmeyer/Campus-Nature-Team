package com.example.campusexplorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.example.campusexplorer.ui.NatureQuestScreen
import com.example.campusexplorer.ui.camera.CameraScreen
import com.example.campusexplorer.ui.map.MapScreen
import com.example.campusexplorer.ui.theme.CampusExplorerTheme
import kotlinx.coroutines.launch

private enum class Tab(
    val label: String,
    @param:DrawableRes val icon: Int
) {
    Map("Map", R.drawable.ic_map),
    Camera("Camera", R.drawable.ic_camera),
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            CampusExplorerTheme {
                CampusExplorerApp()
            }
        }
    }
}

@Composable
fun CampusExplorerApp() {
    // true = Nature Quest design
    // false = original navigation layout
    val useNatureQuestUI = true

    var tab by rememberSaveable {
        mutableStateOf(Tab.Map)
    }

    val snackbar = remember {
        SnackbarHostState()
    }

    val scope = rememberCoroutineScope()

    val showMessage: (String) -> Unit = { message ->
        scope.launch {
            snackbar.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),

        snackbarHost = {
            SnackbarHost(snackbar)
        },

        bottomBar = {
            if (!useNatureQuestUI) {
                NavigationBar {
                    Tab.entries.forEach { currentTab ->
                        NavigationBarItem(
                            selected = tab == currentTab,

                            onClick = {
                                tab = currentTab
                            },

                            icon = {
                                Icon(
                                    painter = painterResource(
                                        currentTab.icon
                                    ),
                                    contentDescription = null
                                )
                            },

                            label = {
                                Text(currentTab.label)
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (useNatureQuestUI) {
            NatureQuestScreen(
                mapContent = {
                    MapScreen(
                        modifier = Modifier.fillMaxSize()
                    )
                },

                cameraContent = {
                    CameraScreen(
                        onMessage = showMessage,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            )
        } else {
            val contentModifier = Modifier.padding(innerPadding)

            when (tab) {
                Tab.Map -> {
                    MapScreen(contentModifier)
                }

                Tab.Camera -> {
                    CameraScreen(
                        onMessage = showMessage,
                        modifier = contentModifier
                    )
                }
            }
        }
    }
}
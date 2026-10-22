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
import com.example.campusexplorer.ui.Feature
import com.example.campusexplorer.ui.FeaturePlaceholder
import com.example.campusexplorer.ui.NatureQuestScreen
import com.example.campusexplorer.ui.camera.CameraScreen
import com.example.campusexplorer.ui.map.MapScreen
import com.example.campusexplorer.ui.theme.CampusExplorerTheme
import kotlinx.coroutines.launch

// Switches between the Nature Quest design (true) and the plain tab bar layout (false).
private const val USE_NATURE_QUEST_UI = true

// The tabs in the bottom bar, each with a label and an icon from res/drawable.
private enum class Tab(val label: String, @param:DrawableRes val icon: Int) {
    Map("Map", R.drawable.ic_map),
    Camera("Camera", R.drawable.ic_camera),
}

// The app's only screen. Everything is drawn by CampusExplorerApp below.
class MainActivity : ComponentActivity() {

    // Runs once when the app opens.
    override fun onCreate(savedInstanceState: Bundle?) {

        // Let Android do its own setup first.
        super.onCreate(savedInstanceState)

        // Draw behind the status and navigation bars.
        enableEdgeToEdge()

        // Set the UI, wrapped in our theme.
        setContent {

            // Apply our colors and fonts.
            CampusExplorerTheme {

                // Draw the app.
                CampusExplorerApp()
            }
        }
    }
}

// The whole app: a bottom tab bar and the selected tab's screen.
@Composable
fun CampusExplorerApp() {

    // The selected tab. rememberSaveable keeps it when the phone rotates.
    var tab by rememberSaveable { mutableStateOf(Tab.Map) }

    // Controls the pop-up messages (snackbars) at the bottom of the screen.
    val snackbar = remember { SnackbarHostState() }

    // Used to show snackbars, which run in the background.
    val scope = rememberCoroutineScope()

    // Shows a pop-up message. Passed to each screen.
    val showMessage: (String) -> Unit = { scope.launch { snackbar.showSnackbar(it) } }

    // Standard page layout with slots for a bottom bar and snackbars.
    Scaffold(

        // Fill the whole screen.
        modifier = Modifier.fillMaxSize(),

        // Where pop-up messages appear.
        snackbarHost = { SnackbarHost(snackbar) },

        // The plain tab bar. The Nature Quest design draws its own instead.
        bottomBar = {

            if (!USE_NATURE_QUEST_UI) {
                NavigationBar {

                    // One button per tab.
                    Tab.entries.forEach {

                        NavigationBarItem(

                            // Highlight the selected tab.
                            selected = tab == it,

                            // Switch to this tab when tapped.
                            onClick = { tab = it },

                            // Tab icon and label.
                            icon = { Icon(painterResource(it.icon), contentDescription = null) },
                            label = { Text(it.label) },
                        )
                    }
                }
            }
        },
    )

    // innerPadding is the space the bars take up, so content isn't hidden behind them.
    { innerPadding ->

        // Nature Quest design: a home screen of features plus back/forward/home navigation
        // (see ui/HomeScreen.kt and ui/MapCameraUI.kt).
        if (USE_NATURE_QUEST_UI) {

            NatureQuestScreen { feature ->

                // The screen for each feature. Features without one yet show a placeholder.
                when (feature) {
                    Feature.Map -> MapScreen(onMessage = showMessage, modifier = Modifier.fillMaxSize())
                    Feature.Camera -> CameraScreen(onMessage = showMessage, modifier = Modifier.fillMaxSize())
                    else -> FeaturePlaceholder(feature)
                }
            }

        // Plain layout: show the screen for the selected tab (see MapScreen.kt and CameraScreen.kt).
        } else {

            val contentModifier = Modifier.padding(innerPadding)
            when (tab) {
                Tab.Map -> MapScreen(onMessage = showMessage, modifier = contentModifier)
                Tab.Camera -> CameraScreen(onMessage = showMessage, modifier = contentModifier)
            }
        }
    }
}

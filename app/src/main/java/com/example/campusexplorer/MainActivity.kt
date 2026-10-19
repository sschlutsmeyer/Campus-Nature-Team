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
import com.example.campusexplorer.ui.camera.CameraScreen
import com.example.campusexplorer.ui.map.MapScreen
import com.example.campusexplorer.ui.theme.CampusExplorerTheme
import kotlinx.coroutines.launch

// List of tabs in the bottom bar.
// Each tab has a text label and an icon (the Int is the ID of an image in res/drawable).
private enum class Tab(val label: String, @param:DrawableRes val icon: Int) {
    Map("Map", R.drawable.ic_map),
    Camera("Camera", R.drawable.ic_camera),
}

// Main
class MainActivity : ComponentActivity() {

    // onCreate runs once when the screen is being created. "override" means we are
    // replacing the parent class's version with our own.
    override fun onCreate(savedInstanceState: Bundle?) {

        // Always call the parent's onCreate first so Android can do its own setup.
        super.onCreate(savedInstanceState)

        // Let our UI draw all the way to the edges of the phone screen.
        enableEdgeToEdge()

        // Set what the screen should look like
        setContent {

            // Wrap everything in our app's theme so colors and fonts are consistent.
            CampusExplorerTheme {

                // Function that draws the app. defined below.
                CampusExplorerApp()
            }
        }
    }
}

// @Composable means this function draws UI.
@Composable
fun CampusExplorerApp() {
    // Stores which tab is currently selected. Starts on the Map tab.
    // "by rememberSaveable" keeps the choice even if the phone is rotated.
    var tab by rememberSaveable { mutableStateOf(Tab.Map) }

    // The controller for pop-ups.
    // "remember" keeps the same one around instead of making a new one on every redraw.
    val snackbar = remember { SnackbarHostState() }

    // Sets a scope for doing background tasks such as loading popups.
    val scope = rememberCoroutineScope()

    // Function that shows a message in a snackbar (pop-up) using the scope we set.
    val showMessage: (String) -> Unit = { scope.launch { snackbar.showSnackbar(it) } }

    // Scaffold is a standard page layout with slots we can fill in.
    Scaffold(

        // Make it fill the whole screen.
        modifier = Modifier.fillMaxSize(),

        // Set the pop-up host to be in the place where pop-ups go (the snackbar).
        snackbarHost = { SnackbarHost(snackbar) },

        // Fill in the bottom bar of the scaffold.
        bottomBar = {

            // The bottom bar that holds our tabs.
            NavigationBar {

                // Loop over every tab in our Tab enum. "it" is the current tab.
                Tab.entries.forEach {

                    // Draw one tab button.
                    NavigationBarItem(

                        // "selected" Highlight this tab if this tab is the one currently selected.
                        selected = tab == it,

                        // "onClick" On click make this tab the selected one.
                        onClick = { tab = it },

                        // "icon" Show the tab's icon.
                        icon = { Icon(painterResource(it.icon), contentDescription = null) },

                        // "label" Show the tab's text label.
                        label = { Text(it.label) },
                    )
                }
            }
        },
    ) // end of scaffold

    // Scaffold gives us "innerPadding" variable for how much space the bars take up
    // which we use so our content doesn't get hidden behind them.
    { innerPadding ->

        // A Modifier that adds that padding, which we hand to whichever screen we show.
        val contentModifier = Modifier.padding(innerPadding)

        // "when" like a switch statement. pick what to show based on the selected tab.
        when (tab) {

            // When the Map tab is selected, show the map screen and let it show messages.
            Tab.Map -> MapScreen(onMessage = showMessage, modifier = contentModifier)

            // When the Camera tab is selected, show the camera screen and let it show messages.
            Tab.Camera -> CameraScreen(onMessage = showMessage, modifier = contentModifier)

            // See MapScreen.kt and CameraScreen.kt for the above functions
        }
    }
}

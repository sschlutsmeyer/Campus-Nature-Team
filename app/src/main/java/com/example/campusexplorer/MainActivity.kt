package com.example.campusexplorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.campusexplorer.ui.NatureQuestScreen
import com.example.campusexplorer.ui.camera.CameraScreen
import com.example.campusexplorer.ui.map.MapScreen
import com.example.campusexplorer.ui.theme.CampusExplorerTheme
import kotlinx.coroutines.launch

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
    var selectedTab by rememberSaveable { mutableStateOf("Map") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val showMessage: (String) -> Unit = { message ->
        scope.launch {
            snackbar.showSnackbar(message)
        }
    }

    NatureQuestScreen(
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        snackbarHost = { SnackbarHost(snackbar) },
        mapContent = {
            MapScreen(
                modifier = Modifier.fillMaxSize(),
                onRecordSighting = { selectedTab = "Camera" },
            )
        },
        cameraContent = {
            CameraScreen(
                onMessage = showMessage,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}
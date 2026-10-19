package com.example.campusexplorer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// Colors for dark mode (defined in Color.kt).
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

// Colors for light mode.
private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

// The app's theme. Everything inside it uses these colors and fonts.
@Composable
fun CampusExplorerTheme(

    // Follow the phone's dark mode setting by default.
    darkTheme: Boolean = isSystemInDarkTheme(),

    // Use colors from the user's wallpaper (Android 12+ only).
    dynamicColor: Boolean = true,

    // The UI drawn inside the theme.
    content: @Composable () -> Unit
) {

    // Pick colors: wallpaper colors if available, otherwise our dark or light set.
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Apply the colors and the text styles from Type.kt.
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

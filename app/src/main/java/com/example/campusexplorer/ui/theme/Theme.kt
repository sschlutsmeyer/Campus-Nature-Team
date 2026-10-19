// The "package" says which folder/namespace this file belongs to.
package com.example.campusexplorer.ui.theme

// Build: tells us which Android version the phone is running.
import android.os.Build
// isSystemInDarkTheme: true if the phone is set to dark mode.
import androidx.compose.foundation.isSystemInDarkTheme
// MaterialTheme: applies colors and fonts to everything inside it.
import androidx.compose.material3.MaterialTheme
// Functions that build a dark or light set of colors from the colors we choose.
import androidx.compose.material3.darkColorScheme
// Functions that build colors from the user's wallpaper (Android 12+ "dynamic color").
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
// Composable: marks a function as one that draws UI.
import androidx.compose.runtime.Composable
// LocalContext: gives access to the Android Context (needed for dynamic colors).
import androidx.compose.ui.platform.LocalContext

// The set of colors used when the phone is in dark mode (colors come from Color.kt).
private val DarkColorScheme = darkColorScheme(
    // The main color, used for things like buttons.
    primary = Purple80,
    // A less prominent color for secondary elements.
    secondary = PurpleGrey80,
    // An accent color for extra highlights.
    tertiary = Pink80
)

// The set of colors used when the phone is in light mode.
private val LightColorScheme = lightColorScheme(
    // The main color, used for things like buttons.
    primary = Purple40,
    // A less prominent color for secondary elements.
    secondary = PurpleGrey40,
    // An accent color for extra highlights.
    tertiary = Pink40
)

// @Composable means this function draws UI.
@Composable
// Our app's theme. Anything placed inside it uses these colors and fonts.
fun CampusExplorerTheme(
    // Whether to use dark colors. By default, follow the phone's dark mode setting.
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    // Whether to use colors based on the user's wallpaper. On by default.
    dynamicColor: Boolean = true,
    // The UI to draw inside the theme (the rest of the app).
    content: @Composable () -> Unit
) {
    // Pick which set of colors to use. "when" checks each condition in order and uses the first match.
    val colorScheme = when {
        // If dynamic color is on AND the phone runs Android 12 ("S") or newer...
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            // ...get the Android Context...
            val context = LocalContext.current
            // ...and build colors from the wallpaper, in dark or light form.
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        // Otherwise, if dark mode is on, use our dark colors.
        darkTheme -> DarkColorScheme
        // Otherwise, use our light colors.
        else -> LightColorScheme
    }

    // Apply the chosen colors and fonts to everything inside.
    MaterialTheme(
        // The colors we picked above.
        colorScheme = colorScheme,
        // The text styles from Type.kt.
        typography = Typography,
        // The UI to draw with this theme.
        content = content
    )
}
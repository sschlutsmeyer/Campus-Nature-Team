// The "package" says which folder/namespace this file belongs to.
package com.example.campusexplorer.ui.theme

// Typography: a collection of text styles (titles, body text, labels...).
import androidx.compose.material3.Typography
// TextStyle: describes how a piece of text looks (font, size, weight, spacing).
import androidx.compose.ui.text.TextStyle
// FontFamily: which font to use.
import androidx.compose.ui.text.font.FontFamily
// FontWeight: how thick/bold the text is.
import androidx.compose.ui.text.font.FontWeight
// sp: "scale-independent pixels", a text size unit that respects the user's font size setting.
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
// Any style we don't list here (like titleLarge) uses Material's built-in default.
val Typography = Typography(
    // The style for normal-sized body text.
    bodyLarge = TextStyle(
        // Use the phone's default font.
        fontFamily = FontFamily.Default,
        // Regular thickness (not bold).
        fontWeight = FontWeight.Normal,
        // Text size of 16sp.
        fontSize = 16.sp,
        // Each line takes up 24sp of height (gives space between lines).
        lineHeight = 24.sp,
        // A tiny bit of extra space between letters.
        letterSpacing = 0.5.sp
    )
)
package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = Color(0xFF003544),
    primaryContainer = CyanPrimaryContainer,
    onPrimaryContainer = CyanOnPrimaryContainer,
    secondary = TealSecondary,
    onSecondary = Color(0xFF003831),
    secondaryContainer = TealSecondaryContainer,
    onSecondaryContainer = TealOnSecondaryContainer,
    tertiary = ActiveStreamBlue,
    onTertiary = Color.White,
    background = StudioDarkBg,
    onBackground = Color(0xFFE2E8F0),
    surface = StudioSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = StudioSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = StudioCardBorder,
    error = VuRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = CyanPrimaryDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC7F0FF),
    onPrimaryContainer = Color(0xFF001F29),
    secondary = Color(0xFF006A5F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF73F8E4),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = ActiveStreamBlue,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = VuRed
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else DarkColorScheme // default to sleek dark studio look

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

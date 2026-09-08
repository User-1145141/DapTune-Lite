package com.weich.daptune.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * DapTune Lite color system
 *
 * Visual identity:
 *   Black  #000000
 *   White  #FFFFFF
 *   Cyan   #00FFFF
 *   Red    #FF0000
 *
 * Dynamic system colors are intentionally disabled.
 */

private val LightColors = lightColorScheme(
    // Cyan: primary interaction color
    primary = Color(0xFF00FFFF),
    onPrimary = Color(0xFF000000),

    // Dark cyan container for readable contrast
    primaryContainer = Color(0xFFCCFFFF),
    onPrimaryContainer = Color(0xFF000000),

    // Grayscale secondary colors
    secondary = Color(0xFF555555),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6E6E6),
    onSecondaryContainer = Color(0xFF000000),

    // Pure red: emphasis / warning / destructive accent
    tertiary = Color(0xFFFF0000),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFFFFCCCC),
    onTertiaryContainer = Color(0xFF000000),

    // Pure white background
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),

    // White surfaces
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),

    // Light grayscale surface variant
    surfaceVariant = Color(0xFFE6E6E6),
    onSurfaceVariant = Color(0xFF333333),

    // Grayscale outlines
    outline = Color(0xFF666666),
    outlineVariant = Color(0xFFCCCCCC),

    // Error: pure red
    error = Color(0xFFFF0000),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFFFFCCCC),
    onErrorContainer = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    // Cyan: primary interaction color
    primary = Color(0xFF00FFFF),
    onPrimary = Color(0xFF000000),

    // Dark cyan container
    primaryContainer = Color(0xFF003333),
    onPrimaryContainer = Color(0xFF00FFFF),

    // Grayscale secondary colors
    secondary = Color(0xFFB3B3B3),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF333333),
    onSecondaryContainer = Color(0xFFFFFFFF),

    // Pure red: emphasis / warning / destructive accent
    tertiary = Color(0xFFFF0000),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF660000),
    onTertiaryContainer = Color(0xFFFFCCCC),

    // Pure black background
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),

    // Pure black surfaces
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),

    // Slightly lifted dark surface
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFCCCCCC),

    // Grayscale outlines
    outline = Color(0xFF999999),
    outlineVariant = Color(0xFF333333),

    // Error: pure red
    error = Color(0xFFFF0000),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF660000),
    onErrorContainer = Color(0xFFFFCCCC),
)

@Composable
fun DapTuneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) {
        DarkColors
    } else {
        LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

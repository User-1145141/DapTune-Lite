package com.weich.daptune.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF2457D6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5FF),
    onPrimaryContainer = Color(0xFF0B2E7A),
    secondary = Color(0xFF53617C),
    secondaryContainer = Color(0xFFDDE5F9),
    tertiary = Color(0xFF006A66),
    tertiaryContainer = Color(0xFF9CF2EC),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF191B21),
    surface = Color(0xFFFBF9FF),
    surfaceVariant = Color(0xFFE5E7EF),
    onSurfaceVariant = Color(0xFF45464F),
    outline = Color(0xFF767780),
    outlineVariant = Color(0xFFC6C6D0),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB5C6FF),
    onPrimary = Color(0xFF002A78),
    primaryContainer = Color(0xFF143F9C),
    onPrimaryContainer = Color(0xFFDCE5FF),
    secondary = Color(0xFFBBC6E1),
    secondaryContainer = Color(0xFF3B465D),
    tertiary = Color(0xFF80D5CF),
    tertiaryContainer = Color(0xFF00504D),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC6C6D0),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF45464F),
    error = Color(0xFFFFB4AB),
)

@Composable
fun DapTuneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

package com.weich.daptune.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    // 主色：鲜明蓝
    primary = Color(0xFF0057D9),
    onPrimary = Color.White,

    // 主色容器
    primaryContainer = Color(0xFFD9E7FF),
    onPrimaryContainer = Color(0xFF001A41),

    // 次要色：冷灰蓝
    secondary = Color(0xFF4E6078),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE5F3),
    onSecondaryContainer = Color(0xFF0B1C2E),

    // 强调色：青色
    tertiary = Color(0xFF007A76),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF9CF2E8),
    onTertiaryContainer = Color(0xFF00201E),

    // 背景
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF171A20),

    // 表面 / 卡片
    surface = Color(0xFFFCFCFF),
    onSurface = Color(0xFF171A20),

    // 表面变体
    surfaceVariant = Color(0xFFE2E7F0),
    onSurfaceVariant = Color(0xFF444850),

    // 边框
    outline = Color(0xFF747880),
    outlineVariant = Color(0xFFC4C8D0),

    // 错误
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    // 主色：明亮蓝
    primary = Color(0xFF9DBBFF),
    onPrimary = Color(0xFF002D70),

    // 主色容器
    primaryContainer = Color(0xFF174A9F),
    onPrimaryContainer = Color(0xFFD9E7FF),

    // 次要色
    secondary = Color(0xFFB9C7DF),
    onSecondary = Color(0xFF233144),
    secondaryContainer = Color(0xFF39495F),
    onSecondaryContainer = Color(0xFFDCE5F3),

    // 强调色：青色
    tertiary = Color(0xFF69DDD4),
    onTertiary = Color(0xFF003734),
    tertiaryContainer = Color(0xFF005550),
    onTertiaryContainer = Color(0xFF9CF2E8),

    // 背景
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE2E5EC),

    // 表面 / 卡片
    surface = Color(0xFF12151A),
    onSurface = Color(0xFFE2E5EC),

    // 表面变体
    surfaceVariant = Color(0xFF41464F),
    onSurfaceVariant = Color(0xFFC4C8D0),

    // 边框
    outline = Color(0xFF8E939C),
    outlineVariant = Color(0xFF41464F),

    // 错误
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
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

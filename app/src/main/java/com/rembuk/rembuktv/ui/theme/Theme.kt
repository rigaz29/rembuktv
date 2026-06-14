package com.rembuk.rembuktv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.rembuk.rembuktv.domain.model.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Purple,
    onPrimary = OnDark,
    secondary = PurpleLight,
    background = DarkBackground,
    onBackground = OnDark,
    surface = DarkSurface,
    onSurface = OnDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = OnDarkMuted,
    error = ErrorRed,
)

private val AmoledColors = DarkColors.copy(
    background = AmoledBlack,
    surface = AmoledBlack,
    surfaceVariant = DarkSurface,
)

private val LightColors = lightColorScheme(
    primary = PurpleDark,
    secondary = Purple,
)

@Composable
fun LiveTvTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> systemDark
    }
    val colorScheme = when {
        themeMode == ThemeMode.AMOLED && dark -> AmoledColors
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

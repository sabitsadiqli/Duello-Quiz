package com.sabit.duelloquiz.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.sabit.duelloquiz.core.model.ThemeMode

/** Game colours of the current theme (correct, wrong, timer, Əminlik chips, banners). */
val LocalGameColors = staticCompositionLocalOf { LightGameColors }

/** `MaterialTheme.gameColors` reads [LocalGameColors]. */
val MaterialTheme.gameColors: GameColors
    @Composable
    @ReadOnlyComposable
    get() = LocalGameColors.current

/** Resolves the stored [ThemeMode] against the system setting. */
@Composable
fun ThemeMode.isDarkTheme(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Duello Quiz theme built from design/tokens.json. Dynamic colour (Material You) is off on
 * purpose: brand and game colours must stay consistent and contrast-checked.
 */
@Composable
fun DuelloTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val gameColors = if (darkTheme) DarkGameColors else LightGameColors
    CompositionLocalProvider(LocalGameColors provides gameColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DuelloTypography,
            shapes = DuelloShapes,
            content = content,
        )
    }
}

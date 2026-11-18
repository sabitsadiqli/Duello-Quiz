package com.sabit.duelloquiz.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.sabit.duelloquiz.core.model.ThemeMode

/** Game colours of the current theme (cards, correct/wrong, timer, Əminlik, banners, streak, nav). */
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
 * Duello Quiz theme built from design/tokens.json 3.0.0 (v3 "dəftər": paper and ink, one nar
 * accent). Dynamic colour (Material You) is off on purpose: the palette is contrast-checked.
 * `surfaceTint` equals `surface`, so Material 3 tonal elevation adds no tint.
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

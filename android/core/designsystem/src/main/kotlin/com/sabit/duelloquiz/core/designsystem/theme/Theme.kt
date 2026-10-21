package com.sabit.duelloquiz.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.sabit.duelloquiz.core.model.ThemeMode

/** Game colours of the current theme (pressables, cards, correct/wrong, timer, Əminlik, banners). */
val LocalGameColors = staticCompositionLocalOf { LightGameColors }

/** color.<theme>.category, keyed by slot name ([CategoryAccent.Order]). */
val LocalCategoryColors = staticCompositionLocalOf { LightCategoryColors }

/** color.<theme>.gradient */
val LocalGradients = staticCompositionLocalOf { LightGradients }

/** `MaterialTheme.gameColors` reads [LocalGameColors]. */
val MaterialTheme.gameColors: GameColors
    @Composable
    @ReadOnlyComposable
    get() = LocalGameColors.current

/** `MaterialTheme.gradients` reads [LocalGradients]. */
val MaterialTheme.gradients: DuelloGradients
    @Composable
    @ReadOnlyComposable
    get() = LocalGradients.current

/**
 * Colours of the category accent [slot] in the current theme; an unknown slot gets the first
 * slot of [CategoryAccent.Order] (use [CategoryAccent.resolve] to pick the slot).
 */
@Composable
@ReadOnlyComposable
fun categoryColors(slot: String): CategoryColors {
    val palette = LocalCategoryColors.current
    return palette[slot] ?: palette.getValue(CategoryAccent.Order.first())
}

/** Resolves the stored [ThemeMode] against the system setting. */
@Composable
fun ThemeMode.isDarkTheme(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Duello Quiz theme built from design/tokens.json 2.0.0. Dynamic colour (Material You) is off on
 * purpose: brand, game and category colours must stay consistent and contrast-checked.
 */
@Composable
fun DuelloTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val gameColors = if (darkTheme) DarkGameColors else LightGameColors
    val categoryColors = if (darkTheme) DarkCategoryColors else LightCategoryColors
    val gradients = if (darkTheme) DarkGradients else LightGradients
    CompositionLocalProvider(
        LocalGameColors provides gameColors,
        LocalCategoryColors provides categoryColors,
        LocalGradients provides gradients,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DuelloTypography,
            shapes = DuelloShapes,
            content = content,
        )
    }
}

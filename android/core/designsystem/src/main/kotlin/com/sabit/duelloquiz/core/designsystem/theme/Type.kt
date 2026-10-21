package com.sabit.duelloquiz.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sabit.duelloquiz.core.designsystem.R

/**
 * Nunito, bundled as static instances (design/README.md, third_party/nunito/README.md):
 * weights 500/600/700/800/900, no downloadable fonts.
 */
val NunitoFontFamily = FontFamily(
    Font(R.font.nunito_medium, FontWeight.W500),
    Font(R.font.nunito_semibold, FontWeight.W600),
    Font(R.font.nunito_bold, FontWeight.W700),
    Font(R.font.nunito_extrabold, FontWeight.W800),
    Font(R.font.nunito_black, FontWeight.W900),
)

private fun style(fontSize: Int, lineHeight: Int, weight: Int, letterSpacing: Double) = TextStyle(
    fontFamily = NunitoFontFamily,
    fontWeight = FontWeight(weight),
    fontSize = fontSize.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

/** typography.scale from design/tokens.json 2.0.0, 1:1. */
internal val DuelloTypography = Typography(
    displayLarge = style(56, 64, 900, -0.5),
    displayMedium = style(44, 52, 900, -0.25),
    displaySmall = style(36, 44, 900, 0.0),
    headlineLarge = style(32, 40, 800, 0.0),
    headlineMedium = style(28, 36, 800, 0.0),
    headlineSmall = style(24, 32, 800, 0.0),
    titleLarge = style(20, 28, 800, 0.0),
    titleMedium = style(17, 24, 800, 0.0),
    titleSmall = style(14, 20, 800, 0.1),
    bodyLarge = style(16, 24, 600, 0.1),
    bodyMedium = style(14, 20, 600, 0.15),
    bodySmall = style(12, 16, 600, 0.3),
    labelLarge = style(16, 20, 800, 0.2),
    labelMedium = style(13, 16, 800, 0.2),
    labelSmall = style(11, 16, 700, 0.3),
)

/**
 * Tabular figures for timers, progress, scores and rating (tokens: fontFamily.brand.features).
 * Nunito has no `tnum` feature but its default digits are already fixed-width, so this is a
 * no-op today and only matters if the font changes.
 */
fun TextStyle.tabularFigures(): TextStyle = copy(fontFeatureSettings = "tnum")

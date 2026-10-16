package com.sabit.duelloquiz.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sabit.duelloquiz.core.designsystem.R

/** Inter, bundled (design/README.md): weights 400/500/600/700, no downloadable fonts. */
val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.W400),
    Font(R.font.inter_medium, FontWeight.W500),
    Font(R.font.inter_semibold, FontWeight.W600),
    Font(R.font.inter_bold, FontWeight.W700),
)

private fun style(fontSize: Int, lineHeight: Int, weight: Int, letterSpacing: Double) = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight(weight),
    fontSize = fontSize.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

/** typography.scale from design/tokens.json, 1:1. */
internal val DuelloTypography = Typography(
    displayLarge = style(57, 64, 700, -0.25),
    displayMedium = style(45, 52, 700, 0.0),
    displaySmall = style(36, 44, 700, 0.0),
    headlineLarge = style(32, 40, 600, 0.0),
    headlineMedium = style(28, 36, 600, 0.0),
    headlineSmall = style(24, 32, 600, 0.0),
    titleLarge = style(22, 28, 600, 0.0),
    titleMedium = style(16, 24, 600, 0.15),
    titleSmall = style(14, 20, 600, 0.1),
    bodyLarge = style(16, 24, 400, 0.15),
    bodyMedium = style(14, 20, 400, 0.25),
    bodySmall = style(12, 16, 400, 0.4),
    labelLarge = style(14, 20, 600, 0.1),
    labelMedium = style(12, 16, 500, 0.5),
    labelSmall = style(11, 16, 500, 0.5),
)

/** Tabular figures for timers, progress, scores and rating (tokens: fontFamily.brand.features). */
fun TextStyle.tabularFigures(): TextStyle = copy(fontFeatureSettings = "tnum")

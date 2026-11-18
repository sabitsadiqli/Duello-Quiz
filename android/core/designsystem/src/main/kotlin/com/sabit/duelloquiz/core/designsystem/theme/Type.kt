package com.sabit.duelloquiz.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sabit.duelloquiz.core.designsystem.R

/**
 * typography.fontFamily.display: Fraunces, static instances (third_party/README.md). Display,
 * headline and the numeral roles only; it has no tabular figures, so only static numbers.
 */
val DisplayFontFamily = FontFamily(
    Font(R.font.fraunces_regular, FontWeight.W400),
    Font(R.font.fraunces_semibold, FontWeight.W600),
)

/** typography.fontFamily.brand: Plus Jakarta Sans 500/600/700/800, all UI text. */
val BrandFontFamily = FontFamily(
    Font(R.font.plusjakartasans_medium, FontWeight.W500),
    Font(R.font.plusjakartasans_semibold, FontWeight.W600),
    Font(R.font.plusjakartasans_bold, FontWeight.W700),
    Font(R.font.plusjakartasans_extrabold, FontWeight.W800),
)

private fun style(family: FontFamily, fontSize: Int, lineHeight: Int, weight: Int, letterSpacing: Double) = TextStyle(
    fontFamily = family,
    fontWeight = FontWeight(weight),
    fontSize = fontSize.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

private fun display(fontSize: Int, lineHeight: Int, weight: Int, letterSpacing: Double) =
    style(DisplayFontFamily, fontSize, lineHeight, weight, letterSpacing)

private fun brand(fontSize: Int, lineHeight: Int, weight: Int, letterSpacing: Double) =
    style(BrandFontFamily, fontSize, lineHeight, weight, letterSpacing)

/** typography.scale from design/tokens.json 3.0.0, 1:1 (family per `scale.*.family`). */
internal val DuelloTypography = Typography(
    displayLarge = display(52, 56, 600, -1.0),
    displayMedium = display(44, 48, 600, -0.5),
    displaySmall = display(36, 40, 600, -0.25),
    headlineLarge = display(32, 36, 600, -0.25),
    headlineMedium = display(28, 34, 600, 0.0),
    headlineSmall = display(24, 30, 600, 0.0),
    titleLarge = brand(20, 28, 700, 0.0),
    titleMedium = brand(16, 24, 700, 0.1),
    titleSmall = brand(14, 20, 700, 0.1),
    bodyLarge = brand(16, 24, 500, 0.0),
    bodyMedium = brand(14, 20, 500, 0.1),
    bodySmall = brand(12, 16, 500, 0.2),
    labelLarge = brand(15, 20, 700, 0.1),
    labelMedium = brand(13, 16, 700, 0.2),
    labelSmall = brand(11, 16, 700, 0.4),
)

/** typography.custom: extra roles that Material 3 Typography does not have. */
object DuelloTextStyles {
    /** Section labels ("TƏK OYNA · 10 SUAL"); strings are authored in capitals, never uppercased at runtime. */
    val overline: TextStyle = brand(11, 16, 800, 1.4)

    /** Category list numbers 01–05 (Fraunces Regular). Static numbers only. */
    val numeral: TextStyle = display(20, 24, 400, 0.0)

    /** "#12" on the Gündəlik tab, the final daily score (Fraunces SemiBold). Static numbers only. */
    val numeralLarge: TextStyle = display(56, 60, 600, -1.0)
}

/** `MaterialTheme.typography.overline` etc. (custom roles of [DuelloTextStyles]). */
val Typography.overline: TextStyle get() = DuelloTextStyles.overline
val Typography.numeral: TextStyle get() = DuelloTextStyles.numeral
val Typography.numeralLarge: TextStyle get() = DuelloTextStyles.numeralLarge

/**
 * Tabular figures for timers, progress, scores, rating and countdowns (tokens:
 * fontFamily.brand.features). Plus Jakarta Sans has `tnum`; Fraunces has none, so do not use
 * this on display styles for numbers that change while visible.
 */
fun TextStyle.tabularFigures(): TextStyle = copy(fontFeatureSettings = "tnum")

package com.sabit.duelloquiz.core.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** typography.scale and typography.custom in design/tokens.json must match the theme 1:1. */
class TypographyTokensTest {

    private val typography: JsonObject by lazy { readTokens().getValue("typography").jsonObject }

    @Test
    fun everyScaleStyleMatchesTokens() {
        val styles: Map<String, TextStyle> = with(DuelloTypography) {
            mapOf(
                "displayLarge" to displayLarge, "displayMedium" to displayMedium, "displaySmall" to displaySmall,
                "headlineLarge" to headlineLarge, "headlineMedium" to headlineMedium, "headlineSmall" to headlineSmall,
                "titleLarge" to titleLarge, "titleMedium" to titleMedium, "titleSmall" to titleSmall,
                "bodyLarge" to bodyLarge, "bodyMedium" to bodyMedium, "bodySmall" to bodySmall,
                "labelLarge" to labelLarge, "labelMedium" to labelMedium, "labelSmall" to labelSmall,
            )
        }
        val scale = typography.getValue("scale").jsonObject
        assertEquals(scale.keys, styles.keys)
        styles.forEach { (name, style) -> assertStyle(name, scale.getValue(name).jsonObject, style) }
    }

    @Test
    fun customStylesMatchTokens() {
        val styles = mapOf(
            "overline" to DuelloTextStyles.overline,
            "numeral" to DuelloTextStyles.numeral,
            "numeralLarge" to DuelloTextStyles.numeralLarge,
        )
        val custom = typography.getValue("custom").jsonObject
        assertEquals(custom.keys, styles.keys)
        styles.forEach { (name, style) -> assertStyle(name, custom.getValue(name).jsonObject, style) }
    }

    private fun assertStyle(name: String, token: JsonObject, style: TextStyle) {
        assertEquals("$name.fontSize", token.getValue("fontSize").jsonPrimitive.double, style.fontSize.value.toDouble(), EPS)
        assertEquals("$name.lineHeight", token.getValue("lineHeight").jsonPrimitive.double, style.lineHeight.value.toDouble(), EPS)
        assertEquals("$name.fontWeight", token.getValue("fontWeight").jsonPrimitive.int, style.fontWeight?.weight)
        assertEquals(
            "$name.letterSpacing",
            token.getValue("letterSpacing").jsonPrimitive.double,
            style.letterSpacing.value.toDouble(),
            EPS,
        )
        val expectedFamily: FontFamily = when (val family = token.getValue("family").jsonPrimitive.content) {
            "display" -> DisplayFontFamily
            "brand" -> BrandFontFamily
            else -> throw AssertionError("$name: unknown family $family")
        }
        assertEquals("$name.fontFamily", expectedFamily, style.fontFamily)
    }

    private companion object {
        const val EPS = 0.001
    }
}

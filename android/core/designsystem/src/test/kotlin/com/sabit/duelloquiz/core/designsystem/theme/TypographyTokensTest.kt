package com.sabit.duelloquiz.core.designsystem.theme

import androidx.compose.ui.text.TextStyle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** typography.scale in design/tokens.json must match [DuelloTypography] 1:1. */
class TypographyTokensTest {

    private val scale: JsonObject by lazy {
        Json.parseToJsonElement(findTokensFile().readText()).jsonObject
            .getValue("typography").jsonObject.getValue("scale").jsonObject
    }

    @Test
    fun everyStyleMatchesTokens() {
        val styles: Map<String, TextStyle> = with(DuelloTypography) {
            mapOf(
                "displayLarge" to displayLarge, "displayMedium" to displayMedium, "displaySmall" to displaySmall,
                "headlineLarge" to headlineLarge, "headlineMedium" to headlineMedium, "headlineSmall" to headlineSmall,
                "titleLarge" to titleLarge, "titleMedium" to titleMedium, "titleSmall" to titleSmall,
                "bodyLarge" to bodyLarge, "bodyMedium" to bodyMedium, "bodySmall" to bodySmall,
                "labelLarge" to labelLarge, "labelMedium" to labelMedium, "labelSmall" to labelSmall,
            )
        }
        assertEquals(scale.keys, styles.keys)
        styles.forEach { (name, style) ->
            val token = scale.getValue(name).jsonObject
            assertEquals("$name.fontSize", token.getValue("fontSize").jsonPrimitive.double, style.fontSize.value.toDouble(), EPS)
            assertEquals("$name.lineHeight", token.getValue("lineHeight").jsonPrimitive.double, style.lineHeight.value.toDouble(), EPS)
            assertEquals("$name.fontWeight", token.getValue("fontWeight").jsonPrimitive.int, style.fontWeight?.weight)
            assertEquals(
                "$name.letterSpacing",
                token.getValue("letterSpacing").jsonPrimitive.double,
                style.letterSpacing.value.toDouble(),
                EPS,
            )
            assertEquals("$name.fontFamily", NunitoFontFamily, style.fontFamily)
        }
    }

    private companion object {
        const val EPS = 0.001
    }
}

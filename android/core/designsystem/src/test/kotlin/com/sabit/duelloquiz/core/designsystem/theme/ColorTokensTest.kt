package com.sabit.duelloquiz.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every colour in design/tokens.json must be mapped 1:1 to the Compose theme:
 * color.<theme>.scheme -> ColorScheme property of the same name,
 * color.<theme>.game -> GameColors property of the same name,
 * color.<theme>.category.<slot> -> CategoryColors in the slot map,
 * color.<theme>.gradient.<name> -> GradientColors of the same name,
 * color.categoryAccentOrder / categoryAccentDefaults -> CategoryAccent.
 */
class ColorTokensTest {

    private val tokens: JsonObject by lazy { readTokens() }

    @Test
    fun lightSchemeMatchesTokens() = assertMatches("light", "scheme", LightColorScheme)

    @Test
    fun darkSchemeMatchesTokens() = assertMatches("dark", "scheme", DarkColorScheme)

    @Test
    fun lightGameColorsMatchTokens() = assertMatches("light", "game", LightGameColors)

    @Test
    fun darkGameColorsMatchTokens() = assertMatches("dark", "game", DarkGameColors)

    @Test
    fun gameColorsHaveNoPropertyWithoutToken() {
        val tokenKeys = colorGroup("light", "game").keys
        val properties = GameColors::class.java.declaredMethods
            .map { it.name }
            .filter { it.startsWith("get") && it.contains('-') }
            .map { it.removePrefix("get").substringBefore('-').replaceFirstChar(Char::lowercaseChar) }
            .toSet()
        assertEquals(tokenKeys, properties)
    }

    @Test
    fun lightCategoryColorsMatchTokens() = assertCategories("light", LightCategoryColors)

    @Test
    fun darkCategoryColorsMatchTokens() = assertCategories("dark", DarkCategoryColors)

    @Test
    fun lightGradientsMatchTokens() = assertGradients("light", LightGradients)

    @Test
    fun darkGradientsMatchTokens() = assertGradients("dark", DarkGradients)

    @Test
    fun accentOrderAndDefaultsMatchTokens() {
        val color = tokens.getValue("color").jsonObject
        val order = color.getValue("categoryAccentOrder").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(order, CategoryAccent.Order)
        assertEquals(order.toSet(), LightCategoryColors.keys)
        assertEquals(order.toSet(), DarkCategoryColors.keys)
        val defaults = color.getValue("categoryAccentDefaults").jsonObject
            .filterKeys { it != "notes" }
            .mapValues { it.value.jsonPrimitive.content }
        assertEquals(defaults, CategoryAccent.Defaults)
    }

    private fun assertCategories(theme: String, palette: Map<String, CategoryColors>) {
        val slots = tokens.getValue("color").jsonObject.getValue(theme).jsonObject.getValue("category").jsonObject
        assertEquals(slots.keys, palette.keys)
        slots.forEach { (slot, roles) ->
            val colors = palette.getValue(slot)
            roles.jsonObject.forEach { (role, hex) ->
                assertEquals(
                    "color.$theme.category.$slot.$role",
                    parseHex(hex.jsonPrimitive.content).toHex(),
                    readColor(colors, role).toHex(),
                )
            }
        }
    }

    private fun assertGradients(theme: String, gradients: DuelloGradients) {
        val group = tokens.getValue("color").jsonObject.getValue(theme).jsonObject.getValue("gradient").jsonObject
        assertEquals(setOf("duelHero", "resultWin"), group.keys)
        group.forEach { (name, value) ->
            val token = value.jsonObject
            val actual = when (name) {
                "duelHero" -> gradients.duelHero
                else -> gradients.resultWin
            }
            assertEquals("$theme.$name.start", parseHex(token.getValue("start").jsonPrimitive.content).toHex(), actual.start.toHex())
            assertEquals("$theme.$name.end", parseHex(token.getValue("end").jsonPrimitive.content).toHex(), actual.end.toHex())
            assertEquals("$theme.$name.on", parseHex(token.getValue("on").jsonPrimitive.content).toHex(), actual.on.toHex())
            assertEquals("$theme.$name.angle", token.getValue("angle").jsonPrimitive.content.toFloat(), actual.angleDegrees)
        }
    }

    private fun assertMatches(theme: String, group: String, target: Any) {
        val entries = colorGroup(theme, group)
        assertTrue("no tokens for $theme.$group", entries.isNotEmpty())
        entries.forEach { (key, value) ->
            val expected = parseHex(value.jsonPrimitive.content)
            val actual = readColor(target, key)
            assertEquals("color.$theme.$group.$key", expected.toHex(), actual.toHex())
        }
    }

    private fun colorGroup(theme: String, group: String): JsonObject =
        tokens.getValue("color").jsonObject.getValue(theme).jsonObject.getValue(group).jsonObject

    /**
     * Reads a Color property by its token name. Color is a value class, so the JVM getter name
     * is mangled ("getPrimary-0d7_KjU") and returns the packed long.
     */
    private fun readColor(target: Any, key: String): Color {
        val getterPrefix = "get" + key.replaceFirstChar(Char::uppercaseChar)
        val getter = target.javaClass.methods.firstOrNull {
            it.parameterCount == 0 && (it.name == getterPrefix || it.name.startsWith("$getterPrefix-"))
        } ?: throw AssertionError("${target.javaClass.simpleName} has no property '$key'")
        val packed = getter.invoke(target) as Long
        return Color(packed.toULong())
    }

    private fun parseHex(hex: String): Color {
        val rgb = hex.removePrefix("#").toLong(radix = 16)
        return Color(0xFF000000 or rgb)
    }

    private fun Color.toHex(): String = "%08X".format(toArgb())
}

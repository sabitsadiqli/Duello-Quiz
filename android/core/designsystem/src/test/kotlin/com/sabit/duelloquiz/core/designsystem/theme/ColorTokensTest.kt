package com.sabit.duelloquiz.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every colour in design/tokens.json must be mapped 1:1 to the Compose theme:
 * color.<theme>.scheme -> ColorScheme property of the same name,
 * color.<theme>.game -> GameColors property of the same name.
 */
class ColorTokensTest {

    private val tokens: JsonObject by lazy {
        Json.parseToJsonElement(findTokensFile().readText()).jsonObject
    }

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

    private fun findTokensFile(): File {
        var dir: File? = File(checkNotNull(System.getProperty("user.dir"))).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "design/tokens.json")
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        throw AssertionError("design/tokens.json not found above ${System.getProperty("user.dir")}")
    }
}

package com.sabit.duelloquiz.core.designsystem.theme

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The XML colours used before Compose draws (window, splash) and by the system (notification
 * accent) follow design/tokens.json (design/brand.md v3, OQ-D37): surface and primary per theme.
 */
class BrandResourceColorsTest {

    private val tokens by lazy { readTokens() }

    @Test
    fun lightResourceColorsMatchTokens() = assertColors(qualifier = "values", theme = "light")

    @Test
    fun darkResourceColorsMatchTokens() = assertColors(qualifier = "values-night", theme = "dark")

    private fun assertColors(qualifier: String, theme: String) {
        val colors = readColors(qualifier)
        val scheme = tokens.getValue("color").jsonObject.getValue(theme).jsonObject.getValue("scheme").jsonObject
        fun token(key: String) = "FF" + scheme.getValue(key).jsonPrimitive.content.removePrefix("#").uppercase()
        assertEquals("$qualifier ds_window_background", token("surface"), colors["ds_window_background"])
        assertEquals("$qualifier splash_background", token("surface"), colors["splash_background"])
        assertEquals("$qualifier notification_accent", token("primary"), colors["notification_accent"])
    }

    private fun readColors(qualifier: String): Map<String, String> {
        val file = File(checkNotNull(System.getProperty("user.dir")), "src/main/res/$qualifier/colors.xml")
        return COLOR.findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2].uppercase() }
    }

    private companion object {
        val COLOR = Regex("""<color name="([a-z_]+)">#([0-9A-Fa-f]{8})</color>""")
    }
}

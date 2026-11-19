package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import com.sabit.duelloquiz.core.designsystem.theme.DarkColorScheme
import com.sabit.duelloquiz.core.designsystem.theme.LightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** design/brand.md v3: back bubble = primary, front bubble = onSurface, '?' = surface; no fixed colours. */
class LogoMarkTest {

    @Test
    fun lightMarkUsesSchemeRoles() = assertRoles(
        back = LightColorScheme.primary,
        front = LightColorScheme.onSurface,
        glyph = LightColorScheme.surface,
    )

    @Test
    fun darkMarkUsesSchemeRoles() = assertRoles(
        back = DarkColorScheme.primary,
        front = DarkColorScheme.onSurface,
        glyph = DarkColorScheme.surface,
    )

    @Test
    fun viewportIsTheSvgViewBox() {
        val mark = duelloLogoMark(Color.Red, Color.Green, Color.Blue)
        assertEquals(66f, mark.viewportWidth)
        assertEquals(66f, mark.viewportHeight)
    }

    private fun assertRoles(back: Color, front: Color, glyph: Color) {
        val paths = duelloLogoMark(back, front, glyph).root.paths()
        assertEquals(3, paths.size)
        assertTrue(paths.all { it.pathData.isNotEmpty() })
        assertEquals(listOf(back, front, glyph), paths.map { (it.fill as SolidColor).value })
    }

    private fun VectorNode.paths(): List<VectorPath> = when (this) {
        is VectorPath -> listOf(this)
        is VectorGroup -> flatMap { it.paths() }
    }
}

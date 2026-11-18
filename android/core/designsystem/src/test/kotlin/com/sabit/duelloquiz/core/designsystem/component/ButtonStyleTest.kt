package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.ui.graphics.Color
import com.sabit.duelloquiz.core.designsystem.theme.DarkColorScheme
import com.sabit.duelloquiz.core.designsystem.theme.LightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** components.md §1 v3 variant table. */
class ButtonStyleTest {

    private val scheme = LightColorScheme

    @Test
    fun variantsUseTheirSchemeRoles() {
        assertEquals(ButtonStyle(scheme.primary, scheme.onPrimary, null), ButtonVariant.Primary.style(scheme))
        assertEquals(ButtonStyle(Color.Transparent, scheme.onSurface, scheme.outline), ButtonVariant.Secondary.style(scheme))
        assertEquals(ButtonStyle(scheme.surface, scheme.onSurface, null), ButtonVariant.OnInk.style(scheme))
        assertEquals(ButtonStyle(scheme.error, scheme.onError, null), ButtonVariant.Destructive.style(scheme))
    }

    @Test
    fun onlySecondaryHasABorder() {
        assertEquals(scheme.outline, ButtonVariant.Secondary.style(scheme).border)
        ButtonVariant.entries.filter { it != ButtonVariant.Secondary }.forEach { assertNull(it.name, it.style(scheme).border) }
    }

    @Test
    fun onInkIsPaperOnInkInBothThemes() {
        assertEquals(DarkColorScheme.surface, ButtonVariant.OnInk.style(DarkColorScheme).face)
        assertEquals(DarkColorScheme.onSurface, ButtonVariant.OnInk.style(DarkColorScheme).label)
    }
}

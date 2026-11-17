package com.sabit.duelloquiz.core.designsystem.component

import com.sabit.duelloquiz.core.designsystem.theme.DarkColorScheme
import com.sabit.duelloquiz.core.designsystem.theme.DarkGameColors
import com.sabit.duelloquiz.core.designsystem.theme.Borders
import com.sabit.duelloquiz.core.designsystem.theme.LightColorScheme
import com.sabit.duelloquiz.core.designsystem.theme.LightGameColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** components.md §3 state table. */
class OptionCardStyleTest {

    private val scheme = LightColorScheme
    private val game = LightGameColors

    private fun style(state: OptionCardState) = optionCardStyle(state, scheme, game)

    @Test
    fun correctAndWrongNeverDifferByColourAlone() {
        val correct = style(OptionCardState.Correct)
        val wrong = style(OptionCardState.Wrong)
        assertEquals(OptionBadgeGlyph.Check, correct.glyph)
        assertEquals(OptionOutcome.Correct, correct.outcome)
        assertEquals(OptionBadgeGlyph.Cross, wrong.glyph)
        assertEquals(OptionOutcome.Wrong, wrong.outcome)
    }

    @Test
    fun onlyCorrectAndWrongShowAnOutcomeOrGlyph() {
        OptionCardState.entries.filter { it != OptionCardState.Correct && it != OptionCardState.Wrong }.forEach {
            assertEquals(it.name, OptionBadgeGlyph.Letter, style(it).glyph)
            assertNull(it.name, style(it).outcome)
        }
    }

    @Test
    fun defaultIsAFlatCardWithAHairline() {
        val default = style(OptionCardState.Default)
        assertEquals(scheme.surface, default.face)
        assertEquals(scheme.outline, default.border)
        assertEquals(Borders.hairline, default.borderWidth)
        assertFalse(default.dashed)
        assertEquals(scheme.surfaceContainerHigh, default.badge)
        assertEquals(scheme.onSurface, default.onBadge)
    }

    @Test
    fun revealedStatesUseTheirGameRolesAndAnEmphasisBorder() {
        val correct = style(OptionCardState.Correct)
        assertEquals(game.correctContainer, correct.face)
        assertEquals(game.correct, correct.border)
        assertEquals(game.correct, correct.badge)
        assertEquals(game.onCorrect, correct.onBadge)
        assertEquals(Borders.emphasis, correct.borderWidth)

        val wrong = style(OptionCardState.Wrong)
        assertEquals(game.wrongContainer, wrong.face)
        assertEquals(game.wrong, wrong.border)
        assertEquals(Borders.emphasis, wrong.borderWidth)
    }

    @Test
    fun selectedIsMarkedInInkNotTheAccent() {
        val selected = style(OptionCardState.Selected)
        assertEquals(game.pendingContainer, selected.face)
        assertEquals(scheme.onSurface, selected.border)
        assertEquals(Borders.emphasis, selected.borderWidth)
        assertEquals(scheme.inverseSurface, selected.badge)
        assertEquals(scheme.inverseOnSurface, selected.onBadge)
        assertNotEquals(scheme.primary, selected.border)
        assertEquals(OptionTrailing.Progress, selected.trailing)
    }

    @Test
    fun dimmedIsReadable() {
        val dimmed = style(OptionCardState.Dimmed)
        assertEquals(game.cardBorder, dimmed.border)
        assertEquals(scheme.onSurfaceVariant, dimmed.text)
        assertEquals(1f, dimmed.text.alpha)
    }

    @Test
    fun queuedIsDashedWithTheCloudIcon() {
        val queued = style(OptionCardState.Queued)
        assertEquals(OptionTrailing.CloudUpload, queued.trailing)
        assertTrue(queued.dashed)
        OptionCardState.entries.filter { it != OptionCardState.Queued }.forEach { assertFalse(it.name, style(it).dashed) }
    }

    @Test
    fun darkThemeResolvesItsOwnColours() {
        val light = style(OptionCardState.Correct)
        val dark = optionCardStyle(OptionCardState.Correct, DarkColorScheme, DarkGameColors)
        assertNotEquals(light.face, dark.face)
        assertEquals(DarkGameColors.correctContainer, dark.face)
    }
}

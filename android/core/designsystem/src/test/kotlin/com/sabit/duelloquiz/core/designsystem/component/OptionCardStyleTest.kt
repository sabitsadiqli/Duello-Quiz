package com.sabit.duelloquiz.core.designsystem.component

import com.sabit.duelloquiz.core.designsystem.theme.DarkColorScheme
import com.sabit.duelloquiz.core.designsystem.theme.DarkGameColors
import com.sabit.duelloquiz.core.designsystem.theme.Depth
import com.sabit.duelloquiz.core.designsystem.theme.LightColorScheme
import com.sabit.duelloquiz.core.designsystem.theme.LightGameColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun defaultIsAWhiteCardWithTheCardEdge() {
        val default = style(OptionCardState.Default)
        assertEquals(game.pressableNeutral, default.face)
        assertEquals(game.cardBorder, default.border)
        assertEquals(Depth.border, default.borderWidth)
        assertEquals(game.cardDepth, default.depth)
        assertEquals(scheme.primaryContainer, default.badge)
        assertEquals(scheme.onPrimaryContainer, default.onBadge)
    }

    @Test
    fun revealedStatesUseTheirGameRolesAndAThickBorder() {
        val correct = style(OptionCardState.Correct)
        assertEquals(game.correctContainer, correct.face)
        assertEquals(game.correct, correct.border)
        assertEquals(game.correctDepth, correct.depth)
        assertEquals(game.correct, correct.badge)
        assertEquals(game.onCorrect, correct.onBadge)
        assertEquals(Depth.borderThick, correct.borderWidth)

        val wrong = style(OptionCardState.Wrong)
        assertEquals(game.wrongContainer, wrong.face)
        assertEquals(game.wrong, wrong.border)
        assertEquals(game.wrongDepth, wrong.depth)
        assertEquals(Depth.borderThick, wrong.borderWidth)
    }

    @Test
    fun selectedShowsProgressAndPendingRoles() {
        val selected = style(OptionCardState.Selected)
        assertEquals(game.pendingContainer, selected.face)
        assertEquals(scheme.primary, selected.border)
        assertEquals(game.pendingDepth, selected.depth)
        assertEquals(OptionTrailing.Progress, selected.trailing)
    }

    @Test
    fun dimmedIsFlatButReadable() {
        val dimmed = style(OptionCardState.Dimmed)
        assertNull(dimmed.depth)
        assertEquals(scheme.onSurfaceVariant, dimmed.text)
        assertEquals(1f, dimmed.text.alpha)
    }

    @Test
    fun queuedShowsTheCloudIcon() {
        val queued = style(OptionCardState.Queued)
        assertEquals(OptionTrailing.CloudUpload, queued.trailing)
        assertNotNull(queued.depth)
    }

    @Test
    fun darkThemeResolvesItsOwnColours() {
        val light = style(OptionCardState.Correct)
        val dark = optionCardStyle(OptionCardState.Correct, DarkColorScheme, DarkGameColors)
        assertNotEquals(light.face, dark.face)
        assertEquals(DarkGameColors.correctContainer, dark.face)
    }
}

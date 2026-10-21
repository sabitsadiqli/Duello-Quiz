package com.sabit.duelloquiz.core.designsystem.component

import com.sabit.duelloquiz.core.designsystem.theme.LightGameColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** components.md §1 variant table. */
class ButtonStyleTest {

    private val game = LightGameColors

    @Test
    fun variantsUseTheirPressableRoles() {
        assertEquals(
            ButtonStyle(game.pressablePrimary, game.onPressablePrimary, game.pressablePrimaryDepth, null),
            ButtonVariant.Primary.style(game),
        )
        assertEquals(
            ButtonStyle(game.pressableDuel, game.onPressableDuel, game.pressableDuelDepth, null),
            ButtonVariant.Duel.style(game),
        )
        assertEquals(
            ButtonStyle(game.pressableDestructive, game.onPressableDestructive, game.pressableDestructiveDepth, null),
            ButtonVariant.Destructive.style(game),
        )
    }

    @Test
    fun onlyNeutralHasABorder() {
        assertNotNull(ButtonVariant.Neutral.style(game).border)
        assertEquals(game.pressableNeutralBorder, ButtonVariant.Neutral.style(game).border)
        ButtonVariant.entries.filter { it != ButtonVariant.Neutral }.forEach { assertNull(it.style(game).border) }
    }
}

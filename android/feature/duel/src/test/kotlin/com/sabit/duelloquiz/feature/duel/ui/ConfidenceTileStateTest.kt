package com.sabit.duelloquiz.feature.duel.ui

import com.sabit.duelloquiz.core.designsystem.component.ConfidenceTileState
import com.sabit.duelloquiz.feature.duel.ChipState
import org.junit.Assert.assertEquals
import org.junit.Test

/** components.md §7: feature chip states → design-system Əminlik tile states. */
class ConfidenceTileStateTest {

    @Test
    fun everyChipStateMapsToTheSpecifiedTileState() {
        val expected = mapOf(
            ChipState.ENABLED to ConfidenceTileState.Enabled,
            ChipState.SELECTED to ConfidenceTileState.Sending,
            ChipState.LOCKED to ConfidenceTileState.Locked,
            // The server default (1) shows the same ring and lock as an own pick.
            ChipState.DEFAULTED to ConfidenceTileState.Locked,
            ChipState.FADED to ConfidenceTileState.Faded,
            ChipState.DISABLED to ConfidenceTileState.Disabled,
            ChipState.DISABLED_LIMIT to ConfidenceTileState.Disabled,
        )
        assertEquals(ChipState.entries.toSet(), expected.keys)
        expected.forEach { (chip, tile) -> assertEquals(chip.name, tile, chip.toTileState()) }
    }
}

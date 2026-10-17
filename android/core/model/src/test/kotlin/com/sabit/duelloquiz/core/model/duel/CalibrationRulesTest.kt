package com.sabit.duelloquiz.core.model.duel

import org.junit.Assert.assertEquals
import org.junit.Test

class CalibrationRulesTest {

    private fun levels(n1: Int, c1: Int, n2: Int, c2: Int, n3: Int, c3: Int) = listOf(
        CalibrationLevel(1, n1, c1),
        CalibrationLevel(2, n2, c2),
        CalibrationLevel(3, n3, c3),
    )

    @Test
    fun fewerThanThreeCountedRoundsOnTheCardIsFewData() {
        assertEquals(CalibrationHint.FEW_DATA, CalibrationRules.cardHint(levels(1, 1, 1, 0, 0, 0)))
        assertEquals(CalibrationHint.FEW_DATA, CalibrationRules.cardHint(levels(0, 0, 0, 0, 0, 0)))
    }

    @Test
    fun wrongOnHighConfidenceIsOverconfident() {
        // 3: 1 of 2 correct is not < 0.5, but level 1 is better (1/1).
        assertEquals(CalibrationHint.OVERCONFIDENT, CalibrationRules.cardHint(levels(1, 1, 2, 1, 2, 1)))
        // 3: 0 of 2 correct.
        assertEquals(CalibrationHint.OVERCONFIDENT, CalibrationRules.cardHint(levels(0, 0, 3, 3, 2, 0)))
    }

    @Test
    fun safePlayWithHighAccuracyIsUnderconfident() {
        assertEquals(CalibrationHint.UNDERCONFIDENT, CalibrationRules.cardHint(levels(5, 5, 1, 0, 0, 0)))
    }

    @Test
    fun accuracyRisingWithTheLevelIsGood() {
        assertEquals(CalibrationHint.GOOD, CalibrationRules.cardHint(levels(2, 1, 3, 2, 2, 2)))
    }

    @Test
    fun otherwiseNeutral() {
        // Level 2 worse than level 1, level 3 unused.
        assertEquals(CalibrationHint.NEUTRAL, CalibrationRules.cardHint(levels(2, 2, 3, 1, 0, 0)))
        // Only one level used.
        assertEquals(CalibrationHint.NEUTRAL, CalibrationRules.cardHint(levels(0, 0, 4, 2, 0, 0)))
    }

    @Test
    fun theProfileNeedsTenRoundsAndIgnoresLevelsWithFewerThanFivePicks() {
        assertEquals(CalibrationHint.FEW_DATA, CalibrationRules.profileHint(levels(3, 3, 3, 2, 3, 0)))
        // Level 3 has 4 picks (all wrong) → treated as unused, so not overconfident.
        assertEquals(CalibrationHint.GOOD, CalibrationRules.profileHint(levels(6, 3, 6, 4, 4, 0)))
        assertEquals(CalibrationHint.OVERCONFIDENT, CalibrationRules.profileHint(levels(6, 3, 6, 4, 5, 1)))
    }

    @Test
    fun percentIsRoundedHalfUp() {
        assertEquals(72, CalibrationRules.percent(CalibrationLevel(3, 18, 13)))
        assertEquals(67, CalibrationRules.percent(CalibrationLevel(1, 3, 2)))
        assertEquals(0, CalibrationRules.percent(CalibrationLevel(2, 0, 0)))
    }
}

package com.sabit.duelloquiz.backend.duel

import kotlin.test.Test
import kotlin.test.assertEquals

class DuelRulesTest {

    @Test
    fun `scoring table for every confidence level`() {
        // docs/mvp-spec.md §2: correct +100/+200/+300, wrong or timeout 0/−100/−200.
        assertEquals(listOf(100, 200, 300), (1..3).map { DuelRules.betPoints(it, correct = true, allIn = false) })
        assertEquals(listOf(0, -100, -200), (1..3).map { DuelRules.betPoints(it, correct = false, allIn = false) })
    }

    @Test
    fun `round 7 doubles only the bet points`() {
        assertEquals(listOf(200, 400, 600), (1..3).map { DuelRules.betPoints(it, correct = true, allIn = true) })
        assertEquals(listOf(0, -200, -400), (1..3).map { DuelRules.betPoints(it, correct = false, allIn = true) })
        // DUEL-10: Əminlik 3, correct, 8 s left → 600 + 40 = 640.
        assertEquals(640, DuelRules.roundPoints(3, correct = true, allIn = true, speedBonus = 40))
        assertEquals(-400, DuelRules.roundPoints(3, correct = false, allIn = true, speedBonus = 40))
    }

    @Test
    fun `speed bonus is floor of remaining time and clamped at the deadline`() {
        val deadline = 20_000L
        fun bonus(receivedAt: Long) = DuelRules.speedBonus(receivedAt, deadline, 10_000)
        assertEquals(50, bonus(10_000))      // instant answer
        assertEquals(31, bonus(13_800))      // 6.2 s left → floor(31.0) (DUEL-09 example)
        assertEquals(49, bonus(10_001))      // floor, never rounded up
        assertEquals(0, bonus(19_999))       // 1 ms left → floor(0.005)
        assertEquals(0, bonus(20_000))       // exactly at the deadline
        assertEquals(0, bonus(20_250))       // inside the grace window: never negative
        assertEquals(331, DuelRules.roundPoints(3, correct = true, allIn = false, speedBonus = bonus(13_800)))
        assertEquals(-100, DuelRules.roundPoints(2, correct = false, allIn = false, speedBonus = 50))
    }

    @Test
    fun `elo is zero-sum and rounded half away from zero`() {
        assertEquals(16, DuelRules.eloChangeA(1000, 1000, 1.0))
        assertEquals(-16, DuelRules.eloChangeA(1000, 1000, 0.0))
        assertEquals(0, DuelRules.eloChangeA(1000, 1000, 0.5))
        // 32 × (1 − 1/(1 + 10^(16/400))) = 16.73 → 17; the opponent gets exactly −17.
        assertEquals(17, DuelRules.eloChangeA(1000, 1016, 1.0))
        assertEquals(-17, -DuelRules.eloChangeA(1000, 1016, 1.0))
        // Draw against a much stronger player: 32 × (0.5 − 0.0909) = 13.09 → 13.
        assertEquals(13, DuelRules.eloChangeA(1000, 1400, 0.5))
        assertEquals(-13, DuelRules.eloChangeA(1400, 1000, 0.5))
        // No floor (OQ-039).
        assertEquals(-16, DuelRules.eloChangeA(5, 5, 0.0))
        assertEquals(3, DuelRules.roundHalfAwayFromZero(2.5))
        assertEquals(-3, DuelRules.roundHalfAwayFromZero(-2.5))
        assertEquals(2, DuelRules.roundHalfAwayFromZero(2.4999))
    }
}

package com.sabit.duelloquiz.feature.home

import com.sabit.duelloquiz.core.designsystem.component.StreakDayState
import com.sabit.duelloquiz.core.testing.testProgressSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Streak chip, greeting rotation and the Baku date line of the Oyna tab (HOME-04, HOME-08). */
class HomeRetentionTest {

    private val hour = 3_600_000L

    // Streak chip (HOME-08, PROG-06)

    @Test
    fun theChipShowsTheStreakAndNeverClaimsTodayFromAnOldCache() {
        val today = streakChipUi(testProgressSummary(streak = 7, todayActive = true, nextResetEpochMs = 10 * hour), false, true, 0)
            as StreakChipUi.Content
        assertEquals(7, today.days)
        assertEquals(StreakDayState.ActiveToday, today.state)
        assertEquals("2026-10-22", today.dayKey)

        val afterMidnight = streakChipUi(testProgressSummary(todayActive = true, nextResetEpochMs = 10 * hour), false, true, 11 * hour)
            as StreakChipUi.Content
        assertEquals(StreakDayState.NotYetToday, afterMidnight.state)

        val notYet = streakChipUi(testProgressSummary(todayActive = false), false, true, 0) as StreakChipUi.Content
        assertEquals(StreakDayState.NotYetToday, notYet.state)
    }

    @Test
    fun withoutProgressTheChipIsASkeletonThenHiddenNeverZero() {
        assertEquals(StreakChipUi.Skeleton, streakChipUi(null, loading = true, attempted = false, localNowMs = 0))
        assertEquals(StreakChipUi.Skeleton, streakChipUi(null, loading = true, attempted = true, localNowMs = 0))
        assertEquals(StreakChipUi.Hidden, streakChipUi(null, loading = false, attempted = true, localNowMs = 0))
    }

    @Test
    fun theFlameIgnitesOncePerDay() {
        assertTrue(StreakIgnite.firstTimeToday("2030-01-01"))
        assertFalse(StreakIgnite.firstTimeToday("2030-01-01"))
        assertTrue(StreakIgnite.firstTimeToday("2030-01-02"))
    }

    // Greeting rotation (home.md, OQ-020)

    @Test
    fun theGreetingIsStableForADayAndRotatesThroughTheWholePool() {
        val lines = (0L until GreetingPool.SIZE).map { GreetingPool.lineFor(20_000 + it, duelEnabled = true) }
        assertEquals((1..GreetingPool.SIZE).toSet(), lines.toSet())
        assertEquals(GreetingPool.lineFor(20_000, true), GreetingPool.lineFor(20_000, true))
        // The next day shows a different line.
        assertTrue(GreetingPool.lineFor(20_000, true) != GreetingPool.lineFor(20_001, true))
    }

    @Test
    fun withTheDuelFlagOffDuelLinesAreNeverShown() {
        val lines = (0L until 50L).map { GreetingPool.lineFor(it, duelEnabled = false) }.toSet()
        assertTrue(lines.none { it in GreetingPool.DUEL_LINES })
        assertEquals(GreetingPool.SIZE - GreetingPool.DUEL_LINES.size, lines.size)
    }

    @Test
    fun negativeEpochDaysStillPickALine() {
        assertTrue(GreetingPool.lineFor(-3, duelEnabled = true) in 1..GreetingPool.SIZE)
    }

    @Test
    fun theFirstNameIsTheFirstWord() {
        assertEquals("Aysel", GreetingPool.firstName("Aysel Məmmədova"))
        assertEquals("Aysel", GreetingPool.firstName("  Aysel  "))
        assertEquals("Elvin", GreetingPool.firstName("Elvin"))
        assertNull(GreetingPool.firstName("   "))
        assertNull(GreetingPool.firstName(null))
    }

    // Date line in Baku (HOME-04, OQ-074)

    @Test
    fun theDateLineFollowsTheBakuDayNotTheDeviceZone() {
        // 2026-11-16 20:30 UTC = 2026-11-17 00:30 in Baku (UTC+4), a Tuesday.
        val nowMs = 1_794_861_000_000L
        val today = todayUi(nowMs, duelEnabled = true)
        assertEquals(17, today.dayOfMonth)
        assertEquals(10, today.monthIndex)
        assertEquals(1, today.weekdayIndex)
        assertEquals(BakuDay.of(nowMs).epochDay, today.seed)
    }
}

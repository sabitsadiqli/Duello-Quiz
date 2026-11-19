package com.sabit.duelloquiz.backend.progress

import com.sabit.duelloquiz.backend.common.BakuTime
import com.sabit.duelloquiz.backend.progress.WeekDayState.ACTIVE
import com.sabit.duelloquiz.backend.progress.WeekDayState.FREEZE
import com.sabit.duelloquiz.backend.progress.WeekDayState.MISSED
import com.sabit.duelloquiz.backend.progress.WeekDayState.NONE
import com.sabit.duelloquiz.backend.progress.WeekDayState.TODAY_PENDING
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** OQ-D35: per-day states of the current Baku week on `GET /v1/me/progress`. */
class WeekRowTest {
    // Week of 2026-11-16 (Monday) … 2026-11-22 (Sunday).
    private val mon = LocalDate.of(2026, 11, 16)
    private val tue = mon.plusDays(1)
    private val wed = mon.plusDays(2)
    private val thu = mon.plusDays(3)
    private val fri = mon.plusDays(4)
    private val sun = mon.plusDays(6)

    private fun states(today: LocalDate, history: WeekHistory = WeekHistory(), streak: StreakState = StreakState()) =
        WeekRow.build(today, history, streak).map { it.second }

    private fun streakLastActive(day: LocalDate, current: Int = 1, freezeHeld: Boolean = false) =
        StreakState(current = current, best = current, lastActiveDay = day, freezeHeld = freezeHeld)

    @Test
    fun `monday and sunday of a week`() {
        assertEquals(mon, WeekRow.monday(mon))
        assertEquals(mon, WeekRow.monday(sun))
        assertEquals(sun, WeekRow.sunday(mon))
        assertEquals(sun, WeekRow.sunday(sun))
        assertEquals(mon.plusDays(7), WeekRow.monday(sun.plusDays(1)))
    }

    @Test
    fun `always 7 entries from Monday to Sunday`() {
        (0L until 21L).map { mon.minusDays(7).plusDays(it) }.forEach { today ->
            val row = WeekRow.build(today, WeekHistory(activeBeforeWeek = true), StreakState())
            assertEquals(7, row.size, "today=$today")
            assertEquals(DayOfWeek.MONDAY, row.first().first.dayOfWeek)
            assertEquals(DayOfWeek.SUNDAY, row.last().first.dayOfWeek)
            row.zipWithNext().forEach { (a, b) -> assertEquals(a.first.plusDays(1), b.first) }
            assertEquals(1, row.count { it.first == today })
        }
    }

    @Test
    fun `monday as today`() {
        assertEquals(
            listOf(TODAY_PENDING, NONE, NONE, NONE, NONE, NONE, NONE),
            states(mon, WeekHistory(activeBeforeWeek = true), streakLastActive(mon.minusDays(1))),
        )
        assertEquals(
            listOf(ACTIVE, NONE, NONE, NONE, NONE, NONE, NONE),
            states(mon, WeekHistory(activeDays = setOf(mon), activeBeforeWeek = true), streakLastActive(mon, 2)),
        )
    }

    @Test
    fun `sunday as today`() {
        val history = WeekHistory(activeDays = setOf(mon, tue, thu, fri), activeBeforeWeek = true)
        assertEquals(
            listOf(ACTIVE, ACTIVE, MISSED, ACTIVE, ACTIVE, MISSED, TODAY_PENDING),
            states(sun, history, streakLastActive(fri, 2)),
        )
        assertEquals(
            listOf(ACTIVE, ACTIVE, MISSED, ACTIVE, ACTIVE, MISSED, ACTIVE),
            states(sun, history.copy(activeDays = history.activeDays + sun), streakLastActive(sun, 1)),
        )
    }

    @Test
    fun `the Baku day decides the week position, not UTC`() {
        // 2026-11-18T20:00Z is 00:00 on Thursday 2026-11-19 in Baku (UTC+4).
        val thursday = BakuTime.day(Instant.parse("2026-11-18T20:00:00Z"))
        assertEquals(thu, thursday)
        assertEquals(DayOfWeek.THURSDAY, thursday.dayOfWeek)
        assertEquals(listOf(NONE, NONE, NONE, TODAY_PENDING, NONE, NONE, NONE), states(thursday))
        // One second earlier it is still Wednesday in Baku.
        val wednesday = BakuTime.day(Instant.parse("2026-11-18T19:59:59Z"))
        assertEquals(wed, wednesday)
        assertEquals(listOf(NONE, NONE, TODAY_PENDING, NONE, NONE, NONE, NONE), states(wednesday))
        // Sunday 23:59 Baku vs Monday 00:00 Baku: a new week starts at Baku midnight.
        assertEquals(sun, BakuTime.day(Instant.parse("2026-11-22T19:59:59Z")))
        assertEquals(mon.plusDays(7), WeekRow.monday(BakuTime.day(Instant.parse("2026-11-22T20:00:00Z"))))
    }

    @Test
    fun `a stored freeze day shows as freeze`() {
        val history = WeekHistory(activeDays = setOf(mon, wed, thu), freezeDays = setOf(tue), activeBeforeWeek = true)
        assertEquals(
            listOf(ACTIVE, FREEZE, ACTIVE, ACTIVE, TODAY_PENDING, NONE, NONE),
            states(fri, history, streakLastActive(thu, 10)),
        )
    }

    @Test
    fun `a held freeze covering yesterday shows as freeze before it is consumed`() {
        // Last active Tuesday, freeze held, today Thursday: Wednesday is covered right now.
        val streak = streakLastActive(tue, 8, freezeHeld = true)
        val history = WeekHistory(activeDays = setOf(mon, tue), activeBeforeWeek = true)
        assertEquals(wed, Streaks.pendingFreezeDay(streak, thu))
        assertEquals(listOf(ACTIVE, ACTIVE, FREEZE, TODAY_PENDING, NONE, NONE, NONE), states(thu, history, streak))
        // Without a freeze held the same day is missed.
        assertNull(Streaks.pendingFreezeDay(streak.copy(freezeHeld = false), thu))
        assertEquals(listOf(ACTIVE, ACTIVE, MISSED, TODAY_PENDING, NONE, NONE, NONE), states(thu, history, streak.copy(freezeHeld = false)))
        // One day later the freeze cannot cover two days: both missed.
        assertNull(Streaks.pendingFreezeDay(streak, fri))
        assertEquals(listOf(ACTIVE, ACTIVE, MISSED, MISSED, TODAY_PENDING, NONE, NONE), states(fri, history, streak))
    }

    @Test
    fun `a past day without activity after the first active day is missed`() {
        val history = WeekHistory(activeDays = setOf(mon, wed), activeBeforeWeek = true)
        assertEquals(
            listOf(ACTIVE, MISSED, ACTIVE, TODAY_PENDING, NONE, NONE, NONE),
            states(thu, history, streakLastActive(wed)),
        )
        // Activity only before the week: every past day of this week is missed.
        assertEquals(
            listOf(MISSED, MISSED, MISSED, TODAY_PENDING, NONE, NONE, NONE),
            states(thu, WeekHistory(activeBeforeWeek = true), streakLastActive(mon.minusDays(3), 4)),
        )
    }

    @Test
    fun `first activity mid-week leaves earlier days none`() {
        val history = WeekHistory(activeDays = setOf(wed))
        assertEquals(
            listOf(NONE, NONE, ACTIVE, MISSED, TODAY_PENDING, NONE, NONE),
            states(fri, history, streakLastActive(wed)),
        )
    }

    @Test
    fun `a new user sees only today pending`() {
        assertEquals(listOf(NONE, NONE, NONE, TODAY_PENDING, NONE, NONE, NONE), states(thu))
        assertEquals(listOf(NONE, NONE, NONE, NONE, NONE, NONE, TODAY_PENDING), states(sun))
    }

    @Test
    fun `today is pending until played, then active`() {
        val before = WeekHistory(activeDays = setOf(wed), activeBeforeWeek = true)
        assertEquals(TODAY_PENDING, states(thu, before, streakLastActive(wed, 3))[3])
        val after = before.copy(activeDays = before.activeDays + thu)
        assertEquals(ACTIVE, states(thu, after, streakLastActive(thu, 4))[3])
    }

    @Test
    fun `future days are none even when stored facts exist for them`() {
        val history = WeekHistory(activeDays = setOf(sun), freezeDays = setOf(fri), activeBeforeWeek = true)
        assertEquals(listOf(MISSED, MISSED, TODAY_PENDING, NONE, NONE, NONE, NONE), states(wed, history))
    }

    @Test
    fun `replay equals simulate and collects two freezes in one week`() {
        // Day 1 = 2026-11-01. Days 1–7 earn a freeze; 8 and 9 missed → reset on 10, freeze
        // kept; 10–15 → 6; 16 missed, 17 played → freeze used (7, which grants a new one);
        // 18 missed, 19 played → second freeze used. Days 16 and 18 = Monday and Wednesday.
        val d1 = LocalDate.of(2026, 11, 1)
        val days = (listOf(1L, 2, 3, 4, 5, 6, 7) + (10L..15L) + listOf(17L, 19L)).map { d1.plusDays(it - 1) }
        val replay = Streaks.replay(days.shuffled(Random(3)))
        assertEquals(Streaks.simulate(days), replay.state)
        assertEquals(days.fold(StreakState()) { s, d -> Streaks.apply(s, d) }, replay.state)
        assertEquals(listOf(mon, wed), replay.freezeDays)
        assertEquals(8, replay.state.current)
        assertEquals(wed, replay.state.freezeUsedDay)

        val history = WeekHistory(
            activeDays = days.filter { !it.isBefore(mon) }.toSet(),
            freezeDays = replay.freezeDays.toSet(),
            activeBeforeWeek = true,
        )
        assertEquals(listOf(FREEZE, ACTIVE, FREEZE, ACTIVE, NONE, NONE, NONE), states(thu, history, replay.state))
    }

    @Test
    fun `replay without freezes collects none`() {
        val days = (0L until 5L).map { mon.plusDays(it) }
        val replay = Streaks.replay(days)
        assertEquals(emptyList(), replay.freezeDays)
        assertEquals(Streaks.simulate(days), replay.state)
        assertEquals(Streaks.Replay(StreakState(), emptyList()), Streaks.replay(emptyList()))
    }
}

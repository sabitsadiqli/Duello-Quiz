package com.sabit.duelloquiz.feature.home

import com.sabit.duelloquiz.core.designsystem.component.WeekDotState
import com.sabit.duelloquiz.core.designsystem.component.WeekDotState.Active
import com.sabit.duelloquiz.core.designsystem.component.WeekDotState.Freeze
import com.sabit.duelloquiz.core.designsystem.component.WeekDotState.Missed
import com.sabit.duelloquiz.core.designsystem.component.WeekDotState.TodayPending
import com.sabit.duelloquiz.core.designsystem.component.WeekDotState.Unknown
import com.sabit.duelloquiz.core.model.WeekDay
import com.sabit.duelloquiz.core.model.WeekDayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Baku day math, the server week states and the OQ-D35 week-row fallback (daily-tab.md B, PROG-11). */
class BakuDayTest {

    /** 2026-11-18, a Wednesday (index 2); Monday of that week is 2026-11-16. */
    private val wednesday = BakuDay.parse("2026-11-18")!!.epochDay
    private val monday = wednesday - 2

    private fun states(
        today: Long = wednesday,
        progressDay: Long? = today,
        streak: Int,
        todayActive: Boolean,
        freeze: Long? = null,
        missed: Long? = null,
    ): List<WeekDotState> = BakuDay.fallbackWeek(today, progressDay, streak, todayActive, freeze, missed).map { it.state }

    @Test
    fun parsesDayKeysAndWeekdays() {
        val day = BakuDay.parse("2026-11-18")!!
        assertEquals(18, day.dayOfMonth)
        assertEquals(10, day.monthIndex)
        assertEquals(2026, day.year)
        assertEquals(2, day.weekdayIndex)
        assertEquals(0, BakuDay.weekdayIndex(monday))
        assertNull(BakuDay.parse("2026-13-01"))
        assertNull(BakuDay.parse("yesterday"))
    }

    @Test
    fun theBakuDayChangesAtBakuMidnight() {
        // 2026-11-17 19:59:59 UTC = 23:59:59 Baku; one second later it is the 18th in Baku.
        val beforeMidnight = 1_794_945_599_000L
        assertEquals(17, BakuDay.of(beforeMidnight).dayOfMonth)
        assertEquals(18, BakuDay.of(beforeMidnight + 1_000).dayOfMonth)
    }

    @Test
    fun theWeekIsMondayToSundayWithTodayMarked() {
        val week = BakuDay.fallbackWeek(wednesday, wednesday, 0, false)
        assertEquals((0..6).toList(), week.map { it.weekdayIndex })
        assertEquals(listOf(false, false, true, false, false, false, false), week.map { it.isToday })
    }

    @Test
    fun anActiveTodayEndsTheStreakToday() {
        assertEquals(
            listOf(Active, Active, Active, Unknown, Unknown, Unknown, Unknown),
            states(streak = 3, todayActive = true),
        )
    }

    @Test
    fun notYetTodayEndsTheStreakYesterdayAndTodayIsDashed() {
        assertEquals(
            listOf(Active, Active, TodayPending, Unknown, Unknown, Unknown, Unknown),
            states(streak = 2, todayActive = false),
        )
        // One day of streak: only yesterday; Monday is not claimed as missed.
        assertEquals(
            listOf(Unknown, Active, TodayPending, Unknown, Unknown, Unknown, Unknown),
            states(streak = 1, todayActive = false),
        )
    }

    @Test
    fun aLongStreakFillsTheWeekUpToTodayOnly() {
        assertEquals(
            listOf(Active, Active, Active, Unknown, Unknown, Unknown, Unknown),
            states(streak = 40, todayActive = true),
        )
        // On a Monday nothing of the previous week is shown.
        assertEquals(
            listOf(Active, Unknown, Unknown, Unknown, Unknown, Unknown, Unknown),
            states(today = monday, streak = 5, todayActive = true),
        )
    }

    @Test
    fun aCacheFromYesterdayNeverClaimsToday() {
        // Cached on Tuesday with Tuesday active; it is Wednesday now (PROG-06).
        assertEquals(
            listOf(Active, Active, TodayPending, Unknown, Unknown, Unknown, Unknown),
            states(progressDay = wednesday - 1, streak = 2, todayActive = true),
        )
    }

    @Test
    fun aNewUserSeesOnlyTodayDashed() {
        assertEquals(
            listOf(Unknown, Unknown, TodayPending, Unknown, Unknown, Unknown, Unknown),
            states(streak = 0, todayActive = false),
        )
    }

    @Test
    fun freezeAndMissedDotsOnlyFromServerEvents() {
        assertEquals(
            listOf(Active, Freeze, Active, Unknown, Unknown, Unknown, Unknown),
            states(streak = 3, todayActive = true, freeze = monday + 1),
        )
        assertEquals(
            listOf(Missed, Unknown, Active, Unknown, Unknown, Unknown, Unknown),
            states(streak = 1, todayActive = true, missed = monday),
        )
        // An event day outside this week changes nothing.
        assertEquals(states(streak = 1, todayActive = true), states(streak = 1, todayActive = true, missed = monday - 3))
    }

    // Server week states (OQ-D35)

    /** The example of docs/api/openapi.yaml 0.4.0: Monday 2026-11-16 … Sunday 2026-11-22, today Thursday. */
    private val serverExample = listOf(
        WeekDay("2026-11-16", WeekDayState.ACTIVE),
        WeekDay("2026-11-17", WeekDayState.FREEZE),
        WeekDay("2026-11-18", WeekDayState.MISSED),
        WeekDay("2026-11-19", WeekDayState.TODAY_PENDING),
        WeekDay("2026-11-20", WeekDayState.NONE),
        WeekDay("2026-11-21", WeekDayState.NONE),
        WeekDay("2026-11-22", WeekDayState.NONE),
    )
    private val thursday = wednesday + 1

    private fun server(today: Long, week: List<WeekDay>) = BakuDay.serverWeek(today, week).map { it.state }

    @Test
    fun serverStatesMapToTheirDots() {
        val week = BakuDay.serverWeek(thursday, serverExample)
        assertEquals(listOf(Active, Freeze, Missed, TodayPending, Unknown, Unknown, Unknown), week.map { it.state })
        assertEquals((0..6).toList(), week.map { it.weekdayIndex })
        assertEquals(listOf(false, false, false, true, false, false, false), week.map { it.isToday })
    }

    @Test
    fun anActiveTodayIsFilled() {
        val played = serverExample.map { if (it.dayKey == "2026-11-19") it.copy(state = WeekDayState.ACTIVE) else it }
        assertEquals(listOf(Active, Freeze, Missed, Active, Unknown, Unknown, Unknown), server(thursday, played))
    }

    @Test
    fun noneAndUnknownStatesAreNeutralAndFutureDaysAreNeverClaimed() {
        val odd = listOf(
            WeekDay("2026-11-16", WeekDayState.NONE),
            WeekDay("2026-11-17", WeekDayState.TODAY_PENDING),
            WeekDay("not-a-day", WeekDayState.ACTIVE),
            WeekDay("2026-11-19", WeekDayState.NONE),
            WeekDay("2026-11-20", WeekDayState.ACTIVE),
        )
        assertEquals(listOf(Unknown, Unknown, Unknown, TodayPending, Unknown, Unknown, Unknown), server(thursday, odd))
    }

    @Test
    fun aStaleWeekOfTheSameWeekKeepsPastFactsAndNeverClaimsToday() {
        // Cached on Thursday (today_pending), it is Friday now: Thursday is not claimed as missed.
        assertEquals(listOf(Active, Freeze, Missed, Unknown, TodayPending, Unknown, Unknown), server(thursday + 1, serverExample))
        // Cached on Thursday with Thursday active: the fact stays, Friday is dashed.
        val played = serverExample.map { if (it.dayKey == "2026-11-19") it.copy(state = WeekDayState.ACTIVE) else it }
        assertEquals(listOf(Active, Freeze, Missed, Active, TodayPending, Unknown, Unknown), server(thursday + 1, played))
    }

    @Test
    fun aStaleWeekOfAPreviousWeekShowsTheCurrentWeekNeutral() {
        // Cached last week, it is Monday 2026-11-23 now: last week is not a history (PROG-11).
        val nextMonday = monday + 7
        assertEquals(
            listOf(TodayPending, Unknown, Unknown, Unknown, Unknown, Unknown, Unknown),
            server(nextMonday, serverExample),
        )
        assertEquals(
            listOf(Unknown, Unknown, TodayPending, Unknown, Unknown, Unknown, Unknown),
            server(nextMonday + 2, serverExample),
        )
    }
}

package com.sabit.duelloquiz.feature.home.daily

import com.sabit.duelloquiz.core.data.repository.DailyProgress
import com.sabit.duelloquiz.core.designsystem.component.AnswerBoxState
import com.sabit.duelloquiz.core.designsystem.component.DailyCardUi
import com.sabit.duelloquiz.core.designsystem.component.StreakMessageVariant
import com.sabit.duelloquiz.core.designsystem.component.WeekDotState
import com.sabit.duelloquiz.core.designsystem.daily.ResultSquareState
import com.sabit.duelloquiz.core.model.AnswerResult
import com.sabit.duelloquiz.core.model.DailyResultSummary
import com.sabit.duelloquiz.core.model.DailySquare
import com.sabit.duelloquiz.core.model.DailyStatus
import com.sabit.duelloquiz.core.model.DailyStatusType
import com.sabit.duelloquiz.core.model.StreakEvent
import com.sabit.duelloquiz.core.model.StreakEventType
import com.sabit.duelloquiz.core.model.WeekDay
import com.sabit.duelloquiz.core.model.WeekDayState
import com.sabit.duelloquiz.core.testing.testDailyAttempt
import com.sabit.duelloquiz.core.testing.testProgressSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Card, streak section and message mapping of the Gündəlik tab (daily-tab.md A, B). */
class DailyTabMappingTest {

    private val hour = 3_600_000L

    private fun status(
        type: DailyStatusType,
        nextReset: Long = 10 * hour,
        offset: Long = 0,
        result: DailyResultSummary? = null,
    ) = DailyStatus(
        dayKey = "2026-10-22",
        number = if (type == DailyStatusType.UNAVAILABLE) null else 12,
        status = type,
        questionCount = 5,
        nextResetEpochMs = nextReset,
        clockOffsetMs = offset,
        attemptId = "a1".takeIf { type == DailyStatusType.IN_PROGRESS || type == DailyStatusType.FINISHED },
        attemptDeadlineEpochMs = null,
        result = result,
        streakCurrent = 7,
    )

    private val finishedSummary = DailyResultSummary(
        3,
        5,
        listOf(DailySquare.CORRECT, DailySquare.WRONG, DailySquare.CORRECT, DailySquare.CORRECT, DailySquare.UNANSWERED),
    )
    private val loaded = DailyLoad(attempted = true)
    private val dashed = List(5) { AnswerBoxState.NotPlayed }

    // Card (daily-tab.md A)

    @Test
    fun availableAndInProgressFaces() {
        assertEquals(DailyCardUi.Available(12, 5), dailyCardUi(status(DailyStatusType.AVAILABLE), loaded, online = true, localNowMs = 0))
        assertEquals(DailyCardUi.InProgress(12, dashed), dailyCardUi(status(DailyStatusType.IN_PROGRESS), loaded, online = true, localNowMs = 0))
        assertEquals(
            DailyCardUi.Available(12, 5, starting = true),
            dailyCardUi(status(DailyStatusType.AVAILABLE), loaded.copy(starting = true), online = true, localNowMs = 0),
        )
    }

    @Test
    fun inProgressBoxesComeFromTheInMemoryAttempt() {
        val attempt = testDailyAttempt()
        val progress = DailyProgress(
            attempt = attempt,
            resolved = mapOf(
                "q1" to AnswerResult("q1", "q1-a", correct = true, correctOptionId = "q1-a", explanation = null),
                "q2" to AnswerResult("q2", "q2-b", correct = false, correctOptionId = "q2-a", explanation = null),
                "q3" to AnswerResult("q3", null, correct = false, correctOptionId = "q3-a", explanation = null),
            ),
            queued = listOf("q4"),
            result = null,
        )
        val boxes = inProgressBoxes(progress)
        assertEquals(
            listOf(AnswerBoxState.Correct, AnswerBoxState.Wrong, AnswerBoxState.None, AnswerBoxState.Pending, AnswerBoxState.NotPlayed),
            boxes,
        )
        assertEquals(
            DailyCardUi.InProgress(12, boxes!!),
            dailyCardUi(status(DailyStatusType.IN_PROGRESS), loaded, online = true, localNowMs = 0, inProgressBoxes = boxes),
        )
        // Without the attempt in memory (process death, another device): all dashed.
        assertNull(inProgressBoxes(null))
        assertEquals(
            DailyCardUi.InProgress(12, dashed),
            dailyCardUi(status(DailyStatusType.IN_PROGRESS), loaded, online = true, localNowMs = 0, inProgressBoxes = null),
        )
    }

    @Test
    fun finishedShowsScoreSquaresAndTheServerOffsetCountdown() {
        // Device clock 30 min behind the server; next reset at 10 h on the server clock.
        val card = dailyCardUi(
            status(DailyStatusType.FINISHED, nextReset = 10 * hour, offset = hour / 2, result = finishedSummary),
            loaded,
            online = true,
            localNowMs = 4 * hour,
        ) as DailyCardUi.Finished

        assertEquals("05:30:00", card.countdown)
        assertEquals(5 to 30, card.remainingHours to card.remainingMinutes)
        assertEquals(3, card.correctCount)
        assertEquals(ResultSquareState.None, card.squares?.last())
        assertEquals(AnswerBoxState.None, card.boxes.last())
        assertTrue(card.shareEnabled)
    }

    @Test
    fun finishedWithoutSquaresHasNeutralBoxesAndNoShare() {
        val card = dailyCardUi(status(DailyStatusType.FINISHED), loaded, online = true, localNowMs = 0) as DailyCardUi.Finished
        assertNull(card.squares)
        assertEquals(List(5) { AnswerBoxState.Unknown }, card.boxes)
        assertFalse(card.shareEnabled)
    }

    @Test
    fun atMidnightTheFinishedCardRefreshesAndNeverShowsANegativeCountdown() {
        val card = dailyCardUi(
            status(DailyStatusType.FINISHED, nextReset = 10 * hour, result = finishedSummary),
            loaded,
            online = true,
            localNowMs = 10 * hour + 5_000,
        ) as DailyCardUi.Finished
        assertNull(card.countdown)
    }

    @Test
    fun anOlderDayIsNeverShownAsToday() {
        val yesterday = status(DailyStatusType.AVAILABLE, nextReset = hour)
        assertEquals(DailyCardUi.OfflineUnknown, dailyCardUi(yesterday, loaded.copy(offline = true, failed = true), online = false, localNowMs = 2 * hour))
        assertEquals(DailyCardUi.Skeleton, dailyCardUi(yesterday, loaded.copy(loading = true), online = true, localNowMs = 2 * hour))
        assertEquals(DailyCardUi.Error, dailyCardUi(yesterday, loaded.copy(failed = true), online = true, localNowMs = 2 * hour))
        val finishedYesterday = status(DailyStatusType.FINISHED, nextReset = hour, result = finishedSummary)
        assertEquals(DailyCardUi.OfflineUnknown, dailyCardUi(finishedYesterday, loaded.copy(offline = true), online = false, localNowMs = 2 * hour))
    }

    @Test
    fun aWrongDeviceClockDoesNotUnlockEarly() {
        val ahead = 24 * hour
        val card = dailyCardUi(
            status(DailyStatusType.FINISHED, nextReset = 10 * hour, offset = -ahead, result = finishedSummary),
            loaded,
            online = true,
            localNowMs = ahead,
        ) as DailyCardUi.Finished
        assertEquals("10:00:00", card.countdown)
    }

    @Test
    fun loadingErrorAndUnavailableFaces() {
        assertEquals(DailyCardUi.Skeleton, dailyCardUi(null, DailyLoad(), online = true, localNowMs = 0))
        assertEquals(DailyCardUi.Error, dailyCardUi(null, loaded.copy(failed = true), online = true, localNowMs = 0))
        assertEquals(DailyCardUi.OfflineUnknown, dailyCardUi(null, loaded.copy(failed = true, offline = true), online = false, localNowMs = 0))
        assertEquals(DailyCardUi.Unavailable, dailyCardUi(status(DailyStatusType.UNAVAILABLE), loaded, online = true, localNowMs = 0))
    }

    // Streak section (daily-tab.md B)

    @Test
    fun streakSectionStates() {
        val loading = streakSectionUi(null, loading = true, attempted = false, localNowMs = 0) as StreakSectionUi.Loading
        assertEquals(7, loading.week.size)
        assertTrue(loading.week.all { it.state == WeekDotState.Unknown })
        assertEquals(StreakSectionUi.Error, streakSectionUi(null, loading = false, attempted = true, localNowMs = 0))

        val content = streakSectionUi(testProgressSummary(streak = 7, todayActive = true, nextResetEpochMs = 10 * hour), false, true, 0)
            as StreakSectionUi.Content
        assertEquals(7, content.current)
        assertEquals(12, content.best)
        assertTrue(content.todayActive)
        assertFalse(content.isNew)
        assertEquals(7, content.week.size)
    }

    @Test
    fun aCacheAfterBakuMidnightShowsTodayAsNotYet() {
        val content = streakSectionUi(testProgressSummary(streak = 7, todayActive = true, nextResetEpochMs = 10 * hour), false, true, 11 * hour)
            as StreakSectionUi.Content
        assertFalse(content.todayActive)
    }

    @Test
    fun aNewUserHasTheEmptyFace() {
        val content = streakSectionUi(testProgressSummary(streak = 0, best = 0), false, true, 0) as StreakSectionUi.Content
        assertTrue(content.isNew)
    }

    // Week row (PROG-11, OQ-D35)

    /** 2026-11-19 12:00 Baku (Thursday) and the next Baku midnight. */
    private val thursdayNoon = 1_795_075_200_000L
    private val thursdayReset = 1_795_118_400_000L

    private val serverWeek = listOf(
        WeekDay("2026-11-16", WeekDayState.ACTIVE),
        WeekDay("2026-11-17", WeekDayState.FREEZE),
        WeekDay("2026-11-18", WeekDayState.MISSED),
        WeekDay("2026-11-19", WeekDayState.TODAY_PENDING),
        WeekDay("2026-11-20", WeekDayState.NONE),
        WeekDay("2026-11-21", WeekDayState.NONE),
        WeekDay("2026-11-22", WeekDayState.NONE),
    )

    private fun thursdayProgress(streak: Int = 1, week: List<WeekDay>?, event: StreakEvent? = null) =
        testProgressSummary(streak = streak, todayActive = false, nextResetEpochMs = thursdayReset, event = event)
            .copy(dayKey = "2026-11-19", week = week)

    @Test
    fun theWeekRowRendersTheServerStates() {
        val content = streakSectionUi(thursdayProgress(week = serverWeek), false, true, thursdayNoon) as StreakSectionUi.Content
        assertEquals(
            listOf(WeekDotState.Active, WeekDotState.Freeze, WeekDotState.Missed, WeekDotState.TodayPending) +
                List(3) { WeekDotState.Unknown },
            content.week.map { it.state },
        )
        assertFalse(content.weekFallback)
        assertEquals(1, content.activeDays)
    }

    @Test
    fun serverStatesWinOverTheStreakAndItsEvents() {
        // The streak and a lost-streak event would derive other dots; the server week decides.
        val event = StreakEvent(StreakEventType.STREAK_LOST, "2026-11-16", 4)
        val content = streakSectionUi(thursdayProgress(streak = 3, week = serverWeek, event = event), false, true, thursdayNoon)
            as StreakSectionUi.Content
        assertEquals(WeekDotState.Active, content.week[0].state)
        assertEquals(WeekDotState.Missed, content.week[2].state)
    }

    @Test
    fun withoutWeekTheRowIsTheFallback() {
        val content = streakSectionUi(thursdayProgress(streak = 2, week = null), false, true, thursdayNoon) as StreakSectionUi.Content
        assertTrue(content.weekFallback)
        // Tuesday and Wednesday derived as played, no freeze or missed claims.
        assertEquals(
            listOf(WeekDotState.Unknown, WeekDotState.Active, WeekDotState.Active, WeekDotState.TodayPending) +
                List(3) { WeekDotState.Unknown },
            content.week.map { it.state },
        )
        assertEquals(2, content.activeDays)
        // An empty list is treated like an absent week.
        assertTrue((streakSectionUi(thursdayProgress(week = emptyList()), false, true, thursdayNoon) as StreakSectionUi.Content).weekFallback)
    }

    @Test
    fun aStaleServerWeekAfterMidnightNeverClaimsToday() {
        val fridayNoon = thursdayNoon + 24 * hour
        val content = streakSectionUi(thursdayProgress(week = serverWeek), false, true, fridayNoon) as StreakSectionUi.Content
        assertEquals(
            listOf(WeekDotState.Active, WeekDotState.Freeze, WeekDotState.Missed, WeekDotState.Unknown, WeekDotState.TodayPending) +
                List(2) { WeekDotState.Unknown },
            content.week.map { it.state },
        )
        assertTrue(content.week[4].isToday)
        assertFalse(content.todayActive)
        assertFalse(content.weekFallback)
    }

    @Test
    fun aServerWeekOfAPreviousWeekShowsTheCurrentWeekNeutral() {
        val nextWednesdayNoon = thursdayNoon + 6 * 24 * hour
        val content = streakSectionUi(thursdayProgress(week = serverWeek), false, true, nextWednesdayNoon) as StreakSectionUi.Content
        assertEquals(
            listOf(WeekDotState.Unknown, WeekDotState.Unknown, WeekDotState.TodayPending) + List(4) { WeekDotState.Unknown },
            content.week.map { it.state },
        )
        assertEquals(0, content.activeDays)
    }

    // Message (PROG-06 … PROG-08)

    @Test
    fun messagePriorityIsLostThenFreezeThenHint() {
        val progress = testProgressSummary(streak = 3, todayActive = false, nextResetEpochMs = 10 * hour, best = 12)
        val lost = StreakEvent(StreakEventType.STREAK_LOST, "2026-10-21", 5)
        val freeze = StreakEvent(StreakEventType.FREEZE_USED, "2026-10-21", 9)

        assertEquals(StreakMessageUi(StreakMessageVariant.StreakLost, days = 12), streakMessageUi(lost, progress, null, 0))
        assertEquals(StreakMessageUi(StreakMessageVariant.FreezeUsed), streakMessageUi(freeze, progress, null, 0))
        assertEquals(StreakMessageUi(StreakMessageVariant.StreakHint), streakMessageUi(null, progress, null, 0))
        assertNull(streakMessageUi(null, progress, hintDismissedDay = "2026-10-22", localNowMs = 0))
        assertNull(streakMessageUi(null, progress.copy(todayActive = true), null, 0))
        assertNull(streakMessageUi(null, progress.copy(streakCurrent = 0), null, 0))
        assertNull(streakMessageUi(null, progress, null, localNowMs = 11 * hour))
    }
}

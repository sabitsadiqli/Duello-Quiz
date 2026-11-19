package com.sabit.duelloquiz.feature.home.daily

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.sabit.duelloquiz.core.common.HomeNotice
import com.sabit.duelloquiz.core.common.NavResults
import com.sabit.duelloquiz.core.common.Telemetry
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.designsystem.component.AnswerBoxState
import com.sabit.duelloquiz.core.designsystem.component.DailyCardUi
import com.sabit.duelloquiz.core.designsystem.component.StreakMessageVariant
import com.sabit.duelloquiz.core.designsystem.component.WeekDotState
import com.sabit.duelloquiz.core.model.AnswerResult
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DailyResultSummary
import com.sabit.duelloquiz.core.model.DailySquare
import com.sabit.duelloquiz.core.model.DailyStart
import com.sabit.duelloquiz.core.model.DailyStatus
import com.sabit.duelloquiz.core.model.DailyStatusType
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.NotificationSettings
import com.sabit.duelloquiz.core.model.ReminderType
import com.sabit.duelloquiz.core.model.StreakEvent
import com.sabit.duelloquiz.core.model.StreakEventType
import com.sabit.duelloquiz.core.model.WeekDay
import com.sabit.duelloquiz.core.model.WeekDayState
import com.sabit.duelloquiz.core.notifications.NotificationSettingsAction
import com.sabit.duelloquiz.core.notifications.NotificationSettingsEvent
import com.sabit.duelloquiz.core.notifications.NotificationSettingsMessage
import com.sabit.duelloquiz.core.testing.MainDispatcherRule
import com.sabit.duelloquiz.core.testing.TestDailyRepository
import com.sabit.duelloquiz.core.testing.TestNetworkMonitor
import com.sabit.duelloquiz.core.testing.TestNotificationPromptRepository
import com.sabit.duelloquiz.core.testing.TestNotificationSettingsRepository
import com.sabit.duelloquiz.core.testing.TestProgressRepository
import com.sabit.duelloquiz.core.testing.TestSystemNotifications
import com.sabit.duelloquiz.core.testing.testDailyAttempt
import com.sabit.duelloquiz.core.testing.testDailyResult
import com.sabit.duelloquiz.core.testing.testProgressSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The Gündəlik tab (daily-tab.md, DAILY-01 … DAILY-07, PROG-06 … PROG-08, NOTIF-06). */
@OptIn(ExperimentalCoroutinesApi::class)
class DailyTabViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val hour = 3_600_000L

    private fun status(
        type: DailyStatusType,
        nextReset: Long = 10 * hour,
        result: DailyResultSummary? = null,
        attemptId: String? = "a1".takeIf { type == DailyStatusType.IN_PROGRESS || type == DailyStatusType.FINISHED },
    ) = DailyStatus(
        dayKey = "2026-10-22",
        number = if (type == DailyStatusType.UNAVAILABLE) null else 12,
        status = type,
        questionCount = 5,
        nextResetEpochMs = nextReset,
        clockOffsetMs = 0,
        attemptId = attemptId,
        attemptDeadlineEpochMs = null,
        result = result,
        streakCurrent = 7,
    )

    private val finishedSummary = DailyResultSummary(
        3,
        5,
        listOf(DailySquare.CORRECT, DailySquare.WRONG, DailySquare.CORRECT, DailySquare.CORRECT, DailySquare.UNANSWERED),
    )

    private val daily = TestDailyRepository()
    private val progress = TestProgressRepository()
    private val settings = TestNotificationSettingsRepository(initial = NotificationSettings(dailyReminder = true, streakReminder = true))
    private val system = TestSystemNotifications()
    private val prompts = TestNotificationPromptRepository()
    private val network = TestNetworkMonitor(online = true)
    private val savedStateHandle = SavedStateHandle()

    private class RecordingTelemetry : Telemetry {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun setUserId(userId: String?) = Unit
        override fun recordNonFatal(throwable: Throwable) = Unit
        override fun logEvent(name: String, params: Map<String, Any>) {
            events += name to params
        }
    }

    private val telemetry = RecordingTelemetry()

    private fun TestScope.viewModel(): DailyTabViewModel {
        val vm = DailyTabViewModel(
            daily, progress, settings, system, prompts, network, savedStateHandle, WallClock { testScheduler.currentTime }, telemetry,
        )
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    // Loading and refresh

    @Test
    fun theFirstLoadFetchesStatusProgressAndReminderSettingsTogether() = runTest {
        daily.nextStatus = DataResult.Success(status(DailyStatusType.AVAILABLE))
        progress.nextRefresh = DataResult.Success(testProgressSummary(streak = 3))
        val vm = viewModel()
        runCurrent()

        assertEquals(1, daily.statusCount)
        assertEquals(1, progress.refreshCount)
        assertEquals(DailyCardUi.Available(12, 5), vm.uiState.value.card)
        assertTrue(vm.uiState.value.streak is StreakSectionUi.Content)
        assertTrue(vm.uiState.value.reminder.daily.checked)
        assertFalse(vm.uiState.value.showOfflineBanner)
    }

    @Test
    fun tabEntryRefreshesOnlyAfterOneMinute() = runTest {
        val vm = viewModel()
        runCurrent()
        vm.onAction(DailyTabAction.Resumed)
        runCurrent()
        assertEquals(1, daily.statusCount)

        advanceTimeBy(DailyTabViewModel.REENTRY_REFRESH_MS + 1)
        vm.onAction(DailyTabAction.Paused)
        vm.onAction(DailyTabAction.Resumed)
        runCurrent()
        assertEquals(2, daily.statusCount)
        assertEquals(2, progress.refreshCount)
        vm.onAction(DailyTabAction.Paused)
    }

    @Test
    fun returningFromTheDailyFlowRefreshesAndAnimatesTheBoxesOnce() = runTest {
        daily.nextStatus = DataResult.Success(status(DailyStatusType.FINISHED, result = finishedSummary))
        val vm = viewModel()
        runCurrent()

        savedStateHandle[NavResults.TAB_REFRESH] = true
        runCurrent()
        assertEquals(2, daily.statusCount)
        assertTrue(vm.uiState.value.animateBoxes)
        assertEquals(false, savedStateHandle.get<Boolean>(NavResults.TAB_REFRESH))

        vm.onAction(DailyTabAction.BoxesAnimated)
        runCurrent()
        assertFalse(vm.uiState.value.animateBoxes)
    }

    @Test
    fun aNoticeFromTheDailyFlowShowsASnackbarOnce() = runTest {
        val vm = viewModel()
        vm.events.test {
            savedStateHandle[NavResults.HOME_NOTICE] = HomeNotice.Generic.name
            runCurrent()
            assertEquals(DailyTabEvent.ShowMessage(DailyTabMessage.Generic), awaitItem())
            expectNoEvents()
        }
        assertNull(savedStateHandle.get<String>(NavResults.HOME_NOTICE))
    }

    @Test
    fun pullToRefreshReloadsStatusAndProgressAndEndsTheIndicator() = runTest {
        val vm = viewModel()
        runCurrent()
        vm.onAction(DailyTabAction.PullToRefresh)
        runCurrent()
        // Both requests ran again and the indicator is gone once they are back.
        assertFalse(vm.uiState.value.isRefreshing)
        assertEquals(2, daily.statusCount)
        assertEquals(2, progress.refreshCount)
    }

    @Test
    fun offlineOverCacheShowsTheBanner() = runTest {
        daily.setStatus(status(DailyStatusType.AVAILABLE))
        network.online.value = false
        val vm = viewModel()
        runCurrent()

        assertTrue(vm.uiState.value.showOfflineBanner)
        assertEquals(DailyCardUi.Available(12, 5), vm.uiState.value.card)
    }

    // Start, resume, share (DAILY-02, DAILY-05)

    @Test
    fun aQuickStartOpensTheChallengeWithTheAttempt() = runTest {
        daily.nextStatus = DataResult.Success(status(DailyStatusType.AVAILABLE))
        daily.startResult = DataResult.Success(DailyStart.Started(testDailyAttempt()))
        val vm = viewModel()
        runCurrent()

        vm.events.test {
            vm.onAction(DailyTabAction.StartClicked)
            runCurrent()
            assertEquals(DailyTabEvent.NavigateToDaily("a1"), awaitItem())
        }
    }

    @Test
    fun aSlowStartHandsOverToTheLoadingScreenAndADoubleTapSendsOneRequest() = runTest {
        daily.nextStatus = DataResult.Success(status(DailyStatusType.AVAILABLE))
        daily.startGate = CompletableDeferred()
        daily.startResult = DataResult.Success(DailyStart.Started(testDailyAttempt()))
        val vm = viewModel()
        runCurrent()

        vm.events.test {
            vm.onAction(DailyTabAction.StartClicked)
            runCurrent()
            vm.onAction(DailyTabAction.StartClicked)
            assertTrue((vm.uiState.value.card as DailyCardUi.Available).starting)
            advanceTimeBy(1_001)
            assertEquals(DailyTabEvent.NavigateToDaily(attemptId = null), awaitItem())
        }
        assertEquals(1, daily.startCount)
    }

    @Test
    fun alreadyPlayedOpensTheStoredResult() = runTest {
        daily.nextStatus = DataResult.Success(status(DailyStatusType.AVAILABLE))
        daily.startResult = DataResult.Success(DailyStart.AlreadyPlayed(testDailyResult(attemptId = "old")))
        val vm = viewModel()
        runCurrent()

        vm.events.test {
            vm.onAction(DailyTabAction.StartClicked)
            runCurrent()
            assertEquals(DailyTabEvent.NavigateToDailyResult("old"), awaitItem())
        }
    }

    @Test
    fun offlineStartShowsTheNeedsInternetMessage() = runTest {
        daily.nextStatus = DataResult.Success(status(DailyStatusType.AVAILABLE))
        val vm = viewModel()
        runCurrent()
        network.online.value = false
        runCurrent()

        vm.events.test {
            vm.onAction(DailyTabAction.StartClicked)
            assertEquals(DailyTabEvent.ShowMessage(DailyTabMessage.NeedsInternet), awaitItem())
        }
        assertEquals(0, daily.startCount)
    }

    @Test
    fun aStartAfterMidnightRefreshesFirstAndNeverOpensYesterday() = runTest {
        daily.nextStatus = DataResult.Success(status(DailyStatusType.AVAILABLE, nextReset = 1_000))
        val vm = viewModel()
        runCurrent()
        advanceTimeBy(2_000)

        vm.events.test {
            vm.onAction(DailyTabAction.StartClicked)
            runCurrent()
            expectNoEvents()
        }
        assertEquals(0, daily.startCount)
        assertEquals(2, daily.statusCount)
    }

    @Test
    fun unavailableOnStartRefreshesTheCard() = runTest {
        daily.nextStatus = DataResult.Success(status(DailyStatusType.AVAILABLE))
        daily.startResult = DataResult.Failure(DataError.Api(ApiErrorCode.DailyUnavailable))
        val vm = viewModel()
        runCurrent()
        daily.nextStatus = DataResult.Success(status(DailyStatusType.UNAVAILABLE))

        vm.onAction(DailyTabAction.StartClicked)
        runCurrent()

        assertEquals(DailyCardUi.Unavailable, vm.uiState.value.card)
    }

    @Test
    fun theInProgressCardFillsAnsweredBoxesFromTheAttemptInMemory() = runTest {
        val answered = listOf(AnswerResult("q1", "q1-a", correct = true, correctOptionId = "q1-a", explanation = null))
        daily.register(testDailyAttempt(answers = answered))
        daily.nextStatus = DataResult.Success(status(DailyStatusType.IN_PROGRESS))
        val vm = viewModel()
        runCurrent()

        val card = vm.uiState.value.card as DailyCardUi.InProgress
        assertEquals(AnswerBoxState.Correct, card.boxes.first())
        assertEquals(List(4) { AnswerBoxState.NotPlayed }, card.boxes.drop(1))
    }

    @Test
    fun shareUsesTheServerSquaresAndStreak() = runTest {
        daily.nextStatus = DataResult.Success(status(DailyStatusType.FINISHED, result = finishedSummary))
        val vm = viewModel()
        runCurrent()

        vm.events.test {
            vm.onAction(DailyTabAction.ShareClicked)
            runCurrent()
            val share = awaitItem() as DailyTabEvent.ShareDaily
            assertEquals(12, share.number)
            assertEquals(5, share.squares.size)
            assertEquals(7, share.streak)
        }
    }

    @Test
    fun theCountdownReloadsTheNewDayAtMidnightAtMostEveryTenSeconds() = runTest {
        daily.nextStatus = DataResult.Success(status(DailyStatusType.FINISHED, nextReset = 3_000, result = finishedSummary))
        val vm = viewModel()
        runCurrent()
        vm.onAction(DailyTabAction.Resumed)
        runCurrent()
        val before = daily.statusCount

        advanceTimeBy(3_500)
        runCurrent()
        assertEquals(before + 1, daily.statusCount)
        advanceTimeBy(9_000)
        assertEquals(before + 1, daily.statusCount)
        daily.nextStatus = DataResult.Success(status(DailyStatusType.AVAILABLE, nextReset = 90_000_000))
        advanceTimeBy(1_500)
        runCurrent()
        assertEquals(before + 2, daily.statusCount)
        assertEquals(DailyCardUi.Available(12, 5), vm.uiState.value.card)
        vm.onAction(DailyTabAction.Paused)
    }

    // Streak messages (PROG-06 … PROG-08)

    @Test
    fun aStreakEventIsShownOnceAndDismissed() = runTest {
        progress.nextRefresh = DataResult.Success(
            testProgressSummary(streak = 0, best = 12, event = StreakEvent(StreakEventType.STREAK_LOST, "2026-10-21", 6)),
        )
        val vm = viewModel()
        runCurrent()
        assertEquals(StreakMessageUi(StreakMessageVariant.StreakLost, 12), vm.uiState.value.message)

        vm.onAction(DailyTabAction.DismissMessage)
        runCurrent()
        assertNull(vm.uiState.value.message)

        val again = viewModel()
        runCurrent()
        assertNull(again.uiState.value.message)
    }

    @Test
    fun dismissingTheHintHidesItForTheDay() = runTest {
        progress.nextRefresh = DataResult.Success(testProgressSummary(streak = 3, todayActive = false))
        val vm = viewModel()
        runCurrent()
        assertEquals(StreakMessageUi(StreakMessageVariant.StreakHint), vm.uiState.value.message)

        vm.onAction(DailyTabAction.DismissMessage)
        runCurrent()

        assertNull(vm.uiState.value.message)
        assertEquals("2026-10-22", progress.dismissedHintDay)
    }

    @Test
    fun aStreakFailureWithoutCacheRetriesOnlyTheSection() = runTest {
        val vm = viewModel()
        runCurrent()
        assertEquals(StreakSectionUi.Error, vm.uiState.value.streak)

        progress.nextRefresh = DataResult.Success(testProgressSummary(streak = 2))
        vm.onAction(DailyTabAction.RetryStreakClicked)
        runCurrent()
        assertTrue(vm.uiState.value.streak is StreakSectionUi.Content)
        assertEquals(1, daily.statusCount)
    }

    // Week row (PROG-11, OQ-D35)

    /** 2026-11-19 12:00 Baku (Thursday) as server clock − test clock, and the next Baku midnight. */
    private val thursdayNoon = 1_795_075_200_000L
    private val thursdayReset = 1_795_118_400_000L

    private val serverWeek = listOf(
        WeekDay("2026-11-16", WeekDayState.ACTIVE),
        WeekDay("2026-11-17", WeekDayState.FREEZE),
        WeekDay("2026-11-18", WeekDayState.MISSED),
        WeekDay("2026-11-19", WeekDayState.ACTIVE),
        WeekDay("2026-11-20", WeekDayState.NONE),
        WeekDay("2026-11-21", WeekDayState.NONE),
        WeekDay("2026-11-22", WeekDayState.NONE),
    )

    private fun thursdayProgress(week: List<WeekDay>?) =
        testProgressSummary(streak = 2, todayActive = true, nextResetEpochMs = thursdayReset)
            .copy(dayKey = "2026-11-19", clockOffsetMs = thursdayNoon, week = week)

    @Test
    fun theWeekRowShowsTheServerStates() = runTest {
        progress.nextRefresh = DataResult.Success(thursdayProgress(serverWeek))
        val vm = viewModel()
        runCurrent()

        val streak = vm.uiState.value.streak as StreakSectionUi.Content
        assertEquals(
            listOf(WeekDotState.Active, WeekDotState.Freeze, WeekDotState.Missed, WeekDotState.Active) +
                List(3) { WeekDotState.Unknown },
            streak.week.map { it.state },
        )
        assertTrue(streak.week[3].isToday)
        assertFalse(streak.weekFallback)
    }

    @Test
    fun weekViewedIsLoggedOncePerBakuDayWithServerStates() = runTest {
        progress.nextRefresh = DataResult.Success(thursdayProgress(serverWeek))
        val vm = viewModel()
        runCurrent()

        vm.onAction(DailyTabAction.WeekRowShown)
        vm.onAction(DailyTabAction.WeekRowShown)
        runCurrent()

        assertEquals(
            listOf(
                DailyTabViewModel.EVENT_STREAK_WEEK_VIEWED to
                    mapOf(DailyTabViewModel.PARAM_ACTIVE_DAYS to 2, DailyTabViewModel.PARAM_FALLBACK to false),
            ),
            telemetry.events,
        )

        // A recreated ViewModel (same back stack entry) does not log the same day again.
        val again = viewModel()
        runCurrent()
        again.onAction(DailyTabAction.WeekRowShown)
        assertEquals(1, telemetry.events.size)
    }

    @Test
    fun weekViewedReportsTheFallbackWhenTheWeekIsMissing() = runTest {
        progress.nextRefresh = DataResult.Success(thursdayProgress(week = null))
        val vm = viewModel()
        runCurrent()

        vm.onAction(DailyTabAction.WeekRowShown)

        val (_, params) = telemetry.events.single()
        assertEquals(true, params[DailyTabViewModel.PARAM_FALLBACK])
        // Fallback: Wednesday and Thursday derived from streak 2 with today active.
        assertEquals(2, params[DailyTabViewModel.PARAM_ACTIVE_DAYS])
    }

    @Test
    fun weekViewedIsNotLoggedWithoutContent() = runTest {
        val vm = viewModel()
        runCurrent()

        vm.onAction(DailyTabAction.WeekRowShown)

        assertTrue(telemetry.events.isEmpty())
    }

    // Reminder mirror (daily-tab.md C, NOTIF-06)

    @Test
    fun theReminderSwitchWritesOnlyTheDailyValue() = runTest {
        val vm = viewModel()
        runCurrent()

        vm.onAction(DailyTabAction.Reminder(NotificationSettingsAction.Toggle(ReminderType.DAILY, enabled = false)))
        runCurrent()

        assertEquals(listOf(NotificationSettings(dailyReminder = false, streakReminder = true)), settings.updates)
        assertFalse(vm.uiState.value.reminder.daily.checked)
    }

    @Test
    fun aFailedSaveRevertsWithASnackbar() = runTest {
        settings.nextUpdate = DataResult.Failure(DataError.Server)
        val vm = viewModel()
        runCurrent()

        vm.events.test {
            vm.onAction(DailyTabAction.Reminder(NotificationSettingsAction.Toggle(ReminderType.DAILY, enabled = false)))
            runCurrent()
            assertEquals(DailyTabEvent.Reminder(NotificationSettingsEvent.ShowMessage(NotificationSettingsMessage.SaveFailed)), awaitItem())
        }
        assertTrue(vm.uiState.value.reminder.daily.checked)
    }
}

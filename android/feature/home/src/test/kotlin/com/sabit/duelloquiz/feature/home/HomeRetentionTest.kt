package com.sabit.duelloquiz.feature.home

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.auth.AuthResult
import com.sabit.duelloquiz.core.common.AppConfig
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.designsystem.component.DailyCardUi
import com.sabit.duelloquiz.core.designsystem.component.HomeMessageVariant
import com.sabit.duelloquiz.core.designsystem.component.StreakBadgeState
import com.sabit.duelloquiz.core.designsystem.daily.ResultSquareState
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.AuthProvider
import com.sabit.duelloquiz.core.model.AuthUser
import com.sabit.duelloquiz.core.model.DailyResultSummary
import com.sabit.duelloquiz.core.model.DailySquare
import com.sabit.duelloquiz.core.model.DailyStart
import com.sabit.duelloquiz.core.model.DailyStatus
import com.sabit.duelloquiz.core.model.DailyStatusType
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.ReminderType
import com.sabit.duelloquiz.core.model.StreakEvent
import com.sabit.duelloquiz.core.model.StreakEventType
import com.sabit.duelloquiz.core.notifications.ReminderOpens
import com.sabit.duelloquiz.core.testing.InMemoryActiveDuelStore
import com.sabit.duelloquiz.core.testing.MainDispatcherRule
import com.sabit.duelloquiz.core.testing.TestCategoryRepository
import com.sabit.duelloquiz.core.testing.TestDailyRepository
import com.sabit.duelloquiz.core.testing.TestNetworkMonitor
import com.sabit.duelloquiz.core.testing.TestProfileRepository
import com.sabit.duelloquiz.core.testing.TestProgressRepository
import com.sabit.duelloquiz.core.testing.TestQuizRepository
import com.sabit.duelloquiz.core.testing.TestVerificationBannerRepository
import com.sabit.duelloquiz.core.testing.testDailyAttempt
import com.sabit.duelloquiz.core.testing.testDailyResult
import com.sabit.duelloquiz.core.testing.testProgressSummary
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Daily card, level/streak header and streak messages on home (HOME-07, HOME-08, DAILY-01). */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeRetentionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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

    private val finishedSummary = DailyResultSummary(3, 5, listOf(DailySquare.CORRECT, DailySquare.WRONG, DailySquare.CORRECT, DailySquare.CORRECT, DailySquare.UNANSWERED))
    private val loaded = DailyLoad(attempted = true)

    // Pure card states (daily-challenge.md A)

    @Test
    fun availableAndInProgressFaces() {
        assertEquals(DailyCardUi.Available(12, 5), dailyCardUi(status(DailyStatusType.AVAILABLE), loaded, online = true, localNowMs = 0))
        assertEquals(DailyCardUi.InProgress(12), dailyCardUi(status(DailyStatusType.IN_PROGRESS), loaded, online = true, localNowMs = 0))
        assertEquals(
            DailyCardUi.Available(12, 5, starting = true),
            dailyCardUi(status(DailyStatusType.AVAILABLE), loaded.copy(starting = true), online = true, localNowMs = 0),
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
        assertTrue(card.shareEnabled)
    }

    @Test
    fun finishedWithoutSquaresHasNoShare() {
        val card = dailyCardUi(status(DailyStatusType.FINISHED), loaded, online = true, localNowMs = 0) as DailyCardUi.Finished
        assertNull(card.squares)
        assertTrue(!card.shareEnabled)
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
        // Offline with yesterday's cache: unknown, not "Başla" (DAILY-01).
        assertEquals(DailyCardUi.OfflineUnknown, dailyCardUi(yesterday, loaded.copy(offline = true, failed = true), online = false, localNowMs = 2 * hour))
        // Online, the new day is loading: skeleton until the server confirms.
        assertEquals(DailyCardUi.Skeleton, dailyCardUi(yesterday, loaded.copy(loading = true), online = true, localNowMs = 2 * hour))
        val finishedYesterday = status(DailyStatusType.FINISHED, nextReset = hour, result = finishedSummary)
        assertEquals(DailyCardUi.OfflineUnknown, dailyCardUi(finishedYesterday, loaded.copy(offline = true), online = false, localNowMs = 2 * hour))
    }

    @Test
    fun aWrongDeviceClockDoesNotUnlockEarly() {
        // The device clock is set a day ahead; the server says midnight is still 10 h away.
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

    // Header and message (HOME-08, PROG-06 … PROG-08)

    @Test
    fun headerShowsLevelAndStreakAndNeverClaimsTodayFromAnOldCache() {
        val today = headerProgressUi(testProgressSummary(todayActive = true, nextResetEpochMs = 10 * hour), loading = false, attempted = true, localNowMs = 0)
            as HeaderProgressUi.Content
        assertEquals(StreakBadgeState.ActiveToday, today.badge)
        assertEquals(5, today.level)
        assertEquals(0.54f, today.fraction, 0.001f)

        val afterMidnight = headerProgressUi(testProgressSummary(todayActive = true, nextResetEpochMs = 10 * hour), loading = false, attempted = true, localNowMs = 11 * hour)
            as HeaderProgressUi.Content
        assertEquals(StreakBadgeState.NotYetToday, afterMidnight.badge)

        assertEquals(HeaderProgressUi.Skeleton, headerProgressUi(null, loading = true, attempted = false, localNowMs = 0))
        assertEquals(HeaderProgressUi.Hidden, headerProgressUi(null, loading = false, attempted = true, localNowMs = 0))
    }

    @Test
    fun messagePriorityIsLostThenFreezeThenHint() {
        val progress = testProgressSummary(streak = 3, todayActive = false, nextResetEpochMs = 10 * hour, best = 12)
        val lost = StreakEvent(StreakEventType.STREAK_LOST, "2026-10-21", 5)
        val freeze = StreakEvent(StreakEventType.FREEZE_USED, "2026-10-21", 9)

        assertEquals(HomeMessageUi(HomeMessageVariant.StreakLost, days = 12), homeMessageUi(lost, progress, null, 0))
        assertEquals(HomeMessageUi(HomeMessageVariant.FreezeUsed), homeMessageUi(freeze, progress, null, 0))
        assertEquals(HomeMessageUi(HomeMessageVariant.StreakHint), homeMessageUi(null, progress, null, 0))
        // Dismissed today, today already active, no streak or an old cache: no hint.
        assertNull(homeMessageUi(null, progress, hintDismissedDay = "2026-10-22", localNowMs = 0))
        assertNull(homeMessageUi(null, progress.copy(todayActive = true), null, 0))
        assertNull(homeMessageUi(null, progress.copy(streakCurrent = 0), null, 0))
        assertNull(homeMessageUi(null, progress, null, localNowMs = 11 * hour))
    }

    // ViewModel flows

    private val user = MutableStateFlow<AuthUser?>(
        AuthUser("uid-1", "aysel@example.com", "Aysel", null, isEmailVerified = true, provider = AuthProvider.GOOGLE),
    )
    private val authRepository: AuthRepository = mockk {
        every { currentUser } returns user
        coEvery { reloadUser() } returns AuthResult.Success(Unit)
    }
    private val daily = TestDailyRepository()
    private val progress = TestProgressRepository()
    private val network = TestNetworkMonitor(online = true)
    private val reminderOpens = ReminderOpens()

    private fun TestScope.viewModel(): HomeViewModel {
        val vm = HomeViewModel(
            authRepository, TestVerificationBannerRepository(), TestProfileRepository(), TestCategoryRepository(),
            TestQuizRepository(), network, SavedStateHandle(), AppConfig("1", 1, "", duelEnabled = false),
            InMemoryActiveDuelStore(), progress, daily, reminderOpens, WallClock { testScheduler.currentTime },
        )
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun aQuickStartOpensTheChallengeWithTheAttempt() = runTest {
        daily.nextStatus = DataResult.Success(status(DailyStatusType.AVAILABLE))
        daily.startResult = DataResult.Success(DailyStart.Started(testDailyAttempt()))
        val vm = viewModel()
        runCurrent()

        vm.events.test {
            vm.onAction(HomeAction.DailyStartClicked)
            runCurrent()
            assertEquals(HomeEvent.NavigateToDaily("a1"), awaitItem())
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
            vm.onAction(HomeAction.DailyStartClicked)
            runCurrent()
            vm.onAction(HomeAction.DailyStartClicked)
            assertTrue((vm.uiState.value.daily as DailyCardUi.Available).starting)
            advanceTimeBy(1_001)
            assertEquals(HomeEvent.NavigateToDaily(attemptId = null), awaitItem())
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
            vm.onAction(HomeAction.DailyStartClicked)
            runCurrent()
            assertEquals(HomeEvent.NavigateToDailyResult("old"), awaitItem())
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
            vm.onAction(HomeAction.DailyStartClicked)
            assertEquals(HomeEvent.ShowMessage(HomeMessage.DailyNeedsInternet), awaitItem())
        }
        assertEquals(0, daily.startCount)
    }

    @Test
    fun unavailableOnStartRefreshesTheCard() = runTest {
        daily.nextStatus = DataResult.Success(status(DailyStatusType.AVAILABLE))
        daily.startResult = DataResult.Failure(DataError.Api(ApiErrorCode.DailyUnavailable))
        val vm = viewModel()
        runCurrent()
        daily.nextStatus = DataResult.Success(status(DailyStatusType.UNAVAILABLE))

        vm.onAction(HomeAction.DailyStartClicked)
        runCurrent()

        assertEquals(DailyCardUi.Unavailable, vm.uiState.value.daily)
    }

    @Test
    fun shareFromHomeUsesTheServerSquaresAndStreak() = runTest {
        daily.nextStatus = DataResult.Success(status(DailyStatusType.FINISHED, result = finishedSummary))
        val vm = viewModel()
        runCurrent()

        vm.events.test {
            vm.onAction(HomeAction.DailyShareClicked)
            runCurrent()
            val share = awaitItem() as HomeEvent.ShareDaily
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
        vm.onAction(HomeAction.Resumed)
        runCurrent()
        val before = daily.statusCount

        advanceTimeBy(3_500)
        runCurrent()
        assertEquals(before + 1, daily.statusCount)
        // The server still returns the old day: no request for the next 10 s.
        advanceTimeBy(9_000)
        assertEquals(before + 1, daily.statusCount)
        daily.nextStatus = DataResult.Success(status(DailyStatusType.AVAILABLE, nextReset = 90_000_000))
        advanceTimeBy(1_500)
        runCurrent()
        assertEquals(before + 2, daily.statusCount)
        assertEquals(DailyCardUi.Available(12, 5), vm.uiState.value.daily)
        vm.onAction(HomeAction.Paused)
    }

    @Test
    fun aStreakEventIsShownOnceAndDismissed() = runTest {
        progress.nextRefresh = DataResult.Success(
            testProgressSummary(streak = 0, best = 12, event = StreakEvent(StreakEventType.STREAK_LOST, "2026-10-21", 6)),
        )
        val vm = viewModel()
        runCurrent()
        assertEquals(HomeMessageUi(HomeMessageVariant.StreakLost, 12), vm.uiState.value.message)

        vm.onAction(HomeAction.DismissMessage)
        runCurrent()
        assertNull(vm.uiState.value.message)

        // Back on home later (new ViewModel): the same event is not repeated.
        val again = viewModel()
        runCurrent()
        assertNull(again.uiState.value.message)
    }

    @Test
    fun dismissingTheHintHidesItForTheDay() = runTest {
        progress.nextRefresh = DataResult.Success(testProgressSummary(streak = 3, todayActive = false))
        val vm = viewModel()
        runCurrent()
        assertEquals(HomeMessageUi(HomeMessageVariant.StreakHint), vm.uiState.value.message)

        vm.onAction(HomeAction.DismissMessage)
        runCurrent()

        assertNull(vm.uiState.value.message)
        assertEquals("2026-10-22", progress.dismissedHintDay)
    }

    @Test
    fun openedFromADailyReminderScrollsToTheCardWithoutStarting() = runTest {
        val vm = viewModel()
        vm.events.test {
            reminderOpens.onOpened(ReminderType.DAILY)
            runCurrent()
            assertEquals(HomeEvent.ScrollToDaily, awaitItem())
        }
        assertEquals(0, daily.startCount)
        assertNull(reminderOpens.pending.value)
    }
}

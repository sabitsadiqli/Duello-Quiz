package com.sabit.duelloquiz.feature.quiz.daily

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.designsystem.daily.ResultSquareState
import com.sabit.duelloquiz.core.model.DailySquare
import com.sabit.duelloquiz.core.model.DailyStart
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.NotificationSettings
import com.sabit.duelloquiz.core.model.ReminderType
import com.sabit.duelloquiz.core.model.SystemNotificationState
import com.sabit.duelloquiz.core.testing.MainDispatcherRule
import com.sabit.duelloquiz.core.testing.TestCategoryRepository
import com.sabit.duelloquiz.core.testing.TestDailyRepository
import com.sabit.duelloquiz.core.testing.TestNetworkMonitor
import com.sabit.duelloquiz.core.testing.TestNotificationPromptRepository
import com.sabit.duelloquiz.core.testing.TestNotificationSettingsRepository
import com.sabit.duelloquiz.core.testing.TestProgressRepository
import com.sabit.duelloquiz.core.testing.TestSystemNotifications
import com.sabit.duelloquiz.core.testing.testDailyAttempt
import com.sabit.duelloquiz.core.testing.testDailyResult
import com.sabit.duelloquiz.core.testing.testProgressGain
import com.sabit.duelloquiz.feature.quiz.result.PermissionCardUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DailyResultViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = TestDailyRepository()
    private val monitor = TestNetworkMonitor(online = true)
    private val noPermission = SystemNotificationState(permissionGranted = false, appNotificationsEnabled = true, dailyChannelEnabled = true, streakChannelEnabled = true)
    private val system = TestSystemNotifications(initial = noPermission)
    private val prompts = TestNotificationPromptRepository()
    private val settings = TestNotificationSettingsRepository(NotificationSettings(dailyReminder = false, streakReminder = false))

    private fun TestScope.viewModel(attemptId: String = "a1", fromStored: Boolean = false) = DailyResultViewModel(
        repository, TestCategoryRepository(), TestProgressRepository(), system, prompts, settings, monitor,
        WallClock { testScheduler.currentTime },
        SavedStateHandle(mapOf(DailyResultViewModel.ARG_ATTEMPT_ID to attemptId, DailyResultViewModel.ARG_FROM_STORED to fromStored)),
    )

    private suspend fun started() {
        repository.startResult = DataResult.Success(DailyStart.Started(testDailyAttempt()))
        repository.start()
    }

    @Test
    fun theServerResultBringsSquaresXpAndStreak() = runTest {
        started()
        repository.finishResult = DataResult.Success(testDailyResult(progress = testProgressGain(xp = 50)))
        val vm = viewModel()
        runCurrent()

        val state = vm.uiState.value
        assertEquals(12, state.number)
        assertEquals(3, state.correctCount)
        assertEquals(
            listOf(ResultSquareState.Correct, ResultSquareState.Wrong, ResultSquareState.Correct, ResultSquareState.Correct, ResultSquareState.None),
            state.squares,
        )
        assertEquals(50, state.progress?.xpGained)
        assertTrue(state.progress?.animate == true)
        assertEquals(7, state.shareStreak)
        assertTrue(state.shareEnabled)
        assertEquals(DailyBand.Good, state.band)
        // NOTIF-03 / NOTIF-04: the own reminders are removed.
        assertEquals(listOf(ReminderType.DAILY, ReminderType.STREAK), system.cancelled)
    }

    @Test
    fun offlineFinishShowsLocalSquaresWithoutXpAndRetriesOnline() = runTest {
        started()
        repository.submitAnswer("a1", "q1", "q1-a", 1_000)
        repository.enqueueAnswer("a1", "q2", "q2-b", 2_000)
        repository.finishResult = DataResult.Failure(DataError.Offline)
        monitor.online.value = false
        val vm = viewModel()
        runCurrent()

        val offline = vm.uiState.value
        assertTrue(offline.notSynced)
        assertNull(offline.progress)
        assertNull(offline.shareStreak)
        assertEquals(ResultSquareState.Correct, offline.squares[0])
        assertEquals(ResultSquareState.Pending, offline.squares[1])
        // Never share guessed squares (DAILY-05).
        assertFalse(offline.shareEnabled)

        repository.finishResult = DataResult.Success(testDailyResult())
        monitor.online.value = true
        runCurrent()

        assertFalse(vm.uiState.value.notSynced)
        assertTrue(vm.uiState.value.shareEnabled)
        assertTrue(vm.uiState.value.progress != null)
    }

    @Test
    fun shareCarriesOnlyServerConfirmedData() = runTest {
        started()
        repository.finishResult = DataResult.Success(testDailyResult(streak = 0))
        val vm = viewModel()
        vm.events.test {
            runCurrent()
            vm.onAction(DailyResultAction.ShareClicked)
            val share = awaitItem() as DailyResultEvent.Share
            assertEquals(12, share.number)
            assertEquals(3, share.correctCount)
            assertEquals(5, share.total)
            // Streak 0: the share text leaves the streak line out.
            assertNull(share.streak)
        }
    }

    @Test
    fun aStoredResultHasNoReviewNoPermissionCardAndNoAnimation() = runTest {
        repository.startResult = DataResult.Success(DailyStart.AlreadyPlayed(testDailyResult(attemptId = "old")))
        repository.start()
        val vm = viewModel(attemptId = "old", fromStored = true)
        runCurrent()

        val state = vm.uiState.value
        assertNull(state.reviewCount)
        assertNull(state.permissionCard)
        assertEquals(3, state.correctCount)
        assertEquals(0, prompts.shownCount)
    }

    @Test
    fun withoutTheAttemptInMemoryTheScreenGoesHome() = runTest {
        val vm = viewModel(attemptId = "gone")
        vm.events.test { assertEquals(DailyResultEvent.NavigateHome, awaitItem()) }
    }

    @Test
    fun zeroCorrectStillShowsTheCompletionBonusAndAnEncouragingBand() = runTest {
        started()
        repository.finishResult = DataResult.Success(
            testDailyResult(squares = List(5) { DailySquare.WRONG }, progress = testProgressGain(xp = 20)),
        )
        val vm = viewModel()
        runCurrent()

        assertEquals(DailyBand.Zero, vm.uiState.value.band)
        assertEquals(20, vm.uiState.value.progress?.xpGained)
    }

    @Test
    fun permissionCardOnTheFirstResultAndGrantTurnsBothRemindersOn() = runTest {
        started()
        repository.finishResult = DataResult.Success(testDailyResult())
        val vm = viewModel()
        vm.events.test {
            runCurrent()
            assertEquals(PermissionCardUi(blocked = false), vm.uiState.value.permissionCard)
            assertEquals(1, prompts.shownCount)

            vm.onAction(DailyResultAction.PermissionAllowClicked)
            assertEquals(DailyResultEvent.RequestPermission, awaitItem())
            system.set(SystemNotificationState.AllAllowed)
            vm.onAction(DailyResultAction.PermissionResult(granted = true, blocked = false))
            runCurrent()
            assertEquals(DailyResultEvent.NotificationsEnabled, awaitItem())
        }
        assertNull(vm.uiState.value.permissionCard)
        assertEquals(NotificationSettings(dailyReminder = true, streakReminder = true), settings.updates.single())
    }

    @Test
    fun laterHidesTheCardAndItWaitsSevenDays() = runTest {
        started()
        repository.finishResult = DataResult.Success(testDailyResult())
        val vm = viewModel()
        runCurrent()

        vm.onAction(DailyResultAction.PermissionLaterClicked)
        runCurrent()

        assertNull(vm.uiState.value.permissionCard)
        assertFalse(prompts.canShow())
    }

    @Test
    fun noPermissionCardWhenGrantedOrOnOldAndroid() = runTest {
        started()
        repository.finishResult = DataResult.Success(testDailyResult())
        system.set(SystemNotificationState.AllAllowed)
        val granted = viewModel()
        runCurrent()
        assertNull(granted.uiState.value.permissionCard)

        val oldAndroid = TestSystemNotifications(initial = noPermission, needsRuntimePermission = false)
        val vm = DailyResultViewModel(
            repository, TestCategoryRepository(), TestProgressRepository(), oldAndroid, prompts, settings, monitor,
            WallClock { testScheduler.currentTime },
            SavedStateHandle(mapOf(DailyResultViewModel.ARG_ATTEMPT_ID to "a1")),
        )
        runCurrent()
        assertNull(vm.uiState.value.permissionCard)
    }

    @Test
    fun aBlockedPermissionOpensTheSystemSettings() = runTest {
        started()
        repository.finishResult = DataResult.Success(testDailyResult())
        prompts.setPermissionBlocked(true)
        val vm = viewModel()
        vm.events.test {
            runCurrent()
            assertEquals(PermissionCardUi(blocked = true), vm.uiState.value.permissionCard)
            vm.onAction(DailyResultAction.PermissionAllowClicked)
            assertTrue(awaitItem() is DailyResultEvent.OpenSettings)
        }
    }
}

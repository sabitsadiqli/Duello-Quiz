package com.sabit.duelloquiz.feature.quiz.result

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.Category
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.QuizOption
import com.sabit.duelloquiz.core.model.QuizResult
import com.sabit.duelloquiz.core.model.QuizSessionStatus
import com.sabit.duelloquiz.core.model.ReminderType
import com.sabit.duelloquiz.core.model.ReviewItem
import com.sabit.duelloquiz.core.model.SystemNotificationState
import com.sabit.duelloquiz.core.testing.MainDispatcherRule
import com.sabit.duelloquiz.core.testing.TestCategoryRepository
import com.sabit.duelloquiz.core.testing.TestDailyRepository
import com.sabit.duelloquiz.core.testing.TestNetworkMonitor
import com.sabit.duelloquiz.core.testing.TestNotificationPromptRepository
import com.sabit.duelloquiz.core.testing.TestNotificationSettingsRepository
import com.sabit.duelloquiz.core.testing.TestProgressRepository
import com.sabit.duelloquiz.core.testing.TestQuizRepository
import com.sabit.duelloquiz.core.testing.TestSystemNotifications
import com.sabit.duelloquiz.core.testing.testProgressGain
import com.sabit.duelloquiz.core.testing.testQuizSession
import com.sabit.duelloquiz.feature.quiz.review.QuizReviewAction
import com.sabit.duelloquiz.feature.quiz.review.QuizReviewUiState
import com.sabit.duelloquiz.feature.quiz.review.QuizReviewViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class QuizResultViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = TestQuizRepository()
    private val monitor = TestNetworkMonitor(online = true)
    private val categories = TestCategoryRepository()

    private fun handle(sessionClosed: Boolean = false) = SavedStateHandle(
        mapOf(
            "sessionId" to "session-1",
            "categoryId" to 1,
            "categoryName" to "Coğrafiya",
            "sessionClosed" to sessionClosed,
        ),
    )

    private val progress = TestProgressRepository()
    private val system = TestSystemNotifications()
    private val prompts = TestNotificationPromptRepository()
    private val notificationSettings = TestNotificationSettingsRepository()

    private fun TestScope.viewModel(sessionClosed: Boolean = false) = QuizResultViewModel(
        repository, categories, progress, system, prompts, notificationSettings, monitor,
        WallClock { testScheduler.currentTime }, handle(sessionClosed),
    )

    private suspend fun answer(vararg optionIds: String) {
        optionIds.forEachIndexed { index, optionId -> repository.submitAnswer("session-1", "q${index + 1}", optionId, 1_000) }
    }

    private fun review(count: Int) = (1..count).map {
        ReviewItem("q$it", it, "Sual $it", null, null, QuizOption("q$it-a", "A"), null)
    }

    // XP and permission card (PROG-02, PROG-09, NOTIF-01)

    @Test
    fun theProgressCardComesFromTheServerAndTheStreakReminderIsRemoved() = runTest {
        repository.register(testQuizSession())
        answer("q1-a")
        system.set(SystemNotificationState.AllAllowed)
        repository.finishResult = DataResult.Success(
            QuizResult("session-1", QuizSessionStatus.FINISHED, 1, 1, 10, review(9), progress = testProgressGain(xp = 10, levelBefore = 4, levelAfter = 5)),
        )
        val vm = viewModel()
        runCurrent()

        val card = vm.uiState.value.progress
        assertEquals(10, card?.xpGained)
        assertTrue(card?.leveledUp == true)
        assertTrue(card?.animate == true)
        assertEquals(listOf(ReminderType.STREAK), system.cancelled)
        // Granted already: no permission card.
        assertNull(vm.uiState.value.permissionCard)
    }

    @Test
    fun noProgressCardWhileFinishFailsAndNoXpLineForZero() = runTest {
        repository.register(testQuizSession())
        repository.finishResult = DataResult.Failure(DataError.Offline)
        val offline = viewModel()
        runCurrent()
        assertNull(offline.uiState.value.progress)

        repository.register(testQuizSession())
        repository.finishResult = DataResult.Success(
            QuizResult("session-1", QuizSessionStatus.EXPIRED, 0, 0, 10, review(10), progress = testProgressGain(xp = 0, extended = false)),
        )
        val zero = viewModel()
        runCurrent()
        assertEquals(0, zero.uiState.value.progress?.xpGained)
        assertFalse(zero.uiState.value.progress?.showCapNote == true)
    }

    @Test
    fun theCapNoteIsShownOncePerDay() = runTest {
        repository.register(testQuizSession())
        answer("q1-a")
        repository.finishResult = DataResult.Success(
            QuizResult("session-1", QuizSessionStatus.FINISHED, 1, 1, 10, review(9), progress = testProgressGain(xp = 0, capped = true)),
        )
        val first = viewModel()
        runCurrent()
        val second = viewModel()
        runCurrent()

        assertTrue(first.uiState.value.progress?.showCapNote == true)
        assertFalse(second.uiState.value.progress?.showCapNote == true)
    }

    @Test
    fun thePermissionCardAppearsOnTheFirstResultOnly() = runTest {
        system.set(SystemNotificationState(permissionGranted = false, appNotificationsEnabled = true, dailyChannelEnabled = true, streakChannelEnabled = true))
        repository.register(testQuizSession())
        repository.finishResult = DataResult.Success(QuizResult("session-1", QuizSessionStatus.FINISHED, 0, 0, 10, review(10)))
        val first = viewModel()
        runCurrent()
        assertEquals(PermissionCardUi(blocked = false), first.uiState.value.permissionCard)
        first.onAction(QuizResultAction.PermissionLaterClicked)
        runCurrent()
        assertNull(first.uiState.value.permissionCard)

        // The next result within 7 days: no card.
        val next = viewModel()
        runCurrent()
        assertNull(next.uiState.value.permissionCard)
    }

    @Test
    fun theHeroUsesTheCategorysAccentAndIconFromTheServerOrder() = runTest {
        repository.register(testQuizSession())
        categories.nextRefresh = DataResult.Success(
            listOf(
                Category(id = 7, slug = "history", name = "Tarix", iconKey = "history"),
                Category(id = 3, slug = "physics", name = "Fizika", iconKey = "physics"),
                Category(id = 1, slug = "geography", name = "Coğrafiya", iconKey = "geography"),
            ),
        )
        categories.refresh()
        val vm = viewModel()
        runCurrent()

        assertEquals("teal", vm.uiState.value.categoryAccent)
        assertEquals("geography", vm.uiState.value.categoryIconKey)
    }

    @Test
    fun withoutCachedCategoriesTheHeroKeepsTheDefaultAccent() = runTest {
        repository.register(testQuizSession())
        val vm = viewModel()
        runCurrent()

        assertEquals("violet", vm.uiState.value.categoryAccent)
        assertEquals("", vm.uiState.value.categoryIconKey)
    }

    @Test
    fun theLocalScoreShowsAtOnceAndTheServerResultAddsTheReview() = runTest {
        repository.register(testQuizSession())
        answer("q1-a", "q2-a", "q3-b")
        repository.finishResult = DataResult.Success(
            QuizResult("session-1", QuizSessionStatus.FINISHED, 2, 2, 10, review(8)),
        )
        val vm = viewModel()
        runCurrent()

        val state = vm.uiState.value
        assertEquals(2, state.correctCount)
        assertEquals(10, state.total)
        assertEquals(8, state.reviewCount)
        assertFalse(state.notSynced)
        assertEquals(ScoreBand.Low, state.band)
        assertEquals(1, repository.finishCount)
    }

    @Test
    fun scoreBandsFollowTheSpec() {
        fun band(correct: Int) = QuizResultUiState("x", correctCount = correct, total = 10).band
        assertEquals(ScoreBand.Perfect, band(10))
        assertEquals(ScoreBand.Great, band(7))
        assertEquals(ScoreBand.Good, band(6))
        assertEquals(ScoreBand.Good, band(4))
        assertEquals(ScoreBand.Low, band(1))
        assertEquals(ScoreBand.Zero, band(0))
    }

    @Test
    fun aPerfectScoreHasNoReviewEntry() = runTest {
        repository.register(testQuizSession())
        answer(*Array(10) { "q${it + 1}-a" })
        val vm = viewModel()
        runCurrent()

        assertTrue(vm.uiState.value.isPerfect)
        assertNull(vm.uiState.value.reviewCount)
    }

    @Test
    fun finishFailingOfflineShowsTheBannerAndRetriesWhenOnline() = runTest {
        repository.register(testQuizSession())
        answer("q1-a")
        repository.finishResult = DataResult.Failure(DataError.Offline)
        monitor.online.value = false
        val vm = viewModel()
        runCurrent()
        assertTrue(vm.uiState.value.notSynced)
        assertEquals(1, vm.uiState.value.correctCount)

        repository.finishResult = null
        monitor.online.value = true
        runCurrent()

        assertEquals(2, repository.finishCount)
        assertFalse(vm.uiState.value.notSynced)
    }

    @Test
    fun anExpiredSessionShowsTheNote() = runTest {
        repository.register(testQuizSession())
        repository.finishResult = DataResult.Success(QuizResult("session-1", QuizSessionStatus.EXPIRED, 0, 0, 10, review(10)))
        val vm = viewModel()
        runCurrent()

        assertTrue(vm.uiState.value.isExpired)
        assertEquals(ScoreBand.Zero, vm.uiState.value.band)
    }

    @Test
    fun aClosedSessionShowsItsMessage() = runTest {
        repository.register(testQuizSession())
        val vm = viewModel(sessionClosed = true)

        vm.events.test { assertEquals(QuizResultEvent.ShowMessage(QuizResultMessage.SessionClosed), awaitItem()) }
    }

    @Test
    fun aMissingSessionGoesHome() = runTest {
        val vm = viewModel()

        vm.events.test { assertEquals(QuizResultEvent.NavigateHome, awaitItem()) }
    }

    @Test
    fun playAgainOfflineStaysWithAMessage() = runTest {
        repository.register(testQuizSession())
        monitor.online.value = false
        val vm = viewModel()
        runCurrent()

        vm.events.test {
            vm.onAction(QuizResultAction.PlayAgainClicked)
            assertEquals(QuizResultEvent.ShowMessage(QuizResultMessage.QuizNeedsInternet), awaitItem())
        }
        assertEquals(0, repository.startCount)
    }

    @Test
    fun playAgainOpensANewSessionOrHandsOverAfterOneSecond() = runTest {
        repository.register(testQuizSession())
        repository.startResult = DataResult.Success(testQuizSession(id = "session-2"))
        val vm = viewModel()
        runCurrent()

        vm.events.test {
            vm.onAction(QuizResultAction.PlayAgainClicked)
            vm.onAction(QuizResultAction.PlayAgainClicked)
            runCurrent()
            assertEquals(QuizResultEvent.NavigateToQuiz(1, "Coğrafiya", "session-2"), awaitItem())

            repository.startGate = CompletableDeferred()
            vm.onAction(QuizResultAction.PlayAgainClicked)
            advanceTimeBy(1_001)
            assertEquals(QuizResultEvent.NavigateToQuiz(1, "Coğrafiya", null), awaitItem())
        }
        assertEquals(2, repository.startCount)
    }

    @Test
    fun playAgainErrorsShowTheirMessage() = runTest {
        repository.register(testQuizSession())
        val vm = viewModel()
        runCurrent()
        val cases = mapOf(
            DataError.RateLimited(null) to QuizResultMessage.RateLimited,
            DataError.Api(ApiErrorCode.CategoryNotPlayable) to QuizResultMessage.CategoryNotPlayable,
            DataError.Server to QuizResultMessage.Generic,
        )
        cases.forEach { (error, message) ->
            repository.startResult = DataResult.Failure(error)
            vm.events.test {
                vm.onAction(QuizResultAction.PlayAgainClicked)
                runCurrent()
                assertEquals(QuizResultEvent.ShowMessage(message), awaitItem())
            }
        }
    }

    // Review (QUIZ-06)

    @Test
    fun reviewShowsWrongAndUnansweredQuestionsFromTheServer() = runTest {
        repository.register(testQuizSession())
        repository.finishResult = DataResult.Success(
            QuizResult(
                "session-1", QuizSessionStatus.FINISHED, 8, 8, 10,
                listOf(
                    ReviewItem("q2", 2, "Sual 2", null, QuizOption("q2-b", "B"), QuizOption("q2-a", "A"), "İzah"),
                    ReviewItem("q5", 5, "Sual 5", null, null, QuizOption("q5-a", "A"), null),
                ),
            ),
        )
        val vm = QuizReviewViewModel(repository, TestDailyRepository(), SavedStateHandle(mapOf("sessionId" to "session-1")))
        runCurrent()

        val items = (vm.uiState.value as QuizReviewUiState.Content).items
        assertEquals(listOf(2, 5), items.map { it.position })
        assertEquals("B", items[0].selectedText)
        assertEquals("İzah", items[0].explanation)
        assertNull(items[1].selectedText)
        assertNull(items[1].explanation)
    }

    @Test
    fun reviewOfflineShowsAnErrorAndRetries() = runTest {
        repository.register(testQuizSession())
        repository.finishResult = DataResult.Failure(DataError.Offline)
        val vm = QuizReviewViewModel(repository, TestDailyRepository(), SavedStateHandle(mapOf("sessionId" to "session-1")))
        runCurrent()
        assertEquals(QuizReviewUiState.Error(offline = true), vm.uiState.value)

        repository.finishResult = null
        vm.onAction(QuizReviewAction.RetryClicked)
        runCurrent()
        assertTrue(vm.uiState.value is QuizReviewUiState.Content)
    }
}

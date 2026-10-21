package com.sabit.duelloquiz.feature.quiz.question

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.sabit.duelloquiz.core.common.HomeNotice
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.testing.MainDispatcherRule
import com.sabit.duelloquiz.core.testing.TestQuizRepository
import com.sabit.duelloquiz.core.testing.TestQuizRepository.SubmittedAnswer
import com.sabit.duelloquiz.core.testing.testQuizSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuizViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = TestQuizRepository()

    private fun TestScope.viewModel(
        sessionId: String? = null,
        handle: SavedStateHandle = SavedStateHandle(
            mapOf("categoryId" to 1, "categoryName" to "Coğrafiya", "sessionId" to sessionId),
        ),
    ): QuizViewModel = QuizViewModel(repository, WallClock { testScheduler.currentTime }, handle)

    private val QuizViewModel.question: QuestionUi
        get() = (uiState.value.phase as QuizPhase.Playing).question

    private fun QuestionUi.stateOf(optionId: String) = options.first { it.id == optionId }.state

    // Start and timer (QUIZ-01, QUIZ-02)

    @Test
    fun startShowsTheFirstQuestionWithA20SecondTimer() = runTest {
        val vm = viewModel()
        assertEquals(QuizPhase.Loading, vm.uiState.value.phase)
        runCurrent()

        val question = vm.question
        assertEquals(1, question.position)
        assertEquals(10, question.total)
        assertEquals(TimerStatus.Running, question.timer.status)
        assertEquals(20, question.timer.remainingSeconds)
        assertEquals(20_000L, question.timer.deadlineWallClockMs)
        assertTrue(question.options.all { it.state == OptionDisplayState.Default })
        assertEquals(1, repository.startCount)
    }

    @Test
    fun theTimerCountsDownOnTheWallClock() = runTest {
        val vm = viewModel()
        runCurrent()

        advanceTimeBy(5_001)
        assertEquals(15, vm.question.timer.remainingSeconds)
        advanceTimeBy(10_000)
        assertEquals(5, vm.question.timer.remainingSeconds)
    }

    @Test
    fun anExistingSessionFromHomeIsUsedWithoutAnotherStart() = runTest {
        repository.register(testQuizSession(id = "from-home"))
        val vm = viewModel(sessionId = "from-home")
        runCurrent()

        assertEquals(0, repository.startCount)
        assertEquals("q1", vm.question.id)
    }

    @Test
    fun recreatedAfterProcessDeathGoesHome() = runTest {
        val handle = SavedStateHandle(mapOf("categoryId" to 1, "categoryName" to "Coğrafiya", QuizViewModel.KEY_STARTED to true))
        val vm = viewModel(handle = handle)

        vm.events.test { assertEquals(QuizEvent.NavigateHome(), awaitItem()) }
        assertEquals(0, repository.startCount)
    }

    @Test
    fun startErrors() = runTest {
        repository.startResult = DataResult.Failure(DataError.RateLimited(retryAfterSeconds = 60))
        val limited = viewModel()
        runCurrent()
        assertEquals(QuizPhase.StartFailed(QuizStartError.RateLimited), limited.uiState.value.phase)

        repository.startResult = DataResult.Failure(DataError.Offline)
        val offline = viewModel()
        runCurrent()
        assertEquals(QuizPhase.StartFailed(QuizStartError.Offline), offline.uiState.value.phase)

        repository.startResult = DataResult.Success(testQuizSession())
        offline.onAction(QuizAction.RetryStartClicked)
        runCurrent()
        assertTrue(offline.uiState.value.phase is QuizPhase.Playing)
    }

    @Test
    fun notPlayableGoesHomeWithTheNotice() = runTest {
        repository.startResult = DataResult.Failure(DataError.Api(ApiErrorCode.CategoryNotPlayable))
        val vm = viewModel()

        vm.events.test {
            runCurrent()
            assertEquals(QuizEvent.NavigateHome(HomeNotice.CategoryNotPlayable), awaitItem())
        }
    }

    @Test
    fun closingTheLoadingScreenCancelsTheStart() = runTest {
        repository.startGate = CompletableDeferred()
        val vm = viewModel()
        runCurrent()

        vm.events.test {
            vm.onAction(QuizAction.CloseClicked)
            assertEquals(QuizEvent.NavigateHome(), awaitItem())
        }
        assertEquals(listOf(1), repository.cancelledStarts)
        assertFalse(vm.uiState.value.showExitDialog)
    }

    // Answer, reveal and auto-advance (QUIZ-03, OQ-023)

    @Test
    fun aTapSendsTheAnswerRevealsForOneAndAHalfSecondsThenAdvances() = runTest {
        val vm = viewModel()
        runCurrent()
        advanceTimeBy(3_200)

        vm.onAction(QuizAction.OptionClicked("q1-a"))
        runCurrent()

        assertEquals(listOf(SubmittedAnswer("q1", "q1-a", 3_200)), repository.submitted)
        val revealed = vm.question
        assertEquals(QuestionStatus.Correct, revealed.status)
        assertEquals(OptionDisplayState.Correct, revealed.stateOf("q1-a"))
        assertEquals(OptionDisplayState.Dimmed, revealed.stateOf("q1-b"))
        assertEquals(TimerStatus.Stopped, revealed.timer.status)
        assertEquals(16_800L, revealed.timer.frozenRemainingMs)
        assertFalse(revealed.acceptsAnswers)

        advanceTimeBy(1_499)
        assertEquals("q1", vm.question.id)
        advanceTimeBy(2)
        assertEquals("q2", vm.question.id)
        assertEquals(20, vm.question.timer.remainingSeconds)
    }

    @Test
    fun aWrongTapMarksItWrongAndTheServersCorrectOption() = runTest {
        val vm = viewModel()
        runCurrent()

        vm.onAction(QuizAction.OptionClicked("q1-c"))
        runCurrent()

        val question = vm.question
        assertEquals(QuestionStatus.Wrong, question.status)
        assertEquals(OptionDisplayState.Wrong, question.stateOf("q1-c"))
        assertEquals(OptionDisplayState.Correct, question.stateOf("q1-a"))
    }

    @Test
    fun aSecondTapIsIgnored() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.answerHandler = { gate.await(); DataResult.Success(TestQuizRepository.defaultVerdict(it)) }
        val vm = viewModel()
        runCurrent()

        vm.onAction(QuizAction.OptionClicked("q1-b"))
        vm.onAction(QuizAction.OptionClicked("q1-c"))
        runCurrent()

        assertEquals(1, repository.submitted.size)
        assertEquals(OptionDisplayState.Selected, vm.question.stateOf("q1-b"))
        assertEquals(OptionDisplayState.Dimmed, vm.question.stateOf("q1-c"))
        gate.complete(Unit)
    }

    @Test
    fun afterTheTenthAnswerTheResultOpens() = runTest {
        val vm = viewModel()
        runCurrent()

        vm.events.test {
            repeat(10) { index ->
                vm.onAction(QuizAction.OptionClicked("q${index + 1}-a"))
                runCurrent()
                advanceTimeBy(1_501)
            }
            assertEquals(QuizEvent.NavigateToResult("session-1", sessionClosed = false), awaitItem())
        }
        assertEquals(10, repository.submitted.size)
    }

    // Timeout (QUIZ-04, OQ-016)

    @Test
    fun aTimeoutSendsANullOptionWith20000ElapsedAndShowsTheCorrectOption() = runTest {
        val vm = viewModel()
        runCurrent()

        advanceTimeBy(20_001)

        assertEquals(listOf(SubmittedAnswer("q1", null, 20_000)), repository.submitted)
        val question = vm.question
        assertEquals(QuestionStatus.Timeout, question.status)
        assertEquals(TimerStatus.Expired, question.timer.status)
        assertEquals(0, question.timer.remainingSeconds)
        assertEquals(OptionDisplayState.Correct, question.stateOf("q1-a"))

        vm.onAction(QuizAction.OptionClicked("q1-b"))
        runCurrent()
        assertEquals(1, repository.submitted.size)
    }

    @Test
    fun theTimerUsesTheServerDeadlineCorrectedByTheClockOffset() = runTest {
        // Server clock 60 s ahead; the session deadline is 10 s away in local time.
        repository.startResult = DataResult.Success(
            testQuizSession(startedAtEpochMs = 60_000 - 235_000, clockOffsetMs = 60_000),
        )
        val vm = viewModel()
        runCurrent()
        assertEquals(10, vm.question.timer.remainingSeconds)

        vm.events.test {
            advanceTimeBy(10_001)
            assertEquals(QuizEvent.NavigateToResult("session-1", sessionClosed = false), awaitItem())
        }
        // The deadline ended the quiz: no timeout answer is sent after it.
        assertTrue(repository.submitted.isEmpty())
    }

    // Network problems (QUIZ-03, QUIZ-08, OQ-046, OQ-047)

    @Test
    fun reconnectingShowsAfterTwoSecondsPending() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.answerHandler = { gate.await(); DataResult.Success(TestQuizRepository.defaultVerdict(it)) }
        val vm = viewModel()
        runCurrent()

        vm.onAction(QuizAction.OptionClicked("q1-a"))
        advanceTimeBy(1_999)
        assertEquals(QuestionStatus.None, vm.question.status)
        advanceTimeBy(2)
        assertEquals(QuestionStatus.Reconnecting, vm.question.status)

        gate.complete(Unit)
        runCurrent()
        assertEquals(QuestionStatus.Correct, vm.question.status)
        // The 1.5 s reveal starts when the result arrives.
        advanceTimeBy(1_499)
        assertEquals("q1", vm.question.id)
    }

    @Test
    fun aNetworkFailureRetriesTheSameAnswerAndShowsReconnectingAtOnce() = runTest {
        var calls = 0
        repository.answerHandler = {
            calls++
            if (calls == 1) DataResult.Failure(DataError.Offline) else DataResult.Success(TestQuizRepository.defaultVerdict(it))
        }
        val vm = viewModel()
        runCurrent()

        vm.onAction(QuizAction.OptionClicked("q1-b"))
        runCurrent()
        assertEquals(QuestionStatus.Reconnecting, vm.question.status)

        advanceTimeBy(1_001)
        assertEquals(2, repository.submitted.size)
        assertEquals(repository.submitted[0], repository.submitted[1])
        assertEquals(QuestionStatus.Wrong, vm.question.status)
    }

    @Test
    fun afterTenSecondsOfflineTheAnswerIsQueuedAndLaterAnswersGoStraightToTheQueue() = runTest {
        repository.answerHandler = { DataResult.Failure(DataError.Offline) }
        val vm = viewModel()
        runCurrent()
        advanceTimeBy(4_000)

        vm.onAction(QuizAction.OptionClicked("q1-b"))
        advanceTimeBy(9_999)
        assertEquals(QuestionStatus.Reconnecting, vm.question.status)
        advanceTimeBy(2)

        assertEquals(listOf(SubmittedAnswer("q1", "q1-b", 4_000)), repository.queued)
        assertEquals(QuestionStatus.AnswerSaved, vm.question.status)
        assertEquals(OptionDisplayState.Queued, vm.question.stateOf("q1-b"))
        assertTrue(vm.uiState.value.showOfflineBanner)

        advanceTimeBy(1_501)
        assertEquals("q2", vm.question.id)
        val submittedBefore = repository.submitted.size

        // q2 opened at 15 500 (queued at 14 000 + 1 500 hold); tap 2 500 ms later.
        advanceTimeBy(2_498)
        vm.onAction(QuizAction.OptionClicked("q2-a"))
        runCurrent()
        assertEquals(submittedBefore, repository.submitted.size)
        assertEquals(SubmittedAnswer("q2", "q2-a", 2_500), repository.queued.last())
        assertEquals(QuestionStatus.AnswerSaved, vm.question.status)

        repository.drainQueue("session-1")
        runCurrent()
        assertFalse(vm.uiState.value.showOfflineBanner)
    }

    @Test
    fun sessionExpiredOrClosedOpensTheResult() = runTest {
        repository.answerHandler = { DataResult.Failure(DataError.Api(ApiErrorCode.SessionExpired)) }
        val expired = viewModel()
        runCurrent()
        expired.events.test {
            expired.onAction(QuizAction.OptionClicked("q1-a"))
            runCurrent()
            assertEquals(QuizEvent.NavigateToResult("session-1", sessionClosed = false), awaitItem())
        }

        repository.answerHandler = { DataResult.Failure(DataError.Api(ApiErrorCode.SessionClosed)) }
        val closed = viewModel()
        runCurrent()
        closed.events.test {
            closed.onAction(QuizAction.OptionClicked("q1-a"))
            runCurrent()
            assertEquals(QuizEvent.NavigateToResult("session-1", sessionClosed = true), awaitItem())
        }
    }

    @Test
    fun aClientErrorGoesHomeWithTheInvalidQuizNotice() = runTest {
        repository.answerHandler = { DataResult.Failure(DataError.Api(ApiErrorCode.InvalidOption)) }
        val vm = viewModel()
        runCurrent()

        vm.events.test {
            vm.onAction(QuizAction.OptionClicked("q1-a"))
            runCurrent()
            assertEquals(QuizEvent.NavigateHome(HomeNotice.QuizInvalid), awaitItem())
        }
        assertEquals(listOf("session-1"), repository.discarded)
    }

    // Leaving (QUIZ-07)

    @Test
    fun closeAsksFirstAndTheTimerKeepsRunningBehindTheDialog() = runTest {
        val vm = viewModel()
        runCurrent()

        vm.onAction(QuizAction.CloseClicked)
        assertTrue(vm.uiState.value.showExitDialog)
        advanceTimeBy(6_001)
        assertEquals(14, vm.question.timer.remainingSeconds)

        vm.onAction(QuizAction.ExitDismissed)
        assertFalse(vm.uiState.value.showExitDialog)

        vm.events.test {
            vm.onAction(QuizAction.CloseClicked)
            vm.onAction(QuizAction.ExitConfirmed)
            assertEquals(QuizEvent.NavigateHome(), awaitItem())
        }
        assertEquals(listOf("session-1"), repository.discarded)
    }

    @Test
    fun aTimeoutBehindTheDialogIsShownWhenItCloses() = runTest {
        val vm = viewModel()
        runCurrent()

        vm.onAction(QuizAction.CloseClicked)
        advanceTimeBy(20_001)
        vm.onAction(QuizAction.ExitDismissed)

        assertEquals(QuestionStatus.Timeout, vm.question.status)
        assertEquals(listOf(SubmittedAnswer("q1", null, 20_000)), repository.submitted)
    }
}

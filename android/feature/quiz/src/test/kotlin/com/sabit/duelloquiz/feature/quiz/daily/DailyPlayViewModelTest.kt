package com.sabit.duelloquiz.feature.quiz.daily

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.model.AnswerResult
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DailyStart
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.testing.MainDispatcherRule
import com.sabit.duelloquiz.core.testing.TestDailyRepository
import com.sabit.duelloquiz.core.testing.testDailyAttempt
import com.sabit.duelloquiz.core.testing.testDailyResult
import com.sabit.duelloquiz.feature.quiz.question.OptionDisplayState
import com.sabit.duelloquiz.feature.quiz.question.QuestionStatus
import com.sabit.duelloquiz.feature.quiz.question.QuestionUi
import com.sabit.duelloquiz.feature.quiz.question.TimerStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DailyPlayViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = TestDailyRepository()

    private fun TestScope.viewModel(attemptId: String? = null) = DailyPlayViewModel(
        repository,
        WallClock { testScheduler.currentTime },
        SavedStateHandle(mapOf(DailyPlayViewModel.ARG_ATTEMPT_ID to attemptId)),
    )

    private val DailyPlayViewModel.question: QuestionUi
        get() = (uiState.value.phase as DailyPhase.Playing).question

    @Test
    fun startShowsTheFirstQuestionWithFiveSteps() = runTest {
        repository.startResult = DataResult.Success(DailyStart.Started(testDailyAttempt()))
        val vm = viewModel()
        assertEquals(DailyPhase.Loading, vm.uiState.value.phase)
        runCurrent()

        assertEquals(1, vm.question.position)
        assertEquals(5, vm.question.total)
        assertEquals(20, vm.question.timer.remainingSeconds)
        assertEquals(
            listOf(DailyStep.Current, DailyStep.Upcoming, DailyStep.Upcoming, DailyStep.Upcoming, DailyStep.Upcoming),
            vm.uiState.value.steps,
        )
    }

    @Test
    fun anAttemptHandedOverByHomeIsUsedWithoutAnotherStart() = runTest {
        repository.register(testDailyAttempt(id = "from-home"))
        val vm = viewModel(attemptId = "from-home")
        runCurrent()

        assertEquals(0, repository.startCount)
        assertEquals("q1", vm.question.id)
    }

    @Test
    fun answersRevealThenAdvanceAndFillTheStepper() = runTest {
        repository.startResult = DataResult.Success(DailyStart.Started(testDailyAttempt()))
        val vm = viewModel()
        runCurrent()

        vm.onAction(DailyPlayAction.OptionClicked("q1-a"))
        runCurrent()
        assertEquals(QuestionStatus.Correct, vm.question.status)
        assertEquals(OptionDisplayState.Correct, vm.question.options.first { it.id == "q1-a" }.state)
        advanceTimeBy(1_501)
        vm.onAction(DailyPlayAction.OptionClicked("q2-b"))
        runCurrent()

        assertEquals(DailyStep.Correct, vm.uiState.value.steps[0])
        assertEquals(DailyStep.Wrong, vm.uiState.value.steps[1])
        assertEquals("q2", vm.question.id)
    }

    @Test
    fun theFifthAnswerOpensTheResult() = runTest {
        repository.startResult = DataResult.Success(DailyStart.Started(testDailyAttempt()))
        val vm = viewModel()
        vm.events.test {
            runCurrent()
            repeat(5) { index ->
                vm.onAction(DailyPlayAction.OptionClicked("q${index + 1}-a"))
                runCurrent()
                advanceTimeBy(1_501)
            }
            assertEquals(DailyPlayEvent.NavigateToResult("a1", fromStored = false), awaitItem())
        }
    }

    @Test
    fun alreadyPlayedOpensTheStoredResult() = runTest {
        repository.startResult = DataResult.Success(DailyStart.AlreadyPlayed(testDailyResult(attemptId = "old")))
        val vm = viewModel()

        vm.events.test {
            runCurrent()
            assertEquals(DailyPlayEvent.NavigateToResult("old", fromStored = true), awaitItem())
        }
    }

    @Test
    fun unavailableAndOfflineStartStates() = runTest {
        repository.startResult = DataResult.Failure(DataError.Api(ApiErrorCode.DailyUnavailable))
        val unavailable = viewModel()
        runCurrent()
        assertEquals(DailyPhase.Unavailable, unavailable.uiState.value.phase)

        repository.startResult = DataResult.Failure(DataError.Offline)
        val offline = viewModel()
        runCurrent()
        assertEquals(DailyPhase.StartFailed(offline = true), offline.uiState.value.phase)

        // "Yenidən cəhd et" uses the same endpoint, which resumes an attempt that was created.
        repository.startResult = DataResult.Success(DailyStart.Started(testDailyAttempt()))
        offline.onAction(DailyPlayAction.RetryClicked)
        runCurrent()
        assertTrue(offline.uiState.value.phase is DailyPhase.Playing)

        repository.startResult = DataResult.Failure(DataError.Server)
        val server = viewModel()
        runCurrent()
        assertEquals(DailyPhase.StartFailed(offline = false), server.uiState.value.phase)
    }

    @Test
    fun resumeOpensTheFirstUnansweredQuestionWithAFreshTimer() = runTest {
        val answered = AnswerResult("q1", "q1-a", true, "q1-a", null)
        val timeout = AnswerResult("q2", null, false, "q2-a", null)
        repository.startResult = DataResult.Success(DailyStart.Started(testDailyAttempt(answers = listOf(answered, timeout))))
        val vm = viewModel()
        runCurrent()

        assertEquals("q3", vm.question.id)
        assertEquals(20, vm.question.timer.remainingSeconds)
        assertEquals(listOf(DailyStep.Correct, DailyStep.Timeout, DailyStep.Current), vm.uiState.value.steps.take(3))
    }

    @Test
    fun aQuestionWhoseTimeRanOutWhileTheAppWasDeadIsSentAsATimeout() = runTest {
        // The process died at t = 25 s while q1 (shown at 0) was open; the app returns at 30 s.
        advanceTimeBy(30_000)
        repository.setShownAt("a1", "q1", 0)
        repository.startResult = DataResult.Success(DailyStart.Started(testDailyAttempt()))
        val vm = viewModel()
        runCurrent()

        assertEquals(TestDailyRepository.SubmittedAnswer("q1", null, 20_000), repository.submitted.first())
        assertEquals("q2", vm.question.id)
        assertEquals(DailyStep.Timeout, vm.uiState.value.steps[0])
    }

    @Test
    fun aQuestionShownBeforeKeepsItsRemainingTime() = runTest {
        advanceTimeBy(12_000)
        repository.setShownAt("a1", "q1", 5_000)
        repository.startResult = DataResult.Success(DailyStart.Started(testDailyAttempt()))
        val vm = viewModel()
        runCurrent()

        assertEquals("q1", vm.question.id)
        // Shown at 5 s, now 12 s: 13 s are left, not a fresh 20 s.
        assertEquals(13, vm.question.timer.remainingSeconds)
    }

    @Test
    fun theTimerIsCappedByTheAttemptDeadline() = runTest {
        // Started 130 s ago: only 7.5 s of the 137.5 s deadline are left.
        advanceTimeBy(130_000)
        repository.startResult = DataResult.Success(DailyStart.Started(testDailyAttempt(startedAt = 0)))
        val vm = viewModel()
        runCurrent()

        assertEquals(8, vm.question.timer.remainingSeconds)
    }

    @Test
    fun aWrongDeviceClockDoesNotChangeTheTimer() = runTest {
        // The device clock is 1 h behind the server: offset +1 h. The attempt just started.
        val offset = 3_600_000L
        repository.startResult = DataResult.Success(DailyStart.Started(testDailyAttempt(startedAt = offset, offset = offset)))
        val vm = viewModel()
        runCurrent()

        assertEquals(20, vm.question.timer.remainingSeconds)
        assertEquals(TimerStatus.Running, vm.question.timer.status)
    }

    @Test
    fun afterTheDeadlineTheResultOpens() = runTest {
        advanceTimeBy(200_000)
        repository.startResult = DataResult.Success(DailyStart.Started(testDailyAttempt(startedAt = 0)))
        val vm = viewModel()

        vm.events.test {
            runCurrent()
            assertEquals(DailyPlayEvent.NavigateToResult("a1", fromStored = false), awaitItem())
        }
    }

    @Test
    fun leavingAsksFirstAndKeepsTheAttempt() = runTest {
        repository.startResult = DataResult.Success(DailyStart.Started(testDailyAttempt()))
        val vm = viewModel()
        runCurrent()

        vm.onAction(DailyPlayAction.CloseClicked)
        assertTrue(vm.uiState.value.showExitDialog)
        vm.onAction(DailyPlayAction.ExitDismissed)
        assertTrue(!vm.uiState.value.showExitDialog)

        vm.events.test {
            vm.onAction(DailyPlayAction.CloseClicked)
            vm.onAction(DailyPlayAction.ExitConfirmed)
            assertEquals(DailyPlayEvent.NavigateHome(), awaitItem())
        }
        // Nothing was sent to end the attempt: home shows "Davam et" until the deadline.
        assertEquals("a1", repository.attempt("a1")?.id)
        assertTrue(repository.submitted.isEmpty())
    }

    @Test
    fun anOfflineAnswerIsQueuedAndShownAsQueued() = runTest {
        repository.startResult = DataResult.Success(DailyStart.Started(testDailyAttempt()))
        val vm = viewModel()
        runCurrent()
        repeat(10) { repository.answerResults += DataResult.Failure(DataError.Offline) }

        vm.onAction(DailyPlayAction.OptionClicked("q1-b"))
        advanceTimeBy(10_001)
        runCurrent()

        assertEquals(listOf(TestDailyRepository.SubmittedAnswer("q1", "q1-b", 0)), repository.queued)
        assertEquals(DailyStep.Queued, vm.uiState.value.steps[0])
        assertTrue(vm.uiState.value.showOfflineBanner)
    }

    @Test
    fun stepsFromVerdictsQueueAndCurrent() {
        val attempt = testDailyAttempt()
        val steps = dailySteps(
            questions = attempt.questions,
            resolved = mapOf(
                "q1" to AnswerResult("q1", "q1-a", true, "q1-a", null),
                "q2" to AnswerResult("q2", "q2-b", false, "q2-a", null),
                "q3" to AnswerResult("q3", null, false, "q3-a", null),
            ),
            queued = listOf("q4"),
            currentIndex = 4,
        )

        assertEquals(listOf(DailyStep.Correct, DailyStep.Wrong, DailyStep.Timeout, DailyStep.Queued, DailyStep.Current), steps)
    }
}

package com.sabit.duelloquiz.feature.quiz.daily

import androidx.compose.runtime.Immutable
import com.sabit.duelloquiz.core.common.HomeNotice
import com.sabit.duelloquiz.core.model.AnswerResult
import com.sabit.duelloquiz.core.model.QuizQuestion
import com.sabit.duelloquiz.feature.quiz.question.QuestionUi

/** One segment of the 5-step result stepper (daily-challenge.md C.2). */
enum class DailyStep { Correct, Wrong, Timeout, Queued, Current, Upcoming }

@Immutable
sealed interface DailyPhase {
    /** Start or resume running; the loading view shows after its own delay. */
    data object Loading : DailyPhase

    data class StartFailed(val offline: Boolean) : DailyPhase

    /** The status became `unavailable` meanwhile (409 `daily_unavailable`). */
    data object Unavailable : DailyPhase

    data class Playing(val question: QuestionUi) : DailyPhase
}

@Immutable
data class DailyPlayUiState(
    val phase: DailyPhase = DailyPhase.Loading,
    val steps: List<DailyStep> = emptyList(),
    /** "Çıxsan da vaxt gedir…" (DAILY-03); the timer keeps running behind it. */
    val showExitDialog: Boolean = false,
    val showOfflineBanner: Boolean = false,
    val prefetchImageUrls: List<String> = emptyList(),
)

sealed interface DailyPlayAction {
    data class OptionClicked(val optionId: String) : DailyPlayAction
    data object CloseClicked : DailyPlayAction
    data object ExitConfirmed : DailyPlayAction
    data object ExitDismissed : DailyPlayAction
    data object RetryClicked : DailyPlayAction

    /** Back in the foreground: recompute the timer from the wall clock (DAILY-08). */
    data object Resumed : DailyPlayAction
}

sealed interface DailyPlayEvent {
    data class NavigateHome(val notice: HomeNotice? = null) : DailyPlayEvent

    /** [fromStored]: 409 `daily_already_played` — the stored result, without review. */
    data class NavigateToResult(val attemptId: String, val fromStored: Boolean) : DailyPlayEvent
}

/**
 * The stepper segments from the server verdicts, the offline queue and the question on screen
 * (pure; unit-tested). A timeout is a verdict without an option.
 */
internal fun dailySteps(
    questions: List<QuizQuestion>,
    resolved: Map<String, AnswerResult>,
    queued: Collection<String>,
    currentIndex: Int?,
): List<DailyStep> = questions.mapIndexed { index, question ->
    val verdict = resolved[question.id]
    when {
        verdict != null && verdict.optionId == null -> DailyStep.Timeout
        verdict != null && verdict.correct -> DailyStep.Correct
        verdict != null -> DailyStep.Wrong
        question.id in queued -> DailyStep.Queued
        index == currentIndex -> DailyStep.Current
        else -> DailyStep.Upcoming
    }
}

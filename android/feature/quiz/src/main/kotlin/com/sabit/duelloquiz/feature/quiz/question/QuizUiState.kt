package com.sabit.duelloquiz.feature.quiz.question

import androidx.compose.runtime.Immutable
import com.sabit.duelloquiz.core.common.HomeNotice

@Immutable
data class QuizUiState(
    val categoryName: String,
    val phase: QuizPhase = QuizPhase.Loading,
    /** Leave-quiz confirmation (QUIZ-07); the timer keeps running behind it. */
    val showExitDialog: Boolean = false,
    /** "İnternet yoxdur. Cavablar göndəriləcək." while answers wait in the offline queue (QUIZ-08). */
    val showOfflineBanner: Boolean = false,
    /** Images Coil should fetch ahead of time (first two at the start, then the next one). */
    val prefetchImageUrls: List<String> = emptyList(),
)

@Immutable
sealed interface QuizPhase {
    /** Start request running (only shown after 1 s, the loading view itself waits 300 ms). */
    data object Loading : QuizPhase

    data class StartFailed(val error: QuizStartError) : QuizPhase

    data class Playing(val question: QuestionUi) : QuizPhase
}

enum class QuizStartError {
    Offline,
    Generic,

    /** 429: only "Geri" is offered (quiz-question.md). */
    RateLimited,
}

@Immutable
data class QuestionUi(
    val id: String,
    /** 1-based. */
    val position: Int,
    val total: Int,
    val text: String,
    val image: QuestionImageUi?,
    val options: List<OptionUi>,
    val timer: TimerUi,
    val status: QuestionStatus,
    /** Taps are accepted only while the timer runs and no answer is pending. */
    val acceptsAnswers: Boolean,
)

@Immutable
data class QuestionImageUi(
    val url: String,
    val dominantColorArgb: Long?,
    val alt: String?,
)

@Immutable
data class OptionUi(
    val id: String,
    val text: String,
    val state: OptionDisplayState,
)

/** Mirrors the option card states of components.md §3. */
enum class OptionDisplayState { Default, Selected, Correct, Wrong, Dimmed, Queued }

enum class TimerStatus { Running, Stopped, Expired }

@Immutable
data class TimerUi(
    val status: TimerStatus,
    /** Whole seconds left (rounded up), as shown by the digits. */
    val remainingSeconds: Int,
    val totalMs: Long,
    /** Local wall-clock time when the question timer reaches 0 (server deadline − clock offset). */
    val deadlineWallClockMs: Long,
    /** Remaining time shown while stopped. */
    val frozenRemainingMs: Long,
)

/** The status line under the options (quiz-question.md, OQ-046, OQ-047). */
enum class QuestionStatus { None, Correct, Wrong, Timeout, Reconnecting, AnswerSaved }

sealed interface QuizEvent {
    /** Leave the quiz; [notice] is shown as a snackbar on home. */
    data class NavigateHome(val notice: HomeNotice? = null) : QuizEvent

    /** Quiz over (10 answers, deadline or closed session); the result screen replaces the quiz. */
    data class NavigateToResult(val sessionId: String, val sessionClosed: Boolean) : QuizEvent
}

sealed interface QuizAction {
    data class OptionClicked(val optionId: String) : QuizAction

    /** Close icon or system back. */
    data object CloseClicked : QuizAction
    data object ExitConfirmed : QuizAction
    data object ExitDismissed : QuizAction
    data object RetryStartClicked : QuizAction

    /** Back from the foreground: recompute the timer from the wall clock (QUIZ-08). */
    data object Resumed : QuizAction
}

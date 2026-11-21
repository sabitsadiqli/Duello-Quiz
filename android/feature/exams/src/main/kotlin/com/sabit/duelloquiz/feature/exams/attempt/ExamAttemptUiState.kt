package com.sabit.duelloquiz.feature.exams.attempt

import androidx.compose.runtime.Immutable
import com.sabit.duelloquiz.core.data.exam.ExamDownloadState
import com.sabit.duelloquiz.core.model.exam.ExamGroupKind
import com.sabit.duelloquiz.core.model.exam.ExamTaskType

@Immutable
data class ExamOptionUi(val id: String, val letter: String, val text: String)

/** One task page; the answer fields are the local (Room) state, never correctness (EXAM-06). */
@Immutable
data class ExamTaskUi(
    val id: String,
    /** Exam number ("Tapşırıq 14"); kept when listening is skipped (EXAM-28). */
    val number: Int,
    val sectionId: String,
    val sectionTitle: String,
    val groupId: String?,
    val groupKind: ExamGroupKind?,
    val type: ExamTaskType,
    val prompt: String,
    val instructions: String?,
    val options: List<ExamOptionUi>,
    val selectedOptionId: String?,
    val text: String,
    val flagged: Boolean,
) {
    val answered: Boolean get() = if (type == ExamTaskType.WRITTEN) text.isNotBlank() else selectedOptionId != null
}

@Immutable
data class ExamPassageUi(val groupId: String, val title: String?, val passage: String)

/** Caption line of the attempt player (components.md §42 state table). */
enum class PlayerCaption { None, CountsAfter, Interrupted, Preparing, Redownloading, Restored }

/** The listening player of a group (components.md §42), derived from [com.sabit.duelloquiz.feature.exams.audio.ListeningPlayState]. */
@Immutable
data class ExamPlayerUi(
    val groupId: String,
    val maxPlays: Int,
    val playsUsed: Int,
    val playsLeft: Int,
    val currentPlayNumber: Int,
    val playActive: Boolean,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val countsAfterSeconds: Int,
    val caption: PlayerCaption,
    val exhausted: Boolean,
    val failed: Boolean,
    val failedOffline: Boolean,
    val busy: Boolean,
    val readOnly: Boolean,
)

enum class TaskSegment { TASK, PASSAGE }

@Immutable
data class NavigatorSectionUi(
    val title: String,
    val firstNumber: Int,
    val lastNumber: Int,
    /** Skipped listening (EXAM-28): "Keçildi" instead of cells. */
    val skipped: Boolean,
    /** Indices into [ExamAttemptUiState.pages]. */
    val pageIndices: List<Int>,
)

@Immutable
data class ExamCounts(val answered: Int, val unanswered: Int, val flagged: Int)

@Immutable
sealed interface AttemptPhase {
    /** Room + package parse took > 300 ms: header with the live timer, body "Sınaq açılır…". */
    data object Opening : AttemptPhase
    data object Active : AttemptPhase

    /** EXAM-13: the package files are gone; download again while the timer runs. */
    data class PackageMissing(val download: ExamDownloadState, val sizeMb: String) : AttemptPhase

    /** H: user submit in progress (back disabled). */
    data object Submitting : AttemptPhase

    /** G: time is up; [sending] = flushing + submitting, else the offline view. */
    data class TimeUp(val sending: Boolean) : AttemptPhase

    /** H offline: "Nəticə internet olanda hesablanacaq". */
    data object SubmitOffline : AttemptPhase
}

@Immutable
sealed interface AttemptDialog {
    data class Leave(val minutesLeft: Int) : AttemptDialog
    data class Submit(val counts: ExamCounts) : AttemptDialog

    /** 409 `attempt_closed` (EXAM-13). */
    data object Closed : AttemptDialog
}

@Immutable
data class ExamAttemptUiState(
    val phase: AttemptPhase = AttemptPhase.Opening,
    val variantNumber: Int = 0,
    val remainingMs: Long = 0,
    val durationMs: Long = 1,
    val totalTasks: Int = 0,
    val sectionTitles: List<String> = emptyList(),
    val pages: List<ExamTaskUi> = emptyList(),
    val currentIndex: Int = 0,
    val segment: TaskSegment = TaskSegment.TASK,
    val passages: Map<String, ExamPassageUi> = emptyMap(),
    val passageOffsets: Map<String, Int> = emptyMap(),
    val player: ExamPlayerUi? = null,
    val listeningSkipped: Boolean = false,
    val navigator: List<NavigatorSectionUi> = emptyList(),
    val navigatorOpen: Boolean = false,
    val counts: ExamCounts = ExamCounts(0, 0, 0),
    val dialog: AttemptDialog? = null,
    val offline: Boolean = false,
    val maxTextLength: Int = DEFAULT_MAX_TEXT,
    /** Exam content language (`en`): keyboard hint locale of the written field (components.md §43). */
    val language: String = "en",
) {
    val readOnly: Boolean get() = phase != AttemptPhase.Active
    val warning: Boolean get() = remainingMs <= WARNING_MS
    val timeFraction: Float get() = (remainingMs.toFloat() / durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
    val currentTask: ExamTaskUi? get() = pages.getOrNull(currentIndex)
    val isLastPage: Boolean get() = currentIndex >= pages.lastIndex

    companion object {
        const val DEFAULT_MAX_TEXT = 1_000
        const val WARNING_MS = 600_000L
    }
}

sealed interface ExamAttemptAction {
    data class PageChanged(val index: Int) : ExamAttemptAction
    data object Next : ExamAttemptAction
    data object Previous : ExamAttemptAction
    data class GoTo(val index: Int) : ExamAttemptAction
    data class SelectSegment(val segment: TaskSegment) : ExamAttemptAction
    data class SelectOption(val taskId: String, val optionId: String) : ExamAttemptAction
    data class ClearAnswer(val taskId: String) : ExamAttemptAction
    data class TextChanged(val taskId: String, val text: String) : ExamAttemptAction
    data class ToggleFlag(val taskId: String) : ExamAttemptAction
    data class PassageScrolled(val groupId: String, val charOffset: Int) : ExamAttemptAction
    data object PlayPause : ExamAttemptAction
    data object RetryAudio : ExamAttemptAction
    data object OpenNavigator : ExamAttemptAction
    data object CloseNavigator : ExamAttemptAction
    data object CloseClicked : ExamAttemptAction
    data object LeaveLater : ExamAttemptAction
    data object FinishClicked : ExamAttemptAction
    data object ConfirmSubmit : ExamAttemptAction
    data object DismissDialog : ExamAttemptAction
    data object OpenResult : ExamAttemptAction
    data object BackToCatalogue : ExamAttemptAction
    data object DownloadPackage : ExamAttemptAction
    data object TimerTapped : ExamAttemptAction
    data class Foreground(val visible: Boolean) : ExamAttemptAction
}

sealed interface ExamAttemptEvent {
    data class OpenResult(val attemptId: String) : ExamAttemptEvent
    data object Leave : ExamAttemptEvent

    /** 30 / 10 / 1 min left: short snackbar + polite announcement (EXAM-04). */
    data class TimeNotice(val minutes: Int) : ExamAttemptEvent

    /** Time is up: close the keyboard, assertive announcement once (EXAM-14). */
    data object TimeUp : ExamAttemptEvent
    data object MergedFromOtherDevice : ExamAttemptEvent
    data class FlagChanged(val flagged: Boolean) : ExamAttemptEvent

    /** "Qalan vaxt: 42 dəqiqə" on a timer tap (EXAM-22). */
    data class AnnounceTime(val remainingMs: Long) : ExamAttemptEvent
    data object ListeningCounted : ExamAttemptEvent
}

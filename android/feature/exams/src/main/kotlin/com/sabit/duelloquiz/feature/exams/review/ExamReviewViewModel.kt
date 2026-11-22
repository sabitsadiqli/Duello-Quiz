package com.sabit.duelloquiz.feature.exams.review

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.common.Telemetry
import com.sabit.duelloquiz.core.data.exam.ExamRepository
import com.sabit.duelloquiz.core.data.exam.LocalExamMark
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.exam.ExamGroupKind
import com.sabit.duelloquiz.core.model.exam.ExamMark
import com.sabit.duelloquiz.core.model.exam.ExamOutcome
import com.sabit.duelloquiz.core.model.exam.ExamReview
import com.sabit.duelloquiz.core.model.exam.ExamReviewTask
import com.sabit.duelloquiz.core.model.exam.ExamTaskType
import com.sabit.duelloquiz.core.model.exam.ExamTranscriptLine
import com.sabit.duelloquiz.feature.exams.audio.ExamAudioEngine
import com.sabit.duelloquiz.feature.exams.audio.ReviewPlayerController
import com.sabit.duelloquiz.feature.exams.navigation.ExamArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ReviewFilter { ALL, WRONG, UNANSWERED, FLAGGED, WRITTEN }

@Immutable
data class ReviewTaskUi(
    val task: ExamReviewTask,
    /** Local mark first (pending or confirmed), then the server's `selfMark`. */
    val mark: ExamMark?,
)

@Immutable
data class ReviewGroupUi(
    val id: String,
    val kind: ExamGroupKind,
    val title: String?,
    val passage: String?,
    val transcript: List<ExamTranscriptLine>,
    val excluded: Boolean,
    val hasAudio: Boolean,
)

@Immutable
sealed interface ReviewItem {
    data class Section(val title: String, val first: Int, val last: Int) : ReviewItem
    data class Group(val group: ReviewGroupUi) : ReviewItem
    data class Task(val task: ReviewTaskUi) : ReviewItem
}

@Immutable
sealed interface ExamReviewUiState {
    data object Loading : ExamReviewUiState
    data object Error : ExamReviewUiState
    data object Offline : ExamReviewUiState
    data class Content(val items: List<ReviewItem>, val filter: ReviewFilter, val counts: Map<ReviewFilter, Int>) : ExamReviewUiState
}

sealed interface ExamReviewEvent {
    /** 409 before submit: back to the catalogue with a generic error (EXAM-18). */
    data object LeaveNotFinished : ExamReviewEvent
}

/** Review "Təhlil" (EXAM-18, EXAM-26): cached review, filters, local marks, the review player. */
@HiltViewModel
class ExamReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ExamRepository,
    private val audio: ExamAudioEngine,
    private val telemetry: Telemetry,
) : ViewModel() {

    val attemptId = ExamArgs.attemptId(savedStateHandle)
    private val filter = MutableStateFlow(ReviewFilter.ALL)
    private val failure = MutableStateFlow<DataError?>(null)
    private val attempted = MutableStateFlow(false)
    private val _events = Channel<ExamReviewEvent>(Channel.BUFFERED)
    val events: Flow<ExamReviewEvent> = _events.receiveAsFlow()
    private val players = mutableMapOf<String, ReviewPlayerController>()

    val uiState: StateFlow<ExamReviewUiState> = combine(
        repository.cachedReview(attemptId),
        repository.localMarks(attemptId),
        filter,
        failure,
        attempted,
    ) { review, marks, filter, failure, attempted ->
        when {
            review != null -> buildReviewState(review, marks, filter)
            !attempted -> ExamReviewUiState.Loading
            failure == DataError.Offline -> ExamReviewUiState.Offline
            else -> ExamReviewUiState.Error
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ExamReviewUiState.Loading)

    init {
        refresh()
        telemetry.logEvent("exam_review_open", mapOf("filter" to "all"))
    }

    fun refresh() {
        viewModelScope.launch {
            val result = repository.refreshReview(attemptId)
            val error = (result as? DataResult.Failure)?.error
            if (error is DataError.Api && error.code == ApiErrorCode.AttemptNotFinished) {
                telemetry.recordNonFatal(IllegalStateException("exam review before submit"))
                _events.send(ExamReviewEvent.LeaveNotFinished)
            }
            failure.value = error
            attempted.value = true
        }
    }

    fun selectFilter(next: ReviewFilter) {
        filter.value = next
        telemetry.logEvent("exam_review_open", mapOf("filter" to next.name.lowercase()))
    }

    /** One controller per listening group; only one player plays at a time (shared engine). */
    fun player(groupId: String): ReviewPlayerController? {
        players[groupId]?.let { return it }
        val review = (uiState.value as? ExamReviewUiState.Content) ?: return null
        val group = review.items.filterIsInstance<ReviewItem.Group>().firstOrNull { it.group.id == groupId } ?: return null
        val media = repositoryAudio[groupId] ?: return null
        return ReviewPlayerController("review:$attemptId:$groupId", media, group.group.title.orEmpty(), audio, repository, viewModelScope)
            .also { players[groupId] = it }
    }

    private val repositoryAudio = mutableMapOf<String, com.sabit.duelloquiz.core.model.exam.ExamMedia>()

    private fun buildReviewState(review: ExamReview, marks: Map<String, LocalExamMark>, filter: ReviewFilter): ExamReviewUiState {
        review.groups.forEach { group -> group.audio?.let { repositoryAudio[group.id] = it } }
        return reviewContent(review, marks, filter)
    }

    override fun onCleared() {
        players.values.forEach { it.release() }
        super.onCleared()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

internal fun ReviewTaskUi.matches(filter: ReviewFilter): Boolean = when (filter) {
    ReviewFilter.ALL -> true
    ReviewFilter.WRONG -> task.outcome == ExamOutcome.WRONG
    ReviewFilter.UNANSWERED -> task.outcome == ExamOutcome.BLANK ||
        (task.type == ExamTaskType.WRITTEN && task.text.isNullOrBlank()) ||
        (task.outcome == ExamOutcome.EXCLUDED && task.selectedOptionId == null && task.text.isNullOrBlank())
    ReviewFilter.FLAGGED -> task.flagged
    ReviewFilter.WRITTEN -> task.type == ExamTaskType.WRITTEN
}

/** Pure (unit-tested): sections in exam order, a context card before each group, filtered tasks. */
internal fun reviewContent(review: ExamReview, marks: Map<String, LocalExamMark>, filter: ReviewFilter): ExamReviewUiState.Content {
    val tasks = review.tasks.map { task -> ReviewTaskUi(task, if (task.id in marks) marks.getValue(task.id).mark else task.selfMark) }
    val counts = ReviewFilter.entries.associateWith { f -> tasks.count { it.matches(f) } }
    val groups = review.groups.associateBy { it.id }
    val items = buildList {
        for (section in review.sections) {
            val inSection = tasks.filter { it.task.sectionId == section.id }
            val shown = inSection.filter { it.matches(filter) }
            if (shown.isEmpty()) continue
            add(ReviewItem.Section(section.title, inSection.minOf { it.task.position }, inSection.maxOf { it.task.position }))
            var lastGroup: String? = null
            for (task in shown) {
                val groupId = task.task.groupId
                if (groupId != null && groupId != lastGroup) {
                    groups[groupId]?.let { group ->
                        add(
                            ReviewItem.Group(
                                ReviewGroupUi(
                                    id = group.id,
                                    kind = group.kind,
                                    title = group.title,
                                    passage = group.passage,
                                    transcript = group.transcript,
                                    excluded = inSection.any { it.task.groupId == group.id && it.task.outcome == ExamOutcome.EXCLUDED },
                                    hasAudio = group.audio != null,
                                ),
                            ),
                        )
                    }
                }
                lastGroup = groupId
                add(ReviewItem.Task(task))
            }
        }
    }
    return ExamReviewUiState.Content(items, filter, counts)
}

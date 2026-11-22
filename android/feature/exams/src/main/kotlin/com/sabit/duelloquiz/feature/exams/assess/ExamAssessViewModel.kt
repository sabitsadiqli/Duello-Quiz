package com.sabit.duelloquiz.feature.exams.assess

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.common.Telemetry
import com.sabit.duelloquiz.core.data.exam.ExamMarkOutcome
import com.sabit.duelloquiz.core.data.exam.ExamRepository
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.exam.ExamGroupKind
import com.sabit.duelloquiz.core.model.exam.ExamMark
import com.sabit.duelloquiz.core.model.exam.ExamReviewGroup
import com.sabit.duelloquiz.core.model.exam.ExamReviewTask
import com.sabit.duelloquiz.core.model.exam.ExamTaskType
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

@Immutable
data class AssessPageUi(
    val task: ExamReviewTask,
    val sectionTitle: String,
    val group: ExamReviewGroup?,
    val mark: ExamMark?,
)

@Immutable
sealed interface ExamAssessUiState {
    data object Loading : ExamAssessUiState
    data object Error : ExamAssessUiState
    data object Offline : ExamAssessUiState
    data class Content(
        val pages: List<AssessPageUi>,
        val initialPage: Int,
        val savedOffline: Boolean,
    ) : ExamAssessUiState
}

sealed interface ExamAssessEvent {
    data object Rejected : ExamAssessEvent
}

/**
 * Self-assessment (EXAM-16): one page per written task, marks 0 / ⅓ / ½ / ⅔ / 1 saved at once
 * (Room first, then the server, which recomputes the estimate). Editable any time (OQ-D47).
 */
@HiltViewModel
class ExamAssessViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ExamRepository,
    private val audio: ExamAudioEngine,
    private val telemetry: Telemetry,
) : ViewModel() {

    private val attemptId = ExamArgs.attemptId(savedStateHandle)
    private val startTaskId = ExamArgs.taskId(savedStateHandle)
    private val edit = ExamArgs.edit(savedStateHandle)
    private val attempted = MutableStateFlow(false)
    private val failure = MutableStateFlow<DataError?>(null)
    private val savedOffline = MutableStateFlow(false)
    private var initialPage: Int? = null
    private val _events = Channel<ExamAssessEvent>(Channel.BUFFERED)
    val events: Flow<ExamAssessEvent> = _events.receiveAsFlow()
    private val players = mutableMapOf<String, ReviewPlayerController>()
    private val touched = mutableSetOf<String>()

    val uiState: StateFlow<ExamAssessUiState> = combine(
        repository.cachedReview(attemptId),
        repository.localMarks(attemptId),
        attempted,
        failure,
        savedOffline,
    ) { review, marks, attempted, failure, offline ->
        when {
            review != null -> {
                val sections = review.sections.associateBy { it.id }
                val groups = review.groups.associateBy { it.id }
                val pages = review.tasks.filter { it.type == ExamTaskType.WRITTEN }.map { task ->
                    AssessPageUi(
                        task = task,
                        sectionTitle = sections[task.sectionId]?.title.orEmpty(),
                        group = task.groupId?.let(groups::get),
                        mark = if (task.id in marks) marks.getValue(task.id).mark else task.selfMark,
                    )
                }
                val start = initialPage ?: startPage(pages, startTaskId, edit).also { initialPage = it }
                ExamAssessUiState.Content(pages, start, savedOffline = offline || marks.values.any { it.pending })
            }
            !attempted -> ExamAssessUiState.Loading
            failure == DataError.Offline -> ExamAssessUiState.Offline
            else -> ExamAssessUiState.Error
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ExamAssessUiState.Loading)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val result = repository.refreshReview(attemptId)
            failure.value = (result as? DataResult.Failure)?.error
            attempted.value = true
        }
    }

    fun setMark(taskId: String, mark: ExamMark?) {
        touched += taskId
        viewModelScope.launch {
            when (repository.setMark(attemptId, taskId, mark)) {
                is ExamMarkOutcome.Saved -> savedOffline.value = false
                ExamMarkOutcome.Queued -> savedOffline.value = true
                ExamMarkOutcome.Rejected -> _events.send(ExamAssessEvent.Rejected)
                is ExamMarkOutcome.Failed -> savedOffline.value = true
            }
        }
    }

    fun player(group: ExamReviewGroup): ReviewPlayerController? {
        val media = group.audio ?: return null
        if (group.kind != ExamGroupKind.LISTENING) return null
        return players.getOrPut(group.id) {
            ReviewPlayerController("review:$attemptId:${group.id}", media, group.title.orEmpty(), audio, repository, viewModelScope)
        }
    }

    /** Analytics when the student leaves the flow. */
    fun onLeave() {
        val content = uiState.value as? ExamAssessUiState.Content ?: return
        telemetry.logEvent(
            "exam_self_assess_done",
            mapOf(
                "assessed_count" to content.pages.count { it.mark != null },
                "skipped_count" to content.pages.count { it.mark == null },
                "is_edit" to edit,
            ),
        )
    }

    override fun onCleared() {
        players.values.forEach { it.release() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** [taskId] wins; else the first unassessed (or the first page when editing). Pure, unit-tested. */
internal fun startPage(pages: List<AssessPageUi>, taskId: String?, edit: Boolean): Int {
    if (taskId != null) pages.indexOfFirst { it.task.id == taskId }.takeIf { it >= 0 }?.let { return it }
    if (edit) return 0
    return pages.indexOfFirst { it.mark == null }.coerceAtLeast(0)
}

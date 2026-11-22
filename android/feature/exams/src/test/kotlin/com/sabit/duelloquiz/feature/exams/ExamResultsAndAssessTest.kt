package com.sabit.duelloquiz.feature.exams

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.common.NoOpTelemetry
import com.sabit.duelloquiz.core.data.exam.ExamMarkOutcome
import com.sabit.duelloquiz.core.model.exam.ExamAttemptStatus
import com.sabit.duelloquiz.core.model.exam.ExamClosedCounts
import com.sabit.duelloquiz.core.model.exam.ExamCriterion
import com.sabit.duelloquiz.core.model.exam.ExamEstimate
import com.sabit.duelloquiz.core.model.exam.ExamMark
import com.sabit.duelloquiz.core.model.exam.ExamMode
import com.sabit.duelloquiz.core.model.exam.ExamOutcome
import com.sabit.duelloquiz.core.model.exam.ExamResult
import com.sabit.duelloquiz.core.model.exam.ExamReview
import com.sabit.duelloquiz.core.model.exam.ExamReviewTask
import com.sabit.duelloquiz.core.model.exam.ExamSection
import com.sabit.duelloquiz.core.model.exam.ExamSubmitTrigger
import com.sabit.duelloquiz.core.model.exam.ExamTaskType
import com.sabit.duelloquiz.core.model.exam.ExamWrittenCounts
import com.sabit.duelloquiz.core.model.exam.ListeningExclusion
import com.sabit.duelloquiz.core.testing.MainDispatcherRule
import com.sabit.duelloquiz.core.testing.TestExamRepository
import com.sabit.duelloquiz.core.testing.TestNetworkMonitor
import com.sabit.duelloquiz.feature.exams.assess.ExamAssessUiState
import com.sabit.duelloquiz.feature.exams.assess.ExamAssessViewModel
import com.sabit.duelloquiz.feature.exams.result.ExamResultUiState
import com.sabit.duelloquiz.feature.exams.result.ExamResultViewModel
import com.sabit.duelloquiz.feature.exams.result.ResultNote
import com.sabit.duelloquiz.feature.exams.result.buildResultContent
import com.sabit.duelloquiz.feature.exams.review.ReviewFilter
import com.sabit.duelloquiz.feature.exams.review.reviewContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

object TestResults {
    fun result(
        attemptId: String,
        total: Double = 58.4,
        max: Double = 100.0,
        exclusion: ListeningExclusion? = null,
        trigger: ExamSubmitTrigger = ExamSubmitTrigger.USER,
        assessed: Int = 0,
    ) = ExamResult(
        attemptId = attemptId, variantId = "v1", version = 2, templateId = "buraxilis-ingilis", mode = ExamMode.SUBJECT,
        status = ExamAttemptStatus.SUBMITTED, isRetake = false, submitTrigger = trigger, submittedAtEpochMs = 1_000_000,
        estimate = ExamEstimate(total, 46.9, 11.5, max, if (exclusion != null) 28 else 37, exclusion != null, exclusion),
        closed = ExamClosedCounts(17, 5, 1, 23),
        written = ExamWrittenCounts(assessed, 7, 1.33, 7),
        sections = emptyList(), answeredCount = 22, flaggedCount = 3,
    )

    fun task(id: String, position: Int, type: ExamTaskType, outcome: ExamOutcome, flagged: Boolean = false, mark: ExamMark? = null) = ExamReviewTask(
        id = id, position = position, sectionId = "s1", groupId = null, type = type, prompt = "P$position", options = emptyList(),
        selectedOptionId = null, correctOptionId = null, outcome = outcome, text = if (type == ExamTaskType.WRITTEN) "text" else null,
        flagged = flagged, explanation = null, modelAnswer = "Model",
        criteria = listOf(ExamCriterion(ExamMark.ONE, "Full")), selfMark = mark,
    )

    fun review(vararg tasks: ExamReviewTask) = ExamReview("a1", "v1", 2, "en", listOf(ExamSection("s1", "Oxu", 1)), emptyList(), tasks.toList())
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExamResultsAndAssessTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun resultShowsTheServerNumbersAndTheReducedMaximum() {
        val content = buildResultContent(
            TestResults.result("a1", total = 58.4, max = 75.7, exclusion = ListeningExclusion.SKIPPED, trigger = ExamSubmitTrigger.DEADLINE),
            variantNumber = 1, subject = "İngilis dili", marksPending = false, offline = false,
        )
        assertEquals("58,4", content.score)
        assertEquals("75,7", content.maxScore)
        assertEquals(listOf(ResultNote.AutoSubmitted, ResultNote.ListeningSkipped), content.notes)
        assertEquals(7, content.unassessed)
        assertEquals("46,9", content.autoScore)
    }

    @Test
    fun fullMaximumSplitsIntoAutoAndWrittenParts() {
        val content = buildResultContent(TestResults.result("a1"), null, null, marksPending = false, offline = false)
        assertEquals("100", content.maxScore)
        assertEquals("62,2", content.autoMax)
        assertEquals("37,8", content.selfMax)
    }

    @Test
    fun resultViewModelReadsTheCacheAndRefreshes() = runTest {
        val repository = TestExamRepository().apply { results.value = mapOf("a1" to TestResults.result("a1")) }
        val vm = ExamResultViewModel(SavedStateHandle(mapOf("attemptId" to "a1")), repository, TestNetworkMonitor(), NoOpTelemetry)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        val state = vm.uiState.value as ExamResultUiState.Content
        assertEquals("58,4", state.content.score)
        vm.viewModelScope.cancel()
    }

    @Test
    fun assessmentOpensAtTheFirstUnassessedTaskAndQueuesOfflineMarks() = runTest {
        val repository = TestExamRepository().apply {
            reviews.value = mapOf(
                "a1" to TestResults.review(
                    TestResults.task("w1", 4, ExamTaskType.WRITTEN, ExamOutcome.ASSESSED, mark = ExamMark.HALF),
                    TestResults.task("c1", 5, ExamTaskType.CLOSED_SINGLE, ExamOutcome.CORRECT),
                    TestResults.task("w2", 6, ExamTaskType.WRITTEN, ExamOutcome.NOT_ASSESSED),
                ),
            )
            markOutcome = ExamMarkOutcome.Queued
        }
        val vm = ExamAssessViewModel(SavedStateHandle(mapOf("attemptId" to "a1")), repository, FakeAudioEngine(), NoOpTelemetry)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        var state = vm.uiState.value as ExamAssessUiState.Content
        assertEquals(listOf("w1", "w2"), state.pages.map { it.task.id })
        assertEquals(1, state.initialPage)

        vm.setMark("w2", ExamMark.TWO_THIRDS)
        runCurrent()
        state = vm.uiState.value as ExamAssessUiState.Content
        assertEquals(ExamMark.TWO_THIRDS, state.pages[1].mark)
        assertTrue(state.savedOffline)
        assertEquals(listOf("w2" to ExamMark.TWO_THIRDS), repository.markWrites)
        vm.viewModelScope.cancel()
    }

    @Test
    fun aRejectedMarkRevertsToTheServerValue() = runTest {
        val repository = TestExamRepository().apply {
            reviews.value = mapOf("a1" to TestResults.review(TestResults.task("w1", 4, ExamTaskType.WRITTEN, ExamOutcome.ASSESSED, mark = ExamMark.HALF)))
            markOutcome = ExamMarkOutcome.Rejected
        }
        val vm = ExamAssessViewModel(SavedStateHandle(mapOf("attemptId" to "a1", "edit" to true)), repository, FakeAudioEngine(), NoOpTelemetry)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        vm.setMark("w1", ExamMark.ONE)
        runCurrent()
        assertEquals(ExamMark.HALF, (vm.uiState.value as ExamAssessUiState.Content).pages.single().mark)
        vm.viewModelScope.cancel()
    }

    @Test
    fun reviewFiltersByServerOutcomeAndFlags() {
        val review = TestResults.review(
            TestResults.task("c1", 1, ExamTaskType.CLOSED_SINGLE, ExamOutcome.WRONG, flagged = true),
            TestResults.task("c2", 2, ExamTaskType.CLOSED_SINGLE, ExamOutcome.CORRECT),
            TestResults.task("c3", 3, ExamTaskType.CLOSED_SINGLE, ExamOutcome.BLANK),
            TestResults.task("w1", 4, ExamTaskType.WRITTEN, ExamOutcome.NOT_ASSESSED),
        )
        val all = reviewContent(review, emptyMap(), ReviewFilter.ALL)
        assertEquals(1, all.counts[ReviewFilter.WRONG])
        assertEquals(1, all.counts[ReviewFilter.UNANSWERED])
        assertEquals(1, all.counts[ReviewFilter.FLAGGED])
        assertEquals(1, all.counts[ReviewFilter.WRITTEN])
        val wrong = reviewContent(review, emptyMap(), ReviewFilter.WRONG)
        assertEquals(2, wrong.items.size) // section header + the wrong task
        assertTrue(reviewContent(review, emptyMap(), ReviewFilter.FLAGGED).items.isNotEmpty())
        val localMark = mapOf("w1" to com.sabit.duelloquiz.core.data.exam.LocalExamMark("w1", null, pending = true))
        val written = reviewContent(review, localMark, ReviewFilter.WRITTEN).items.last() as com.sabit.duelloquiz.feature.exams.review.ReviewItem.Task
        assertNull(written.task.mark)
    }
}

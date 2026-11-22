package com.sabit.duelloquiz.feature.exams

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.sabit.duelloquiz.core.common.NoOpTelemetry
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.data.exam.ExamSubmitOutcome
import com.sabit.duelloquiz.core.data.exam.LocalAttemptPhase
import com.sabit.duelloquiz.core.data.exam.LocalExamAttempt
import com.sabit.duelloquiz.core.data.exam.LocalListening
import com.sabit.duelloquiz.core.model.exam.ExamGroup
import com.sabit.duelloquiz.core.model.exam.ExamGroupKind
import com.sabit.duelloquiz.core.model.exam.ExamMedia
import com.sabit.duelloquiz.core.model.exam.ExamOption
import com.sabit.duelloquiz.core.model.exam.ExamPackage
import com.sabit.duelloquiz.core.model.exam.ExamSection
import com.sabit.duelloquiz.core.model.exam.ExamSubmitTrigger
import com.sabit.duelloquiz.core.model.exam.ExamTask
import com.sabit.duelloquiz.core.model.exam.ExamTaskType
import com.sabit.duelloquiz.core.testing.MainDispatcherRule
import com.sabit.duelloquiz.core.testing.TestExamRepository
import com.sabit.duelloquiz.core.testing.TestNetworkMonitor
import com.sabit.duelloquiz.feature.exams.attempt.AttemptDialog
import com.sabit.duelloquiz.feature.exams.attempt.AttemptPhase
import com.sabit.duelloquiz.feature.exams.attempt.ExamAttemptAction
import com.sabit.duelloquiz.feature.exams.attempt.ExamAttemptEvent
import com.sabit.duelloquiz.feature.exams.attempt.ExamAttemptViewModel
import com.sabit.duelloquiz.feature.exams.attempt.PlayerCaption
import com.sabit.duelloquiz.feature.exams.attempt.TaskSegment
import com.sabit.duelloquiz.feature.exams.audio.AudioSnapshot
import com.sabit.duelloquiz.feature.exams.audio.ExamAudioEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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
import java.io.File

/** Records the commands; the test drives positions like the Media3 engine would. */
class FakeAudioEngine : ExamAudioEngine {
    val snapshot = MutableStateFlow(AudioSnapshot())
    override val state: StateFlow<AudioSnapshot> = snapshot
    var plays = 0
    var seeks = 0
    override fun load(key: String, file: File, startPositionMs: Long, seekAllowed: Boolean, title: String) {
        snapshot.value = AudioSnapshot(key = key, positionMs = startPositionMs, durationMs = 185_000)
    }
    override fun play() {
        plays++
        snapshot.update { it.copy(isPlaying = true, ended = false) }
    }
    override fun pause() = snapshot.update { it.copy(isPlaying = false) }
    override fun seekTo(positionMs: Long) {
        seeks++
    }
    override fun stop(key: String) {
        snapshot.value = AudioSnapshot()
    }
    fun moveTo(positionMs: Long) = snapshot.update { it.copy(positionMs = positionMs) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExamAttemptViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val base = 1_000_000L
    private val audioMedia = ExamMedia("https://m/a.m4a", 1_000, "a".repeat(64), "audio/mp4", 185_000)
    private val pkg = ExamPackage(
        variantId = "v1", version = 2, templateId = "buraxilis-ingilis", language = "en",
        durationMs = 3_600_000, graceMs = 120_000, optionCount = 5, listeningMaxPlays = 2,
        listeningPlayCountsAfterMs = 10_000, maxTextLength = 1_000,
        sections = listOf(ExamSection("listening", "Dinləmə", 1), ExamSection("language", "Qrammatika və lüğət", 2), ExamSection("reading", "Oxu", 3)),
        groups = listOf(
            ExamGroup("g1", ExamGroupKind.LISTENING, "listening", 1, "Library", null, null, audioMedia),
            ExamGroup("g2", ExamGroupKind.READING, "reading", 2, "Garden", null, "One.\n\nTwo.", null),
        ),
        tasks = listOf(
            ExamTask("t1", 1, "listening", "g1", ExamTaskType.CLOSED_SINGLE, "Q1", listOf(ExamOption("o1", "A", "a"), ExamOption("o2", "B", "b"))),
            ExamTask("t2", 2, "language", null, ExamTaskType.CLOSED_SINGLE, "Q2", listOf(ExamOption("o3", "A", "a"), ExamOption("o4", "B", "b"))),
            ExamTask("t3", 3, "reading", "g2", ExamTaskType.WRITTEN, "Q3", emptyList()),
        ),
        media = listOf(audioMedia),
        packageBytes = 1_000,
    )

    private fun attempt(skipListening: Boolean = false, offset: Long = 0, currentTask: String? = null) = LocalExamAttempt(
        attemptId = "a1", variantId = "v1", variantNumber = 1, version = 2, templateId = "buraxilis-ingilis",
        templateTitle = "Buraxılış — İngilis dili", isRetake = false, skipListening = skipListening,
        startedAtEpochMs = base, endsAtEpochMs = base + 3_600_000, deadlineEpochMs = base + 3_720_000,
        lateUploadUntilEpochMs = base + 90_000_000, clockOffsetMs = offset, phase = LocalAttemptPhase.RUNNING,
        submitTrigger = null, currentTaskId = currentTask, visitedGroupIds = emptySet(), reminderPosted = false,
    )

    private val network = TestNetworkMonitor(online = true)
    private val audio = FakeAudioEngine()
    private lateinit var repository: TestExamRepository

    private fun TestScope.viewModel(attempt: LocalExamAttempt = attempt()): ExamAttemptViewModel {
        repository = TestExamRepository(attempt, pkg).apply { mediaFile = File("audio") }
        val vm = ExamAttemptViewModel(
            savedStateHandle = SavedStateHandle(mapOf("attemptId" to "a1")),
            repository = repository,
            networkMonitor = network,
            audio = audio,
            clock = WallClock { base + testScheduler.currentTime },
            telemetry = NoOpTelemetry,
        )
        runCurrent()
        return vm
    }

    private fun ExamAttemptViewModel.finish() = viewModelScope.cancel()

    @Test
    fun countdownTargetsEndsAtOnTheServerClock() = runTest {
        // Server 30 s ahead of the phone: 59:30 are left, not 60:00.
        val vm = viewModel(attempt(offset = 30_000))
        assertEquals(3_570_000L, vm.uiState.value.remainingMs)
        assertEquals(AttemptPhase.Active, vm.uiState.value.phase)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(3_510_000L, vm.uiState.value.remainingMs)
        vm.finish()
    }

    @Test
    fun timeUpStopsInputAndAutoSubmitsWithTheDeadlineTrigger() = runTest {
        val vm = viewModel()
        repository.submitOutcome = ExamSubmitOutcome.Queued
        vm.events.test {
            advanceTimeBy(3_600_000)
            runCurrent()
            var event = awaitItem()
            while (event !is ExamAttemptEvent.TimeUp) event = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        runCurrent()
        assertEquals(listOf(ExamSubmitTrigger.DEADLINE), repository.submits)
        assertEquals(AttemptPhase.TimeUp(sending = false), vm.uiState.value.phase)
        vm.onAction(ExamAttemptAction.SelectOption("t2", "o3"))
        runCurrent()
        assertTrue(repository.answers.value.isEmpty())
        vm.finish()
    }

    @Test
    fun timeUpOnOpenGoesStraightToTheTimeUpState() = runTest {
        val vm = viewModel(attempt(offset = 3_600_000))
        runCurrent()
        assertEquals(listOf(ExamSubmitTrigger.DEADLINE), repository.submits)
        assertTrue(vm.uiState.value.phase is AttemptPhase.TimeUp)
        vm.finish()
    }

    @Test
    fun answersAreSavedAsRevisionsAndSynced() = runTest {
        val vm = viewModel()
        vm.onAction(ExamAttemptAction.SelectOption("t2", "o3"))
        vm.onAction(ExamAttemptAction.SelectOption("t2", "o4"))
        runCurrent()
        val answer = repository.answers.value.getValue("t2")
        assertEquals(2, answer.revision)
        assertEquals("o4", answer.optionId)
        val syncsBefore = repository.syncs
        advanceTimeBy(1_100)
        runCurrent()
        assertTrue(repository.syncs > syncsBefore)
        assertEquals(1, vm.uiState.value.counts.answered)
        vm.finish()
    }

    @Test
    fun theOfflineQueueSyncsWhenTheNetworkReturns() = runTest {
        network.online.value = false
        val vm = viewModel()
        assertTrue(vm.uiState.value.offline)
        val syncsBefore = repository.syncs
        network.online.value = true
        runCurrent()
        assertFalse(vm.uiState.value.offline)
        assertTrue(repository.syncs > syncsBefore)
        vm.finish()
    }

    @Test
    fun writtenTextIsDebouncedAndLimited() = runTest {
        val vm = viewModel()
        vm.onAction(ExamAttemptAction.TextChanged("t3", "a".repeat(1_200)))
        runCurrent()
        assertTrue(repository.textWrites.isEmpty())
        assertEquals(1_000, vm.uiState.value.pages.first { it.id == "t3" }.text.length)
        advanceTimeBy(600)
        runCurrent()
        assertEquals(1_000, repository.textWrites.single().second.length)
        vm.finish()
    }

    @Test
    fun firstVisitOfAReadingGroupOpensThePassage() = runTest {
        val vm = viewModel()
        vm.onAction(ExamAttemptAction.GoTo(2))
        runCurrent()
        assertEquals(TaskSegment.PASSAGE, vm.uiState.value.segment)
        vm.onAction(ExamAttemptAction.Previous)
        vm.onAction(ExamAttemptAction.Next)
        runCurrent()
        assertEquals(TaskSegment.TASK, vm.uiState.value.segment)
        vm.finish()
    }

    @Test
    fun resumeLandsOnTheLastTask() = runTest {
        val vm = viewModel(attempt(currentTask = "t2"))
        assertEquals(1, vm.uiState.value.currentIndex)
        vm.finish()
    }

    @Test
    fun skippedListeningRemovesItsTasksButKeepsNumbers() = runTest {
        val vm = viewModel(attempt(skipListening = true))
        assertEquals(listOf(2, 3), vm.uiState.value.pages.map { it.number })
        assertNull(vm.uiState.value.player)
        assertTrue(vm.uiState.value.navigator.first().skipped)
        vm.finish()
    }

    @Test
    fun aPlayCountsAfterTenSecondsAndIsSaved() = runTest {
        val vm = viewModel()
        runCurrent()
        vm.onAction(ExamAttemptAction.PlayPause)
        runCurrent()
        assertEquals(1, audio.plays)
        assertEquals(PlayerCaption.CountsAfter, vm.uiState.value.player?.caption)
        var position = 0L
        while (position < 10_000) {
            position += 250
            audio.moveTo(position)
            runCurrent()
        }
        val player = vm.uiState.value.player!!
        assertEquals(1, player.playsUsed)
        assertEquals(PlayerCaption.None, player.caption)
        val saved = repository.savedListening.last()
        assertEquals(1, saved.playsUsed)
        assertTrue(saved.playInProgress)
        assertEquals(0, audio.seeks)
        vm.finish()
    }

    @Test
    fun aCountedPlayKilledByTheSystemResumesPausedAtItsPosition() = runTest {
        repository = TestExamRepository(attempt(), pkg)
        val engine = audio
        val vm = run {
            val repo = TestExamRepository(attempt(), pkg).apply {
                mediaFile = File("audio")
                listeningState.value = mapOf("g1" to LocalListening("g1", playsUsed = 1, audioFailed = false, positionMs = 72_000, playInProgress = true))
            }
            repository = repo
            ExamAttemptViewModel(
                SavedStateHandle(mapOf("attemptId" to "a1")), repo, network, engine,
                WallClock { base + testScheduler.currentTime }, NoOpTelemetry,
            ).also { runCurrent() }
        }
        val player = vm.uiState.value.player!!
        assertTrue(player.playActive)
        assertFalse(player.playing)
        assertEquals(72_000, player.positionMs)
        assertEquals(PlayerCaption.Restored, player.caption)
        assertEquals(72_000, audio.snapshot.value.positionMs)
        vm.finish()
    }

    @Test
    fun noticesFireOnceWhenCrossedLiveButNotAfterTheBackground() = runTest {
        val vm = viewModel()
        vm.events.test {
            advanceTimeBy(30 * 60_000L + 1_000)
            runCurrent()
            assertEquals(ExamAttemptEvent.TimeNotice(30), awaitItem())
            vm.onAction(ExamAttemptAction.Foreground(false))
            advanceTimeBy(20 * 60_000L + 1_000)
            runCurrent()
            vm.onAction(ExamAttemptAction.Foreground(true))
            runCurrent()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        vm.finish()
    }

    @Test
    fun userSubmitShowsCountsThenOpensTheResult() = runTest {
        val vm = viewModel()
        vm.onAction(ExamAttemptAction.SelectOption("t1", "o1"))
        vm.onAction(ExamAttemptAction.ToggleFlag("t2"))
        runCurrent()
        vm.onAction(ExamAttemptAction.FinishClicked)
        val dialog = vm.uiState.value.dialog as AttemptDialog.Submit
        // The empty written task counts as unanswered (EXAM-12).
        assertEquals(2, dialog.counts.unanswered)
        assertEquals(1, dialog.counts.flagged)
        repository.submitOutcome = ExamSubmitOutcome.Submitted(TestResults.result("a1"))
        vm.events.test {
            vm.onAction(ExamAttemptAction.ConfirmSubmit)
            runCurrent()
            var event = awaitItem()
            while (event !is ExamAttemptEvent.OpenResult) event = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(ExamSubmitTrigger.USER), repository.submits)
        vm.finish()
    }

    @Test
    fun offlineUserSubmitShowsTheOfflineView() = runTest {
        val vm = viewModel()
        vm.onAction(ExamAttemptAction.FinishClicked)
        vm.onAction(ExamAttemptAction.ConfirmSubmit)
        runCurrent()
        assertEquals(AttemptPhase.SubmitOffline, vm.uiState.value.phase)
        vm.finish()
    }
}

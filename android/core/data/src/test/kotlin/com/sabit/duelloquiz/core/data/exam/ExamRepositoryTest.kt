package com.sabit.duelloquiz.core.data.exam

import com.sabit.duelloquiz.core.auth.fake.InMemoryAuthRepository
import com.sabit.duelloquiz.core.common.NoOpTelemetry
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.exam.ExamAnswer
import com.sabit.duelloquiz.core.model.exam.ExamAttemptStatus
import com.sabit.duelloquiz.core.model.exam.ExamListeningState
import com.sabit.duelloquiz.core.model.exam.ExamMark
import com.sabit.duelloquiz.core.model.exam.ExamStartOutcome
import com.sabit.duelloquiz.core.model.exam.ExamSubmitTrigger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExamRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val network = FakeExamNetwork()
    private val dao = FakeExamDao()
    private val scheduler = RecordingScheduler()
    private val auth = InMemoryAuthRepository(latencyMs = 0)
    private var now = 1_100_000L
    private val clock = WallClock { now }

    private fun repository() = DefaultExamRepository(
        network = network,
        dao = dao,
        store = ExamPackageStore(tmp.root),
        downloads = ExamDownloadProgress(),
        scheduler = scheduler,
        authRepository = auth,
        deviceIds = ExamDeviceIdProvider { "device-1" },
        clock = clock,
        telemetry = NoOpTelemetry,
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    private suspend fun startedRepository(): DefaultExamRepository {
        auth.signUpWithEmail("Aysel", "aysel@example.com", "password1")
        val repository = repository()
        val outcome = repository.start(FakeExamNetwork.variant(), version = 2, skipListening = false, idempotencyKey = "key-0001")
        assertTrue((outcome as DataResult.Success).value is ExamStartOutcome.Started)
        return repository
    }

    @Test
    fun startStoresTheAttemptForTheBanner() = runTest {
        val repository = startedRepository()

        val active = repository.activeAttempt.first()!!

        assertEquals("a1", active.attemptId)
        assertEquals(1, active.variantNumber)
        assertEquals(LocalAttemptPhase.RUNNING, active.phase)
        assertEquals(3_500_000L, active.remainingMs(now))
    }

    @Test
    fun everyTapIsOneRevisionAndTheLastTapWins() = runTest {
        val repository = startedRepository()

        repository.selectOption("a1", "t1", "o1")
        repository.selectOption("a1", "t1", "o2")
        repository.setFlag("a1", "t1", true)

        val answer = repository.attemptSnapshot("a1").first()!!.answers.getValue("t1")
        assertEquals(3, answer.revision)
        assertEquals("o2", answer.optionId)
        assertTrue(answer.flagged)
        assertFalse(answer.synced)
    }

    @Test
    fun syncUploadsTheQueueAndMarksItSynced() = runTest {
        val repository = startedRepository()
        repository.selectOption("a1", "t1", "o1")
        repository.setText("a1", "t2", "Because it rains")

        assertEquals(ExamSyncOutcome.Synced, repository.sync("a1"))

        assertEquals(listOf("t1", "t2"), network.batches.single().map { it.taskId })
        assertEquals("Because it rains", network.batches.single().last().text)
        assertTrue(repository.attemptSnapshot("a1").first()!!.answers.values.all { it.synced })
    }

    @Test
    fun offlineKeepsAnswersQueuedAndSchedulesTheWorker() = runTest {
        val repository = startedRepository()
        network.answersError = DataError.Offline
        repository.selectOption("a1", "t1", "o1")

        val outcome = repository.sync("a1")

        assertEquals(ExamSyncOutcome.Pending(DataError.Offline), outcome)
        assertEquals(1, scheduler.syncs)
        assertFalse(repository.attemptSnapshot("a1").first()!!.answers.getValue("t1").synced)

        network.answersError = null
        assertTrue(repository.syncAllPending())
        assertTrue(repository.attemptSnapshot("a1").first()!!.answers.getValue("t1").synced)
    }

    @Test
    fun largeQueuesGoInBatchesOfAHundred() = runTest {
        val repository = startedRepository()
        repeat(150) { repository.selectOption("a1", "t$it", "o1") }

        repository.sync("a1")

        assertEquals(listOf(100, 50), network.batches.map { it.size })
    }

    @Test
    fun malformedAnswersLeaveTheQueueSilently() = runTest {
        val repository = startedRepository()
        network.answersError = DataError.Api(ApiErrorCode.InvalidOption)
        repository.selectOption("a1", "t1", "o9")

        assertEquals(ExamSyncOutcome.Synced, repository.sync("a1"))

        // Kept locally (EXAM-13), but no endless resend.
        assertEquals("o9", repository.attemptSnapshot("a1").first()!!.answers.getValue("t1").optionId)
        assertEquals(0, scheduler.syncs)
    }

    @Test
    fun closedAttemptStopsAnswering() = runTest {
        val repository = startedRepository()
        network.answersError = DataError.Api(ApiErrorCode.AttemptClosed)
        repository.selectOption("a1", "t1", "o1")

        assertEquals(ExamSyncOutcome.Closed, repository.sync("a1"))
        repository.selectOption("a1", "t1", "o2")

        val snapshot = repository.attemptSnapshot("a1").first()!!
        assertEquals(LocalAttemptPhase.CLOSED, snapshot.attempt.phase)
        assertEquals("o1", snapshot.answers.getValue("t1").optionId)
    }

    @Test
    fun offlineSubmitIsQueuedReadOnlyAndSentLater() = runTest {
        val repository = startedRepository()
        repository.selectOption("a1", "t1", "o1")
        network.answersError = DataError.Offline

        assertEquals(ExamSubmitOutcome.Queued, repository.submit("a1", ExamSubmitTrigger.DEADLINE))
        repository.selectOption("a1", "t1", "o2") // read-only now

        assertEquals(LocalAttemptPhase.SUBMIT_PENDING, repository.localAttempt("a1")!!.phase)
        assertEquals("o1", repository.attemptSnapshot("a1").first()!!.answers.getValue("t1").optionId)

        network.answersError = null
        assertTrue(repository.syncAllPending())
        assertEquals(listOf(ExamSubmitTrigger.DEADLINE), network.submits)
        assertEquals(LocalAttemptPhase.SUBMITTED, repository.localAttempt("a1")!!.phase)
        assertEquals(46.9, repository.cachedResult("a1").first()!!.estimate.total, 0.0)
        assertNull(repository.activeAttempt.first())
    }

    @Test
    fun submitFlushesTheQueueFirst() = runTest {
        val repository = startedRepository()
        repository.selectOption("a1", "t1", "o1")

        val outcome = repository.submit("a1", ExamSubmitTrigger.USER)

        assertTrue(outcome is ExamSubmitOutcome.Submitted)
        assertEquals(1, network.batches.size)
        assertEquals(listOf(ExamSubmitTrigger.USER), network.submits)
    }

    @Test
    fun lateWindowOverClosesTheAttempt() = runTest {
        val repository = startedRepository()
        repository.selectOption("a1", "t1", "o1")
        now = FakeExamNetwork.attempt().lateUploadUntilEpochMs + 1

        assertEquals(ExamSyncOutcome.Closed, repository.sync("a1"))
        assertTrue(network.batches.isEmpty())
    }

    @Test
    fun otherDeviceAnswersMergeByRevision() = runTest {
        val repository = startedRepository()
        repository.selectOption("a1", "t1", "o1") // local revision 1
        repository.selectOption("a1", "t2", "o1")
        repository.selectOption("a1", "t2", "o2")
        repository.selectOption("a1", "t2", "o3") // local revision 3
        network.attempt = FakeExamNetwork.attempt(
            answers = listOf(
                ExamAnswer("t1", revision = 2, optionId = "o4", text = null, flagged = false, savedAtEpochMs = 1_050_000),
                ExamAnswer("t2", revision = 1, optionId = "o9", text = null, flagged = false, savedAtEpochMs = 1_050_000),
            ),
        )

        val changed = (repository.refreshAttempt("a1") as DataResult.Success).value

        assertTrue(changed)
        val answers = repository.attemptSnapshot("a1").first()!!.answers
        assertEquals("o4", answers.getValue("t1").optionId)
        assertTrue(answers.getValue("t1").synced)
        assertEquals("o3", answers.getValue("t2").optionId)
        assertFalse(answers.getValue("t2").synced)
    }

    @Test
    fun listeningPlaysNeverGoDownAndFailureIsSticky() = runTest {
        val repository = startedRepository()
        repository.saveListening("a1", LocalListening("g1", playsUsed = 1, audioFailed = true, positionMs = 12_000, playInProgress = true))
        repository.saveListening("a1", LocalListening("g1", playsUsed = 0, audioFailed = false, positionMs = 13_000, playInProgress = true))

        val state = repository.attemptSnapshot("a1").first()!!.listening.getValue("g1")
        assertEquals(1, state.playsUsed)
        assertTrue(state.audioFailed)
        assertEquals(13_000L, state.positionMs)

        repository.sync("a1")
        assertEquals(listOf("g1" to 1), network.listeningWrites)
    }

    @Test
    fun serverPlaysAndPositionRestoreAfterReinstall() = runTest {
        auth.signUpWithEmail("Aysel", "aysel@example.com", "password1")
        network.attempt = FakeExamNetwork.attempt(listening = listOf(ExamListeningState("g1", 1, false, 72_000)))
        val repository = repository()

        repository.adoptServerAttempt("a1", variantNumber = 1, templateTitle = "Buraxılış — İngilis dili")

        val state = repository.attemptSnapshot("a1").first()!!.listening.getValue("g1")
        assertEquals(1, state.playsUsed)
        assertEquals(72_000L, state.positionMs)
        assertTrue(state.playInProgress)
    }

    @Test
    fun finishedOnTheServerBecomesSubmittedLocally() = runTest {
        val repository = startedRepository()
        network.attempt = FakeExamNetwork.attempt(status = ExamAttemptStatus.FINALIZED)

        repository.refreshAttempt("a1")

        assertEquals(LocalAttemptPhase.SUBMITTED, repository.localAttempt("a1")!!.phase)
    }

    @Test
    fun marksOfflineStayPendingThenSync() = runTest {
        val repository = startedRepository()
        network.marksError = DataError.Offline

        assertEquals(ExamMarkOutcome.Queued, repository.setMark("a1", "t5", ExamMark.TWO_THIRDS))
        assertEquals(LocalExamMark("t5", ExamMark.TWO_THIRDS, pending = true), repository.localMarks("a1").first()["t5"])

        network.marksError = null
        assertTrue(repository.syncAllPending())
        assertEquals(listOf(listOf("t5" to ExamMark.TWO_THIRDS)), network.markWrites)
        assertFalse(repository.localMarks("a1").first().getValue("t5").pending)
    }

    @Test
    fun rejectedMarkRevertsToTheServerValue() = runTest {
        val repository = startedRepository()
        network.marksError = DataError.Api(ApiErrorCode.ValidationFailed)

        assertEquals(ExamMarkOutcome.Rejected, repository.setMark("a1", "t5", ExamMark.ONE))
        assertTrue(repository.localMarks("a1").first().isEmpty())
    }

    @Test
    fun clearAccountDataRemovesAttemptsMarksAndCaches() = runTest {
        val repository = startedRepository()
        repository.selectOption("a1", "t1", "o1")
        repository.setMark("a1", "t5", ExamMark.HALF)
        repository.refreshResult("a1")

        repository.clearAccountData()

        assertNull(repository.activeAttempt.first())
        assertNull(repository.cachedResult("a1").first())
        assertTrue(repository.localMarks("a1").first().isEmpty())
    }
}

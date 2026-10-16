package com.sabit.duelloquiz.core.data.repository

import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.data.testdoubles.FakeNetworkDataSource
import com.sabit.duelloquiz.core.data.testdoubles.FakeNetworkMonitor
import com.sabit.duelloquiz.core.data.testdoubles.testAppScope
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuizRepositoryTest {

    private val network = FakeNetworkDataSource()
    private val monitor = FakeNetworkMonitor(online = true)

    private fun TestScope.repository() = DefaultQuizRepository(
        network = network,
        networkMonitor = monitor,
        clock = WallClock { testScheduler.currentTime },
        appScope = testAppScope(),
    )

    private suspend fun DefaultQuizRepository.progressOf(id: String) = progress(id).filterNotNull().first()

    @Test
    fun aCallerThatStopsWaitingLeavesTheStartForTheNextCaller() = runTest {
        val repository = repository()
        val gate = CompletableDeferred<Unit>()
        network.startGate = gate

        // Home waits 1 s, then hands over to the loading screen (HOME-02).
        assertNull(withTimeoutOrNull(1_000) { repository.startSession(categoryId = 1) })
        val loading = async { repository.startSession(categoryId = 1) }
        runCurrent()
        gate.complete(Unit)

        assertTrue(loading.await() is DataResult.Success)
        assertEquals(listOf("start:1"), network.calls)
        assertEquals("s1", repository.session("s1")?.id)
    }

    @Test
    fun aClaimedStartIsNeverHandedOutTwice() = runTest {
        val repository = repository()

        repository.startSession(categoryId = 1)
        repository.startSession(categoryId = 1)

        assertEquals(listOf("start:1", "start:1"), network.calls)
    }

    @Test
    fun anOldUnclaimedStartIsNotReused() = runTest {
        val repository = repository()
        val gate = CompletableDeferred<Unit>()
        network.startGate = gate
        withTimeoutOrNull(1_000) { repository.startSession(categoryId = 1) }
        gate.complete(Unit)
        runCurrent()

        advanceTimeBy(11_000)
        repository.startSession(categoryId = 1)

        assertEquals(listOf("start:1", "start:1"), network.calls)
    }

    @Test
    fun answersAreRecordedForTheLocalResult() = runTest {
        val repository = repository()
        repository.startSession(1)

        repository.submitAnswer("s1", "q1", "q1-a", 1_200)
        repository.submitAnswer("s1", "q2", "q2-b", 3_000)

        val progress = repository.progressOf("s1")
        assertEquals(1, progress.correctCount)
        assertEquals(2, progress.resolved.size)
        assertEquals(10, progress.total)
    }

    @Test
    fun queuedAnswersAreSentInOrderWhenTheConnectionReturns() = runTest {
        val repository = repository()
        repository.startSession(1)
        monitor.online.value = false
        network.answerResults += FakeNetworkDataSource.offline

        repository.enqueueAnswer("s1", "q1", "q1-a", 4_000)
        repository.enqueueAnswer("s1", "q2", null, 20_000)
        runCurrent()
        assertEquals(2, repository.progressOf("s1").queuedCount)

        monitor.online.value = true
        advanceUntilIdle()

        val progress = repository.progressOf("s1")
        assertEquals(0, progress.queuedCount)
        assertEquals(setOf("q1", "q2"), progress.resolved.keys)
        // First attempt failed offline, then q1 and q2 in order with their original elapsedMs.
        assertEquals(
            listOf(
                FakeNetworkDataSource.Answer("q1", "q1-a", 4_000),
                FakeNetworkDataSource.Answer("q1", "q1-a", 4_000),
                FakeNetworkDataSource.Answer("q2", null, 20_000),
            ),
            network.answers,
        )
    }

    @Test
    fun finishWaitsForQueuedAnswersWhileOffline() = runTest {
        val repository = repository()
        repository.startSession(1)
        monitor.online.value = false
        network.answerResults += FakeNetworkDataSource.offline
        network.answerResults += FakeNetworkDataSource.offline
        repository.enqueueAnswer("s1", "q10", "q10-a", 2_000)
        runCurrent()

        val result = repository.finish("s1")

        assertEquals(DataResult.Failure(DataError.Offline), result)
        assertTrue("finish" !in network.calls)
        assertEquals(1, repository.progressOf("s1").queuedCount)

        val retry = repository.finish("s1")
        assertTrue(retry is DataResult.Success)
        assertEquals(listOf("finish"), network.calls.filter { it == "finish" })
        assertEquals(0, repository.progressOf("s1").queuedCount)
    }

    @Test
    fun finishAfterTheDeadlineDropsUnsentAnswers() = runTest {
        val repository = repository()
        repository.startSession(1)
        monitor.online.value = false
        repeat(3) { network.answerResults += FakeNetworkDataSource.offline }
        repository.enqueueAnswer("s1", "q9", "q9-a", 2_000)
        runCurrent()

        advanceTimeBy(246_000)
        val result = repository.finish("s1")

        assertTrue(result is DataResult.Success)
        assertEquals(0, repository.progressOf("s1").queuedCount)
        assertTrue("q9" !in repository.progressOf("s1").resolved)
    }

    @Test
    fun aClosedSessionStopsTheQueue() = runTest {
        val repository = repository()
        repository.startSession(1)
        network.answerResults += DataResult.Failure(DataError.Api(ApiErrorCode.SessionExpired))

        repository.enqueueAnswer("s1", "q3", "q3-a", 2_000)
        repository.enqueueAnswer("s1", "q4", "q4-a", 2_000)
        advanceUntilIdle()

        assertEquals(0, repository.progressOf("s1").queuedCount)
        assertEquals(listOf("q3"), network.answers.map { it.questionId })
    }

    @Test
    fun finishIsCachedAndDiscardForgetsTheSession() = runTest {
        val repository = repository()
        repository.startSession(1)

        repository.finish("s1")
        repository.finish("s1")
        assertEquals(1, network.calls.count { it == "finish" })

        repository.discard("s1")
        assertNull(repository.session("s1"))
        assertEquals(
            DataResult.Failure(DataError.Api(ApiErrorCode.SessionNotFound)),
            repository.submitAnswer("s1", "q1", "q1-a", 1),
        )
    }

    @Test
    fun cancellingAPendingStartForgetsIt() = runTest {
        val repository = repository()
        val gate = CompletableDeferred<Unit>()
        network.startGate = gate
        val job = launch { repository.startSession(1) }
        runCurrent()
        job.cancel()

        repository.cancelPendingStart(1)
        gate.complete(Unit)
        network.startGate = null
        repository.startSession(1)

        assertEquals(listOf("start:1", "start:1"), network.calls)
    }
}

package com.sabit.duelloquiz.core.data.repository

import com.sabit.duelloquiz.core.auth.SignOutReason
import com.sabit.duelloquiz.core.auth.fake.InMemoryAuthRepository
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.data.testdoubles.FakeDeviceTokenSource
import com.sabit.duelloquiz.core.data.testdoubles.FakeNetworkDataSource
import com.sabit.duelloquiz.core.data.testdoubles.FakeNetworkMonitor
import com.sabit.duelloquiz.core.data.testdoubles.testAppScope
import com.sabit.duelloquiz.core.data.testdoubles.testRetention
import com.sabit.duelloquiz.core.data.testdoubles.testUserCache
import com.sabit.duelloquiz.core.model.AnswerResult
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DailyAttempt
import com.sabit.duelloquiz.core.model.DailyResult
import com.sabit.duelloquiz.core.model.DailySquare
import com.sabit.duelloquiz.core.model.DailyStart
import com.sabit.duelloquiz.core.model.DailyStatus
import com.sabit.duelloquiz.core.model.DailyStatusType
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.ProgressSummary
import com.sabit.duelloquiz.core.model.QuizOption
import com.sabit.duelloquiz.core.model.QuizQuestion
import com.sabit.duelloquiz.core.model.QuizSessionStatus
import com.sabit.duelloquiz.core.model.StreakEvent
import com.sabit.duelloquiz.core.model.StreakEventType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class RetentionRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val network = FakeNetworkDataSource()
    private val auth = InMemoryAuthRepository(latencyMs = 0)

    private suspend fun signIn() {
        auth.signUpWithEmail("Aysel", "aysel@example.com", "password1")
    }

    // Streak event rules (PROG-07, PROG-08)

    @Test
    fun streakEventsAreShownOncePerAccountTypeAndDay() {
        val lost = StreakEvent(StreakEventType.STREAK_LOST, "2026-10-21", previousStreak = 5)
        val freeze = StreakEvent(StreakEventType.FREEZE_USED, "2026-10-21", previousStreak = 9)

        assertTrue(StreakEvents.shouldShow("u1", lost, emptyList()))
        assertFalse(StreakEvents.shouldShow("u1", lost, listOf(StreakEvents.key("u1", lost))))
        // Another account on the same phone sees its own events.
        assertTrue(StreakEvents.shouldShow("u2", lost, listOf(StreakEvents.key("u1", lost))))
        // Another type or another day is a new event.
        assertTrue(StreakEvents.shouldShow("u1", freeze, listOf(StreakEvents.key("u1", lost))))
        assertTrue(StreakEvents.shouldShow("u1", lost.copy(dayKey = "2026-10-25"), listOf(StreakEvents.key("u1", lost))))
    }

    @Test
    fun aLostOneDayStreakIsNotShown() {
        assertFalse(StreakEvents.shouldShow("u1", StreakEvent(StreakEventType.STREAK_LOST, "2026-10-21", 1), emptyList()))
        assertTrue(StreakEvents.shouldShow("u1", StreakEvent(StreakEventType.STREAK_LOST, "2026-10-21", 2), emptyList()))
        // A freeze protecting a 1-day streak is still a real event.
        assertTrue(StreakEvents.shouldShow("u1", StreakEvent(StreakEventType.FREEZE_USED, "2026-10-21", 1), emptyList()))
        assertFalse(StreakEvents.shouldShow(null, StreakEvent(StreakEventType.FREEZE_USED, "2026-10-21", 3), emptyList()))
    }

    @Test
    fun pendingStreakEventIsRememberedOnTheDevice() = runTest {
        val scope = testAppScope()
        val retention = testRetention(scope, tmp.root)
        val repository = OfflineFirstProgressRepository(network, testUserCache(scope, tmp.newFolder()), retention, auth)
        signIn()
        val event = StreakEvent(StreakEventType.FREEZE_USED, "2026-10-21", previousStreak = 7)
        network.progressResult = DataResult.Success(summary(event))

        repository.refresh()
        assertEquals(event, repository.pendingStreakEvent.first())

        repository.markStreakEventShown(event)
        advanceUntilIdle()
        assertNull(repository.pendingStreakEvent.first())

        // A new repository (app restart) reading the same device memory does not repeat it.
        val restarted = OfflineFirstProgressRepository(network, testUserCache(scope, tmp.newFolder()), retention, auth)
        restarted.refresh()
        assertNull(restarted.pendingStreakEvent.first())
    }

    @Test
    fun progressIsCachedPerAccount() = runTest {
        val scope = testAppScope()
        val repository = OfflineFirstProgressRepository(network, testUserCache(scope, tmp.root), testRetention(scope, tmp.newFolder()), auth)
        signIn()
        network.progressResult = DataResult.Success(summary(null))

        repository.refresh()
        assertEquals(5, repository.progress.filterNotNull().first().level)

        auth.signOut(SignOutReason.UserRequested)
        runCurrent()
        assertNull(repository.progress.first())
    }

    @Test
    fun theCapNoteIsShownOncePerDayAndGameType() = runTest {
        val scope = testAppScope()
        val repository = OfflineFirstProgressRepository(network, testUserCache(scope, tmp.root), testRetention(scope, tmp.newFolder()), auth)
        signIn()

        assertTrue(repository.consumeCapNote("quiz", "2026-10-22"))
        assertFalse(repository.consumeCapNote("quiz", "2026-10-22"))
        assertTrue(repository.consumeCapNote("duel", "2026-10-22"))
        assertTrue(repository.consumeCapNote("quiz", "2026-10-23"))
    }

    // Permission card timing (NOTIF-01, OQ-090)

    @Test
    fun permissionCardOnFirstResultThenOnceMoreAfterSevenDays() {
        val day = 24L * 60 * 60 * 1000
        assertTrue(NotificationPromptPolicy.canShow(shownCount = 0, laterAtMs = null, nowMs = 0))
        // "Sonra" at day 0: not before 7 days.
        assertFalse(NotificationPromptPolicy.canShow(1, laterAtMs = 0, nowMs = 7 * day - 1))
        assertTrue(NotificationPromptPolicy.canShow(1, laterAtMs = 0, nowMs = 7 * day))
        // After the second card it never comes back by itself.
        assertFalse(NotificationPromptPolicy.canShow(2, laterAtMs = 0, nowMs = 100 * day))
        // Shown once without any answer recorded (should not happen): never again.
        assertFalse(NotificationPromptPolicy.canShow(1, laterAtMs = null, nowMs = 100 * day))
    }

    @Test
    fun permissionPromptRepositoryCountsPerInstall() = runTest {
        var now = 0L
        val day = 24L * 60 * 60 * 1000
        val repository = DataStoreNotificationPromptRepository(testRetention(testAppScope(), tmp.root), WallClock { now })

        assertTrue(repository.canShow())
        repository.onShown()
        repository.onLater()
        assertFalse(repository.canShow())
        now = 8 * day
        assertTrue(repository.canShow())
        repository.onShown()
        assertFalse(repository.canShow())

        val answered = DataStoreNotificationPromptRepository(testRetention(testAppScope(), tmp.newFolder()), WallClock { now })
        answered.onShown()
        answered.onAnswered()
        now += 30 * day
        assertFalse(answered.canShow())
    }

    // Push token (NOTIF-02, NOTIF-08)

    @Test
    fun registersOncePerDayAndAgainForANewToken() = runTest {
        var now = 0L
        val tokens = FakeDeviceTokenSource("t1")
        val repository = DefaultPushTokenRepository(network, tokens, testRetention(testAppScope(), tmp.root), { "az, en;q=0.5" }, WallClock { now }, auth)
        signIn()

        repository.sync(allowed = true)
        repository.sync(allowed = true)
        assertEquals(listOf("pushPut:t1:az"), network.calls.filter { it.startsWith("push") })

        now += 24L * 60 * 60 * 1000
        repository.sync(allowed = true)
        repository.onNewToken("t2")
        assertEquals(listOf("pushPut:t1:az", "pushPut:t1:az", "pushPut:t2:az"), network.calls.filter { it.startsWith("push") })
    }

    @Test
    fun revokedPermissionDeletesTheRegisteredToken() = runTest {
        val repository = DefaultPushTokenRepository(network, FakeDeviceTokenSource("t1"), testRetention(testAppScope(), tmp.root), { "az" }, WallClock { 0 }, auth)
        signIn()
        repository.sync(allowed = true)

        repository.sync(allowed = false)
        repository.sync(allowed = false)

        assertEquals(listOf("pushPut:t1:az", "pushDelete:t1"), network.calls.filter { it.startsWith("push") })
    }

    @Test
    fun noTokenOrNoUserMeansNoRequest() = runTest {
        val noFirebase = DefaultPushTokenRepository(network, FakeDeviceTokenSource(null), testRetention(testAppScope(), tmp.root), { "az" }, WallClock { 0 }, auth)
        noFirebase.sync(allowed = true) // signed out
        signIn()
        noFirebase.sync(allowed = true) // no token (debug build without Firebase)

        assertTrue(network.calls.none { it.startsWith("push") })
    }

    @Test
    fun signOutUnregistersOnTheServerAndLocally() = runTest {
        val tokens = FakeDeviceTokenSource("t1")
        val repository = DefaultPushTokenRepository(network, tokens, testRetention(testAppScope(), tmp.root), { "az" }, WallClock { 0 }, auth)
        signIn()
        repository.sync(allowed = true)

        repository.unregister()
        repository.forgetLocal()

        assertEquals(listOf("pushPut:t1:az", "pushDelete:t1"), network.calls.filter { it.startsWith("push") })
        assertEquals(2, tokens.deleted)
    }

    @Test
    fun pushRegistrationSerializesTokensWithSeparators() {
        val registration = PushRegistration("u1", "abc|def:123", 42)
        assertEquals(registration, PushRegistration.parse(registration.serialize()))
        assertNull(PushRegistration.parse("garbage"))
        assertNull(PushRegistration.parse(null))
    }

    // Daily challenge (DAILY-02, DAILY-04, DAILY-06)

    private fun TestScope.dailyRepository(): DefaultDailyRepository {
        val scope = testAppScope()
        return DefaultDailyRepository(
            network, testUserCache(scope, tmp.root), testRetention(scope, tmp.newFolder()), FakeNetworkMonitor(),
            WallClock { testScheduler.currentTime }, auth, scope,
        )
    }

    @Test
    fun aDoubleTapAndTheLoadingScreenShareOneStartRequest() = runTest {
        val repository = dailyRepository()
        signIn()
        val gate = CompletableDeferred<Unit>()
        network.dailyStartGate = gate
        network.dailyStart = DataResult.Success(DailyStart.Started(attempt()))

        assertNull(withTimeoutOrNull(1_000) { repository.start() })
        val second = async { repository.start() }
        runCurrent()
        gate.complete(Unit)

        assertTrue(second.await() is DataResult.Success)
        assertEquals(1, network.calls.count { it == "startDaily" })
        assertEquals("a1", repository.attempt("a1")?.id)
    }

    @Test
    fun aResumedAttemptKeepsItsAnswers() = runTest {
        val repository = dailyRepository()
        signIn()
        val answered = AnswerResult("q1", "q1-a", true, "q1-a", null)
        network.dailyStart = DataResult.Success(DailyStart.Started(attempt(answers = listOf(answered))))

        repository.start()

        assertEquals(answered, repository.progress("a1").filterNotNull().first().resolved["q1"])
    }

    @Test
    fun alreadyPlayedStoresTheResultAndUpdatesTheCachedCard() = runTest {
        val repository = dailyRepository()
        signIn()
        network.daily = DataResult.Success(status(DailyStatusType.AVAILABLE))
        repository.refreshStatus()
        network.dailyStart = DataResult.Success(DailyStart.AlreadyPlayed(result()))

        repository.start()
        runCurrent()

        assertEquals("a1", repository.result("a1")?.attemptId)
        val cached = repository.status.filterNotNull().first()
        assertEquals(DailyStatusType.FINISHED, cached.status)
        assertEquals(listOf(DailySquare.CORRECT, DailySquare.UNANSWERED), cached.result?.squares)
        assertEquals(7, cached.streakCurrent)
    }

    @Test
    fun finishIsIdempotentAndQueuedAnswersAreSentFirst() = runTest {
        val repository = dailyRepository()
        signIn()
        network.dailyStart = DataResult.Success(DailyStart.Started(attempt()))
        network.dailyFinish = DataResult.Success(result())
        repository.start()

        repository.enqueueAnswer("a1", "q1", "q1-b", 3_000)
        assertEquals(listOf("q1"), repository.progress("a1").filterNotNull().first().queued)
        val first = repository.finish("a1")
        val second = repository.finish("a1")

        assertTrue(first is DataResult.Success && second is DataResult.Success)
        val calls = network.calls.filter { it.startsWith("daily") }
        assertEquals(listOf("dailyAnswer:q1", "dailyFinish"), calls)
    }

    @Test
    fun finishOfflineKeepsTheQueueAndReportsTheFailure() = runTest {
        val repository = dailyRepository()
        signIn()
        network.dailyStart = DataResult.Success(DailyStart.Started(attempt()))
        repository.start()
        network.answerResults += DataResult.Failure(DataError.Offline)

        repository.enqueueAnswer("a1", "q1", "q1-b", 3_000)
        val result = repository.finish("a1")

        assertEquals(DataResult.Failure(DataError.Offline), result)
        assertTrue(network.calls.none { it == "dailyFinish" })
    }

    @Test
    fun anUnknownAttemptCannotBeAnswered() = runTest {
        val repository = dailyRepository()

        assertEquals(
            DataResult.Failure(DataError.Api(ApiErrorCode.SessionNotFound)),
            repository.submitAnswer("nope", "q1", null, 0),
        )
    }

    @Test
    fun shownTimesAreKeptOnlyForTheCurrentAttempt() = runTest {
        val repository = dailyRepository()

        repository.recordShown("a1", "q1", 1_000)
        repository.recordShown("a1", "q1", 5_000) // first show wins
        repository.recordShown("a1", "q2", 22_000)
        assertEquals(mapOf("q1" to 1_000L, "q2" to 22_000L), repository.shownAt("a1"))

        repository.recordShown("a2", "q1", 90_000)
        assertEquals(emptyMap<String, Long>(), repository.shownAt("a1"))
        assertEquals(mapOf("q1" to 90_000L), repository.shownAt("a2"))
    }

    private fun attempt(answers: List<AnswerResult> = emptyList()) = DailyAttempt(
        id = "a1",
        dayKey = "2026-10-22",
        number = 12,
        startedAtEpochMs = 0,
        deadlineEpochMs = 137_500,
        questionTimeLimitMs = 20_000,
        clockOffsetMs = 0,
        resumed = answers.isNotEmpty(),
        questions = (1..5).map { n ->
            QuizQuestion("q$n", n, "Sual $n", null, listOf("a", "b", "c", "d").map { QuizOption("q$n-$it", it) })
        },
        answers = answers,
    )

    private fun result() = DailyResult(
        attemptId = "a1",
        dayKey = "2026-10-22",
        number = 12,
        status = QuizSessionStatus.FINISHED,
        correctCount = 1,
        total = 2,
        squares = listOf(DailySquare.CORRECT, DailySquare.UNANSWERED),
        review = emptyList(),
        progress = null,
        streakCurrent = 7,
    )

    private fun status(type: DailyStatusType) = DailyStatus(
        dayKey = "2026-10-22",
        number = 12,
        status = type,
        questionCount = 5,
        nextResetEpochMs = 86_400_000,
        clockOffsetMs = 0,
        attemptId = null,
        attemptDeadlineEpochMs = null,
        result = null,
        streakCurrent = 6,
    )

    private fun summary(event: StreakEvent?) = ProgressSummary(
        xpTotal = 1_270, level = 5, xpIntoLevel = 270, xpForNextLevel = 500, streakCurrent = 7, streakBest = 12,
        todayActive = false, freezeAvailable = false, streakEvent = event, dayKey = "2026-10-22",
        nextResetEpochMs = 86_400_000, categories = emptyList(),
    )
}

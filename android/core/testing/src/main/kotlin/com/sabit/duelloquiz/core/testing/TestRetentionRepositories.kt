package com.sabit.duelloquiz.core.testing

import android.content.Intent
import com.sabit.duelloquiz.core.data.repository.AccountRepository
import com.sabit.duelloquiz.core.data.repository.DailyProgress
import com.sabit.duelloquiz.core.data.repository.DailyRepository
import com.sabit.duelloquiz.core.data.repository.NotificationPromptPolicy
import com.sabit.duelloquiz.core.data.repository.NotificationPromptRepository
import com.sabit.duelloquiz.core.data.repository.NotificationSettingsRepository
import com.sabit.duelloquiz.core.data.repository.ProgressRepository
import com.sabit.duelloquiz.core.data.repository.StreakEvents
import com.sabit.duelloquiz.core.model.AnswerResult
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DailyAttempt
import com.sabit.duelloquiz.core.model.DailyResult
import com.sabit.duelloquiz.core.model.DailySquare
import com.sabit.duelloquiz.core.model.DailyStart
import com.sabit.duelloquiz.core.model.DailyStatus
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.NotificationSettings
import com.sabit.duelloquiz.core.model.ProgressGain
import com.sabit.duelloquiz.core.model.ProgressSummary
import com.sabit.duelloquiz.core.model.QuizOption
import com.sabit.duelloquiz.core.model.QuizQuestion
import com.sabit.duelloquiz.core.model.QuizSessionStatus
import com.sabit.duelloquiz.core.model.ReminderType
import com.sabit.duelloquiz.core.model.StreakEvent
import com.sabit.duelloquiz.core.model.SystemNotificationState
import com.sabit.duelloquiz.core.notifications.SystemNotifications
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

/** [ProgressRepository] with a scriptable refresh and in-memory dedupe of one-time messages. */
class TestProgressRepository(initial: ProgressSummary? = null, private val uid: String = "u1") : ProgressRepository {
    private val state = MutableStateFlow(initial)
    private val latestEvent = MutableStateFlow<StreakEvent?>(null)
    private val shown = MutableStateFlow<List<String>>(emptyList())
    private val hintDay = MutableStateFlow<String?>(null)
    private val capNotes = mutableSetOf<String>()

    var nextRefresh: DataResult<ProgressSummary> = DataResult.Failure(DataError.Offline)
    var refreshCount = 0
        private set

    override val progress: Flow<ProgressSummary?> = state
    override val pendingStreakEvent: Flow<StreakEvent?> = combine(latestEvent, shown) { event, keys ->
        event.takeIf { StreakEvents.shouldShow(uid, it, keys) }
    }
    override val streakHintDismissedDay: Flow<String?> = hintDay

    override suspend fun refresh(): DataResult<ProgressSummary> {
        refreshCount++
        val result = nextRefresh
        if (result is DataResult.Success) {
            state.value = result.value
            latestEvent.value = result.value.streakEvent
        }
        return result
    }

    override suspend fun markStreakEventShown(event: StreakEvent) {
        shown.update { it + StreakEvents.key(uid, event) }
    }

    override suspend fun dismissStreakHint(dayKey: String) {
        hintDay.value = dayKey
    }

    override suspend fun consumeCapNote(source: String, dayKey: String): Boolean = capNotes.add("$source|$dayKey")

    val dismissedHintDay: String? get() = hintDay.value
}

/**
 * [DailyRepository] with scripted responses. Started attempts are registered like the real
 * repository; answers resolve with [answerResults] or the default (option `<question>-a` is right).
 */
class TestDailyRepository(initialStatus: DailyStatus? = null) : DailyRepository {
    data class SubmittedAnswer(val questionId: String, val optionId: String?, val elapsedMs: Long)

    private val statusState = MutableStateFlow(initialStatus)
    private val attempts = mutableMapOf<String, DailyAttempt>()
    private val progressStates = mutableMapOf<String, MutableStateFlow<DailyProgress?>>()
    private val results = mutableMapOf<String, DailyResult>()
    private val shown = mutableMapOf<String, MutableMap<String, Long>>()

    var nextStatus: DataResult<DailyStatus> = DataResult.Failure(DataError.Offline)
    var startResult: DataResult<DailyStart> = DataResult.Failure(DataError.Offline)
    var startGate: CompletableDeferred<Unit>? = null
    val answerResults = ArrayDeque<DataResult<AnswerResult>>()
    var finishResult: DataResult<DailyResult>? = null
    var startCount = 0
        private set
    var statusCount = 0
        private set
    val submitted = mutableListOf<SubmittedAnswer>()
    val queued = mutableListOf<SubmittedAnswer>()

    override val status: Flow<DailyStatus?> = statusState

    fun setStatus(value: DailyStatus?) {
        statusState.value = value
    }

    override suspend fun refreshStatus(): DataResult<DailyStatus> {
        statusCount++
        val result = nextStatus
        if (result is DataResult.Success) statusState.value = result.value
        return result
    }

    override suspend fun start(): DataResult<DailyStart> {
        startCount++
        startGate?.await()
        val result = startResult
        when (val value = (result as? DataResult.Success)?.value) {
            is DailyStart.Started -> register(value.attempt)
            is DailyStart.AlreadyPlayed -> results[value.result.attemptId] = value.result
            null -> Unit
        }
        return result
    }

    fun register(attempt: DailyAttempt) {
        attempts[attempt.id] = attempt
        progressStates.getOrPut(attempt.id) { MutableStateFlow(null) }.value =
            DailyProgress(attempt, attempt.answers.associateBy { it.questionId }, emptyList(), null)
    }

    override fun attempt(attemptId: String): DailyAttempt? = attempts[attemptId]

    override fun progress(attemptId: String): Flow<DailyProgress?> = progressStates[attemptId] ?: flowOf(null)

    override fun result(attemptId: String): DailyResult? = results[attemptId]

    override suspend fun submitAnswer(attemptId: String, questionId: String, optionId: String?, elapsedMs: Long): DataResult<AnswerResult> {
        submitted += SubmittedAnswer(questionId, optionId, elapsedMs)
        val result = answerResults.removeFirstOrNull()
            ?: DataResult.Success(AnswerResult(questionId, optionId, optionId == "$questionId-a", "$questionId-a", null))
        if (result is DataResult.Success) {
            progressStates[attemptId]?.update { it?.copy(resolved = it.resolved + (questionId to result.value)) }
        }
        return result
    }

    override fun enqueueAnswer(attemptId: String, questionId: String, optionId: String?, elapsedMs: Long) {
        queued += SubmittedAnswer(questionId, optionId, elapsedMs)
        progressStates[attemptId]?.update { it?.copy(queued = it.queued + questionId) }
    }

    override suspend fun finish(attemptId: String): DataResult<DailyResult> {
        results[attemptId]?.let { return DataResult.Success(it) }
        val attempt = attempts[attemptId] ?: return DataResult.Failure(DataError.Api(ApiErrorCode.SessionNotFound))
        val result = finishResult ?: DataResult.Success(testDailyResult(attempt.id, attempt.number))
        if (result is DataResult.Success) {
            results[attemptId] = result.value
            progressStates[attemptId]?.update { it?.copy(result = result.value) }
        }
        return result
    }

    override suspend fun shownAt(attemptId: String): Map<String, Long> = shown[attemptId].orEmpty()

    override suspend fun recordShown(attemptId: String, questionId: String, serverEpochMs: Long) {
        shown.getOrPut(attemptId) { mutableMapOf() }.putIfAbsent(questionId, serverEpochMs)
    }

    fun setShownAt(attemptId: String, questionId: String, serverEpochMs: Long) {
        shown.getOrPut(attemptId) { mutableMapOf() }[questionId] = serverEpochMs
    }

    override fun clear() {
        attempts.clear()
        results.clear()
    }
}

class TestNotificationSettingsRepository(initial: NotificationSettings? = null) : NotificationSettingsRepository {
    private val state = MutableStateFlow(initial)
    var nextRefresh: DataResult<NotificationSettings> = DataResult.Failure(DataError.Offline)
    var nextUpdate: DataResult<NotificationSettings>? = null
    var updateGate: CompletableDeferred<Unit>? = null
    val updates = mutableListOf<NotificationSettings>()

    override val settings: Flow<NotificationSettings?> = state

    override suspend fun refresh(): DataResult<NotificationSettings> {
        val result = nextRefresh
        if (result is DataResult.Success) state.value = result.value
        return result
    }

    override suspend fun update(settings: NotificationSettings): DataResult<NotificationSettings> {
        updates += settings
        updateGate?.await()
        val result = nextUpdate ?: DataResult.Success(settings)
        if (result is DataResult.Success) state.value = result.value
        return result
    }
}

/** [NotificationPromptRepository] on [NotificationPromptPolicy] with a test clock. */
class TestNotificationPromptRepository(private val nowMs: () -> Long = { 0L }) : NotificationPromptRepository {
    var shownCount = 0
    var laterAtMs: Long? = null
    private val blocked = MutableStateFlow(false)

    override val permissionBlocked: Flow<Boolean> = blocked

    override suspend fun setPermissionBlocked(blocked: Boolean) {
        this.blocked.value = blocked
    }

    override suspend fun canShow(): Boolean = NotificationPromptPolicy.canShow(shownCount, laterAtMs, nowMs())

    override suspend fun onShown() {
        shownCount++
        laterAtMs = nowMs()
    }

    override suspend fun onLater() {
        laterAtMs = nowMs()
    }

    override suspend fun onAnswered() {
        shownCount = NotificationPromptPolicy.MAX_SHOWS
    }

    val isBlocked: Boolean get() = blocked.value
}

/** [SystemNotifications] whose phone state the test sets; records cancelled reminders. */
class TestSystemNotifications(
    initial: SystemNotificationState = SystemNotificationState.AllAllowed,
    override val needsRuntimePermission: Boolean = true,
) : SystemNotifications {
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<SystemNotificationState> = _state.asStateFlow()
    val cancelled = mutableListOf<ReminderType>()

    fun set(value: SystemNotificationState) {
        _state.value = value
    }

    override fun refresh(): SystemNotificationState = _state.value

    override fun cancel(type: ReminderType) {
        cancelled += type
    }

    override fun appSettingsIntent(): Intent = Intent()

    override fun channelSettingsIntent(type: ReminderType): Intent = Intent()
}

/** [AccountRepository] that only records calls. */
class TestAccountRepository(private val onSignOut: suspend () -> Unit = {}) : AccountRepository {
    var signOutCount = 0
        private set
    var deleteResult: DataResult<Unit> = DataResult.Success(Unit)

    override suspend fun deleteAccount(): DataResult<Unit> = deleteResult

    override suspend fun signOut() {
        signOutCount++
        onSignOut()
    }

    override fun startSignOutCleanup() = Unit
}

/** A daily attempt of 5 questions (option `q<n>-a` is correct), started at [startedAt]. */
fun testDailyAttempt(
    id: String = "a1",
    number: Int = 12,
    startedAt: Long = 0,
    offset: Long = 0,
    answers: List<AnswerResult> = emptyList(),
) = DailyAttempt(
    id = id,
    dayKey = "2026-10-22",
    number = number,
    startedAtEpochMs = startedAt,
    deadlineEpochMs = startedAt + 137_500,
    questionTimeLimitMs = 20_000,
    clockOffsetMs = offset,
    resumed = answers.isNotEmpty(),
    questions = (1..5).map { n ->
        QuizQuestion("q$n", n, "Sual $n", null, listOf("a", "b", "c", "d").map { QuizOption("q$n-$it", it) })
    },
    answers = answers,
)

fun testDailyResult(
    attemptId: String = "a1",
    number: Int = 12,
    squares: List<DailySquare> = listOf(
        DailySquare.CORRECT, DailySquare.WRONG, DailySquare.CORRECT, DailySquare.CORRECT, DailySquare.UNANSWERED,
    ),
    progress: ProgressGain? = testProgressGain(),
    streak: Int = 7,
) = DailyResult(
    attemptId = attemptId,
    dayKey = "2026-10-22",
    number = number,
    status = QuizSessionStatus.FINISHED,
    correctCount = squares.count { it == DailySquare.CORRECT },
    total = squares.size,
    squares = squares,
    review = emptyList(),
    progress = progress,
    streakCurrent = streak,
)

fun testProgressGain(
    xp: Int = 50,
    levelBefore: Int = 4,
    levelAfter: Int = 4,
    capped: Boolean = false,
    extended: Boolean = true,
) = ProgressGain(
    dayKey = "2026-10-22",
    xpGained = xp,
    xpCapped = capped,
    xpBreakdown = emptyList(),
    xpTotal = 1_270,
    levelBefore = levelBefore,
    levelAfter = levelAfter,
    xpIntoLevelBefore = 220,
    xpForNextLevelBefore = 400,
    xpIntoLevel = 270,
    xpForNextLevel = 400,
    categoryLevelUps = emptyList(),
    streakCurrent = 7,
    streakBest = 12,
    streakExtendedToday = extended,
    freezeAvailable = false,
)

fun testProgressSummary(
    streak: Int = 7,
    todayActive: Boolean = false,
    nextResetEpochMs: Long = 86_400_000,
    event: StreakEvent? = null,
    best: Int = 12,
) = ProgressSummary(
    xpTotal = 1_270,
    level = 5,
    xpIntoLevel = 270,
    xpForNextLevel = 500,
    streakCurrent = streak,
    streakBest = best,
    todayActive = todayActive,
    freezeAvailable = false,
    streakEvent = event,
    dayKey = "2026-10-22",
    nextResetEpochMs = nextResetEpochMs,
    categories = emptyList(),
)

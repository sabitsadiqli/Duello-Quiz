package com.sabit.duelloquiz.core.data.repository

import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.common.di.ApplicationScope
import com.sabit.duelloquiz.core.data.cache.CacheKeys
import com.sabit.duelloquiz.core.data.cache.CachedDailyStatus
import com.sabit.duelloquiz.core.data.cache.readFor
import com.sabit.duelloquiz.core.data.cache.toCached
import com.sabit.duelloquiz.core.data.cache.toModelOrNull
import com.sabit.duelloquiz.core.data.cache.write
import com.sabit.duelloquiz.core.data.util.NetworkMonitor
import com.sabit.duelloquiz.core.datastore.RetentionPreferencesDataSource
import com.sabit.duelloquiz.core.datastore.UserCacheDataSource
import com.sabit.duelloquiz.core.model.AnswerResult
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DailyAttempt
import com.sabit.duelloquiz.core.model.DailyResult
import com.sabit.duelloquiz.core.model.DailyResultSummary
import com.sabit.duelloquiz.core.model.DailyStart
import com.sabit.duelloquiz.core.model.DailyStatus
import com.sabit.duelloquiz.core.model.DailyStatusType
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.network.DuelloNetworkDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** What the client knows about a daily attempt from the answer responses (DAILY-04 offline result). */
data class DailyProgress(
    val attempt: DailyAttempt,
    /** Server verdicts by question id (includes the answers of a resumed attempt). */
    val resolved: Map<String, AnswerResult>,
    /** Question ids answered offline whose result is not confirmed yet (DAILY-05). */
    val queued: List<String>,
    /** The final result once `finish` succeeded. */
    val result: DailyResult?,
)

/**
 * The daily challenge (DAILY-01 … DAILY-08). The status is cached per account; the running attempt
 * lives in memory and is resumed from the server after process death (`POST /v1/daily/attempt`
 * returns the same attempt with its answers until the deadline, OQ-079).
 */
interface DailyRepository {
    /** Last known `GET /v1/daily` of the signed-in account (possibly of an earlier day). */
    val status: Flow<DailyStatus?>

    suspend fun refreshStatus(): DataResult<DailyStatus>

    /**
     * `POST /v1/daily/attempt` (start or resume). Runs in the application scope: a caller that
     * stops waiting (home hands over to the loading screen after 1 s) leaves it running and the
     * next call joins it, so a double tap never sends two requests (DAILY-02).
     */
    suspend fun start(): DataResult<DailyStart>

    fun attempt(attemptId: String): DailyAttempt?

    fun progress(attemptId: String): Flow<DailyProgress?>

    /** A result opened without a running attempt (409 `daily_already_played`), or the final one. */
    fun result(attemptId: String): DailyResult?

    suspend fun submitAnswer(attemptId: String, questionId: String, optionId: String?, elapsedMs: Long): DataResult<AnswerResult>

    fun enqueueAnswer(attemptId: String, questionId: String, optionId: String?, elapsedMs: Long)

    /** Flushes queued answers, then `finish` (idempotent); updates the cached status. */
    suspend fun finish(attemptId: String): DataResult<DailyResult>

    /** Server time at which each question of [attemptId] was first shown on this device. */
    suspend fun shownAt(attemptId: String): Map<String, Long>

    suspend fun recordShown(attemptId: String, questionId: String, serverEpochMs: Long)

    /** Drops in-memory attempts (sign-out, account deletion). */
    fun clear()
}

@Singleton
internal class DefaultDailyRepository @Inject constructor(
    private val network: DuelloNetworkDataSource,
    private val cache: UserCacheDataSource,
    private val retention: RetentionPreferencesDataSource,
    private val networkMonitor: NetworkMonitor,
    private val clock: WallClock,
    authRepository: AuthRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : DailyRepository {

    private class AttemptRecord(val attempt: DailyAttempt, val answers: AnswerSession<DailyResult>) {
        val progress: Flow<DailyProgress> = answers.state.map { state ->
            DailyProgress(attempt, state.resolved, state.queued, state.result)
        }
    }

    private val uid: Flow<String?> = authRepository.currentUser.map { it?.uid }.distinctUntilChanged()
    private val lock = Any()
    private val records = LinkedHashMap<String, AttemptRecord>()
    private val results = LinkedHashMap<String, DailyResult>()
    private var pendingStart: Deferred<DataResult<DailyStart>>? = null

    override val status: Flow<DailyStatus?> =
        cache.readFor(uid, CacheKeys.DAILY_STATUS, CachedDailyStatus.serializer()).map { it?.toModelOrNull() }

    override suspend fun refreshStatus(): DataResult<DailyStatus> {
        val currentUid = uid.first() ?: return DataResult.Failure(DataError.Unauthorized)
        val result = network.getDaily()
        if (result is DataResult.Success) storeStatus(currentUid, result.value)
        return result
    }

    override suspend fun start(): DataResult<DailyStart> {
        val request = synchronized(lock) {
            pendingStart?.takeIf { it.isActive } ?: newStart().also { pendingStart = it }
        }
        return request.await()
    }

    private fun newStart(): Deferred<DataResult<DailyStart>> = appScope.async(start = CoroutineStart.LAZY) {
        val result = network.startDailyAttempt()
        when (val value = (result as? DataResult.Success)?.value) {
            is DailyStart.Started -> register(value.attempt)
            is DailyStart.AlreadyPlayed -> onResult(value.result)
            null -> Unit
        }
        result
    }.also { it.start() }

    private fun register(attempt: DailyAttempt): AttemptRecord = synchronized(lock) {
        records[attempt.id]?.let { existing ->
            // Resumed again in this process: keep the local queue, add verdicts from other devices.
            existing.answers.mergeResolved(attempt.answers)
            return@synchronized existing
        }
        val answers = AnswerSession(
            sessionId = attempt.id,
            deadlineEpochMs = attempt.deadlineEpochMs,
            clockOffsetMs = attempt.clockOffsetMs,
            clock = clock,
            networkMonitor = networkMonitor,
            appScope = appScope,
            submitCall = { questionId, optionId, elapsedMs ->
                network.submitDailyAnswer(attempt.id, questionId, optionId, elapsedMs)
            },
            finishCall = { network.finishDailyAttempt(attempt.id) },
            initialResolved = attempt.answers.associateBy { it.questionId },
        )
        AttemptRecord(attempt, answers).also { record ->
            records[attempt.id] = record
            while (records.size > MAX_ATTEMPTS) records.remove(records.keys.first())?.answers?.cancel()
        }
    }

    private fun record(attemptId: String): AttemptRecord? = synchronized(lock) { records[attemptId] }

    override fun attempt(attemptId: String): DailyAttempt? = record(attemptId)?.attempt

    override fun progress(attemptId: String): Flow<DailyProgress?> = record(attemptId)?.progress ?: flowOf(null)

    override fun result(attemptId: String): DailyResult? =
        synchronized(lock) { results[attemptId] } ?: record(attemptId)?.answers?.state?.value?.result

    override suspend fun submitAnswer(
        attemptId: String,
        questionId: String,
        optionId: String?,
        elapsedMs: Long,
    ): DataResult<AnswerResult> {
        val record = record(attemptId) ?: return DataResult.Failure(DataError.Api(ApiErrorCode.SessionNotFound))
        return record.answers.submit(questionId, optionId, elapsedMs)
    }

    override fun enqueueAnswer(attemptId: String, questionId: String, optionId: String?, elapsedMs: Long) {
        record(attemptId)?.answers?.enqueue(questionId, optionId, elapsedMs)
    }

    override suspend fun finish(attemptId: String): DataResult<DailyResult> {
        synchronized(lock) { results[attemptId] }?.let { return DataResult.Success(it) }
        val record = record(attemptId) ?: return DataResult.Failure(DataError.Api(ApiErrorCode.SessionNotFound))
        val result = record.answers.finish()
        if (result is DataResult.Success) onResult(result.value)
        return result
    }

    /** A known final result also updates the cached home card (finished, squares, streak). */
    private suspend fun onResult(result: DailyResult) {
        synchronized(lock) {
            results[result.attemptId] = result
            while (results.size > MAX_ATTEMPTS) results.remove(results.keys.first())
        }
        val currentUid = uid.first() ?: return
        val cached = status.first()?.takeIf { it.dayKey == result.dayKey } ?: return
        storeStatus(
            currentUid,
            cached.copy(
                status = DailyStatusType.FINISHED,
                number = result.number,
                attemptId = result.attemptId,
                result = DailyResultSummary(result.correctCount, result.total, result.squares),
                streakCurrent = result.streakCurrent,
            ),
        )
    }

    private suspend fun storeStatus(currentUid: String, value: DailyStatus) {
        cache.write(currentUid, CacheKeys.DAILY_STATUS, CachedDailyStatus.serializer(), value.toCached())
    }

    override suspend fun shownAt(attemptId: String): Map<String, Long> =
        retention.dailyShownAt.first().mapNotNull { entry ->
            val parts = entry.split('|')
            if (parts.size != SHOWN_AT_PARTS || parts[0] != attemptId) return@mapNotNull null
            parts[2].toLongOrNull()?.let { parts[1] to it }
        }.toMap()

    override suspend fun recordShown(attemptId: String, questionId: String, serverEpochMs: Long) {
        val current = retention.dailyShownAt.first()
        if (current.any { it.startsWith("$attemptId|$questionId|") }) return
        // Only the current attempt is kept (one attempt per day).
        val kept = current.filter { it.startsWith("$attemptId|") }.toSet()
        retention.setDailyShownAt(kept + "$attemptId|$questionId|$serverEpochMs")
    }

    override fun clear() {
        val removed = synchronized(lock) {
            pendingStart?.cancel()
            pendingStart = null
            results.clear()
            records.values.toList().also { records.clear() }
        }
        removed.forEach { it.answers.cancel() }
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
        const val SHOWN_AT_PARTS = 3
    }
}

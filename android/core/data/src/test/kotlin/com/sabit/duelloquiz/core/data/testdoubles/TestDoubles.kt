package com.sabit.duelloquiz.core.data.testdoubles

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.sabit.duelloquiz.core.data.util.NetworkMonitor
import com.sabit.duelloquiz.core.datastore.UserCacheDataSource
import com.sabit.duelloquiz.core.model.AnswerResult
import com.sabit.duelloquiz.core.model.Category
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.Profile
import com.sabit.duelloquiz.core.model.QuizOption
import com.sabit.duelloquiz.core.model.QuizQuestion
import com.sabit.duelloquiz.core.model.QuizResult
import com.sabit.duelloquiz.core.model.QuizSession
import com.sabit.duelloquiz.core.model.QuizSessionStatus
import com.sabit.duelloquiz.core.model.duel.CalibrationLevel
import com.sabit.duelloquiz.core.model.duel.DuelRecord
import com.sabit.duelloquiz.core.model.duel.DuelStats
import com.sabit.duelloquiz.core.model.duel.MatchSummary
import com.sabit.duelloquiz.core.network.DuelloNetworkDataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import java.io.File

internal class FakeNetworkMonitor(online: Boolean = true) : NetworkMonitor {
    val online = MutableStateFlow(online)
    override val isOnline: Flow<Boolean> = this.online
}

/** Scriptable backend: each call returns the next scripted result (or the default). */
internal class FakeNetworkDataSource : DuelloNetworkDataSource {

    data class Answer(val questionId: String, val optionId: String?, val elapsedMs: Long)

    val calls = mutableListOf<String>()
    var profile: DataResult<Profile> = DataResult.Success(profile("Oyunçu 1234"))
    val patchResults = ArrayDeque<DataResult<Profile>>()
    var categories: DataResult<List<Category>> = DataResult.Success(emptyList())
    var deleteResult: DataResult<Unit> = DataResult.Success(Unit)
    var startResult: DataResult<QuizSession> = DataResult.Success(session())
    var startGate: CompletableDeferred<Unit>? = null
    val answerResults = ArrayDeque<DataResult<AnswerResult>>()
    val answers = mutableListOf<Answer>()
    var finishResult: DataResult<QuizResult>? = null

    override suspend fun getProfile(): DataResult<Profile> {
        calls += "getProfile"
        return profile
    }

    override suspend fun updateDisplayName(displayName: String): DataResult<Profile> {
        calls += "updateDisplayName:$displayName"
        return patchResults.removeFirstOrNull() ?: DataResult.Success(profile(displayName))
    }

    var stats: DataResult<DuelStats> = DataResult.Success(
        DuelStats(DuelRecord(0, 0, 0, 0), (1..3).map { CalibrationLevel(it, 0, 0) }),
    )
    var matches: DataResult<List<MatchSummary>> = DataResult.Success(emptyList())

    override suspend fun getStats(): DataResult<DuelStats> {
        calls += "getStats"
        return stats
    }

    override suspend fun getMatches(limit: Int): DataResult<List<MatchSummary>> {
        calls += "getMatches:$limit"
        return matches
    }

    override suspend fun deleteAccount(): DataResult<Unit> {
        calls += "deleteAccount"
        return deleteResult
    }

    override suspend fun getCategories(): DataResult<List<Category>> {
        calls += "getCategories"
        return categories
    }

    override suspend fun startQuizSession(categoryId: Int): DataResult<QuizSession> {
        calls += "start:$categoryId"
        startGate?.await()
        return startResult
    }

    override suspend fun submitAnswer(
        sessionId: String,
        questionId: String,
        optionId: String?,
        elapsedMs: Long,
    ): DataResult<AnswerResult> {
        calls += "answer:$questionId"
        answers += Answer(questionId, optionId, elapsedMs)
        return answerResults.removeFirstOrNull() ?: DataResult.Success(verdict(questionId, optionId))
    }

    override suspend fun finishQuizSession(sessionId: String): DataResult<QuizResult> {
        calls += "finish"
        return finishResult ?: DataResult.Success(
            QuizResult(sessionId, QuizSessionStatus.FINISHED, score = 0, correctCount = 0, total = 10, review = emptyList()),
        )
    }

    companion object {
        fun profile(name: String) = Profile(id = "p1", displayName = name, avatarUrl = null, rating = 1000)

        fun verdict(questionId: String, optionId: String?) =
            AnswerResult(questionId, optionId, optionId == "$questionId-a", "$questionId-a", null)

        fun session(id: String = "s1", startedAt: Long = 0, offset: Long = 0) = QuizSession(
            id = id,
            categoryId = 1,
            startedAtEpochMs = startedAt,
            deadlineEpochMs = startedAt + 245_000,
            questionTimeLimitMs = 20_000,
            clockOffsetMs = offset,
            questions = (1..10).map { n ->
                QuizQuestion("q$n", n, "Sual $n", null, listOf("a", "b", "c", "d").map { QuizOption("q$n-$it", it) })
            },
        )

        val offline: DataResult<Nothing> = DataResult.Failure(DataError.Offline)
    }
}

/**
 * Application scope for repositories under test: runs on the test scheduler like foreground work,
 * so `advanceUntilIdle()` also drives what repositories launch (unlike `backgroundScope`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.testAppScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

internal fun testUserCache(scope: CoroutineScope, dir: File): UserCacheDataSource = UserCacheDataSource(
    PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(dir, "user_cache.preferences_pb") }),
)

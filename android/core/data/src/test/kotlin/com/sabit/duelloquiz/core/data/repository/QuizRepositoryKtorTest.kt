package com.sabit.duelloquiz.core.data.repository

import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.data.testdoubles.FakeNetworkMonitor
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.network.createDuelloNetworkDataSource
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** QuizRepository on the real Ktor stack with a MockEngine backend. */
class QuizRepositoryKtorTest {

    private val paths = mutableListOf<String>()
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun TestScope.repository(
        handler: (path: String) -> Pair<HttpStatusCode, String>,
    ): DefaultQuizRepository {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            paths += path
            val (status, body) = handler(path)
            respond(body, status, jsonHeaders)
        }
        val clock = WallClock { LOCAL_NOW }
        val network = createDuelloNetworkDataSource(
            engine = engine,
            baseUrl = "http://test/",
            tokenProvider = { "token" },
            languageProvider = { "az" },
            clock = clock,
        )
        return DefaultQuizRepository(network, FakeNetworkMonitor(), clock, backgroundScope)
    }

    @Test
    fun startAnswerAndFinishThroughTheContract() = runTest {
        val repository = repository { path ->
            when {
                path.endsWith("/sessions") -> HttpStatusCode.Created to SESSION
                path.endsWith("/answers") ->
                    HttpStatusCode.OK to """{"questionId":"q1","optionId":"q1-b","correct":false,"correctOptionId":"q1-a","explanation":"İzah"}"""
                else ->
                    HttpStatusCode.OK to """{"sessionId":"s1","status":"finished","score":0,"correctCount":0,"total":10,"finishedAt":"2026-10-16T10:00:00Z","review":[]}"""
            }
        }

        val session = (repository.startSession(categoryId = 2) as DataResult.Success).value
        assertEquals(SERVER_NOW - LOCAL_NOW, session.clockOffsetMs)

        val answer = repository.submitAnswer(session.id, "q1", "q1-b", 3_400)
        assertEquals("q1-a", (answer as DataResult.Success).value.correctOptionId)
        assertEquals(1, repository.progress(session.id).filterNotNull().first().resolved.size)

        assertTrue(repository.finish(session.id) is DataResult.Success)
        assertEquals(
            listOf("/v1/quiz/sessions", "/v1/quiz/sessions/s1/answers", "/v1/quiz/sessions/s1/finish"),
            paths,
        )
    }

    @Test
    fun notPlayableAndRateLimitedStartsAreReportedByCode() = runTest {
        val notPlayable = repository { HttpStatusCode.Conflict to """{"code":"category_not_playable","message":"x"}""" }
        assertEquals(
            DataResult.Failure(DataError.Api(ApiErrorCode.CategoryNotPlayable)),
            notPlayable.startSession(categoryId = 2),
        )
    }

    private companion object {
        const val LOCAL_NOW = 1_000_000L
        const val SERVER_NOW = 1_002_500L
        val SESSION = buildString {
            append("""{"id":"s1","categoryId":2,"startedAtEpochMs":$SERVER_NOW,"deadlineEpochMs":${SERVER_NOW + 245_000},""")
            append(""""serverNowEpochMs":$SERVER_NOW,"questionTimeLimitMs":20000,"questions":[""")
            append(
                (1..10).joinToString(",") { n ->
                    """{"id":"q$n","position":$n,"text":"Sual $n","image":null,"options":[
                    {"id":"q$n-a","text":"A"},{"id":"q$n-b","text":"B"},{"id":"q$n-c","text":"C"},{"id":"q$n-d","text":"D"}]}"""
                },
            )
            append("]}")
        }
    }
}

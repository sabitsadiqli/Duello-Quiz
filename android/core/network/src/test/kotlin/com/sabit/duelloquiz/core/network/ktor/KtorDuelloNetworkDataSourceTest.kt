package com.sabit.duelloquiz.core.network.ktor

import com.sabit.duelloquiz.core.auth.AuthError
import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.auth.AuthResult
import com.sabit.duelloquiz.core.auth.SignOutReason
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.QuizSessionStatus
import com.sabit.duelloquiz.core.network.auth.AuthRepositoryIdTokenProvider
import com.sabit.duelloquiz.core.network.auth.IdTokenProvider
import com.sabit.duelloquiz.core.network.auth.TokenUnavailableException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class KtorDuelloNetworkDataSourceTest {

    private val requests = mutableListOf<HttpRequestData>()
    private val tokenCalls = mutableListOf<Boolean>()
    private var now = 1_000_000L

    private val tokens = IdTokenProvider { force ->
        tokenCalls += force
        if (force) "fresh-token" else "cached-token"
    }

    private fun dataSource(
        tokenProvider: IdTokenProvider = tokens,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): KtorDuelloNetworkDataSource {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        val client = createDuelloHttpClient(engine, "http://test/", tokenProvider) { "az" }
        return KtorDuelloNetworkDataSource(client) { now }
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK, extra: Map<String, String> = emptyMap()) =
        respond(
            content = body,
            status = status,
            headers = headersOf(
                *(mapOf(HttpHeaders.ContentType to "application/json") + extra).map { it.key to listOf(it.value) }.toTypedArray(),
            ),
        )

    private fun error(code: String) = """{"code":"$code","message":"English developer hint"}"""

    @Test
    fun sendsBearerTokenAndAcceptLanguageAndMapsTheProfile() = runTest {
        val source = dataSource {
            json("""{"id":"u1","displayName":"Aysel","avatarUrl":null,"rating":1000,"createdAt":"2026-10-16T10:00:00Z","extra":1}""")
        }

        val result = source.getProfile()

        assertEquals("Aysel", (result as DataResult.Success).value.displayName)
        assertEquals(1000, result.value.rating)
        val request = requests.single()
        assertEquals("http://test/v1/me", request.url.toString())
        assertEquals("Bearer cached-token", request.headers[HttpHeaders.Authorization])
        assertEquals("az", request.headers[HttpHeaders.AcceptLanguage])
    }

    @Test
    fun unauthorizedForcesOneTokenRefreshAndRetries() = runTest {
        val source = dataSource { request ->
            if (request.headers[HttpHeaders.Authorization] == "Bearer fresh-token") {
                json("""{"items":[{"id":1,"slug":"cografiya","name":"Coğrafiya","iconKey":"geography"}]}""")
            } else {
                json(error("unauthorized"), HttpStatusCode.Unauthorized)
            }
        }

        val result = source.getCategories()

        assertEquals("Coğrafiya", (result as DataResult.Success).value.single().name)
        assertEquals(listOf(false, true), tokenCalls)
        assertEquals(2, requests.size)
    }

    @Test
    fun secondUnauthorizedIsReportedWithoutAThirdAttempt() = runTest {
        val source = dataSource { json(error("unauthorized"), HttpStatusCode.Unauthorized) }

        assertEquals(DataResult.Failure(DataError.Unauthorized), source.getCategories())
        assertEquals(2, requests.size)
    }

    @Test
    fun errorsAreMappedByCodeOnly() = runTest {
        val cases = listOf(
            Triple(HttpStatusCode.Conflict, "category_not_playable", DataError.Api(ApiErrorCode.CategoryNotPlayable)),
            Triple(HttpStatusCode.NotFound, "category_not_found", DataError.Api(ApiErrorCode.CategoryNotFound)),
            Triple(HttpStatusCode.Conflict, "session_expired", DataError.Api(ApiErrorCode.SessionExpired)),
            Triple(HttpStatusCode.BadRequest, "something_new", DataError.Api(ApiErrorCode.Unknown)),
            Triple(HttpStatusCode.InternalServerError, "internal_error", DataError.Server),
            Triple(HttpStatusCode.BadGateway, "auth_provider_error", DataError.Api(ApiErrorCode.AuthProviderError)),
        )
        cases.forEach { (status, code, expected) ->
            val source = dataSource { json(error(code), status) }
            assertEquals(code, DataResult.Failure(expected), source.startQuizSession(categoryId = 1))
        }
    }

    @Test
    fun rateLimitCarriesRetryAfter() = runTest {
        val source = dataSource {
            json(error("rate_limited"), HttpStatusCode.TooManyRequests, mapOf(HttpHeaders.RetryAfter to "120"))
        }

        assertEquals(DataResult.Failure(DataError.RateLimited(120)), source.startQuizSession(categoryId = 1))
    }

    @Test
    fun unreadableErrorBodyOn5xxIsAServerError() = runTest {
        val source = dataSource { respond("<html>Bad gateway</html>", HttpStatusCode.BadGateway) }

        assertEquals(DataResult.Failure(DataError.Server), source.getCategories())
    }

    @Test
    fun connectionFailureIsOffline() = runTest {
        val source = dataSource { throw IOException("no route to host") }

        assertEquals(DataResult.Failure(DataError.Offline), source.getProfile())
    }

    @Test
    fun tokenRefreshFailureOfflineDoesNotLookUnauthorized() = runTest {
        val source = dataSource(tokenProvider = { throw TokenUnavailableException(offline = true) }) {
            json("{}")
        }

        assertEquals(DataResult.Failure(DataError.Offline), source.getProfile())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun deleteTreats204And404AsDone() = runTest {
        val deleted = dataSource { respond("", HttpStatusCode.NoContent) }
        assertEquals(DataResult.Success(Unit), deleted.deleteAccount())
        assertEquals(HttpMethod.Delete, requests.last().method)

        val gone = dataSource { json(error("not_found"), HttpStatusCode.NotFound) }
        assertEquals(DataResult.Success(Unit), gone.deleteAccount())

        val failed = dataSource { json(error("auth_provider_error"), HttpStatusCode.BadGateway) }
        assertEquals(DataResult.Failure(DataError.Api(ApiErrorCode.AuthProviderError)), failed.deleteAccount())
    }

    @Test
    fun startSessionComputesTheClockOffsetFromServerNow() = runTest {
        val source = dataSource {
            now += 200 // round trip of 200 ms, midpoint at +100
            json(SESSION_JSON)
        }
        val sentAt = now

        val session = (source.startQuizSession(categoryId = 3) as DataResult.Success).value

        assertEquals(SERVER_NOW - (sentAt + 100), session.clockOffsetMs)
        assertEquals(10, session.questions.size)
        assertEquals(20_000L, session.questionTimeLimitMs)
        assertNull(session.questions.first().image)
        val image = session.questions[1].image!!
        assertEquals(0xFF336699L, image.dominantColorArgb)
        assertEquals("Xəritə", image.alt)
        val body = Json.parseToJsonElement((requests.single().body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
        assertEquals("3", body.jsonObject["categoryId"]!!.jsonPrimitive.content)
    }

    @Test
    fun timeoutAnswerSendsAnExplicitNullOption() = runTest {
        val source = dataSource {
            json("""{"questionId":"q1","optionId":null,"correct":false,"correctOptionId":"o2","explanation":null}""")
        }

        val result = source.submitAnswer("s1", "q1", optionId = null, elapsedMs = 20_000)

        assertEquals("o2", (result as DataResult.Success).value.correctOptionId)
        val request = requests.single()
        assertEquals("http://test/v1/quiz/sessions/s1/answers", request.url.toString())
        val body = Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
        assertEquals(JsonNull, body.jsonObject["optionId"])
        assertEquals("20000", body.jsonObject["elapsedMs"]!!.jsonPrimitive.content)
    }

    @Test
    fun finishMapsTheReviewInSessionOrder() = runTest {
        val source = dataSource {
            json(
                """{"sessionId":"s1","status":"expired","score":8,"correctCount":8,"total":10,
                "finishedAt":"2026-10-16T10:04:05Z","review":[
                {"questionId":"q5","position":5,"text":"T5","image":null,"selectedOption":null,
                 "correctOption":{"id":"o1","text":"A"},"explanation":null},
                {"questionId":"q2","position":2,"text":"T2","image":null,"selectedOption":{"id":"o3","text":"C"},
                 "correctOption":{"id":"o4","text":"D"},"explanation":"İzah"}]}""",
            )
        }

        val result = (source.finishQuizSession("s1") as DataResult.Success).value

        assertEquals(QuizSessionStatus.EXPIRED, result.status)
        assertEquals(listOf(2, 5), result.review.map { it.position })
        assertNull(result.review[1].selectedOption)
        assertEquals("İzah", result.review[0].explanation)
    }

    @Test
    fun forcedRefreshFailureForADeletedAccountSignsOut() = runTest {
        val authRepository: AuthRepository = mockk(relaxUnitFun = true)
        coEvery { authRepository.getIdToken(false) } returns AuthResult.Success("old")
        coEvery { authRepository.getIdToken(true) } returns AuthResult.Failure(AuthError.InvalidCredentials)
        val source = dataSource(tokenProvider = AuthRepositoryIdTokenProvider(authRepository)) {
            json(error("unauthorized"), HttpStatusCode.Unauthorized)
        }

        assertEquals(DataResult.Failure(DataError.Unauthorized), source.getProfile())
        coVerify(exactly = 1) { authRepository.signOut(SignOutReason.SessionExpired) }
    }

    @Test
    fun forcedRefreshFailureOfflineKeepsTheUserSignedIn() = runTest {
        val authRepository: AuthRepository = mockk(relaxUnitFun = true)
        coEvery { authRepository.getIdToken(false) } returns AuthResult.Success("old")
        coEvery { authRepository.getIdToken(true) } returns AuthResult.Failure(AuthError.Network)
        val source = dataSource(tokenProvider = AuthRepositoryIdTokenProvider(authRepository)) {
            json(error("unauthorized"), HttpStatusCode.Unauthorized)
        }

        assertEquals(DataResult.Failure(DataError.Offline), source.getProfile())
        coVerify(exactly = 0) { authRepository.signOut(any()) }
    }

    private companion object {
        const val SERVER_NOW = 5_000_000L

        val SESSION_JSON = buildString {
            append("""{"id":"s1","categoryId":3,"startedAtEpochMs":4999000,"deadlineEpochMs":5244000,""")
            append(""""serverNowEpochMs":$SERVER_NOW,"questionTimeLimitMs":20000,"questions":[""")
            append(
                (1..10).joinToString(",") { position ->
                    val image = if (position == 2) {
                        """{"url":"http://m/q.webp","thumbUrl":"http://m/q_thumb.webp","width":800,"height":450,"dominantColor":"#336699","alt":"Xəritə"}"""
                    } else {
                        "null"
                    }
                    """{"id":"q$position","position":$position,"text":"Sual $position","image":$image,
                    "options":[{"id":"a$position","text":"A"},{"id":"b$position","text":"B"},
                    {"id":"c$position","text":"C"},{"id":"d$position","text":"D"}]}"""
                },
            )
            append("]}")
        }
    }
}

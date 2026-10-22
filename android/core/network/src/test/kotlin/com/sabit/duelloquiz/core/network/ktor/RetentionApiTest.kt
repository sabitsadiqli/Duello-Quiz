package com.sabit.duelloquiz.core.network.ktor

import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DailySquare
import com.sabit.duelloquiz.core.model.DailyStart
import com.sabit.duelloquiz.core.model.DailyStatusType
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.NotificationSettings
import com.sabit.duelloquiz.core.model.QuizSessionStatus
import com.sabit.duelloquiz.core.model.StreakEventType
import com.sabit.duelloquiz.core.network.auth.IdTokenProvider
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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parsing of the retention payloads (openapi.yaml 0.2.0 examples and schemas). */
class RetentionApiTest {

    private val requests = mutableListOf<HttpRequestData>()
    private var now = 1_000_000L

    private fun dataSource(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): KtorDuelloNetworkDataSource {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        val client = createDuelloHttpClient(engine, "http://test/", IdTokenProvider { "token" }) { "az" }
        return KtorDuelloNetworkDataSource(client) { now }
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "application/json"))

    private val progressJson = """
        {"dayKey": "2026-10-22", "xpGained": 70, "xpCapped": false,
         "xpBreakdown": [{"reason": "correct_answers", "categoryId": 1, "amount": 50},
                         {"reason": "daily_bonus", "categoryId": null, "amount": 20}],
         "xpTotal": 1270, "levelBefore": 4, "levelAfter": 5,
         "xpIntoLevelBefore": 380, "xpForNextLevelBefore": 400, "xpIntoLevel": 50, "xpForNextLevel": 500,
         "categoryLevelUps": [{"categoryId": 1, "levelBefore": 2, "levelAfter": 3}],
         "streakCurrent": 8, "streakBest": 12, "streakExtendedToday": true, "freezeAvailable": true}
    """.trimIndent()

    private val dailyResultJson = """
        {"attemptId": "0b6f", "dayKey": "2026-10-22", "number": 12, "status": "expired", "score": 3,
         "correctCount": 3, "total": 5, "squares": ["correct", "wrong", "correct", "correct", "unanswered"],
         "finishedAt": "2026-10-22T08:00:00Z",
         "review": [{"questionId": "q2", "position": 2, "text": "Sual 2", "image": null,
                     "selectedOption": {"id": "o1", "text": "A"}, "correctOption": {"id": "o2", "text": "B"},
                     "explanation": "İzah"}],
         "progress": $progressJson, "streakCurrent": 8}
    """.trimIndent()

    @Test
    fun dailyStatusFinishedWithSquaresAndClockOffset() = runTest {
        val source = dataSource {
            now += 100 // the response arrives 100 ms later
            json(
                """{"dayKey": "2026-10-22", "number": 12, "status": "finished", "questionCount": 5,
                    "serverNowEpochMs": 1000550, "nextResetEpochMs": 1050000,
                    "attempt": {"id": "0b6f", "status": "finished", "startedAtEpochMs": 900000, "deadlineEpochMs": 1037500},
                    "result": {"correctCount": 3, "total": 5, "squares": ["correct", "wrong", "correct", "correct", "unanswered"]},
                    "streakCurrent": 7}""",
            )
        }

        val status = (source.getDaily() as DataResult.Success).value

        assertEquals(DailyStatusType.FINISHED, status.status)
        assertEquals(12, status.number)
        assertEquals("0b6f", status.attemptId)
        assertEquals(DailySquare.UNANSWERED, status.result?.squares?.last())
        assertEquals(7, status.streakCurrent)
        // Midpoint of the 100 ms trip is 1_000_050; the server said 1_000_550.
        assertEquals(500L, status.clockOffsetMs)
        assertEquals("http://test/v1/daily", requests.single().url.toString())
    }

    @Test
    fun dailyStatusUnavailableHasNoNumber() = runTest {
        val source = dataSource {
            json(
                """{"dayKey": "2026-10-22", "number": null, "status": "unavailable", "questionCount": 5,
                    "serverNowEpochMs": 1000000, "nextResetEpochMs": 1050000, "attempt": null, "result": null,
                    "streakCurrent": 0}""",
            )
        }

        val status = (source.getDaily() as DataResult.Success).value

        assertEquals(DailyStatusType.UNAVAILABLE, status.status)
        assertNull(status.number)
        assertNull(status.result)
    }

    @Test
    fun startDailyCreatesOrResumesWithAnsweredQuestions() = runTest {
        val source = dataSource { request ->
            assertEquals(HttpMethod.Post, request.method)
            json(
                """{"id": "a1", "dayKey": "2026-10-22", "number": 12, "startedAtEpochMs": 990000,
                    "deadlineEpochMs": 1127500, "serverNowEpochMs": 1000000, "questionTimeLimitMs": 20000,
                    "resumed": true,
                    "questions": [
                      {"id": "q2", "position": 2, "text": "İki", "image": null,
                       "options": [{"id": "o1", "text": "A"}, {"id": "o2", "text": "B"}, {"id": "o3", "text": "C"}, {"id": "o4", "text": "D"}]},
                      {"id": "q1", "position": 1, "text": "Bir", "image": null,
                       "options": [{"id": "o5", "text": "A"}, {"id": "o6", "text": "B"}, {"id": "o7", "text": "C"}, {"id": "o8", "text": "D"}]}],
                    "answers": [{"questionId": "q1", "optionId": "o5", "correct": true, "correctOptionId": "o5", "explanation": null}]}""",
                status = HttpStatusCode.OK,
            )
        }

        val start = (source.startDailyAttempt() as DataResult.Success).value as DailyStart.Started

        assertTrue(start.attempt.resumed)
        assertEquals(listOf("q1", "q2"), start.attempt.questions.map { it.id })
        assertEquals("q1", start.attempt.answers.single().questionId)
        assertEquals(1_127_500L, start.attempt.deadlineEpochMs)
        assertEquals("http://test/v1/daily/attempt", requests.single().url.toString())
    }

    @Test
    fun startDailyAlreadyPlayedCarriesTheStoredResult() = runTest {
        val source = dataSource {
            json("""{"code": "daily_already_played", "message": "dev hint", "result": $dailyResultJson}""", HttpStatusCode.Conflict)
        }

        val start = (source.startDailyAttempt() as DataResult.Success).value as DailyStart.AlreadyPlayed

        assertEquals("0b6f", start.result.attemptId)
        assertEquals(QuizSessionStatus.EXPIRED, start.result.status)
        assertEquals(3, start.result.correctCount)
        assertEquals(70, start.result.progress?.xpGained)
    }

    @Test
    fun startDailyUnavailableAndUnknownConflictsAreFailures() = runTest {
        val unavailable = dataSource { json("""{"code": "daily_unavailable", "message": "x"}""", HttpStatusCode.Conflict) }
        assertEquals(
            DataResult.Failure(DataError.Api(ApiErrorCode.DailyUnavailable)),
            unavailable.startDailyAttempt(),
        )
        val brokenBody = dataSource { json("""{"code": "daily_already_played", "message": "x"}""", HttpStatusCode.Conflict) }
        assertEquals(
            DataResult.Failure(DataError.Api(ApiErrorCode.DailyAlreadyPlayed)),
            brokenBody.startDailyAttempt(),
        )
    }

    @Test
    fun dailyAnswerAndFinish() = runTest {
        val source = dataSource { request ->
            if (request.url.encodedPath.endsWith("/answers")) {
                json("""{"questionId": "q1", "optionId": null, "correct": false, "correctOptionId": "o2", "explanation": null}""")
            } else {
                json(dailyResultJson)
            }
        }

        val answer = (source.submitDailyAnswer("a1", "q1", null, 20_000) as DataResult.Success).value
        val result = (source.finishDailyAttempt("a1") as DataResult.Success).value

        assertNull(answer.optionId)
        val body = Json.parseToJsonElement(((requests[0].body) as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
        assertEquals("null", body.getValue("optionId").toString())
        assertEquals("http://test/v1/daily/attempt/a1/answers", requests[0].url.toString())
        assertEquals("http://test/v1/daily/attempt/a1/finish", requests[1].url.toString())
        assertEquals(1, result.review.size)
        assertEquals(listOf(50, 20), result.progress?.xpBreakdown?.map { it.amount })
        assertEquals(3, result.progress?.categoryLevelUps?.single()?.levelAfter)
        assertTrue(result.progress?.leveledUp == true)
    }

    @Test
    fun quizFinishCarriesProgressAndOldSessionsHaveNone() = runTest {
        var withProgress = true
        val source = dataSource {
            val progress = if (withProgress) progressJson else "null"
            json(
                """{"sessionId": "s1", "status": "finished", "score": 7, "correctCount": 7, "total": 10,
                    "finishedAt": "2026-10-22T08:00:00Z", "review": [], "progress": $progress}""",
            )
        }

        val result = (source.finishQuizSession("s1") as DataResult.Success).value
        withProgress = false
        val old = (source.finishQuizSession("s1") as DataResult.Success).value

        assertEquals(70, result.progress?.xpGained)
        assertTrue(result.progress?.streakExtendedToday == true)
        assertNull(old.progress)
    }

    @Test
    fun progressSummaryWithStreakEvent() = runTest {
        val source = dataSource {
            json(
                """{"xpTotal": 12450, "level": 16, "xpIntoLevel": 450, "xpForNextLevel": 1600,
                    "streakCurrent": 0, "streakBest": 12, "todayActive": false, "freezeAvailable": false,
                    "streakEvent": {"type": "streak_lost", "dayKey": "2026-10-21", "previousStreak": 12},
                    "dayKey": "2026-10-22", "nextResetEpochMs": 1050000, "serverNowEpochMs": 1000000,
                    "categories": [{"categoryId": 1, "slug": "cografiya", "name": "Coğrafiya", "iconKey": "geography",
                                    "xp": 240, "level": 2, "xpIntoLevel": 140, "xpForNextLevel": 200}]}""",
            )
        }

        val progress = (source.getProgress() as DataResult.Success).value

        assertEquals(16, progress.level)
        assertEquals(StreakEventType.STREAK_LOST, progress.streakEvent?.type)
        assertEquals(12, progress.streakEvent?.previousStreak)
        assertEquals("Coğrafiya", progress.categories.single().name)
        assertEquals("http://test/v1/me/progress", requests.single().url.toString())
    }

    @Test
    fun unknownStreakEventTypesAreIgnored() = runTest {
        val source = dataSource {
            json(
                """{"xpTotal": 0, "level": 1, "xpIntoLevel": 0, "xpForNextLevel": 100, "streakCurrent": 0,
                    "streakBest": 0, "todayActive": false, "freezeAvailable": false,
                    "streakEvent": {"type": "streak_party", "dayKey": "2026-10-21", "previousStreak": 3},
                    "dayKey": "2026-10-22", "nextResetEpochMs": 1, "serverNowEpochMs": 1, "categories": []}""",
            )
        }

        assertNull((source.getProgress() as DataResult.Success).value.streakEvent)
    }

    @Test
    fun pushTokenAndNotificationSettings() = runTest {
        val source = dataSource { request ->
            when {
                request.url.encodedPath.contains("push-tokens") -> respond("", HttpStatusCode.NoContent)
                else -> json("""{"dailyReminder": false, "streakReminder": true}""")
            }
        }

        assertTrue(source.registerPushToken("tok:en/1", "az") is DataResult.Success)
        assertTrue(source.deletePushToken("tok:en/1") is DataResult.Success)
        val settings = (source.updateNotificationSettings(NotificationSettings(false, true)) as DataResult.Success).value

        assertEquals(HttpMethod.Put, requests[0].method)
        val tokenBody = Json.parseToJsonElement((requests[0].body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
        assertEquals("tok:en/1", tokenBody.getValue("token").jsonPrimitive.content)
        assertEquals("az", tokenBody.getValue("locale").jsonPrimitive.content)
        assertEquals(HttpMethod.Delete, requests[1].method)
        // The token is one path segment: "/" is encoded.
        assertFalse(requests[1].url.encodedPath.removePrefix("/v1/me/push-tokens/").contains("/"))
        assertEquals(HttpMethod.Put, requests[2].method)
        assertFalse(settings.dailyReminder)
        assertTrue(settings.streakReminder)
    }

    @Test
    fun invalidPushTokenIsAKnownCode() = runTest {
        val source = dataSource { json("""{"code": "invalid_push_token", "message": "x"}""", HttpStatusCode.BadRequest) }

        assertEquals(DataResult.Failure(DataError.Api(ApiErrorCode.InvalidPushToken)), source.registerPushToken("", "az"))
    }
}

package com.sabit.duelloquiz.backend.quiz

import com.sabit.duelloquiz.backend.common.ErrorResponse
import com.sabit.duelloquiz.backend.common.QuizConfig
import com.sabit.duelloquiz.backend.common.db.OptionsTable
import com.sabit.duelloquiz.backend.testsupport.DbTest
import com.sabit.duelloquiz.backend.testsupport.TestContent
import com.sabit.duelloquiz.backend.testsupport.TestEnv
import com.sabit.duelloquiz.backend.testsupport.auth
import com.sabit.duelloquiz.backend.testsupport.testConfig
import com.sabit.duelloquiz.backend.testsupport.withTestApp
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuizRoutesTest : DbTest() {

    private suspend fun HttpClient.start(token: String, categoryId: Int = 1): HttpResponse = post("/v1/quiz/sessions") {
        auth(token)
        contentType(ContentType.Application.Json)
        setBody(StartSessionRequest(categoryId))
    }

    private suspend fun HttpClient.answer(token: String, sessionId: String, questionId: String, optionId: String?, elapsedMs: Int? = 1234) =
        post("/v1/quiz/sessions/$sessionId/answers") {
            auth(token)
            contentType(ContentType.Application.Json)
            setBody(SubmitAnswerRequest(questionId, optionId, elapsedMs))
        }

    private suspend fun HttpClient.finish(token: String, sessionId: String) =
        post("/v1/quiz/sessions/$sessionId/finish") { auth(token) }

    private fun correctOptionOf(questionId: String): String = runBlocking {
        db.tx {
            OptionsTable.selectAll()
                .where { OptionsTable.questionId eq UUID.fromString(questionId) }
                .single { it[OptionsTable.isCorrect] }[OptionsTable.id].toString()
        }
    }

    @Test
    fun `start returns 10 distinct questions without answers and a 245 s deadline`() {
        val env = TestEnv(db)
        runBlocking { TestContent.seed(env, count = 15) }
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            val response = client.start(token)
            assertEquals(HttpStatusCode.Created, response.status)
            val raw = response.bodyAsText()
            listOf("isCorrect", "correct", "explanation", "İzah").forEach { assertFalse(it in raw, "response leaks '$it'") }
            val session = response.body<QuizSessionResponse>()
            assertEquals(10, session.questions.size)
            assertEquals(10, session.questions.map { it.id }.toSet().size)
            assertEquals((1..10).toList(), session.questions.map { it.position })
            assertTrue(session.questions.all { it.options.size == 4 && it.image == null })
            assertEquals(env.clock.millis(), session.startedAtEpochMs)
            assertEquals(env.clock.millis() + 245_000, session.deadlineEpochMs)
            assertEquals(20_000, session.questionTimeLimitMs)
        }
    }

    @Test
    fun `unknown category is 404 and a category with fewer than 10 active questions is 409`() {
        val env = TestEnv(db)
        runBlocking {
            TestContent.seed(env, category = "tarix", count = 9)
            env.importer.import(TestContent.input(TestContent.questions("fizika", 5, status = "draft")))
        }
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            client.start(token, categoryId = 999).let {
                assertEquals(HttpStatusCode.NotFound, it.status)
                assertEquals("category_not_found", it.body<ErrorResponse>().code)
            }
            client.start(token, categoryId = 2).let {
                assertEquals(HttpStatusCode.Conflict, it.status)
                assertEquals("category_not_playable", it.body<ErrorResponse>().code)
            }
            client.start(token, categoryId = 4).let { assertEquals("category_not_playable", it.body<ErrorResponse>().code) }
        }
    }

    @Test
    fun `answers are scored by the server, retries are idempotent and a different answer is 409`() {
        val env = TestEnv(db)
        runBlocking { TestContent.seed(env) }
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            val session = client.start(token).body<QuizSessionResponse>()
            val q1 = session.questions[0]
            val correct = correctOptionOf(q1.id)
            val wrong = q1.options.first { it.id != correct }.id

            val first = client.answer(token, session.id, q1.id, correct)
            assertEquals(HttpStatusCode.OK, first.status)
            val result = first.body<AnswerResponse>()
            assertTrue(result.correct)
            assertEquals(correct, result.correctOptionId)
            assertNotNull(result.explanation)

            assertEquals(result, client.answer(token, session.id, q1.id, correct, elapsedMs = 9999).body<AnswerResponse>())
            client.answer(token, session.id, q1.id, wrong).let {
                assertEquals(HttpStatusCode.Conflict, it.status)
                assertEquals("already_answered", it.body<ErrorResponse>().code)
            }

            // Wrong answer and timeout (optionId = null, OQ-016).
            val q2 = session.questions[1]
            val wrongResult = client.answer(token, session.id, q2.id, q2.options.first { it.id != correctOptionOf(q2.id) }.id)
                .body<AnswerResponse>()
            assertFalse(wrongResult.correct)
            assertEquals(correctOptionOf(q2.id), wrongResult.correctOptionId)
            val q3 = session.questions[2]
            val timeout = client.answer(token, session.id, q3.id, null, elapsedMs = 20000).body<AnswerResponse>()
            assertFalse(timeout.correct)
            assertNull(timeout.optionId)
            assertEquals(timeout, client.answer(token, session.id, q3.id, null).body<AnswerResponse>())
            assertEquals("already_answered", client.answer(token, session.id, q3.id, q3.options[0].id).body<ErrorResponse>().code)
        }
    }

    @Test
    fun `answers are checked against the session and its owner`() {
        val env = TestEnv(db)
        runBlocking { TestContent.seed(env, count = 20) }
        val token = env.verifier.tokenFor("u1")
        val other = env.verifier.tokenFor("u2")
        withTestApp(env) { client ->
            val session = client.start(token).body<QuizSessionResponse>()
            val otherSession = client.start(other).body<QuizSessionResponse>()
            val q1 = session.questions[0]
            val foreignQuestion = otherSession.questions.first { q -> session.questions.none { it.id == q.id } }

            client.answer(token, session.id, foreignQuestion.id, foreignQuestion.options[0].id).let {
                assertEquals(HttpStatusCode.BadRequest, it.status)
                assertEquals("question_not_in_session", it.body<ErrorResponse>().code)
            }
            client.answer(token, session.id, q1.id, foreignQuestion.options[0].id).let {
                assertEquals(HttpStatusCode.BadRequest, it.status)
                assertEquals("invalid_option", it.body<ErrorResponse>().code)
            }
            client.answer(token, session.id, "not-a-uuid", null).let {
                assertEquals("validation_failed", it.body<ErrorResponse>().code)
            }
            client.answer(other, session.id, q1.id, q1.options[0].id).let {
                assertEquals(HttpStatusCode.NotFound, it.status)
                assertEquals("session_not_found", it.body<ErrorResponse>().code)
            }
            assertEquals(HttpStatusCode.NotFound, client.finish(other, session.id).status)
            assertEquals(HttpStatusCode.NotFound, client.answer(token, UUID.randomUUID().toString(), q1.id, null).status)
            assertEquals(HttpStatusCode.NotFound, client.finish(token, "garbage").status)
        }
    }

    @Test
    fun `answers after the deadline are rejected and the session expires (virtual clock)`() {
        val env = TestEnv(db)
        runBlocking { TestContent.seed(env) }
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            val session = client.start(token).body<QuizSessionResponse>()
            val q1 = session.questions[0]
            assertEquals(HttpStatusCode.OK, client.answer(token, session.id, q1.id, correctOptionOf(q1.id)).status)

            // Exactly at the deadline is still accepted.
            env.clock.advanceMs(245_000)
            val q2 = session.questions[1]
            assertEquals(HttpStatusCode.OK, client.answer(token, session.id, q2.id, null).status)

            env.clock.advanceMs(1)
            val q3 = session.questions[2]
            client.answer(token, session.id, q3.id, q3.options[0].id).let {
                assertEquals(HttpStatusCode.Conflict, it.status)
                assertEquals("session_expired", it.body<ErrorResponse>().code)
            }
            client.answer(token, session.id, q3.id, q3.options[0].id).let {
                assertEquals("session_closed", it.body<ErrorResponse>().code)
            }
            // The stored answer can still be fetched by a retry.
            assertEquals(HttpStatusCode.OK, client.answer(token, session.id, q1.id, correctOptionOf(q1.id)).status)

            val result = client.finish(token, session.id).body<QuizResultResponse>()
            assertEquals("expired", result.status)
            assertEquals(1, result.score)
            assertEquals(9, result.review.size)
            assertEquals(java.time.Instant.ofEpochMilli(session.deadlineEpochMs).toString(), java.time.Instant.parse(result.finishedAt).toString())
        }
    }

    @Test
    fun `finish returns score and the review of wrong and unanswered questions, idempotently`() {
        val env = TestEnv(db)
        runBlocking { TestContent.seed(env) }
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            val session = client.start(token).body<QuizSessionResponse>()
            val qs = session.questions
            // 7 correct, 1 wrong, 1 timeout, 1 never sent.
            qs.take(7).forEach { client.answer(token, session.id, it.id, correctOptionOf(it.id)) }
            val wrongOption = qs[7].options.first { it.id != correctOptionOf(qs[7].id) }
            client.answer(token, session.id, qs[7].id, wrongOption.id)
            client.answer(token, session.id, qs[8].id, null)
            env.clock.advanceMs(60_000)

            val response = client.finish(token, session.id)
            assertEquals(HttpStatusCode.OK, response.status)
            val result = response.body<QuizResultResponse>()
            assertEquals("finished", result.status)
            assertEquals(7, result.score)
            assertEquals(7, result.correctCount)
            assertEquals(10, result.total)
            assertEquals(listOf(8, 9, 10), result.review.map { it.position })
            assertEquals(wrongOption, result.review[0].selectedOption)
            assertEquals(correctOptionOf(qs[7].id), result.review[0].correctOption.id)
            assertEquals("İzah ${result.review[0].text.removePrefix("Sual ").removeSuffix("?")}", result.review[0].explanation)
            assertNull(result.review[1].selectedOption)
            assertNull(result.review[2].selectedOption)

            env.clock.advanceMs(10_000)
            assertEquals(result, client.finish(token, session.id).body<QuizResultResponse>())
            assertEquals("session_closed", client.answer(token, session.id, qs[9].id, null).body<ErrorResponse>().code)
        }
    }

    @Test
    fun `the expiry job finalizes abandoned sessions`() {
        val env = TestEnv(db)
        runBlocking { TestContent.seed(env) }
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            val session = client.start(token).body<QuizSessionResponse>()
            val q1 = session.questions[0]
            client.answer(token, session.id, q1.id, correctOptionOf(q1.id))
            val service = QuizService(
                env.db, QuizRepository(), com.sabit.duelloquiz.backend.category.CategoryRepository(env.db),
                InMemorySlidingWindowRateLimiter(30, java.time.Duration.ofHours(1)),
                com.sabit.duelloquiz.backend.image.MediaUrls("https://m"), env.config.quiz, env.clock, env.progress,
            )
            assertEquals(0, service.expireOverdueSessions())
            env.clock.advanceMs(245_001)
            assertEquals(1, service.expireOverdueSessions())
            assertEquals(0, service.expireOverdueSessions())
            val result = client.finish(token, session.id).body<QuizResultResponse>()
            assertEquals("expired", result.status)
            assertEquals(1, result.correctCount)
        }
    }

    @Test
    fun `starting more than the hourly limit returns 429 with Retry-After`() {
        val env = TestEnv(db, config = testConfig(QuizConfig(sessionsPerHour = 3, expirySweepIntervalMs = 0)))
        runBlocking { TestContent.seed(env) }
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            repeat(3) { assertEquals(HttpStatusCode.Created, client.start(token).status) }
            val limited = client.start(token)
            assertEquals(HttpStatusCode.TooManyRequests, limited.status)
            assertEquals("rate_limited", limited.body<ErrorResponse>().code)
            assertEquals("3600", limited.headers[HttpHeaders.RetryAfter])
            env.clock.advanceMs(3_600_000)
            assertEquals(HttpStatusCode.Created, client.start(token).status)
        }
    }
}

package com.sabit.duelloquiz.backend.daily

import com.sabit.duelloquiz.backend.common.ErrorResponse
import com.sabit.duelloquiz.backend.common.db.UsersTable
import com.sabit.duelloquiz.backend.quiz.AnswerResponse
import com.sabit.duelloquiz.backend.quiz.SubmitAnswerRequest
import com.sabit.duelloquiz.backend.testsupport.DbTest
import com.sabit.duelloquiz.backend.testsupport.TestContent
import com.sabit.duelloquiz.backend.testsupport.TestEnv
import com.sabit.duelloquiz.backend.testsupport.authGet
import com.sabit.duelloquiz.backend.testsupport.correctOptionOf
import com.sabit.duelloquiz.backend.testsupport.emptyPost
import com.sabit.duelloquiz.backend.testsupport.jsonPost
import com.sabit.duelloquiz.backend.testsupport.seedAllCategories
import com.sabit.duelloquiz.backend.testsupport.setTo
import com.sabit.duelloquiz.backend.testsupport.withTestApp
import com.sabit.duelloquiz.backend.testsupport.wrongOptionOf
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailyRoutesTest : DbTest() {

    private suspend fun HttpClient.status(token: String) = authGet("/v1/daily", token).body<DailyStatusResponse>()
    private suspend fun HttpClient.start(token: String) = emptyPost("/v1/daily/attempt", token)
    private suspend fun HttpClient.answer(token: String, attemptId: String, questionId: String, optionId: String?) =
        jsonPost("/v1/daily/attempt/$attemptId/answers", token, SubmitAnswerRequest(questionId, optionId, 1000))
    private suspend fun HttpClient.finish(token: String, attemptId: String) = emptyPost("/v1/daily/attempt/$attemptId/finish", token)

    @Test
    fun `same five questions for everyone, one per category, without correctness data`() {
        val env = TestEnv(db)
        runBlocking { seedAllCategories(env) }
        val alice = env.verifier.tokenFor("alice")
        val bob = env.verifier.tokenFor("bob")
        withTestApp(env) { client ->
            val status = client.status(alice)
            assertEquals(DailyStatus.AVAILABLE, status.status)
            assertEquals(16, status.number, "2026-10-16 is day #16 when #1 is 2026-10-01")
            assertEquals("2026-10-16", status.dayKey)
            assertEquals(Instant.parse("2026-10-16T20:00:00Z").toEpochMilli(), status.nextResetEpochMs)
            assertNull(status.result)

            val response = client.start(alice)
            assertEquals(HttpStatusCode.Created, response.status)
            val raw = response.bodyAsText()
            listOf("isCorrect", "correctOptionId", "explanation", "İzah").forEach { assertFalse(it in raw, "leaks $it") }
            val a = response.body<DailyAttemptResponse>()
            assertEquals(5, a.questions.size)
            assertEquals((1..5).toList(), a.questions.map { it.position })
            assertEquals(env.clock.millis() + 137_500, a.deadlineEpochMs)
            assertFalse(a.resumed)
            assertTrue(a.answers.isEmpty())
            val b = client.start(bob).body<DailyAttemptResponse>()
            assertEquals(a.questions, b.questions, "same questions, same order, same option order")
            assertEquals(5, a.questions.map { it.id }.toSet().size)
        }
    }

    @Test
    fun `play, resume, finish idempotently and never twice a day`() {
        val env = TestEnv(db)
        runBlocking { seedAllCategories(env) }
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            val attempt = client.start(token).body<DailyAttemptResponse>()
            val qs = attempt.questions
            // 3 correct, 1 wrong, 1 timeout.
            qs.take(3).forEach { assertEquals(HttpStatusCode.OK, client.answer(token, attempt.id, it.id, db.correctOptionOf(it.id)).status) }
            val wrong = client.answer(token, attempt.id, qs[3].id, db.wrongOptionOf(qs[3].id)).body<AnswerResponse>()
            assertFalse(wrong.correct)

            // Second device: same attempt, same deadline, answered results included.
            env.clock.advanceMs(10_000)
            val resumed = client.start(token)
            assertEquals(HttpStatusCode.OK, resumed.status)
            val r = resumed.body<DailyAttemptResponse>()
            assertTrue(r.resumed)
            assertEquals(attempt.id, r.id)
            assertEquals(attempt.deadlineEpochMs, r.deadlineEpochMs)
            assertEquals(4, r.answers.size)
            assertEquals(DailyStatus.IN_PROGRESS, client.status(token).status)

            client.answer(token, attempt.id, qs[4].id, null)
            client.answer(token, attempt.id, qs[0].id, db.wrongOptionOf(qs[0].id)).let {
                assertEquals(HttpStatusCode.Conflict, it.status)
                assertEquals("already_answered", it.body<ErrorResponse>().code)
            }

            val result = client.finish(token, attempt.id).body<DailyResultResponse>()
            assertEquals("finished", result.status)
            assertEquals(3, result.correctCount)
            assertEquals(5, result.total)
            assertEquals(listOf("correct", "correct", "correct", "wrong", "unanswered"), result.squares)
            assertEquals(2, result.review.size)
            val progress = assertNotNull(result.progress)
            assertEquals(50, progress.xpGained, "3 × 10 + 20 bonus")
            assertEquals(1, progress.streakCurrent)
            assertTrue(progress.streakExtendedToday)
            assertEquals(3, progress.xpBreakdown.count { it.reason == "correct_answers" })
            assertEquals(1, result.streakCurrent)

            assertEquals(result, client.finish(token, attempt.id).body<DailyResultResponse>(), "finish is idempotent")
            client.start(token).let {
                assertEquals(HttpStatusCode.Conflict, it.status)
                val body = it.body<DailyAlreadyPlayedResponse>()
                assertEquals("daily_already_played", body.code)
                assertEquals(result, body.result)
            }
            val status = client.status(token)
            assertEquals(DailyStatus.FINISHED, status.status)
            assertEquals(DailyResultSummary(3, 5, result.squares), status.result)
            assertEquals(1, status.streakCurrent)
        }
    }

    @Test
    fun `answers after the deadline are rejected and the attempt still awards XP`() {
        val env = TestEnv(db)
        runBlocking { seedAllCategories(env) }
        val token = env.verifier.tokenFor("u1")
        val other = env.verifier.tokenFor("u2")
        withTestApp(env) { client ->
            val attempt = client.start(token).body<DailyAttemptResponse>()
            val q = attempt.questions
            client.answer(token, attempt.id, q[0].id, db.correctOptionOf(q[0].id))
            assertEquals(HttpStatusCode.NotFound, client.answer(other, attempt.id, q[1].id, null).status)
            env.clock.advanceMs(137_501)
            client.answer(token, attempt.id, q[1].id, null).let {
                assertEquals(HttpStatusCode.Conflict, it.status)
                assertEquals("session_expired", it.body<ErrorResponse>().code)
            }
            assertEquals("session_closed", client.answer(token, attempt.id, q[1].id, null).body<ErrorResponse>().code)
            val result = client.finish(token, attempt.id).body<DailyResultResponse>()
            assertEquals("expired", result.status)
            assertEquals(30, result.progress!!.xpGained)
            assertEquals(listOf("correct", "unanswered", "unanswered", "unanswered", "unanswered"), result.squares)
        }
    }

    @Test
    fun `an attempt without answers that expires gives no XP and no active day`() {
        val env = TestEnv(db)
        runBlocking { seedAllCategories(env) }
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            val attempt = client.start(token).body<DailyAttemptResponse>()
            env.clock.advanceMs(200_000)
            assertEquals(1, env.daily.expireOverdueAttempts())
            val result = client.finish(token, attempt.id).body<DailyResultResponse>()
            assertEquals(0, result.progress!!.xpGained)
            assertEquals(0, result.progress!!.streakCurrent)
        }
    }

    @Test
    fun `fewer than five active questions means no challenge`() {
        val env = TestEnv(db)
        runBlocking { TestContent.seed(env, count = 4) }
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            val status = client.status(token)
            assertEquals(DailyStatus.UNAVAILABLE, status.status)
            assertNull(status.number)
            client.start(token).let {
                assertEquals(HttpStatusCode.Conflict, it.status)
                assertEquals("daily_unavailable", it.body<ErrorResponse>().code)
            }
        }
    }

    @Test
    fun `an attempt started before midnight Baku counts for its day, the new day starts at once`() {
        val env = TestEnv(db)
        runBlocking { seedAllCategories(env) }
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            // 23:59:59 Baku on 2026-10-16.
            env.clock.setTo(Instant.parse("2026-10-16T19:59:59Z"))
            val attempt = client.start(token).body<DailyAttemptResponse>()
            assertEquals(16, attempt.number)
            env.clock.advanceMs(2_000) // 00:00:01 Baku, 2026-10-17
            val q = attempt.questions
            assertEquals(HttpStatusCode.OK, client.answer(token, attempt.id, q[0].id, db.correctOptionOf(q[0].id)).status)
            val result = client.finish(token, attempt.id).body<DailyResultResponse>()
            assertEquals("2026-10-16", result.progress!!.dayKey)

            val today = client.status(token)
            assertEquals("2026-10-17", today.dayKey)
            assertEquals(17, today.number)
            assertEquals(DailyStatus.AVAILABLE, today.status)
            val next = client.start(token).body<DailyAttemptResponse>()
            assertTrue(next.questions.none { n -> q.any { it.id == n.id } }, "not reused within 60 days")
            // Streak: the 16th and the 17th are both active after finishing the new one.
            next.questions.forEach { client.answer(token, next.id, it.id, null) }
            assertEquals(2, client.finish(token, next.id).body<DailyResultResponse>().progress!!.streakCurrent)
        }
    }

    @Test
    fun `the daily never changes the Elo rating`() {
        val env = TestEnv(db)
        runBlocking { seedAllCategories(env) }
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            val attempt = client.start(token).body<DailyAttemptResponse>()
            attempt.questions.forEach { client.answer(token, attempt.id, it.id, db.correctOptionOf(it.id)) }
            assertEquals(70, client.finish(token, attempt.id).body<DailyResultResponse>().progress!!.xpGained)
            val ratings = runBlocking { db.tx { UsersTable.selectAll().map { it[UsersTable.rating] } } }
            assertEquals(listOf(1000), ratings)
        }
    }
}

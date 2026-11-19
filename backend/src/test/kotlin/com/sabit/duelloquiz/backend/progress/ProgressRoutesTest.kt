package com.sabit.duelloquiz.backend.progress

import com.sabit.duelloquiz.backend.daily.DailyAttemptResponse
import com.sabit.duelloquiz.backend.notification.NotificationSettingsDto
import com.sabit.duelloquiz.backend.notification.PushTokenRequest
import com.sabit.duelloquiz.backend.quiz.QuizResultResponse
import com.sabit.duelloquiz.backend.quiz.QuizSessionResponse
import com.sabit.duelloquiz.backend.quiz.StartSessionRequest
import com.sabit.duelloquiz.backend.quiz.SubmitAnswerRequest
import com.sabit.duelloquiz.backend.testsupport.DbTest
import com.sabit.duelloquiz.backend.testsupport.TestEnv
import com.sabit.duelloquiz.backend.testsupport.auth
import com.sabit.duelloquiz.backend.testsupport.authGet
import com.sabit.duelloquiz.backend.testsupport.correctOptionOf
import com.sabit.duelloquiz.backend.testsupport.emptyPost
import com.sabit.duelloquiz.backend.testsupport.jsonPost
import com.sabit.duelloquiz.backend.testsupport.seedAllCategories
import com.sabit.duelloquiz.backend.testsupport.testConfig
import com.sabit.duelloquiz.backend.testsupport.withTestApp
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgressRoutesTest : DbTest() {

    /** Plays a category quiz with [correct] correct answers (the rest wrong) and finishes it. */
    private suspend fun HttpClient.playQuiz(token: String, correct: Int, answered: Int = 10, categoryId: Int = 1): QuizResultResponse {
        val session = jsonPost("/v1/quiz/sessions", token, StartSessionRequest(categoryId)).body<QuizSessionResponse>()
        session.questions.take(answered).forEachIndexed { i, q ->
            val option = if (i < correct) db.correctOptionOf(q.id) else q.options.first { it.id != db.correctOptionOf(q.id) }.id
            jsonPost("/v1/quiz/sessions/${session.id}/answers", token, SubmitAnswerRequest(q.id, option, 1000))
        }
        return emptyPost("/v1/quiz/sessions/${session.id}/finish", token).body()
    }

    private suspend fun HttpClient.progress(token: String) = authGet("/v1/me/progress", token).body<ProgressResponse>()

    @Test
    fun `a new user starts at level 1 with every playable category listed`() {
        val env = TestEnv(db)
        runBlocking { seedAllCategories(env) }
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            val p = client.progress(token)
            assertEquals(0, p.xpTotal)
            assertEquals(1, p.level)
            assertEquals(100, p.xpForNextLevel)
            assertEquals(0, p.streakCurrent)
            assertFalse(p.todayActive)
            assertNull(p.streakEvent)
            assertEquals(listOf("cografiya", "tarix", "biologiya", "fizika", "idman"), p.categories.map { it.slug })
            assertTrue(p.categories.all { it.level == 1 && it.xp == 0L })
            assertEquals("2026-10-16", p.dayKey)
        }
    }

    @Test
    fun `quiz finish awards XP once and progress reflects it`() {
        val env = TestEnv(db)
        runBlocking { seedAllCategories(env) }
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            val result = client.playQuiz(token, correct = 7)
            val progress = assertNotNull(result.progress)
            assertEquals(70, progress.xpGained)
            assertFalse(progress.xpCapped)
            assertEquals(1, progress.levelBefore)
            assertEquals(1, progress.levelAfter)
            assertTrue(progress.streakExtendedToday)
            assertEquals(1, progress.streakCurrent)

            val second = client.playQuiz(token, correct = 5)
            assertEquals(2, second.progress!!.levelAfter, "120 XP = level 2")
            assertFalse(second.progress!!.streakExtendedToday)
            assertEquals(listOf(CategoryLevelUp(1, 1, 2)), second.progress!!.categoryLevelUps)

            val p = client.progress(token)
            assertEquals(120, p.xpTotal)
            assertEquals(2, p.level)
            assertEquals(20, p.xpIntoLevel)
            assertTrue(p.todayActive)
            assertEquals(120, p.categories.first { it.categoryId == 1 }.xp)
            assertEquals(0, p.categories.first { it.categoryId == 2 }.xp)
        }
    }

    @Test
    fun `retried finish returns the same progress and awards nothing twice`() {
        val env = TestEnv(db)
        runBlocking { seedAllCategories(env) }
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            val session = client.jsonPost("/v1/quiz/sessions", token, StartSessionRequest(1)).body<QuizSessionResponse>()
            val q = session.questions[0]
            client.jsonPost("/v1/quiz/sessions/${session.id}/answers", token, SubmitAnswerRequest(q.id, db.correctOptionOf(q.id), 1))
            val first = client.emptyPost("/v1/quiz/sessions/${session.id}/finish", token).body<QuizResultResponse>()
            val again = client.emptyPost("/v1/quiz/sessions/${session.id}/finish", token).body<QuizResultResponse>()
            assertEquals(first, again)
            assertEquals(10, client.progress(token).xpTotal)
        }
    }

    @Test
    fun `a quiz without answers gives no XP and no active day`() {
        val env = TestEnv(db)
        runBlocking { seedAllCategories(env) }
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            val empty = client.playQuiz(token, correct = 0, answered = 0)
            assertEquals(0, empty.progress!!.xpGained)
            assertEquals(0, empty.progress!!.streakCurrent)
            assertFalse(client.progress(token).todayActive)
        }
    }

    @Test
    fun `quiz XP is capped per day but the game still counts`() {
        val config = testConfig(progress = ProgressConfig(quizSessionsPerDay = 2))
        val env = TestEnv(db, config = config)
        runBlocking { seedAllCategories(env) }
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            assertEquals(10, client.playQuiz(token, correct = 1).progress!!.xpGained)
            assertEquals(10, client.playQuiz(token, correct = 1).progress!!.xpGained)
            val capped = client.playQuiz(token, correct = 3).progress!!
            assertEquals(0, capped.xpGained)
            assertTrue(capped.xpCapped)
            assertEquals(20, client.progress(token).xpTotal)
            // The next Baku day the cap starts again.
            env.clock.advanceMs(24 * 3_600_000L)
            assertEquals(30, client.playQuiz(token, correct = 3).progress!!.xpGained)
        }
    }

    @Test
    fun `streak over days, lost after a missed day with a one-time event`() {
        val env = TestEnv(db)
        runBlocking { seedAllCategories(env) }
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            client.playQuiz(token, correct = 1)
            env.clock.advanceMs(24 * 3_600_000L)
            assertEquals(1, client.progress(token).streakCurrent)
            assertFalse(client.progress(token).todayActive)
            assertEquals(2, client.playQuiz(token, correct = 1).progress!!.streakCurrent)
            env.clock.advanceMs(2 * 24 * 3_600_000L) // 2026-10-19, the 18th was missed
            val lost = client.progress(token)
            assertEquals(0, lost.streakCurrent)
            assertEquals(2, lost.streakBest)
            assertEquals(StreakEventDto("streak_lost", "2026-10-18", 2), lost.streakEvent)
        }
    }

    private val day = 24 * 3_600_000L

    private fun weekOf(p: ProgressResponse) = p.week.map { it.state }

    @Test
    fun `week row has 7 days from Monday with today pending for a new user`() {
        val env = TestEnv(db)
        runBlocking { seedAllCategories(env) }
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            // Fixture clock: Friday 2026-10-16, 14:00 Baku.
            val p = client.progress(token)
            assertEquals(
                listOf("2026-10-12", "2026-10-13", "2026-10-14", "2026-10-15", "2026-10-16", "2026-10-17", "2026-10-18"),
                p.week.map { it.dayKey },
            )
            assertEquals(listOf("none", "none", "none", "none", "today_pending", "none", "none"), weekOf(p))
        }
    }

    @Test
    fun `after playing today is active and earlier days stay none`() {
        val env = TestEnv(db)
        runBlocking { seedAllCategories(env) }
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            client.playQuiz(token, correct = 1)
            assertEquals(listOf("none", "none", "none", "none", "active", "none", "none"), weekOf(client.progress(token)))
            // Saturday: Friday active, Saturday pending; Sunday after a missed Saturday: missed.
            env.clock.advanceMs(day)
            assertEquals(listOf("none", "none", "none", "none", "active", "today_pending", "none"), weekOf(client.progress(token)))
            env.clock.advanceMs(day)
            assertEquals(listOf("none", "none", "none", "none", "active", "missed", "today_pending"), weekOf(client.progress(token)))
            // Next Monday: a new week; everything before Monday is outside the row.
            env.clock.advanceMs(day)
            val monday = client.progress(token)
            assertEquals("2026-10-19", monday.week.first().dayKey)
            assertEquals(listOf("today_pending", "none", "none", "none", "none", "none", "none"), weekOf(monday))
        }
    }

    @Test
    fun `an earned and used freeze shows as freeze and is stored`() {
        val env = TestEnv(db)
        runBlocking { seedAllCategories(env) }
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            // Friday 2026-10-16 … Thursday 2026-10-22: 7 days earn a freeze.
            repeat(7) { i ->
                if (i > 0) env.clock.advanceMs(day)
                client.playQuiz(token, correct = 1)
            }
            assertTrue(client.progress(token).freezeAvailable)
            // Friday 2026-10-23 missed; Saturday before playing the held freeze covers it.
            env.clock.advanceMs(2 * day)
            val pending = client.progress(token)
            assertEquals("2026-10-19", pending.week.first().dayKey)
            assertEquals(listOf("active", "active", "active", "active", "freeze", "today_pending", "none"), weekOf(pending))
            assertEquals(0, runBlocking { db.tx { count("user_freeze_days") } }, "not consumed before playing")
            // Playing on Saturday consumes it and stores the covered day.
            client.playQuiz(token, correct = 1)
            val used = client.progress(token)
            assertEquals(listOf("active", "active", "active", "active", "freeze", "active", "none"), weekOf(used))
            assertEquals(8, used.streakCurrent)
            assertEquals(
                listOf("2026-10-23"),
                runBlocking { db.tx { exec("SELECT day::text FROM user_freeze_days") { rs -> buildList { while (rs.next()) add(rs.getString(1)) } } } },
            )
            // The stored day still shows as freeze on later days.
            env.clock.advanceMs(day)
            assertEquals(listOf("active", "active", "active", "active", "freeze", "active", "today_pending"), weekOf(client.progress(token)))
            // Account deletion removes the freeze rows.
            assertEquals(HttpStatusCode.NoContent, client.delete("/v1/me") { auth(token) }.status)
            assertEquals(0, runBlocking { db.tx { count("user_freeze_days") } })
        }
    }

    @Test
    fun `account deletion removes every retention row of the user`() {
        val env = TestEnv(db)
        runBlocking { seedAllCategories(env) }
        val token = env.verifier.tokenFor("del")
        withTestApp(env) { client ->
            client.playQuiz(token, correct = 2)
            val attempt = client.emptyPost("/v1/daily/attempt", token).body<DailyAttemptResponse>()
            client.jsonPost("/v1/daily/attempt/${attempt.id}/answers", token, SubmitAnswerRequest(attempt.questions[0].id, null, 1))
            client.emptyPost("/v1/daily/attempt/${attempt.id}/finish", token)
            client.put("/v1/me/push-tokens") {
                auth(token)
                contentType(ContentType.Application.Json)
                setBody(PushTokenRequest("device-token-1", "az"))
            }
            client.put("/v1/me/notification-settings") {
                auth(token)
                contentType(ContentType.Application.Json)
                setBody(NotificationSettingsDto(dailyReminder = false, streakReminder = true))
            }
            runBlocking { db.tx { exec("INSERT INTO notification_log (user_id, type, day, outcome) SELECT id, 'daily_reminder', DATE '2026-10-16', 'sent' FROM users") } }
            runBlocking { db.tx { exec("INSERT INTO user_freeze_days (user_id, day) SELECT id, DATE '2026-10-15' FROM users") } }
            val tables = listOf(
                "daily_attempts", "user_progress", "user_category_xp", "user_active_days", "user_freeze_days", "progress_awards", "xp_events",
                "push_tokens", "notification_settings", "notification_log",
            )
            tables.forEach { assertTrue(runBlocking { db.tx { count(it) } } > 0, "$it has rows before deletion") }
            assertEquals(HttpStatusCode.NoContent, client.delete("/v1/me") { auth(token) }.status)
            tables.forEach { assertEquals(0, runBlocking { db.tx { count(it) } }, "$it still has rows") }
            assertEquals(0, runBlocking { db.tx { count("daily_answers") } })
            assertEquals(1, runBlocking { db.tx { count("daily_challenges") } }, "the day's set is not personal data")
        }
    }

    private fun JdbcTransaction.count(table: String): Int =
        exec("SELECT count(*) FROM $table") { rs -> rs.next(); rs.getInt(1) } ?: 0
}

package com.sabit.duelloquiz.backend.notification

import com.sabit.duelloquiz.backend.common.ErrorResponse
import com.sabit.duelloquiz.backend.common.db.PushTokensTable
import com.sabit.duelloquiz.backend.quiz.QuizSessionResponse
import com.sabit.duelloquiz.backend.quiz.StartSessionRequest
import com.sabit.duelloquiz.backend.quiz.SubmitAnswerRequest
import com.sabit.duelloquiz.backend.testsupport.DbTest
import com.sabit.duelloquiz.backend.testsupport.TestEnv
import com.sabit.duelloquiz.backend.testsupport.auth
import com.sabit.duelloquiz.backend.testsupport.authGet
import com.sabit.duelloquiz.backend.testsupport.emptyPost
import com.sabit.duelloquiz.backend.testsupport.jsonPost
import com.sabit.duelloquiz.backend.testsupport.seedAllCategories
import com.sabit.duelloquiz.backend.testsupport.setTo
import com.sabit.duelloquiz.backend.testsupport.withTestApp
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationRoutesTest : DbTest() {

    private suspend fun HttpClient.register(token: String, device: String, locale: String? = "az") =
        put("/v1/me/push-tokens") {
            auth(token)
            contentType(ContentType.Application.Json)
            setBody(PushTokenRequest(device, locale))
        }

    private suspend fun HttpClient.putSettings(token: String, daily: Boolean, streak: Boolean) =
        put("/v1/me/notification-settings") {
            auth(token)
            contentType(ContentType.Application.Json)
            setBody(NotificationSettingsDto(daily, streak))
        }

    private fun tokens(): List<Pair<String, String>> = runBlocking {
        db.tx { PushTokensTable.selectAll().map { it[PushTokensTable.token] to it[PushTokensTable.locale] } }
    }

    @Test
    fun `tokens are upserted per device, moved between accounts and deleted`() {
        val env = TestEnv(db)
        val alice = env.verifier.tokenFor("alice")
        val bob = env.verifier.tokenFor("bob")
        withTestApp(env) { client ->
            assertEquals(HttpStatusCode.NoContent, client.register(alice, "fcm:abc-1").status)
            assertEquals(HttpStatusCode.NoContent, client.register(alice, "fcm:abc-1", "az-AZ").status)
            assertEquals(listOf("fcm:abc-1" to "az-az"), tokens())
            // The same device signs in as Bob: the token moves, it is never kept for both.
            client.register(bob, "fcm:abc-1")
            assertEquals(1, tokens().size)
            // Alice cannot delete Bob's token.
            client.delete("/v1/me/push-tokens/${"fcm:abc-1".encodeURLPathPart()}") { auth(alice) }
            assertEquals(1, tokens().size)
            assertEquals(HttpStatusCode.NoContent, client.delete("/v1/me/push-tokens/${"fcm:abc-1".encodeURLPathPart()}") { auth(bob) }.status)
            assertEquals(0, tokens().size)
            // Idempotent.
            assertEquals(HttpStatusCode.NoContent, client.delete("/v1/me/push-tokens/unknown") { auth(bob) }.status)

            client.register(alice, "bad token").let {
                assertEquals(HttpStatusCode.BadRequest, it.status)
                assertEquals("invalid_push_token", it.body<ErrorResponse>().code)
            }
            assertEquals("invalid_push_token", client.register(alice, "").body<ErrorResponse>().code)
            assertEquals("validation_failed", client.register(alice, "ok", "not a locale").body<ErrorResponse>().code)
        }
    }

    @Test
    fun `switches default to on and are stored per account`() {
        val env = TestEnv(db)
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            assertEquals(NotificationSettingsDto(true, true), client.authGet("/v1/me/notification-settings", token).body())
            assertEquals(NotificationSettingsDto(false, true), client.putSettings(token, daily = false, streak = true).body())
            assertEquals(NotificationSettingsDto(false, true), client.authGet("/v1/me/notification-settings", token).body())
        }
    }

    @Test
    fun `daily reminder at 10 00 Baku, once, only to players who have not started`() {
        val env = TestEnv(db)
        runBlocking { seedAllCategories(env) }
        val alice = env.verifier.tokenFor("alice")
        val bob = env.verifier.tokenFor("bob")
        val carol = env.verifier.tokenFor("carol")
        withTestApp(env) { client ->
            client.register(alice, "device-alice")
            client.register(bob, "device-bob")
            client.register(carol, "device-carol")
            client.putSettings(carol, daily = false, streak = true)
            // 2026-10-17 09:30 Baku: nothing is due yet.
            env.clock.setTo(Instant.parse("2026-10-17T05:30:00Z"))
            assertTrue(env.notifications.runDue().isEmpty())
            env.clock.setTo(Instant.parse("2026-10-17T06:00:00Z"))
            client.emptyPost("/v1/daily/attempt", bob)
            env.notifications.runDue()
            assertEquals(listOf("device-alice"), env.push.sent.map { it.token })
            val message = env.push.sent.single()
            assertEquals("Gündəlik çağırış #17 hazırdır", message.title)
            assertEquals(mapOf("type" to "daily_reminder", "dayKey" to "2026-10-17"), message.data)
            // A second run (restart, next tick) sends nothing again.
            env.notifications.runJob(NotificationType.DAILY_REMINDER)
            env.clock.advanceMs(60_000)
            env.notifications.runDue()
            assertEquals(1, env.push.sent.size)
        }
    }

    @Test
    fun `streak reminder at 20 00 for a streak of 2 not yet extended today, invalid tokens are removed`() {
        val env = TestEnv(db)
        runBlocking { seedAllCategories(env) }
        val alice = env.verifier.tokenFor("alice")
        withTestApp(env) { client ->
            suspend fun playQuiz() {
                val s = client.jsonPost("/v1/quiz/sessions", alice, StartSessionRequest(1)).body<QuizSessionResponse>()
                client.jsonPost("/v1/quiz/sessions/${s.id}/answers", alice, SubmitAnswerRequest(s.questions[0].id, null, 1))
                client.emptyPost("/v1/quiz/sessions/${s.id}/finish", alice)
            }
            playQuiz() // 2026-10-16
            env.clock.setTo(Instant.parse("2026-10-17T10:00:00Z"))
            playQuiz() // 2026-10-17, streak 2
            client.register(alice, "device-old")
            client.register(alice, "device-new")
            env.push.invalid += "device-old"
            // 2026-10-18 20:00 Baku, not active today.
            env.clock.setTo(Instant.parse("2026-10-18T16:00:00Z"))
            val report = env.notifications.runJob(NotificationType.STREAK_REMINDER)
            assertEquals(1, report.sent)
            assertEquals(1, report.invalidTokens)
            assertEquals("2 günlük seriyanı qoru", env.push.sent.single().title)
            assertEquals(listOf("device-new"), tokens().map { it.first })
            assertEquals(0, env.push.sent.count { it.data["type"] == "daily_reminder" })
        }
    }

    @Test
    fun `nothing is sent to users inactive for more than 7 days`() {
        val env = TestEnv(db)
        runBlocking { seedAllCategories(env) }
        val alice = env.verifier.tokenFor("alice")
        withTestApp(env) { client ->
            client.register(alice, "device-alice") // account created 2026-10-16, never played
            env.clock.setTo(Instant.parse("2026-10-25T06:00:00Z"))
            val report = env.notifications.runJob(NotificationType.DAILY_REMINDER)
            assertEquals(0, report.sent)
            assertEquals(1, report.skipped["inactive"])
        }
    }
}

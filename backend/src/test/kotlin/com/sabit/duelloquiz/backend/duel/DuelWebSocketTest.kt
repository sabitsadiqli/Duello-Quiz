package com.sabit.duelloquiz.backend.duel

import com.sabit.duelloquiz.backend.common.db.OptionsTable
import com.sabit.duelloquiz.backend.testsupport.DbTest
import com.sabit.duelloquiz.backend.testsupport.TestContent
import com.sabit.duelloquiz.backend.testsupport.TestEnv
import com.sabit.duelloquiz.backend.testsupport.auth
import com.sabit.duelloquiz.backend.testsupport.testConfig
import com.sabit.duelloquiz.backend.testsupport.withTestApp
import com.sabit.duelloquiz.backend.user.CalibrationLevel
import com.sabit.duelloquiz.backend.user.MatchHistoryResponse
import com.sabit.duelloquiz.backend.user.ProfileResponse
import com.sabit.duelloquiz.backend.user.Record
import com.sabit.duelloquiz.backend.user.StatsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** GET /v1/duel end to end: two fake clients play a whole match against the real server and database. */
class DuelWebSocketTest : DbTest() {

    private val fastDuel = DuelConfig(
        introMs = 100, questionMs = 50, confidenceMs = 5_000, answerMs = 5_000, resultMs = 50, queueTimeoutMs = 20_000,
    )

    private val JsonObject.type get() = getValue("type").jsonPrimitive.content

    private fun correctOptionIds(): Set<String> = runBlocking {
        db.tx { OptionsTable.select(OptionsTable.id).where { OptionsTable.isCorrect eq true }.map { it[OptionsTable.id].toString() }.toSet() }
    }

    /** Joins the queue and plays until `match.result`: picks [bet], answers right or wrong. */
    private suspend fun HttpClient.play(token: String, bet: Int, right: Boolean, correct: Set<String>): List<JsonObject> {
        val received = mutableListOf<JsonObject>()
        webSocket("/v1/duel?protocolVersion=1", request = { bearerAuth(token) }) {
            send(Frame.Text("""{"type":"queue.join"}"""))
            var matchId = ""
            while (true) {
                val frame = incoming.receive() as? Frame.Text ?: continue
                val m = DuelJson.parseToJsonElement(frame.readText()).jsonObject
                received += m
                when (m.type) {
                    "match.found" -> matchId = m.getValue("matchId").jsonPrimitive.content
                    "round.confidence" -> {
                        val roundNo = m.getValue("roundNo").jsonPrimitive.int
                        send(Frame.Text("""{"type":"round.bet","matchId":"$matchId","roundNo":$roundNo,"bet":$bet}"""))
                    }
                    "round.options" -> {
                        val roundNo = m.getValue("roundNo").jsonPrimitive.int
                        val ids = DuelJson.decodeFromJsonElement(ServerMessage.RoundOptions.serializer(), m).options.map { it.id }
                        val pick = if (right) ids.first { it in correct } else ids.first { it !in correct }
                        send(Frame.Text("""{"type":"round.answer","matchId":"$matchId","roundNo":$roundNo,"optionId":"$pick"}"""))
                    }
                    "match.result" -> break
                }
            }
        }
        return received
    }

    @Test
    fun `two clients play a full match and the profile shows it`() {
        val env = TestEnv(db, config = testConfig(duel = fastDuel))
        runBlocking { TestContent.seed(env, count = 12) }
        val aliceToken = env.verifier.tokenFor("ws-alice", name = "Alice")
        val bobToken = env.verifier.tokenFor("ws-bob", name = "Bob")
        val correct = correctOptionIds()
        withTestApp(env) { client ->
            // Profiles exist before the match (the handshake would create them too).
            client.get("/v1/me") { auth(aliceToken) }
            client.get("/v1/me") { auth(bobToken) }
            val ws = createClient { install(WebSockets) }
            val (alice, bob) = coroutineScope {
                val a = async { ws.play(aliceToken, bet = 2, right = true, correct = correct) }
                val b = async { ws.play(bobToken, bet = 1, right = false, correct = correct) }
                a.await() to b.await()
            }

            // Envelope: hello first, every message has seq and serverNowEpochMs, the stream is contiguous.
            assertEquals("hello", alice.first().type)
            assertTrue(alice.all { "seq" in it && "serverNowEpochMs" in it })
            val seqs = alice.map { it.getValue("seq").jsonPrimitive.long }.filter { it > 0 }
            assertEquals((1L..seqs.size).toList(), seqs)
            // No correctness before the first reveal.
            val firstResult = alice.indexOfFirst { it.type == "round.result" }
            assertTrue(alice.take(firstResult).none { it.toString().contains("orrect") })
            assertEquals(7, alice.count { it.type == "round.result" })

            val result = DuelJson.decodeFromJsonElement(ServerMessage.MatchResult.serializer(), alice.last())
            assertEquals("win", result.outcome)
            assertEquals(EndReasons.NORMAL, result.endReason)
            assertEquals(1016, result.ratingAfter)
            assertEquals(CalibrationLevel(2, 7, 7), result.calibration[1])
            assertTrue(result.scores.you >= 6 * 200 + 400)
            assertEquals("loss", DuelJson.decodeFromJsonElement(ServerMessage.MatchResult.serializer(), bob.last()).outcome)

            // REST reads the persisted match.
            assertEquals(1016, client.get("/v1/me") { auth(aliceToken) }.body<ProfileResponse>().rating)
            assertEquals(984, client.get("/v1/me") { auth(bobToken) }.body<ProfileResponse>().rating)
            val stats = client.get("/v1/me/stats") { auth(aliceToken) }.body<StatsResponse>()
            assertEquals(Record(1, 0, 0, 1), stats.record)
            assertEquals(listOf(CalibrationLevel(1, 0, 0), CalibrationLevel(2, 7, 7), CalibrationLevel(3, 0, 0)), stats.calibration)
            val bobStats = client.get("/v1/me/stats") { auth(bobToken) }.body<StatsResponse>()
            assertEquals(Record(0, 1, 0, 1), bobStats.record)
            assertEquals(CalibrationLevel(1, 7, 0), bobStats.calibration[0])
            val history = client.get("/v1/me/matches") { auth(aliceToken) }.body<MatchHistoryResponse>().items.single()
            assertEquals("win", history.result)
            assertEquals("normal", history.endReason)
            assertEquals(16, history.ratingChange)
            assertEquals("Bob", history.opponent.displayName)
            assertEquals(result.scores.you to result.scores.opponent, history.scoreOwn to history.scoreOpponent)
        }
    }

    @Test
    fun `handshake requires a token and a supported protocol version`() {
        val env = TestEnv(db, config = testConfig(duel = fastDuel))
        val token = env.verifier.tokenFor("ws-carol", name = "Carol")
        withTestApp(env) { client ->
            val ws = createClient { install(WebSockets) }
            // An upgrade request without a token is refused before the upgrade (401 from `authenticate`).
            assertFailsWith<Exception> { ws.webSocket("/v1/duel?protocolVersion=1") { } }

            ws.webSocket("/v1/duel", request = { bearerAuth(token) }) {
                val error = DuelJson.parseToJsonElement((incoming.receive() as Frame.Text).readText()).jsonObject
                assertEquals("error", error.type)
                assertEquals(DuelErrorCodes.UPGRADE_REQUIRED, error.getValue("code").jsonPrimitive.content)
                assertEquals(1, error.getValue("minProtocolVersion").jsonPrimitive.int)
                assertEquals(DuelCloseCodes.UPGRADE_REQUIRED, closeReason.await()?.code)
            }
        }
    }

    @Test
    fun `bad and unknown messages keep the connection, duel unavailable without questions`() {
        val env = TestEnv(db, config = testConfig(duel = fastDuel))
        val token = env.verifier.tokenFor("ws-dave", name = "Dave")
        withTestApp(env) { _ ->
            val ws = createClient { install(WebSockets) }
            ws.webSocket("/v1/duel?protocolVersion=1", request = { bearerAuth(token) }) {
                suspend fun next() = DuelJson.parseToJsonElement((incoming.receive() as Frame.Text).readText()).jsonObject
                assertEquals("hello", next().type)
                send(Frame.Text("{not json"))
                assertEquals(DuelErrorCodes.BAD_MESSAGE, next().getValue("code").jsonPrimitive.content)
                send(Frame.Text("""{"type":"round.bet","roundNo":1}"""))
                val missing = next()
                assertEquals(DuelErrorCodes.BAD_MESSAGE, missing.getValue("code").jsonPrimitive.content)
                assertEquals("round.bet", missing.getValue("ref").jsonPrimitive.content)
                send(Frame.Text("""{"type":"emoji.send","x":1}"""))
                send(Frame.Text("""{"type":"ping"}"""))
                assertEquals("pong", next().type, "unknown types are ignored")
                send(Frame.Text("""{"type":"queue.join"}"""))
                assertEquals(DuelErrorCodes.DUEL_UNAVAILABLE, next().getValue("code").jsonPrimitive.content)
            }
        }
    }
}

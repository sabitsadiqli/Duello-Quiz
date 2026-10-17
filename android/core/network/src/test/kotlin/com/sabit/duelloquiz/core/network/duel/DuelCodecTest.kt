package com.sabit.duelloquiz.core.network.duel

import com.sabit.duelloquiz.core.model.QuizOption
import com.sabit.duelloquiz.core.model.duel.DuelClientMessage
import com.sabit.duelloquiz.core.model.duel.DuelErrorCode
import com.sabit.duelloquiz.core.model.duel.DuelPhase
import com.sabit.duelloquiz.core.model.duel.DuelPlayer
import com.sabit.duelloquiz.core.model.duel.DuelScores
import com.sabit.duelloquiz.core.model.duel.DuelServerMessage
import com.sabit.duelloquiz.core.model.duel.DuelSide
import com.sabit.duelloquiz.core.model.duel.MatchEndReason
import com.sabit.duelloquiz.core.model.duel.MatchOutcome
import com.sabit.duelloquiz.core.model.duel.OwnBet
import com.sabit.duelloquiz.core.model.duel.RoundNext
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every JSON example of docs/api/duel-protocol.md §4 and §5, decoded and encoded. */
class DuelCodecTest {

    private inline fun <reified T : DuelServerMessage> decode(json: String): T {
        val message = DuelCodec.decode(json)
        assertTrue("decoded $message", message is T)
        return message as T
    }

    private fun assertJson(expected: String, actual: String) {
        assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(actual))
    }

    // Server → client (§5)

    @Test
    fun hello() {
        val hello = decode<DuelServerMessage.Hello>(
            """{"type": "hello", "seq": 0, "serverNowEpochMs": 1792144800000,
             "protocolVersion": 1, "heartbeatIntervalMs": 5000, "activeMatchId": null}""",
        )
        assertEquals(0L, hello.seq)
        assertEquals(1_792_144_800_000L, hello.serverNowEpochMs)
        assertEquals(1, hello.protocolVersion)
        assertEquals(5_000L, hello.heartbeatIntervalMs)
        assertNull(hello.activeMatchId)
        assertFalse(hello.isStream)
    }

    @Test
    fun pongAndQueueMessages() {
        assertEquals(
            1_792_144_800_100L,
            decode<DuelServerMessage.Pong>("""{"type": "pong", "seq": 0, "serverNowEpochMs": 1792144800100}""").serverNowEpochMs,
        )
        assertEquals(
            1_792_144_830_200L,
            decode<DuelServerMessage.QueueJoined>(
                """{"type": "queue.joined", "seq": 0, "serverNowEpochMs": 1792144800200, "deadlineEpochMs": 1792144830200}""",
            ).deadlineEpochMs,
        )
        decode<DuelServerMessage.QueueTimeout>("""{"type": "queue.timeout", "seq": 0, "serverNowEpochMs": 1792144830200}""")
        decode<DuelServerMessage.QueueLeft>("""{"type": "queue.left", "seq": 0, "serverNowEpochMs": 1792144805000}""")
    }

    @Test
    fun matchFound() {
        val found = decode<DuelServerMessage.MatchFound>(
            """{"type": "match.found", "seq": 1, "serverNowEpochMs": 1792144801000,
             "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a",
             "you": {"displayName": "Leyla", "avatarUrl": null, "rating": 1000},
             "opponent": {"displayName": "Murad", "avatarUrl": "https://lh3.googleusercontent.com/a/x", "rating": 1016},
             "rounds": 7, "bet3Limit": 2, "deadlineEpochMs": 1792144804000}""",
        )
        assertEquals(1L, found.seq)
        assertTrue(found.isStream)
        assertEquals(MATCH_ID, found.matchId)
        assertEquals(DuelPlayer("Leyla", null, 1000), found.you)
        assertEquals(DuelPlayer("Murad", "https://lh3.googleusercontent.com/a/x", 1016), found.opponent)
        assertEquals(7, found.rounds)
        assertEquals(2, found.bet3Limit)
        assertEquals(1_792_144_804_000L, found.deadlineEpochMs)
    }

    @Test
    fun matchCancelled() {
        val cancelled = decode<DuelServerMessage.MatchCancelled>(
            """{"type": "match.cancelled", "seq": 2, "serverNowEpochMs": 1792144802000,
             "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a", "by": "opponent"}""",
        )
        assertEquals(DuelSide.OPPONENT, cancelled.by)
        assertEquals(MATCH_ID, cancelled.streamMatchId)
    }

    @Test
    fun roundQuestionHasNoOptions() {
        val question = decode<DuelServerMessage.RoundQuestion>(
            """{"type": "round.question", "seq": 2, "serverNowEpochMs": 1792144804000,
             "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a", "roundNo": 1,
             "allIn": false, "suddenDeath": false, "suddenDeathNo": null,
             "text": "Azərbaycanın ərazisindən axan ən uzun çay hansıdır?", "image": null,
             "categoryName": "Coğrafiya", "deadlineEpochMs": 1792144807000}""",
        )
        assertEquals(1, question.roundNo)
        assertFalse(question.allIn)
        assertFalse(question.suddenDeath)
        assertNull(question.suddenDeathNo)
        assertEquals("Azərbaycanın ərazisindən axan ən uzun çay hansıdır?", question.question.text)
        assertEquals("Coğrafiya", question.question.categoryName)
        assertNull(question.question.image)
        assertEquals(1_792_144_807_000L, question.deadlineEpochMs)
    }

    @Test
    fun roundQuestionWithImage() {
        val question = decode<DuelServerMessage.RoundQuestion>(
            """{"type": "round.question", "seq": 2, "serverNowEpochMs": 1, "matchId": "m", "roundNo": 7,
             "allIn": true, "suddenDeath": false, "suddenDeathNo": null, "text": "?",
             "image": {"url": "https://x/a.webp", "thumbUrl": "https://x/t.webp", "width": 1024, "height": 576,
                       "dominantColor": "#336699", "alt": "Xəritə"},
             "categoryName": "Tarix", "deadlineEpochMs": 2}""",
        )
        assertTrue(question.allIn)
        val image = question.question.image!!
        assertEquals("https://x/a.webp", image.url)
        assertEquals(0xFF336699L, image.dominantColorArgb)
        assertEquals("Xəritə", image.alt)
    }

    @Test
    fun roundConfidence() {
        val confidence = decode<DuelServerMessage.RoundConfidence>(
            """{"type": "round.confidence", "seq": 3, "serverNowEpochMs": 1792144807000,
             "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a", "roundNo": 1, "allIn": false,
             "bet3Left": 2, "deadlineEpochMs": 1792144812000}""",
        )
        assertEquals(2, confidence.bet3Left)
        assertEquals(1_792_144_812_000L, confidence.deadlineEpochMs)
    }

    @Test
    fun betLockedForSelfAndOpponent() {
        val self = decode<DuelServerMessage.BetLocked>(
            """{"type": "round.betLocked", "seq": 4, "serverNowEpochMs": 1792144808200,
             "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a", "roundNo": 1, "who": "self", "bet": 2}""",
        )
        assertEquals(DuelSide.SELF, self.who)
        assertEquals(2, self.bet)
        val opponent = decode<DuelServerMessage.BetLocked>(
            """{"type": "round.betLocked", "seq": 5, "serverNowEpochMs": 1, "matchId": "m", "roundNo": 1,
             "who": "opponent", "bet": null}""",
        )
        assertEquals(DuelSide.OPPONENT, opponent.who)
        assertNull(opponent.bet)
    }

    @Test
    fun roundOptions() {
        val options = decode<DuelServerMessage.RoundOptions>(
            """{"type": "round.options", "seq": 6, "serverNowEpochMs": 1792144810000,
             "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a", "roundNo": 1,
             "options": [
               {"id": "0b6a0f55-3c43-4f63-a0c1-5d0c2f7c9e11", "text": "Araz"},
               {"id": "5d1d2c1a-9b0e-4f0a-8d61-2b7e9d9f0a22", "text": "Kür"},
               {"id": "a4c0e3a2-7f6d-4b8e-9c3d-1e2f3a4b5c33", "text": "Samur"},
               {"id": "e9f8d7c6-b5a4-4392-8170-6f5e4d3c2b44", "text": "Qanıx"}],
             "bet": 2, "betDefaulted": false, "deadlineEpochMs": 1792144820000}""",
        )
        assertEquals(
            listOf("Araz", "Kür", "Samur", "Qanıx"),
            options.options.map(QuizOption::text),
        )
        assertEquals(2, options.bet)
        assertFalse(options.betDefaulted)
        assertEquals(1_792_144_820_000L, options.deadlineEpochMs)
    }

    @Test
    fun answerLocked() {
        val locked = decode<DuelServerMessage.AnswerLocked>(
            """{"type": "round.answerLocked", "seq": 7, "serverNowEpochMs": 1792144813800,
             "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a", "roundNo": 1, "who": "opponent", "optionId": null}""",
        )
        assertEquals(DuelSide.OPPONENT, locked.who)
        assertNull(locked.optionId)
    }

    @Test
    fun roundResult() {
        val message = decode<DuelServerMessage.RoundResultMessage>(ROUND_RESULT)
        val result = message.result
        assertEquals(8L, message.seq)
        assertEquals("5d1d2c1a-9b0e-4f0a-8d61-2b7e9d9f0a22", result.correctOptionId)
        assertEquals(231, result.you.points)
        assertEquals(31, result.you.speedBonus)
        assertEquals(3_800L, result.you.answeredMs)
        assertTrue(result.you.correct)
        assertTrue(result.opponent.missed)
        assertTrue(result.opponent.betDefaulted)
        assertNull(result.opponent.optionId)
        assertEquals(1, result.opponent.missedInARow)
        assertEquals(DuelScores(231, 0), result.scores)
        assertEquals(RoundNext.ROUND, result.next)
        assertNull(result.suddenDeathOutcome)
    }

    @Test
    fun suddenDeathOutcomeValues() {
        listOf("you", "opponent", "same_time", "none").forEach { outcome ->
            val json = ROUND_RESULT.replace("\"suddenDeathOutcome\": null", "\"suddenDeathOutcome\": \"$outcome\"")
            assertEquals(outcome, decode<DuelServerMessage.RoundResultMessage>(json).result.suddenDeathOutcome?.name?.lowercase())
        }
    }

    @Test
    fun opponentConnection() {
        val disconnected = decode<DuelServerMessage.OpponentConnection>(
            """{"type": "opponent.connection", "seq": 9, "serverNowEpochMs": 1792144814000,
             "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a", "state": "disconnected",
             "reconnectDeadlineEpochMs": 1792144824000}""",
        )
        assertFalse(disconnected.connected)
        assertEquals(1_792_144_824_000L, disconnected.reconnectDeadlineEpochMs)
    }

    @Test
    fun matchState() {
        val state = decode<DuelServerMessage.MatchState>(
            """{"type": "match.state", "seq": 14, "serverNowEpochMs": 1792144830000,
             "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a",
             "you": {"displayName": "Leyla", "avatarUrl": null, "rating": 1000},
             "opponent": {"displayName": "Murad", "avatarUrl": null, "rating": 1016, "connected": true},
             "phase": "answer", "deadlineEpochMs": 1792144838000,
             "roundNo": 2, "allIn": false, "suddenDeath": false, "suddenDeathNo": null,
             "question": {"text": "…", "image": null, "categoryName": "Tarix"},
             "options": [{"id": "…", "text": "…"}, {"id": "…", "text": "…"}, {"id": "…", "text": "…"}, {"id": "…", "text": "…"}],
             "ownBet": {"bet": 3, "betDefaulted": false}, "ownOptionId": null,
             "opponentBetLocked": true, "opponentAnswered": false,
             "bet3Left": 1, "scores": {"you": 231, "opponent": 0},
             "missedInARow": {"you": 0, "opponent": 1}, "lastResult": null,
             "rounds": [{"roundNo": 1, "suddenDeath": false, "you": {"bet": 2, "betDefaulted": false, "correct": true, "points": 231, "missed": false},
                         "opponent": {"bet": 1, "betDefaulted": true, "correct": false, "points": 0, "missed": true}}]}""",
        ).snapshot
        assertEquals(DuelPhase.ANSWER, state.phase)
        assertTrue(state.opponentConnected)
        assertEquals(2, state.roundNo)
        assertEquals("Tarix", state.question?.categoryName)
        assertEquals(4, state.options?.size)
        assertEquals(OwnBet(3, false), state.ownBet)
        assertNull(state.ownOptionId)
        assertTrue(state.opponentBetLocked)
        assertFalse(state.opponentAnswered)
        assertEquals(1, state.bet3Left)
        assertEquals(1, state.missedInARowOpponent)
        assertNull(state.lastResult)
        assertEquals(1, state.rounds.size)
        assertTrue(state.rounds.single().opponent.missed)
    }

    @Test
    fun matchStateInTheResultPhaseCarriesTheLastResult() {
        val body = ROUND_RESULT
            .replace("\"type\": \"round.result\", \"seq\": 8, \"serverNowEpochMs\": 1792144813900,", "")
        val state = decode<DuelServerMessage.MatchState>(
            """{"type": "match.state", "seq": 9, "serverNowEpochMs": 1, "matchId": "m",
             "you": {"displayName": "A", "avatarUrl": null, "rating": 1000},
             "opponent": {"displayName": "B", "avatarUrl": null, "rating": 1000, "connected": false},
             "phase": "result", "deadlineEpochMs": 2, "roundNo": 1, "allIn": false, "suddenDeath": false,
             "suddenDeathNo": null, "question": {"text": "?", "image": null, "categoryName": "Tarix"},
             "options": [], "ownBet": {"bet": 2, "betDefaulted": false}, "ownOptionId": "x",
             "opponentBetLocked": false, "opponentAnswered": false, "bet3Left": 2,
             "scores": {"you": 231, "opponent": 0}, "missedInARow": {"you": 0, "opponent": 1},
             "lastResult": $body, "rounds": []}""",
        ).snapshot
        assertFalse(state.opponentConnected)
        assertEquals(231, state.lastResult?.you?.points)
    }

    @Test
    fun matchResult() {
        val result = decode<DuelServerMessage.MatchResultMessage>(
            """{"type": "match.result", "seq": 43, "serverNowEpochMs": 1792144950000,
             "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a",
             "outcome": "win", "endReason": "normal", "forfeitedBy": null, "forfeitCause": null,
             "scores": {"you": 640, "opponent": 410},
             "ratingBefore": 1000, "ratingAfter": 1017, "ratingChange": 17, "opponentRatingAfter": 999,
             "suddenDeathPlayed": 0,
             "calibration": [{"level": 1, "picks": 2, "correct": 1}, {"level": 2, "picks": 3, "correct": 2},
                             {"level": 3, "picks": 2, "correct": 2}],
             "rounds": [{"roundNo": 1, "suddenDeath": false,
                         "you": {"bet": 2, "betDefaulted": false, "correct": true, "points": 231, "missed": false},
                         "opponent": {"bet": 1, "betDefaulted": true, "correct": false, "points": 0, "missed": true}}]}""",
        ).result
        assertEquals(MatchOutcome.WIN, result.outcome)
        assertEquals(MatchEndReason.NORMAL, result.endReason)
        assertNull(result.forfeitedBy)
        assertEquals(DuelScores(640, 410), result.scores)
        assertEquals(1017, result.ratingAfter)
        assertEquals(17, result.ratingChange)
        assertEquals(listOf(1, 2, 3), result.calibration.map { it.level })
        assertEquals(3, result.calibration[1].picks)
    }

    @Test
    fun error() {
        val error = decode<DuelServerMessage.Error>(
            """{"type": "error", "seq": 0, "serverNowEpochMs": 1792144812300, "code": "too_late",
             "message": "Received after the deadline", "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a",
             "roundNo": 1, "ref": "round.bet"}""",
        )
        assertEquals(DuelErrorCode.TOO_LATE, error.code)
        assertEquals("round.bet", error.ref)
        assertEquals(1, error.roundNo)
        assertEquals(
            DuelErrorCode.UPGRADE_REQUIRED,
            decode<DuelServerMessage.Error>(
                """{"type":"error","seq":0,"serverNowEpochMs":1,"code":"upgrade_required","message":"x","minProtocolVersion":2}""",
            ).code,
        )
    }

    @Test
    fun unknownTypesAndCodesAreKeptForForwardCompatibility() {
        val unknown = decode<DuelServerMessage.Unknown>("""{"type": "match.emoji", "seq": 5, "serverNowEpochMs": 7, "x": 1}""")
        assertEquals("match.emoji", unknown.type)
        assertEquals(5L, unknown.seq)
        assertEquals(
            DuelErrorCode.UNKNOWN,
            decode<DuelServerMessage.Error>("""{"type":"error","seq":0,"serverNowEpochMs":1,"code":"new_code"}""").code,
        )
        // Unknown fields are ignored.
        decode<DuelServerMessage.Pong>("""{"type": "pong", "seq": 0, "serverNowEpochMs": 1, "future": {"a": 1}}""")
    }

    @Test
    fun malformedFramesDecodeToNull() {
        assertNull(DuelCodec.decode("not json"))
        assertNull(DuelCodec.decode("[1, 2]"))
        assertNull(DuelCodec.decode("""{"seq": 0}"""))
        // Missing required field.
        assertNull(DuelCodec.decode("""{"type": "queue.joined", "seq": 0, "serverNowEpochMs": 1}"""))
        // Unknown enum value.
        assertNull(DuelCodec.decode("""{"type":"round.betLocked","seq":4,"serverNowEpochMs":1,"matchId":"m","roundNo":1,"who":"both"}"""))
    }

    // Client → server (§4)

    @Test
    fun clientMessagesMatchTheContract() {
        assertJson("""{"type": "ping"}""", DuelCodec.encode(DuelClientMessage.Ping))
        assertJson("""{"type": "queue.join"}""", DuelCodec.encode(DuelClientMessage.QueueJoin))
        assertJson("""{"type": "queue.leave"}""", DuelCodec.encode(DuelClientMessage.QueueLeave))
        assertJson(
            """{"type": "match.rejoin", "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a"}""",
            DuelCodec.encode(DuelClientMessage.MatchRejoin(MATCH_ID)),
        )
        assertJson(
            """{"type": "match.leave", "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a"}""",
            DuelCodec.encode(DuelClientMessage.MatchLeave(MATCH_ID)),
        )
        assertJson(
            """{"type": "round.bet", "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a", "roundNo": 3, "bet": 2}""",
            DuelCodec.encode(DuelClientMessage.RoundBet(MATCH_ID, 3, 2)),
        )
        assertJson(
            """{"type": "round.answer", "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a", "roundNo": 3,
             "optionId": "0b6a0f55-3c43-4f63-a0c1-5d0c2f7c9e11"}""",
            DuelCodec.encode(DuelClientMessage.RoundAnswer(MATCH_ID, 3, "0b6a0f55-3c43-4f63-a0c1-5d0c2f7c9e11")),
        )
    }

    @Test
    fun theTypeOfAClientMessageIsTheRefOfAServerError() {
        assertEquals("round.bet", DuelClientMessage.RoundBet(MATCH_ID, 1, 1).type)
        assertEquals("queue.leave", DuelClientMessage.QueueLeave.type)
    }

    @Test
    fun duelUrlUsesWebSocketSchemesAndTheProtocolVersion() {
        assertEquals("ws://10.0.2.2:8080/v1/duel?protocolVersion=1", KtorDuelSocketFactory.duelUrl("http://10.0.2.2:8080/"))
        assertEquals("wss://api.example.com/v1/duel?protocolVersion=1", KtorDuelSocketFactory.duelUrl("https://api.example.com/"))
    }

    private companion object {
        const val MATCH_ID = "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a"

        const val ROUND_RESULT = """{"type": "round.result", "seq": 8, "serverNowEpochMs": 1792144813900,
 "matchId": "6f1c2b9e-1a7d-4a3e-9a51-0d7f6c1e2b3a", "roundNo": 1,
 "allIn": false, "suddenDeath": false, "suddenDeathNo": null,
 "correctOptionId": "5d1d2c1a-9b0e-4f0a-8d61-2b7e9d9f0a22",
 "you": {"bet": 2, "betDefaulted": false, "optionId": "5d1d2c1a-9b0e-4f0a-8d61-2b7e9d9f0a22",
         "correct": true, "answeredMs": 3800, "speedBonus": 31, "points": 231,
         "missed": false, "missedInARow": 0},
 "opponent": {"bet": 1, "betDefaulted": true, "optionId": null, "correct": false,
              "answeredMs": null, "speedBonus": 0, "points": 0, "missed": true, "missedInARow": 1},
 "scores": {"you": 231, "opponent": 0},
 "bet3Left": 2, "next": "round", "deadlineEpochMs": 1792144816900, "suddenDeathOutcome": null}"""
    }
}

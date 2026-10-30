package com.sabit.duelloquiz.core.network.duel

import com.sabit.duelloquiz.core.model.duel.DuelRecord
import com.sabit.duelloquiz.core.model.duel.DuelServerMessage
import com.sabit.duelloquiz.core.model.duel.MatchResultPlayer
import com.sabit.duelloquiz.core.model.duel.OpponentType
import com.sabit.duelloquiz.core.network.ktor.DuelloJson
import com.sabit.duelloquiz.core.network.model.NetworkMatchSummary
import com.sabit.duelloquiz.core.network.model.NetworkStats
import com.sabit.duelloquiz.core.network.model.asExternalModel
import com.sabit.duelloquiz.core.network.model.asExternalModelOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bot opponent: duel-protocol.md v1.2 (§4, §5, §7a) and openapi.yaml 0.3.0 examples. */
class BotProtocolTest {

    private inline fun <reified T : DuelServerMessage> decode(json: String): T {
        val message = DuelCodec.decode(json)
        assertTrue("decoded $message", message is T)
        return message as T
    }

    @Test
    fun queueJoinedCarriesTheBotMoment() {
        val joined = decode<DuelServerMessage.QueueJoined>(
            """{"type": "queue.joined", "seq": 0, "serverNowEpochMs": 1792144800200, "deadlineEpochMs": 1792144830200,
             "botAtEpochMs": 1792144815200}""",
        )
        assertEquals(1_792_144_815_200L, joined.botAtEpochMs)
        assertEquals(1_792_144_830_200L, joined.deadlineEpochMs)
    }

    @Test
    fun queueJoinedWithoutOrWithNullBotMomentHasNoBot() {
        assertNull(
            decode<DuelServerMessage.QueueJoined>(
                """{"type": "queue.joined", "seq": 0, "serverNowEpochMs": 1, "deadlineEpochMs": 30001, "botAtEpochMs": null}""",
            ).botAtEpochMs,
        )
        assertNull(
            decode<DuelServerMessage.QueueJoined>(
                """{"type": "queue.joined", "seq": 0, "serverNowEpochMs": 1, "deadlineEpochMs": 30001}""",
            ).botAtEpochMs,
        )
    }

    @Test
    fun botMatchFoundHasANullRatingAndTheBotType() {
        val found = decode<DuelServerMessage.MatchFound>(
            """{"type": "match.found", "seq": 1, "serverNowEpochMs": 1792144815200,
             "matchId": "0c8e7d2a-5b1f-4c6e-8a9d-3f2e1d0c9b8a",
             "you": {"displayName": "Leyla", "avatarUrl": null, "rating": 1000},
             "opponent": {"displayName": "Duello Bot", "avatarUrl": null, "rating": null},
             "rounds": 7, "bet3Limit": 2, "deadlineEpochMs": 1792144818200, "opponentType": "bot"}""",
        )
        assertEquals(OpponentType.BOT, found.opponentType)
        assertNull(found.opponent.rating)
        assertEquals("Duello Bot", found.opponent.displayName)
        assertEquals(1000, found.you.rating)
    }

    @Test
    fun humanAndMissingOpponentTypeAreHuman() {
        val human = decode<DuelServerMessage.MatchFound>(
            """{"type": "match.found", "seq": 1, "serverNowEpochMs": 1, "matchId": "m",
             "you": {"displayName": "Leyla", "avatarUrl": null, "rating": 1000},
             "opponent": {"displayName": "Murad", "avatarUrl": null, "rating": 1016},
             "rounds": 7, "bet3Limit": 2, "deadlineEpochMs": 3001, "opponentType": "human"}""",
        )
        assertEquals(OpponentType.HUMAN, human.opponentType)
        assertEquals(1016, human.opponent.rating)
        val old = decode<DuelServerMessage.MatchFound>(
            """{"type": "match.found", "seq": 1, "serverNowEpochMs": 1, "matchId": "m",
             "you": {"displayName": "Leyla", "avatarUrl": null, "rating": 1000},
             "opponent": {"displayName": "Murad", "avatarUrl": null, "rating": 1016},
             "rounds": 7, "bet3Limit": 2, "deadlineEpochMs": 3001}""",
        )
        assertEquals(OpponentType.HUMAN, old.opponentType)
        // A future value never breaks the message.
        val future = decode<DuelServerMessage.MatchFound>(
            """{"type": "match.found", "seq": 1, "serverNowEpochMs": 1, "matchId": "m",
             "you": {"displayName": "Leyla", "avatarUrl": null, "rating": 1000},
             "opponent": {"displayName": "X", "avatarUrl": null, "rating": 1016},
             "rounds": 7, "bet3Limit": 2, "deadlineEpochMs": 3001, "opponentType": "ghost"}""",
        )
        assertEquals(OpponentType.HUMAN, future.opponentType)
    }

    @Test
    fun botMatchStateIsAlwaysConnectedWithoutRating() {
        val snapshot = decode<DuelServerMessage.MatchState>(
            """{"type": "match.state", "seq": 9, "serverNowEpochMs": 1792144830000,
             "matchId": "m", "phase": "confidence", "deadlineEpochMs": 1792144834000,
             "you": {"displayName": "Leyla", "avatarUrl": null, "rating": 1000},
             "opponent": {"displayName": "Duello Bot", "avatarUrl": null, "rating": null,
                          "connected": true, "reconnectDeadlineEpochMs": null},
             "roundNo": 2, "allIn": false, "suddenDeath": false, "suddenDeathNo": null,
             "question": {"text": "Sual", "image": null, "categoryName": "Tarix"},
             "options": null, "ownBet": null, "ownOptionId": null,
             "opponentBetLocked": true, "opponentAnswered": false, "bet3Left": 2,
             "scores": {"you": 200, "opponent": 100}, "missedInARow": {"you": 0, "opponent": 0},
             "lastResult": null, "rounds": [], "opponentType": "bot"}""",
        ).snapshot
        assertEquals(OpponentType.BOT, snapshot.opponentType)
        assertNull(snapshot.opponent.rating)
        assertTrue(snapshot.opponentConnected)
        assertNull(snapshot.opponentReconnectDeadlineEpochMs)
    }

    @Test
    fun botMatchResultHasNoRatingChangeAndNullOpponentRatings() {
        val result = decode<DuelServerMessage.MatchResultMessage>(
            """{"type": "match.result", "seq": 43, "serverNowEpochMs": 1792144950000, "matchId": "m",
             "outcome": "win", "endReason": "normal", "forfeitedBy": null, "forfeitCause": null,
             "scores": {"you": 640, "opponent": 410},
             "ratingBefore": 1000, "ratingAfter": 1000, "ratingChange": 0, "opponentRatingAfter": null,
             "suddenDeathPlayed": 0,
             "calibration": [{"level": 1, "picks": 2, "correct": 1}, {"level": 2, "picks": 3, "correct": 2},
                             {"level": 3, "picks": 2, "correct": 2}],
             "rounds": [],
             "progress": {"xpGained": 25, "xpBreakdown": [{"reason": "duel_bot_played", "categoryId": null, "amount": 10},
                          {"reason": "duel_bot_win", "categoryId": null, "amount": 15}],
                          "xpCapped": false, "xpTotal": 525, "levelBefore": 2, "levelAfter": 2,
                          "xpIntoLevel": 125, "xpForNextLevel": 400, "xpIntoLevelBefore": 100, "xpForNextLevelBefore": 400,
                          "categoryLevelUps": [], "streakCurrent": 3, "streakBest": 5, "streakExtendedToday": true, "dayKey": "2026-10-30"},
             "you": {"displayName": "Leyla", "avatarUrl": null, "ratingBefore": 1000, "ratingAfter": 1000, "deleted": false},
             "opponent": {"displayName": "Duello Bot", "avatarUrl": null, "ratingBefore": null, "ratingAfter": null, "deleted": false},
             "opponentType": "bot"}""",
        ).result
        assertEquals(OpponentType.BOT, result.opponentType)
        assertEquals(0, result.ratingChange)
        assertEquals(result.ratingBefore, result.ratingAfter)
        assertNull(result.opponentRatingAfter)
        assertEquals(MatchResultPlayer("Duello Bot", null, null, null, deleted = false), result.opponent)
        val progress = assertNotNullAndGet(result.progress)
        assertEquals(25, progress.xpGained)
        assertEquals(listOf("duel_bot_played", "duel_bot_win"), progress.xpBreakdown.map { it.reason })
    }

    @Test
    fun matchSummaryOfABotMatchHasNoRatingChange() {
        val row = DuelloJson.decodeFromString(
            NetworkMatchSummary.serializer(),
            """{"id": "b", "opponentType": "bot",
             "opponent": {"displayName": "Duello Bot", "avatarUrl": null, "deleted": false},
             "result": "win", "endReason": "normal", "forfeitCause": null, "scoreOwn": 640, "scoreOpponent": 410,
             "ratingChange": null, "finishedAt": "2026-10-30T10:00:00Z"}""",
        ).asExternalModelOrNull()
        assertNotNull(row)
        row!!
        assertTrue(row.isBot)
        assertNull(row.ratingChange)
        assertEquals("Duello Bot", row.opponentName)
        assertFalse(row.opponentDeleted)
    }

    @Test
    fun matchSummaryOfAHumanMatchKeepsItsNumber() {
        val row = DuelloJson.decodeFromString(
            NetworkMatchSummary.serializer(),
            """{"id": "h", "opponentType": "human",
             "opponent": {"displayName": "Murad", "avatarUrl": null, "deleted": false},
             "result": "loss", "endReason": "normal", "forfeitCause": null, "scoreOwn": 100, "scoreOpponent": 410,
             "ratingChange": -14, "finishedAt": "2026-10-30T10:00:00Z"}""",
        ).asExternalModelOrNull()!!
        assertFalse(row.isBot)
        assertEquals(-14, row.ratingChange)
        assertEquals(OpponentType.HUMAN, row.opponentType)
    }

    @Test
    fun statsCarryTheBotRecordSeparately() {
        val stats = DuelloJson.decodeFromString(
            NetworkStats.serializer(),
            """{"record": {"wins": 12, "losses": 9, "draws": 1, "total": 22},
             "botRecord": {"wins": 3, "losses": 2, "draws": 0, "total": 5},
             "calibration": [{"level": 1, "picks": 8, "correct": 7}, {"level": 2, "picks": 6, "correct": 4},
                             {"level": 3, "picks": 3, "correct": 1}]}""",
        ).asExternalModel()
        assertEquals(DuelRecord(12, 9, 1, 22), stats.record)
        assertEquals(DuelRecord(3, 2, 0, 5), stats.botRecord)
        val old = DuelloJson.decodeFromString(
            NetworkStats.serializer(),
            """{"record": {"wins": 1, "losses": 0, "draws": 0, "total": 1}, "calibration": []}""",
        ).asExternalModel()
        assertEquals(DuelRecord(0, 0, 0, 0), old.botRecord)
    }

    private fun <T> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}

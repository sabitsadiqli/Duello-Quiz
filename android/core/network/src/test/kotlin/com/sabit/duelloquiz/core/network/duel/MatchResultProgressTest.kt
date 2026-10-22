package com.sabit.duelloquiz.core.network.duel

import com.sabit.duelloquiz.core.model.duel.DuelServerMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** duel-protocol.md v1.1: `match.result.progress` (same object as REST `Progress`). */
class MatchResultProgressTest {

    private fun result(progress: String?): String = """
        {"type": "match.result", "seq": 43, "serverNowEpochMs": 1792144950000,
         "matchId": "m1", "outcome": "draw", "endReason": "draw", "forfeitedBy": null, "forfeitCause": null,
         "scores": {"you": 400, "opponent": 400},
         "ratingBefore": 1000, "ratingAfter": 1000, "ratingChange": 0, "opponentRatingAfter": 1000,
         "suddenDeathPlayed": 3,
         "calibration": [{"level": 1, "picks": 0, "correct": 0}, {"level": 2, "picks": 0, "correct": 0},
                         {"level": 3, "picks": 0, "correct": 0}],
         "rounds": []${progress?.let { ", \"progress\": $it" }.orEmpty()}}
    """.trimIndent()

    @Test
    fun progressIsParsed() {
        val message = DuelCodec.decode(
            result(
                """{"dayKey": "2026-10-22", "xpGained": 0, "xpCapped": true, "xpBreakdown": [],
                    "xpTotal": 900, "levelBefore": 4, "levelAfter": 4, "xpIntoLevelBefore": 300,
                    "xpForNextLevelBefore": 400, "xpIntoLevel": 300, "xpForNextLevel": 400,
                    "categoryLevelUps": [], "streakCurrent": 3, "streakBest": 5,
                    "streakExtendedToday": false, "freezeAvailable": false}""",
            ),
        )

        assertTrue(message is DuelServerMessage.MatchResultMessage)
        val progress = (message as DuelServerMessage.MatchResultMessage).result.progress
        assertEquals(0, progress?.xpGained)
        assertTrue(progress?.xpCapped == true)
        assertEquals(3, progress?.streakCurrent)
    }

    @Test
    fun nullOrMissingProgressIsNull() {
        val withNull = DuelCodec.decode(result("null")) as DuelServerMessage.MatchResultMessage
        val missing = DuelCodec.decode(result(null)) as DuelServerMessage.MatchResultMessage

        assertNull(withNull.result.progress)
        assertNull(missing.result.progress)
    }
}

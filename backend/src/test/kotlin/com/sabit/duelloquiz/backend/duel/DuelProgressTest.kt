package com.sabit.duelloquiz.backend.duel

import com.sabit.duelloquiz.backend.progress.ProgressDto
import com.sabit.duelloquiz.backend.progress.XpBreakdownItem
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** `match.result.progress` (protocol v1.1). */
class DuelProgressTest {

    private fun progress(xp: Int) = ProgressDto(
        dayKey = "2027-01-15", xpGained = xp, xpCapped = false,
        xpBreakdown = if (xp > 0) listOf(XpBreakdownItem("duel_played", null, 20), XpBreakdownItem("duel_win", null, 30)) else emptyList(),
        xpTotal = xp.toLong(), levelBefore = 1, levelAfter = 1, xpIntoLevelBefore = 0, xpForNextLevelBefore = 100,
        xpIntoLevel = xp.toLong(), xpForNextLevel = 100, categoryLevelUps = emptyList(), streakCurrent = if (xp > 0) 1 else 0,
        streakBest = 1, streakExtendedToday = xp > 0, freezeAvailable = false,
    )

    @Test
    fun `each player gets their own award in match result, also on rejoin`() = runTest {
        val h = DuelHarness(this)
        h.store.progress = mapOf(Side.A to progress(50), Side.B to progress(0))
        val (a, b) = h.startMatch()
        val matchId = h.matchId(a)
        h.send(h.bob, b, ClientMessage.MatchLeave(matchId))

        assertEquals(progress(50), a.last<ServerMessage.MatchResult>().progress)
        assertEquals(progress(0), b.last<ServerMessage.MatchResult>().progress)
        val json = DuelJson.parseToJsonElement(a.received.last { it.message is ServerMessage.MatchResult }.encode()) as JsonObject
        val p = json.getValue("progress").jsonObject
        assertEquals("50", p.getValue("xpGained").jsonPrimitive.content)
        assertEquals("false", p.getValue("xpCapped").jsonPrimitive.content)

        // Rejoin after the end (in-memory result or rebuilt from storage): the same award.
        h.store.matches[java.util.UUID.fromString(matchId)] =
            h.store.matches.getValue(java.util.UUID.fromString(matchId)).copy(progress = h.store.progress)
        h.advanceBy(h.config.resultRetentionMs + 1)
        val a2 = h.connect(h.alice)
        h.send(h.alice, a2, ClientMessage.MatchRejoin(matchId))
        assertEquals(progress(50), a2.last<ServerMessage.MatchResult>().progress)
    }

    @Test
    fun `progress is null when the store has none`() = runTest {
        val h = DuelHarness(this)
        val (a, b) = h.startMatch()
        h.send(h.bob, b, ClientMessage.MatchLeave(h.matchId(a)))
        assertNull(a.last<ServerMessage.MatchResult>().progress)
        val json = DuelJson.parseToJsonElement(a.received.last { it.message is ServerMessage.MatchResult }.encode()) as JsonObject
        assertEquals(JsonNull, json.getValue("progress"))
    }
}

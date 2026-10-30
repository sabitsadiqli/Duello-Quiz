package com.sabit.duelloquiz.backend.duel

import com.sabit.duelloquiz.backend.quiz.QuizRepository
import com.sabit.duelloquiz.backend.testsupport.DbTest
import com.sabit.duelloquiz.backend.testsupport.TestContent
import com.sabit.duelloquiz.backend.testsupport.TestEnv
import com.sabit.duelloquiz.backend.user.UserRepository
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DbDuelStoreTest : DbTest() {

    private fun seed(): Triple<DbDuelStore, UUID, UUID> = runBlocking {
        val env = TestEnv(db)
        env.importer.import(TestContent.input(TestContent.questions("cografiya", 12) + TestContent.questions("tarix", 3, status = "draft")))
        val users = UserRepository(db)
        val a = users.createIfAbsent("uid-a", "Alice", null, Instant.now()).id
        val b = users.createIfAbsent("uid-b", "Bob", null, Instant.now()).id
        Triple(DbDuelStore(db, QuizRepository(), 32, env.progress), a, b)
    }

    @Test
    fun `picks distinct active questions with four options and one correct`() = runBlocking {
        val (store, _, _) = seed()
        assertEquals(12, store.activeQuestionCount(), "drafts are not active")
        val picked = store.pickQuestions(10)
        assertEquals(10, picked.size)
        assertEquals(10, picked.map { it.id }.toSet().size)
        picked.forEach {
            assertEquals(4, it.options.size)
            assertEquals(1, it.options.count { o -> o.isCorrect })
            assertEquals(listOf(1, 2, 3, 4), it.options.map { o -> o.position })
            assertTrue(it.categoryName.getValue("az").isNotBlank())
        }
    }

    @Test
    fun `rounds and results are stored and Elo is applied once`() = runBlocking {
        val (store, a, b) = seed()
        val matchId = UUID.randomUUID()
        val t0 = 1_800_000_000_000L
        store.createMatch(matchId, a, b, t0)
        val q = store.pickQuestions(1).single()
        val right = q.correctOption.id
        store.saveRound(
            RoundRecord(
                matchId, 1, q.id, allIn = false, suddenDeath = false, optionsSentAtMs = t0 + 11_000, answerDeadlineMs = t0 + 21_000,
                answers = listOf(
                    AnswerRow(a, Side.A, 3, false, right, true, 3_800, 31, 331, false),
                    AnswerRow(b, Side.B, 1, true, null, false, null, 0, 0, true),
                ),
            ),
        )
        val end = MatchEnd(matchId, MatchStatus.FINISHED, EndReasons.FORFEIT, Side.A, Side.B, 331, 0, t0 + 60_000, DuelMatch.FORFEIT_MISSED)
        val endResult = store.endMatch(end, 1000, 1000)
        val ratings = endResult.ratings
        assertEquals(RatingUpdate(1000, 1016, 1000, 984), ratings)
        // XP in the same transaction (OQ-081): winner 20 + 30, the forfeiting player 0.
        assertEquals(50, endResult.progress.getValue(Side.A).xpGained)
        assertEquals(0, endResult.progress.getValue(Side.B).xpGained)
        assertEquals(1, endResult.progress.getValue(Side.A).streakCurrent)
        assertEquals(0, endResult.progress.getValue(Side.B).streakCurrent, "a forfeit is not an active day")
        // Second end (retry): no second rating change, the same awards.
        assertEquals(endResult, store.endMatch(end.copy(winnerSide = Side.B), 1000, 1000))
        val profiles = store.profiles(listOf(a, b))
        assertEquals(1016, profiles.getValue(a).rating)
        assertEquals(984, profiles.getValue(b).rating)

        val stored = assertNotNull(store.findMatch(matchId))
        assertEquals(MatchStatus.FINISHED, stored.status)
        assertEquals(EndReasons.FORFEIT, stored.endReason)
        assertEquals(Side.A, stored.winnerSide)
        assertEquals(Side.B, stored.forfeitedSide)
        assertEquals(DuelMatch.FORFEIT_MISSED, stored.forfeitCause)
        assertEquals(331 to 0, stored.scoreA to stored.scoreB)
        assertEquals(ratings, stored.ratings)
        assertEquals(endResult.progress, stored.progress)
        val round = stored.rounds.single()
        assertEquals(331, round.answers.getValue(Side.A).points)
        assertEquals(3_800, round.answers.getValue(Side.A).answeredMs)
        assertTrue(round.answers.getValue(Side.B).missed)
        assertNull(round.answers.getValue(Side.B).optionId)
    }

    @Test
    fun `active matches are abandoned on startup and cancelled matches keep ratings`() = runBlocking {
        val (store, a, b) = seed()
        val lost = UUID.randomUUID()
        store.createMatch(lost, a, b, 1_800_000_000_000L)
        val cancelled = UUID.randomUUID()
        store.createMatch(cancelled, b, a, 1_800_000_000_000L)
        store.endMatch(MatchEnd(cancelled, MatchStatus.CANCELLED, EndReasons.CANCELLED, null, null, 0, 0, 1_800_000_001_000L), 1000, 1000)
        assertEquals(1, store.abandonActiveMatches(1_800_000_002_000L))
        assertEquals(0, store.abandonActiveMatches(1_800_000_003_000L))
        val abandoned = store.findMatch(lost)!!
        assertEquals(MatchStatus.ABANDONED, abandoned.status)
        assertEquals(EndReasons.ABANDONED, abandoned.endReason)
        assertEquals(RatingUpdate(1000, 1000, 1000, 1000), abandoned.ratings)
        assertEquals(MatchStatus.CANCELLED, store.findMatch(cancelled)!!.status)
        assertEquals(1000, store.profiles(listOf(a)).getValue(a).rating)
    }

    private fun countWhere(sql: String): Long = runBlocking { db.tx { exec(sql) { rs -> rs.next(); rs.getLong(1) }!! } }

    @Test
    fun `a bot match never changes a rating and gives half XP marked as a bot award`() = runBlocking {
        val (store, a, _) = seed()
        val t0 = 1_800_000_000_000L
        val matchId = UUID.randomUUID()
        store.createBotMatch(matchId, a, t0)
        val q = store.pickQuestions(1).single()
        val wrong = q.options.first { !it.isCorrect }.id
        store.saveRound(
            RoundRecord(
                matchId, 1, q.id, allIn = false, suddenDeath = false, optionsSentAtMs = t0 + 11_000, answerDeadlineMs = t0 + 21_000,
                answers = listOf(
                    AnswerRow(a, Side.A, 2, false, q.correctOption.id, true, 2_000, 40, 240, false),
                    // The bot's internal id is never a user: stored as NULL.
                    AnswerRow(UUID.randomUUID(), Side.B, 1, false, wrong, false, 5_000, 0, 0, false),
                ),
            ),
        )
        val end = MatchEnd(matchId, MatchStatus.FINISHED, EndReasons.NORMAL, Side.A, null, 240, 0, t0 + 60_000)
        val result = store.endMatch(end, 1000, 0)
        assertEquals(RatingUpdate(1000, 1000, 0, 0), result.ratings)
        val award = result.progress.getValue(Side.A)
        assertEquals(25, award.xpGained)
        assertEquals(listOf("duel_bot_played", "duel_bot_win"), award.xpBreakdown.map { it.reason })
        assertEquals(1, award.streakCurrent, "a finished bot match is an active day")
        assertEquals(setOf(Side.A), result.progress.keys)
        assertEquals(result, store.endMatch(end, 1000, 0), "idempotent")
        assertEquals(1000, store.profiles(listOf(a)).getValue(a).rating)

        val stored = assertNotNull(store.findMatch(matchId))
        assertEquals(OpponentTypes.BOT, stored.opponentType)
        assertNull(stored.playerB)
        assertEquals(RatingUpdate(1000, 1000, 0, 0), stored.ratings)
        assertEquals(award, stored.progress[Side.A])
        assertEquals(UUID(0, 0), stored.rounds.single().answers.getValue(Side.B).userId)
        assertEquals(1L, countWhere("SELECT count(*) FROM matches WHERE id = '$matchId' AND rating_b_before IS NULL AND rating_b_after IS NULL AND rating_a_after = 1000"))
        assertEquals(1L, countWhere("SELECT count(*) FROM progress_awards WHERE source_id = '$matchId' AND is_bot AND opponent_id IS NULL"))
        assertEquals(listOf(true), store.recentBotResults(a, 10))
    }

    @Test
    fun `bot draws give 18 XP, forfeits and abandoned bot matches give 0`() = runBlocking {
        val (store, a, _) = seed()
        val t0 = 1_800_000_000_000L
        fun id() = UUID.randomUUID()
        val draw = id().also { store.createBotMatch(it, a, t0) }
        assertEquals(18, store.endMatch(MatchEnd(draw, MatchStatus.FINISHED, EndReasons.DRAW, null, null, 300, 300, t0 + 1), 1000, 0).progress.getValue(Side.A).xpGained)
        val forfeit = id().also { store.createBotMatch(it, a, t0) }
        val f = store.endMatch(MatchEnd(forfeit, MatchStatus.FINISHED, EndReasons.FORFEIT, Side.B, Side.A, 0, 100, t0 + 2, DuelMatch.FORFEIT_LEFT), 1000, 0)
        assertEquals(0, f.progress.getValue(Side.A).xpGained)
        val abandoned = id().also { store.createBotMatch(it, a, t0) }
        assertEquals(0, store.endMatch(MatchEnd(abandoned, MatchStatus.ABANDONED, EndReasons.ABANDONED, null, null, 0, 0, t0 + 3), 1000, 0).progress.getValue(Side.A).xpGained)
        val cancelled = id().also { store.createBotMatch(it, a, t0) }
        assertTrue(store.endMatch(MatchEnd(cancelled, MatchStatus.CANCELLED, EndReasons.CANCELLED, null, null, 0, 0, t0 + 4), 1000, 0).progress.isEmpty())
        // Finished ones only, newest first: the forfeit (loss), then the draw.
        assertEquals(listOf(false, false), store.recentBotResults(a, 10))
        assertEquals(1000, store.profiles(listOf(a)).getValue(a).rating)
    }

    @Test
    fun `bot matches share the 20 per day duel cap and are exempt from the per-opponent cap`() = runBlocking {
        val (store, a, b) = seed()
        val t0 = 1_800_000_000_000L
        // One real match against Bob first.
        val real = UUID.randomUUID().also { store.createMatch(it, a, b, t0) }
        assertEquals(50, store.endMatch(MatchEnd(real, MatchStatus.FINISHED, EndReasons.NORMAL, Side.A, null, 1, 0, t0 + 1), 1000, 1000).progress.getValue(Side.A).xpGained)
        // 19 bot matches on the same Baku day: all give XP (no 3-per-opponent cap for the bot).
        repeat(19) { i ->
            val m = UUID.randomUUID().also { store.createBotMatch(it, a, t0 + i) }
            val p = store.endMatch(MatchEnd(m, MatchStatus.FINISHED, EndReasons.NORMAL, Side.B, null, 0, 1, t0 + 100 + i), 1016, 0).progress.getValue(Side.A)
            assertEquals(10, p.xpGained, "bot match ${i + 1}")
            assertFalse(p.xpCapped)
        }
        // The 21st duel of the day (real + bot together) is capped.
        val capped = UUID.randomUUID().also { store.createBotMatch(it, a, t0 + 50) }
        val p = store.endMatch(MatchEnd(capped, MatchStatus.FINISHED, EndReasons.NORMAL, Side.A, null, 1, 0, t0 + 200), 1016, 0).progress.getValue(Side.A)
        assertEquals(0, p.xpGained)
        assertTrue(p.xpCapped)
        assertEquals(1016, store.profiles(listOf(a)).getValue(a).rating, "only the real match moved the rating")
    }
}

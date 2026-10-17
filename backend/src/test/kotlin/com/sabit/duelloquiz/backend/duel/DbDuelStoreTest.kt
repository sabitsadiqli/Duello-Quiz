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
        Triple(DbDuelStore(db, QuizRepository()), a, b)
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
        val ratings = store.endMatch(end, 1000, 1000)
        assertEquals(RatingUpdate(1000, 1016, 1000, 984), ratings)
        // Second end (retry): no second rating change.
        assertEquals(ratings, store.endMatch(end.copy(winnerSide = Side.B), 1000, 1000))
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
}

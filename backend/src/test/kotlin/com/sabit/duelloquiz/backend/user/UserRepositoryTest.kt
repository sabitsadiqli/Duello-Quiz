package com.sabit.duelloquiz.backend.user

import com.sabit.duelloquiz.backend.testsupport.DbTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserRepositoryTest : DbTest() {
    private val now = Instant.parse("2026-10-16T10:00:00Z")

    @Test
    fun `concurrent first calls create exactly one user`() = runBlocking {
        val repo = UserRepository(db)
        val users = (1..8).map { i ->
            async(Dispatchers.IO) { repo.createIfAbsent("uid-1", "Oyunçu $i", null, now) }
        }.awaitAll()
        assertEquals(1, users.map { it.id }.toSet().size)
        assertEquals(1L, db.tx { exec("SELECT count(*) FROM users") { rs -> rs.next(); rs.getLong(1) } })
        assertEquals(1000, users.first().rating)
    }

    @Test
    fun `deleting a user removes quiz data and anonymizes matches`() = runBlocking {
        val repo = UserRepository(db)
        val alice = repo.createIfAbsent("alice", "Alice", null, now)
        val bob = repo.createIfAbsent("bob", "Bob", null, now)
        db.tx {
            exec(
                """
                INSERT INTO quiz_sessions (user_id, category_id, status, started_at, deadline_at)
                VALUES ('${alice.id}', 1, 'active', now(), now() + interval '245 seconds');
                INSERT INTO matches (id, player_a, player_b, score_a, score_b, winner_side, status, end_reason, finished_at)
                VALUES ('00000000-0000-0000-0000-0000000000aa', '${alice.id}', '${bob.id}', 500, 300, 'a', 'finished', 'normal', now());
                """.trimIndent(),
                explicitStatementType = org.jetbrains.exposed.v1.core.statements.StatementType.MULTI,
            )
        }
        assertTrue(repo.deleteByFirebaseUid("alice"))
        assertFalse(repo.deleteByFirebaseUid("alice"))
        assertNull(repo.findByFirebaseUid("alice"))
        assertEquals(0L, db.tx { exec("SELECT count(*) FROM quiz_sessions") { rs -> rs.next(); rs.getLong(1) } })

        // Bob still sees the match, the winner side survives the anonymization.
        val bobsMatches = repo.matchesOf(bob.id, listOf("finished"), 20)
        val match = bobsMatches.single()
        assertNull(match.opponentId)
        assertEquals("b", match.ownSide)
        assertEquals("a", match.winnerSide)
        assertEquals(300, match.scoreOwn)
    }

    @Test
    fun `deleting a user deletes their bot matches and still anonymizes real ones`() = runBlocking {
        val repo = UserRepository(db)
        val alice = repo.createIfAbsent("alice", "Alice", null, now)
        val bob = repo.createIfAbsent("bob", "Bob", null, now)
        db.tx {
            exec(
                """
                INSERT INTO questions (id, external_id, category_id, difficulty, text_i18n, status, is_active, content_hash)
                VALUES ('00000000-0000-0000-0000-00000000000f', 'cografiya-0900', 1, 1, '{"az":"q"}', 'reviewed', true, 'h');
                INSERT INTO matches (id, player_a, player_b, score_a, score_b, winner_side, status, end_reason, finished_at, opponent_type)
                VALUES ('00000000-0000-0000-0000-0000000000aa', '${alice.id}', '${bob.id}', 500, 300, 'a', 'finished', 'normal', now(), 'human'),
                       ('00000000-0000-0000-0000-0000000000b1', '${alice.id}', NULL, 500, 300, 'a', 'finished', 'normal', now(), 'bot'),
                       ('00000000-0000-0000-0000-0000000000b2', '${bob.id}', NULL, 100, 300, 'b', 'finished', 'normal', now(), 'bot');
                INSERT INTO match_rounds (id, match_id, round_no, question_id) OVERRIDING SYSTEM VALUE VALUES
                (11, '00000000-0000-0000-0000-0000000000b1', 1, '00000000-0000-0000-0000-00000000000f');
                INSERT INTO match_answers (round_id, user_id, side, bet) VALUES (11, '${alice.id}', 'a', 2), (11, NULL, 'b', 1);
                """.trimIndent(),
                explicitStatementType = org.jetbrains.exposed.v1.core.statements.StatementType.MULTI,
            )
        }
        val preview = repo.deletionPreview(alice.id)
        assertEquals(1L, preview["matches (anonymized)"])
        assertEquals(1L, preview["matches vs bot (deleted)"])

        assertTrue(repo.deleteByFirebaseUid("alice"))
        suspend fun count(sql: String): Long = db.tx { exec(sql) { rs -> rs.next(); rs.getLong(1) }!! }
        assertEquals(0L, count("SELECT count(*) FROM matches WHERE id = '00000000-0000-0000-0000-0000000000b1'"))
        assertEquals(0L, count("SELECT count(*) FROM match_rounds WHERE id = 11"))
        assertEquals(0L, count("SELECT count(*) FROM match_answers WHERE round_id = 11"))
        // The real match stays, anonymized; Bob's own bot match is untouched.
        assertEquals(1L, count("SELECT count(*) FROM matches WHERE id = '00000000-0000-0000-0000-0000000000aa' AND player_a IS NULL"))
        assertEquals(1L, count("SELECT count(*) FROM matches WHERE id = '00000000-0000-0000-0000-0000000000b2'"))
    }
}

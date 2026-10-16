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
}

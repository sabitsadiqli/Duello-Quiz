package com.sabit.duelloquiz.backend.user

import com.sabit.duelloquiz.backend.common.Db
import com.sabit.duelloquiz.backend.common.db.MatchAnswersTable
import com.sabit.duelloquiz.backend.common.db.MatchRoundsTable
import com.sabit.duelloquiz.backend.common.db.MatchesTable
import com.sabit.duelloquiz.backend.common.db.UsersTable
import com.sabit.duelloquiz.backend.common.db.toDb
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID

data class UserRecord(
    val id: UUID,
    val firebaseUid: String,
    val displayName: String,
    val avatarUrl: String?,
    val rating: Int,
    val createdAt: Instant,
)

/** A finished or abandoned match from the point of view of one player. */
data class MatchRecord(
    val id: UUID,
    val ownSide: String,
    val opponentId: UUID?,
    val scoreOwn: Int,
    val scoreOpponent: Int,
    val winnerSide: String?,
    val status: String,
    val endReason: String,
    val forfeitCause: String?,
    val ratingBefore: Int?,
    val ratingAfter: Int?,
    val finishedAt: Instant,
    /** `human` | `bot` (V6). */
    val opponentType: String = UserRepository.OPPONENT_HUMAN,
)

data class CalibrationRow(val bet: Int, val isCorrect: Boolean)

class UserRepository(private val db: Db) {

    suspend fun findByFirebaseUid(firebaseUid: String): UserRecord? = db.tx {
        UsersTable.selectAll().where { UsersTable.firebaseUid eq firebaseUid }.singleOrNull()?.toUser()
    }

    /**
     * Inserts the profile unless a row for [firebaseUid] exists (`ON CONFLICT DO NOTHING`),
     * then returns the stored row. Safe for concurrent first calls (AUTH-06).
     */
    suspend fun createIfAbsent(firebaseUid: String, displayName: String, avatarUrl: String?, now: Instant): UserRecord =
        db.tx {
            UsersTable.insertIgnore {
                it[id] = UUID.randomUUID()
                it[UsersTable.firebaseUid] = firebaseUid
                it[UsersTable.displayName] = displayName
                it[UsersTable.avatarUrl] = avatarUrl
                it[rating] = DEFAULT_RATING
                it[createdAt] = now.toDb()
                it[updatedAt] = now.toDb()
            }
            UsersTable.selectAll().where { UsersTable.firebaseUid eq firebaseUid }.single().toUser()
        }

    suspend fun updateDisplayName(userId: UUID, displayName: String, now: Instant): UserRecord = db.tx {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[UsersTable.displayName] = displayName
            it[updatedAt] = now.toDb()
        }
        UsersTable.selectAll().where { UsersTable.id eq userId }.single().toUser()
    }

    /**
     * Deletes the profile. The user's bot matches are deleted first, with their rounds
     * and answers (no other person is in them, OQ-106). The schema then cascades to quiz
     * sessions, session questions, quiz answers, daily attempts/answers, XP and streak
     * data, push tokens, notification settings and the send log (V5), and sets the
     * player to NULL in real matches and their answers (OQ-014). One transaction.
     * @return true if a row was deleted.
     */
    suspend fun deleteByFirebaseUid(firebaseUid: String): Boolean = db.tx {
        val userId = UsersTable.select(UsersTable.id).where { UsersTable.firebaseUid eq firebaseUid }
            .singleOrNull()?.get(UsersTable.id) ?: return@tx false
        // match_rounds and match_answers go with ON DELETE CASCADE.
        MatchesTable.deleteWhere { (MatchesTable.playerA eq userId) and (MatchesTable.opponentType eq OPPONENT_BOT) }
        UsersTable.deleteWhere { UsersTable.id eq userId } > 0
    }

    suspend fun findDisplayInfo(ids: Collection<UUID>): Map<UUID, Pair<String, String?>> {
        if (ids.isEmpty()) return emptyMap()
        return db.tx {
            UsersTable.select(UsersTable.id, UsersTable.displayName, UsersTable.avatarUrl)
                .where { UsersTable.id inList ids }
                .associate { it[UsersTable.id] to (it[UsersTable.displayName] to it[UsersTable.avatarUrl]) }
        }
    }

    /** Matches of [userId] with one of [statuses], newest first. */
    suspend fun matchesOf(userId: UUID, statuses: Collection<String>, limit: Int?): List<MatchRecord> = db.tx {
        val query = MatchesTable.selectAll()
            .where {
                ((MatchesTable.playerA eq userId) or (MatchesTable.playerB eq userId)) and
                    (MatchesTable.status inList statuses) and
                    MatchesTable.finishedAt.isNotNull()
            }
            .orderBy(MatchesTable.finishedAt to SortOrder.DESC, MatchesTable.id to SortOrder.ASC)
        (if (limit != null) query.limit(limit) else query).map { row -> row.toMatch(userId) }
    }

    /**
     * Calibration rows (OQ-027): rounds 1–7 of finished matches, actively locked
     * Əminlik only; defaulted picks, missed rounds and sudden death are excluded.
     */
    suspend fun calibrationRows(userId: UUID): List<CalibrationRow> = db.tx {
        MatchAnswersTable
            .join(MatchRoundsTable, JoinType.INNER, MatchAnswersTable.roundId, MatchRoundsTable.id)
            .join(MatchesTable, JoinType.INNER, MatchRoundsTable.matchId, MatchesTable.id)
            .select(MatchAnswersTable.bet, MatchAnswersTable.isCorrect)
            .where {
                (MatchAnswersTable.userId eq userId) and
                    (MatchesTable.status eq MATCH_FINISHED) and
                    (MatchRoundsTable.suddenDeath eq false) and
                    (MatchRoundsTable.roundNo lessEq REGULAR_ROUNDS) and
                    (MatchAnswersTable.betDefaulted eq false) and
                    (MatchAnswersTable.missed eq false)
            }
            .map { CalibrationRow(it[MatchAnswersTable.bet].toInt(), it[MatchAnswersTable.isCorrect]) }
    }

    private fun ResultRow.toUser() = UserRecord(
        id = this[UsersTable.id],
        firebaseUid = this[UsersTable.firebaseUid],
        displayName = this[UsersTable.displayName],
        avatarUrl = this[UsersTable.avatarUrl],
        rating = this[UsersTable.rating],
        createdAt = this[UsersTable.createdAt].toInstant(),
    )

    private fun ResultRow.toMatch(userId: UUID): MatchRecord {
        val own = if (this[MatchesTable.playerA] == userId) SIDE_A else SIDE_B
        val isA = own == SIDE_A
        return MatchRecord(
            id = this[MatchesTable.id],
            ownSide = own,
            opponentId = if (isA) this[MatchesTable.playerB] else this[MatchesTable.playerA],
            scoreOwn = if (isA) this[MatchesTable.scoreA] else this[MatchesTable.scoreB],
            scoreOpponent = if (isA) this[MatchesTable.scoreB] else this[MatchesTable.scoreA],
            winnerSide = this[MatchesTable.winnerSide],
            status = this[MatchesTable.status],
            endReason = this[MatchesTable.endReason] ?: this[MatchesTable.status],
            forfeitCause = this[MatchesTable.forfeitCause],
            ratingBefore = if (isA) this[MatchesTable.ratingABefore] else this[MatchesTable.ratingBBefore],
            ratingAfter = if (isA) this[MatchesTable.ratingAAfter] else this[MatchesTable.ratingBAfter],
            finishedAt = this[MatchesTable.finishedAt]!!.toInstant(),
            opponentType = this[MatchesTable.opponentType],
        )
    }

    /** True if [userId] plays a match that is still `active` (possibly in the running server). */
    suspend fun hasActiveMatch(userId: UUID): Boolean = db.tx {
        MatchesTable.select(MatchesTable.id)
            .where { ((MatchesTable.playerA eq userId) or (MatchesTable.playerB eq userId)) and (MatchesTable.status eq MATCH_ACTIVE) }
            .limit(1)
            .any()
    }

    /** One page of (internal id, Firebase uid), ordered by id, strictly after [afterId] (keyset paging). */
    suspend fun firebaseUidPage(afterId: UUID?, limit: Int): List<Pair<UUID, String>> = db.tx {
        UsersTable.select(UsersTable.id, UsersTable.firebaseUid)
            .apply { if (afterId != null) where { UsersTable.id greater afterId } }
            .orderBy(UsersTable.id to SortOrder.ASC)
            .limit(limit)
            .map { it[UsersTable.id] to it[UsersTable.firebaseUid] }
    }

    /**
     * Row counts a deletion of [userId] would remove (cascade) or anonymize (matches), for
     * the `delete-user --dry-run` output. Table names are constants; the id is a UUID.
     */
    suspend fun deletionPreview(userId: UUID): Map<String, Long> = db.tx {
        fun count(sql: String): Long = exec(sql) { rs -> if (rs.next()) rs.getLong(1) else 0L } ?: 0L
        val id = "'$userId'::uuid"
        CASCADE_TABLES.associateWith { table -> count("SELECT count(*) FROM $table WHERE user_id = $id") } +
            ("matches (anonymized)" to count("SELECT count(*) FROM matches WHERE (player_a = $id OR player_b = $id) AND opponent_type = 'human'")) +
            ("matches vs bot (deleted)" to count("SELECT count(*) FROM matches WHERE player_a = $id AND opponent_type = 'bot'"))
    }

    companion object {
        const val DEFAULT_RATING = 1000
        const val SIDE_A = "a"
        const val SIDE_B = "b"
        const val MATCH_ACTIVE = "active"
        const val MATCH_FINISHED = "finished"
        const val MATCH_ABANDONED = "abandoned"
        const val OPPONENT_HUMAN = "human"
        const val OPPONENT_BOT = "bot"

        /** Tables with `user_id ... ON DELETE CASCADE` (V1, V5); children of these go with them. */
        val CASCADE_TABLES = listOf(
            "quiz_sessions", "daily_attempts", "user_progress", "user_category_xp", "user_active_days",
            "progress_awards", "xp_events", "push_tokens", "notification_settings", "notification_log",
        )
        const val REGULAR_ROUNDS: Short = 7
    }
}

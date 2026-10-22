package com.sabit.duelloquiz.backend.duel

import com.sabit.duelloquiz.backend.common.I18nText
import com.sabit.duelloquiz.backend.progress.ProgressDto
import com.sabit.duelloquiz.backend.quiz.ImageRecord
import com.sabit.duelloquiz.backend.quiz.OptionRecord
import java.util.UUID

enum class Side(val db: String) {
    A("a"), B("b");

    val other: Side get() = if (this == A) B else A

    companion object {
        fun fromDb(value: String?): Side? = entries.firstOrNull { it.db == value }
    }
}

/** A question as it is played in a match; snapshotted at match start (DUEL-19). */
data class DuelQuestion(
    val id: UUID,
    val text: I18nText,
    val categoryName: I18nText,
    val image: ImageRecord?,
    /** Stored order (the importer's shuffled position), same for both players. */
    val options: List<OptionRecord>,
) {
    val correctOption: OptionRecord get() = options.first { it.isCorrect }
}

data class PlayerProfile(val userId: UUID, val displayName: String, val avatarUrl: String?, val rating: Int)

data class AnswerRow(
    val userId: UUID,
    val side: Side,
    val bet: Int,
    val betDefaulted: Boolean,
    val optionId: UUID?,
    val isCorrect: Boolean,
    val answeredMs: Long?,
    val speedBonus: Int,
    val points: Int,
    val missed: Boolean,
)

data class RoundRecord(
    val matchId: UUID,
    val roundNo: Int,
    val questionId: UUID,
    val allIn: Boolean,
    val suddenDeath: Boolean,
    val optionsSentAtMs: Long?,
    val answerDeadlineMs: Long?,
    val answers: List<AnswerRow>,
)

enum class MatchStatus(val db: String) {
    ACTIVE("active"), FINISHED("finished"), ABANDONED("abandoned"), CANCELLED("cancelled");

    companion object {
        fun fromDb(value: String) = entries.first { it.db == value }
    }
}

data class MatchEnd(
    val matchId: UUID,
    val status: MatchStatus,
    val endReason: String,
    val winnerSide: Side?,
    val forfeitedSide: Side?,
    val scoreA: Int,
    val scoreB: Int,
    val finishedAtMs: Long,
    /** `missed_rounds` | `left` for a forfeit (OQ-068), else null. */
    val forfeitCause: String? = null,
)

data class RatingUpdate(val aBefore: Int, val aAfter: Int, val bBefore: Int, val bAfter: Int)

/** Ratings and the XP/streak award per side, written in the transaction that ends the match. */
data class MatchEndResult(val ratings: RatingUpdate, val progress: Map<Side, ProgressDto> = emptyMap())

/** A match read back from storage (rejoin after the in-memory result is gone). */
data class StoredMatch(
    val id: UUID,
    val playerA: UUID?,
    val playerB: UUID?,
    val status: MatchStatus,
    val endReason: String?,
    val winnerSide: Side?,
    val forfeitedSide: Side?,
    val scoreA: Int,
    val scoreB: Int,
    val ratings: RatingUpdate?,
    val rounds: List<StoredRound>,
    val forfeitCause: String? = null,
    /** Stored award snapshots per side (absent for matches before XP existed or deleted players). */
    val progress: Map<Side, ProgressDto> = emptyMap(),
)

data class StoredRound(val roundNo: Int, val suddenDeath: Boolean, val answers: Map<Side, AnswerRow>)

/**
 * Duel persistence (docs/api/duel-protocol.md §8). Implementations: [DbDuelStore];
 * an in-memory fake in tests.
 */
interface DuelStore {
    /** Active questions in active categories (duel needs at least 10, DUEL-19). */
    suspend fun activeQuestionCount(): Int

    /** Up to [count] distinct random active questions from all active categories. */
    suspend fun pickQuestions(count: Int): List<DuelQuestion>

    /** Current profile data (name, avatar, rating) of existing users. */
    suspend fun profiles(userIds: Collection<UUID>): Map<UUID, PlayerProfile>

    suspend fun createMatch(matchId: UUID, playerA: UUID, playerB: UUID, startedAtMs: Long)

    suspend fun saveRound(round: RoundRecord)

    /**
     * Ends an active match. For [MatchStatus.FINISHED] it updates both ratings (Elo)
     * in the same transaction; abandoned and cancelled matches keep the ratings.
     * In the same transaction each existing player of a finished or abandoned match gets
     * the duel XP/streak award (OQ-081, OQ-082, OQ-085).
     * Idempotent: a match that is no longer active is not changed again (returns the
     * stored ratings and awards). [ratingAtStartA]/[ratingAtStartB] are used for a deleted player.
     */
    suspend fun endMatch(end: MatchEnd, ratingAtStartA: Int, ratingAtStartB: Int): MatchEndResult

    /** Marks every still active match as abandoned (server restart, OQ-033). */
    suspend fun abandonActiveMatches(atMs: Long): Int

    suspend fun findMatch(matchId: UUID): StoredMatch?
}

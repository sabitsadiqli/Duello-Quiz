package com.sabit.duelloquiz.backend.duel

import com.sabit.duelloquiz.backend.common.Db
import com.sabit.duelloquiz.backend.common.db.CategoriesTable
import com.sabit.duelloquiz.backend.common.db.MatchAnswersTable
import com.sabit.duelloquiz.backend.common.db.MatchRoundsTable
import com.sabit.duelloquiz.backend.common.db.MatchesTable
import com.sabit.duelloquiz.backend.common.db.QuestionsTable
import com.sabit.duelloquiz.backend.common.db.UsersTable
import com.sabit.duelloquiz.backend.common.db.toDb
import com.sabit.duelloquiz.backend.progress.AwardRequest
import com.sabit.duelloquiz.backend.progress.DuelOutcome
import com.sabit.duelloquiz.backend.progress.ProgressDto
import com.sabit.duelloquiz.backend.progress.ProgressService
import com.sabit.duelloquiz.backend.progress.SourceType
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import com.sabit.duelloquiz.backend.quiz.QuizRepository
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Random
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID

/** PostgreSQL implementation of [DuelStore] (tables of V1__initial_schema.sql). */
class DbDuelStore(
    private val db: Db,
    private val quizRepository: QuizRepository,
    private val eloK: Int = 32,
    private val progress: ProgressService,
) : DuelStore {

    private val activeQuestion = (QuestionsTable.isActive eq true) and (CategoriesTable.isActive eq true)

    override suspend fun activeQuestionCount(): Int = db.tx {
        QuestionsTable.join(CategoriesTable, JoinType.INNER, QuestionsTable.categoryId, CategoriesTable.id)
            .selectAll()
            .where { activeQuestion }
            .count()
            .toInt()
    }

    override suspend fun pickQuestions(count: Int): List<DuelQuestion> = db.tx {
        val picked = QuestionsTable.join(CategoriesTable, JoinType.INNER, QuestionsTable.categoryId, CategoriesTable.id)
            .select(QuestionsTable.id, CategoriesTable.nameI18n)
            .where { activeQuestion }
            .orderBy(Random())
            .limit(count)
            .map { it[QuestionsTable.id] to it[CategoriesTable.nameI18n] }
        val contents = with(quizRepository) { loadQuestions(picked.map { it.first }) }
        picked.mapNotNull { (id, categoryName) ->
            val q = contents[id] ?: return@mapNotNull null
            // Defensive: the importer guarantees 4 options with exactly one correct.
            if (q.options.size != 4 || q.options.count { it.isCorrect } != 1) return@mapNotNull null
            DuelQuestion(q.id, q.text, categoryName, q.image, q.options)
        }
    }

    override suspend fun profiles(userIds: Collection<UUID>): Map<UUID, PlayerProfile> = db.tx {
        UsersTable.selectAll().where { UsersTable.id inList userIds }.associate {
            it[UsersTable.id] to PlayerProfile(it[UsersTable.id], it[UsersTable.displayName], it[UsersTable.avatarUrl], it[UsersTable.rating])
        }
    }

    override suspend fun createMatch(matchId: UUID, playerA: UUID, playerB: UUID, startedAtMs: Long) {
        db.tx {
            val ratings = UsersTable.select(UsersTable.id, UsersTable.rating)
                .where { UsersTable.id inList listOf(playerA, playerB) }
                .associate { it[UsersTable.id] to it[UsersTable.rating] }
            MatchesTable.insert {
                it[id] = matchId
                it[MatchesTable.playerA] = playerA
                it[MatchesTable.playerB] = playerB
                it[status] = MatchStatus.ACTIVE.db
                it[ratingABefore] = ratings[playerA]
                it[ratingBBefore] = ratings[playerB]
                it[startedAt] = Instant.ofEpochMilli(startedAtMs).toDb()
            }
        }
    }

    override suspend fun saveRound(round: RoundRecord) {
        db.tx {
            val roundId = MatchRoundsTable.insert {
                it[matchId] = round.matchId
                it[roundNo] = round.roundNo.toShort()
                it[questionId] = round.questionId
                it[allIn] = round.allIn
                it[suddenDeath] = round.suddenDeath
                it[optionsSentAt] = round.optionsSentAtMs?.let { ms -> Instant.ofEpochMilli(ms).toDb() }
                it[answerDeadlineAt] = round.answerDeadlineMs?.let { ms -> Instant.ofEpochMilli(ms).toDb() }
            }[MatchRoundsTable.id]
            // A player whose account was deleted mid-match is stored anonymized.
            val existing = UsersTable.select(UsersTable.id)
                .where { UsersTable.id inList round.answers.map { it.userId } }
                .map { it[UsersTable.id] }
                .toSet()
            round.answers.forEach { a ->
                MatchAnswersTable.insert {
                    it[MatchAnswersTable.roundId] = roundId
                    it[userId] = a.userId.takeIf { id -> id in existing }
                    it[side] = a.side.db
                    it[bet] = a.bet.toShort()
                    it[betDefaulted] = a.betDefaulted
                    it[optionId] = a.optionId
                    it[isCorrect] = a.isCorrect
                    it[answeredMs] = a.answeredMs?.toInt()
                    it[speedBonus] = a.speedBonus
                    it[points] = a.points
                    it[missed] = a.missed
                }
            }
        }
    }

    override suspend fun endMatch(end: MatchEnd, ratingAtStartA: Int, ratingAtStartB: Int): MatchEndResult = db.tx {
        val row = MatchesTable.selectAll().where { MatchesTable.id eq end.matchId }.forUpdate().singleOrNull()
            ?: error("Match ${end.matchId} not found")
        val stored = RatingUpdate(
            row[MatchesTable.ratingABefore] ?: ratingAtStartA,
            row[MatchesTable.ratingAAfter] ?: row[MatchesTable.ratingABefore] ?: ratingAtStartA,
            row[MatchesTable.ratingBBefore] ?: ratingAtStartB,
            row[MatchesTable.ratingBAfter] ?: row[MatchesTable.ratingBBefore] ?: ratingAtStartB,
        )
        val playersBySide = mapOf(Side.A to row[MatchesTable.playerA], Side.B to row[MatchesTable.playerB])
        // Idempotent on the status: a second end never changes ratings again (DUEL-22).
        if (row[MatchesTable.status] != MatchStatus.ACTIVE.db) {
            return@tx MatchEndResult(stored, storedAwards(end.matchId, playersBySide))
        }

        val playerA = row[MatchesTable.playerA]
        val playerB = row[MatchesTable.playerB]
        val current = UsersTable.select(UsersTable.id, UsersTable.rating)
            .where { UsersTable.id inList listOfNotNull(playerA, playerB) }
            .orderBy(UsersTable.id to SortOrder.ASC)
            .forUpdate()
            .associate { it[UsersTable.id] to it[UsersTable.rating] }
        val aBefore = playerA?.let { current[it] } ?: stored.aBefore
        val bBefore = playerB?.let { current[it] } ?: stored.bBefore
        val ratings = if (end.status == MatchStatus.FINISHED) {
            val scoreA = when (end.winnerSide) {
                Side.A -> 1.0
                Side.B -> 0.0
                null -> 0.5
            }
            val change = DuelRules.eloChangeA(aBefore, bBefore, scoreA, eloK)
            RatingUpdate(aBefore, aBefore + change, bBefore, bBefore - change)
        } else {
            RatingUpdate(aBefore, aBefore, bBefore, bBefore)
        }
        val now = Instant.ofEpochMilli(end.finishedAtMs).toDb()
        if (end.status == MatchStatus.FINISHED) {
            playerA?.takeIf { it in current }?.let { id ->
                UsersTable.update({ UsersTable.id eq id }) {
                    it[rating] = ratings.aAfter
                    it[updatedAt] = now
                }
            }
            playerB?.takeIf { it in current }?.let { id ->
                UsersTable.update({ UsersTable.id eq id }) {
                    it[rating] = ratings.bAfter
                    it[updatedAt] = now
                }
            }
        }
        val keepRatings = end.status != MatchStatus.CANCELLED
        MatchesTable.update({ (MatchesTable.id eq end.matchId) and (MatchesTable.status eq MatchStatus.ACTIVE.db) }) {
            it[status] = end.status.db
            it[endReason] = end.endReason
            it[winnerSide] = end.winnerSide?.db
            it[forfeitedSide] = end.forfeitedSide?.db
            it[forfeitCause] = end.forfeitCause
            it[scoreA] = end.scoreA
            it[scoreB] = end.scoreB
            it[ratingABefore] = if (keepRatings) ratings.aBefore else null
            it[ratingAAfter] = if (keepRatings) ratings.aAfter else null
            it[ratingBBefore] = if (keepRatings) ratings.bBefore else null
            it[ratingBAfter] = if (keepRatings) ratings.bAfter else null
            it[finishedAt] = now
        }
        val startedAt = row[MatchesTable.startedAt].toInstant()
        val awards = if (end.status == MatchStatus.FINISHED || end.status == MatchStatus.ABANDONED) {
            playersBySide.mapNotNull { (side, userId) ->
                if (userId == null || userId !in current) return@mapNotNull null
                val eligible = end.status == MatchStatus.FINISHED && end.forfeitedSide != side
                val outcome = when (end.winnerSide) {
                    null -> DuelOutcome.DRAW
                    side -> DuelOutcome.WIN
                    else -> DuelOutcome.LOSS
                }
                val award = with(progress) {
                    award(
                        AwardRequest(
                            userId = userId,
                            sourceType = SourceType.DUEL,
                            sourceId = end.matchId,
                            startedAt = startedAt,
                            eligible = eligible,
                            items = rules.duel(outcome),
                            opponentId = playersBySide[side.other],
                        ),
                    )
                }
                award?.let { side to it }
            }.toMap()
        } else {
            emptyMap()
        }
        MatchEndResult(ratings, awards)
    }

    private fun JdbcTransaction.storedAwards(matchId: UUID, players: Map<Side, UUID?>): Map<Side, ProgressDto> =
        players.mapNotNull { (side, userId) ->
            userId?.let { id -> with(progress) { storedAward(id, SourceType.DUEL, matchId) }?.let { side to it } }
        }.toMap()

    override suspend fun abandonActiveMatches(atMs: Long): Int = db.tx {
        val now = Instant.ofEpochMilli(atMs).toDb()
        val ids = MatchesTable.select(MatchesTable.id, MatchesTable.ratingABefore, MatchesTable.ratingBBefore)
            .where { MatchesTable.status eq MatchStatus.ACTIVE.db }
            .forUpdate()
            .toList()
        ids.forEach { row ->
            MatchesTable.update({ MatchesTable.id eq row[MatchesTable.id] }) {
                it[status] = MatchStatus.ABANDONED.db
                it[endReason] = EndReasons.ABANDONED
                it[ratingAAfter] = row[MatchesTable.ratingABefore]
                it[ratingBAfter] = row[MatchesTable.ratingBBefore]
                it[finishedAt] = now
            }
        }
        ids.size
    }

    override suspend fun findMatch(matchId: UUID): StoredMatch? = db.tx {
        val row = MatchesTable.selectAll().where { MatchesTable.id eq matchId }.singleOrNull() ?: return@tx null
        val rounds = MatchRoundsTable.selectAll()
            .where { MatchRoundsTable.matchId eq matchId }
            .orderBy(MatchRoundsTable.roundNo to SortOrder.ASC)
            .toList()
        val answers = if (rounds.isEmpty()) {
            emptyMap()
        } else {
            MatchAnswersTable.selectAll()
                .where { MatchAnswersTable.roundId inList rounds.map { it[MatchRoundsTable.id] } }
                .groupBy { it[MatchAnswersTable.roundId] }
        }
        val aBefore = row[MatchesTable.ratingABefore]
        val aAfter = row[MatchesTable.ratingAAfter]
        val bBefore = row[MatchesTable.ratingBBefore]
        val bAfter = row[MatchesTable.ratingBAfter]
        val awards = storedAwards(matchId, mapOf(Side.A to row[MatchesTable.playerA], Side.B to row[MatchesTable.playerB]))
        StoredMatch(
            id = matchId,
            playerA = row[MatchesTable.playerA],
            playerB = row[MatchesTable.playerB],
            status = MatchStatus.fromDb(row[MatchesTable.status]),
            endReason = row[MatchesTable.endReason],
            winnerSide = Side.fromDb(row[MatchesTable.winnerSide]),
            forfeitedSide = Side.fromDb(row[MatchesTable.forfeitedSide]),
            forfeitCause = row[MatchesTable.forfeitCause],
            progress = awards,
            scoreA = row[MatchesTable.scoreA],
            scoreB = row[MatchesTable.scoreB],
            ratings = if (aBefore != null && aAfter != null && bBefore != null && bAfter != null) {
                RatingUpdate(aBefore, aAfter, bBefore, bAfter)
            } else {
                null
            },
            rounds = rounds.map { r ->
                StoredRound(
                    roundNo = r[MatchRoundsTable.roundNo].toInt(),
                    suddenDeath = r[MatchRoundsTable.suddenDeath],
                    answers = answers[r[MatchRoundsTable.id]].orEmpty().associate { a ->
                        val side = Side.fromDb(a[MatchAnswersTable.side])!!
                        side to AnswerRow(
                            userId = a[MatchAnswersTable.userId] ?: UUID(0, 0),
                            side = side,
                            bet = a[MatchAnswersTable.bet].toInt(),
                            betDefaulted = a[MatchAnswersTable.betDefaulted],
                            optionId = a[MatchAnswersTable.optionId],
                            isCorrect = a[MatchAnswersTable.isCorrect],
                            answeredMs = a[MatchAnswersTable.answeredMs]?.toLong(),
                            speedBonus = a[MatchAnswersTable.speedBonus],
                            points = a[MatchAnswersTable.points],
                            missed = a[MatchAnswersTable.missed],
                        )
                    },
                )
            },
        )
    }
}

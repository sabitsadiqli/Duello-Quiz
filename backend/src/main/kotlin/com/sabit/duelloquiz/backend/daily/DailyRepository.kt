package com.sabit.duelloquiz.backend.daily

import com.sabit.duelloquiz.backend.common.db.CategoriesTable
import com.sabit.duelloquiz.backend.common.db.DailyAnswersTable
import com.sabit.duelloquiz.backend.common.db.DailyAttemptsTable
import com.sabit.duelloquiz.backend.common.db.DailyChallengeQuestionsTable
import com.sabit.duelloquiz.backend.common.db.DailyChallengesTable
import com.sabit.duelloquiz.backend.common.db.QuestionsTable
import com.sabit.duelloquiz.backend.common.db.toDb
import com.sabit.duelloquiz.backend.quiz.AnswerRecord
import com.sabit.duelloquiz.backend.quiz.SessionStatus
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class DailyQuestionRef(val position: Int, val questionId: UUID, val categoryId: Int)

data class DailyRecord(val id: UUID, val day: LocalDate, val number: Int, val questions: List<DailyQuestionRef>)

data class AttemptRecord(
    val id: UUID,
    val userId: UUID,
    val dailyId: UUID,
    val status: SessionStatus,
    val correctCount: Int?,
    val startedAt: Instant,
    val deadlineAt: Instant,
    val finishedAt: Instant?,
)

/** Daily challenge persistence; every function runs in the caller's transaction. */
class DailyRepository {

    fun JdbcTransaction.findDaily(day: LocalDate): DailyRecord? =
        DailyChallengesTable.selectAll().where { DailyChallengesTable.day eq day }.singleOrNull()?.let { toDaily(it) }

    fun JdbcTransaction.findDailyById(id: UUID): DailyRecord? =
        DailyChallengesTable.selectAll().where { DailyChallengesTable.id eq id }.singleOrNull()?.let { toDaily(it) }

    private fun JdbcTransaction.toDaily(row: ResultRow): DailyRecord {
        val id = row[DailyChallengesTable.id]
        val questions = DailyChallengeQuestionsTable.selectAll()
            .where { DailyChallengeQuestionsTable.dailyId eq id }
            .orderBy(DailyChallengeQuestionsTable.position to SortOrder.ASC)
            .map {
                DailyQuestionRef(
                    it[DailyChallengeQuestionsTable.position].toInt(),
                    it[DailyChallengeQuestionsTable.questionId],
                    it[DailyChallengeQuestionsTable.categoryId],
                )
            }
        return DailyRecord(id, row[DailyChallengesTable.day], row[DailyChallengesTable.number], questions)
    }

    /**
     * Active questions of active categories with their category; [playableMin] decides
     * which categories are playable; questions used in a daily in [recentFrom, day) are
     * flagged.
     */
    fun JdbcTransaction.candidates(day: LocalDate, recentFrom: LocalDate, playableMin: Int): List<DailyCandidate> {
        val active = QuestionsTable.join(CategoriesTable, JoinType.INNER, QuestionsTable.categoryId, CategoriesTable.id)
            .select(QuestionsTable.id, QuestionsTable.categoryId)
            .where { (QuestionsTable.isActive eq true) and (CategoriesTable.isActive eq true) }
            .map { it[QuestionsTable.id] to it[QuestionsTable.categoryId] }
        val perCategory = active.groupingBy { it.second }.eachCount()
        val recent = DailyChallengeQuestionsTable
            .join(DailyChallengesTable, JoinType.INNER, DailyChallengeQuestionsTable.dailyId, DailyChallengesTable.id)
            .select(DailyChallengeQuestionsTable.questionId)
            .where { (DailyChallengesTable.day greaterEq recentFrom) and (DailyChallengesTable.day less day) }
            .map { it[DailyChallengeQuestionsTable.questionId] }
            .toSet()
        return active.map { (id, category) ->
            DailyCandidate(id, category, (perCategory[category] ?: 0) >= playableMin, id in recent)
        }
    }

    /** Inserts the day's set unless another request did first; returns the stored set. */
    fun JdbcTransaction.createDaily(day: LocalDate, number: Int, picked: List<DailyCandidate>, now: Instant): DailyRecord {
        val id = UUID.randomUUID()
        val inserted = DailyChallengesTable.insertIgnore {
            it[DailyChallengesTable.id] = id
            it[DailyChallengesTable.day] = day
            it[DailyChallengesTable.number] = number
            it[createdAt] = now.toDb()
        }.insertedCount > 0
        if (inserted) {
            DailyChallengeQuestionsTable.batchInsert(picked.withIndex()) { (index, c) ->
                this[DailyChallengeQuestionsTable.dailyId] = id
                this[DailyChallengeQuestionsTable.position] = (index + 1).toShort()
                this[DailyChallengeQuestionsTable.questionId] = c.questionId
                this[DailyChallengeQuestionsTable.categoryId] = c.categoryId
            }
        }
        return findDaily(day) ?: error("daily challenge for $day missing after insert")
    }

    fun JdbcTransaction.findAttempt(userId: UUID, dailyId: UUID, forUpdate: Boolean): AttemptRecord? {
        val q = DailyAttemptsTable.selectAll()
            .where { (DailyAttemptsTable.userId eq userId) and (DailyAttemptsTable.dailyId eq dailyId) }
        return (if (forUpdate) q.forUpdate() else q).singleOrNull()?.toAttempt()
    }

    fun JdbcTransaction.findAttempt(id: UUID, forUpdate: Boolean): AttemptRecord? {
        val q = DailyAttemptsTable.selectAll().where { DailyAttemptsTable.id eq id }
        return (if (forUpdate) q.forUpdate() else q).singleOrNull()?.toAttempt()
    }

    /** Creates the user's attempt unless one exists (unique per user and day). */
    fun JdbcTransaction.insertAttemptIfAbsent(attempt: AttemptRecord): Boolean =
        DailyAttemptsTable.insertIgnore {
            it[id] = attempt.id
            it[userId] = attempt.userId
            it[dailyId] = attempt.dailyId
            it[status] = attempt.status.db
            it[startedAt] = attempt.startedAt.toDb()
            it[deadlineAt] = attempt.deadlineAt.toDb()
        }.insertedCount > 0

    fun JdbcTransaction.answers(attemptId: UUID): Map<UUID, AnswerRecord> =
        DailyAnswersTable.selectAll().where { DailyAnswersTable.attemptId eq attemptId }.associate {
            it[DailyAnswersTable.questionId] to AnswerRecord(
                it[DailyAnswersTable.questionId], it[DailyAnswersTable.optionId], it[DailyAnswersTable.isCorrect],
            )
        }

    fun JdbcTransaction.insertAnswer(attemptId: UUID, answer: AnswerRecord, answeredAt: Instant, elapsedMs: Int) {
        DailyAnswersTable.insert {
            it[DailyAnswersTable.attemptId] = attemptId
            it[questionId] = answer.questionId
            it[optionId] = answer.optionId
            it[isCorrect] = answer.isCorrect
            it[DailyAnswersTable.answeredAt] = answeredAt.toDb()
            it[DailyAnswersTable.elapsedMs] = elapsedMs
        }
    }

    fun JdbcTransaction.closeAttempt(id: UUID, status: SessionStatus, correctCount: Int, finishedAt: Instant): Boolean =
        DailyAttemptsTable.update({ (DailyAttemptsTable.id eq id) and (DailyAttemptsTable.status eq SessionStatus.ACTIVE.db) }) {
            it[DailyAttemptsTable.status] = status.db
            it[DailyAttemptsTable.correctCount] = correctCount.toShort()
            it[DailyAttemptsTable.finishedAt] = finishedAt.toDb()
        } > 0

    fun JdbcTransaction.overdueAttemptIds(now: Instant, limit: Int): List<UUID> =
        DailyAttemptsTable.select(DailyAttemptsTable.id)
            .where { (DailyAttemptsTable.status eq SessionStatus.ACTIVE.db) and (DailyAttemptsTable.deadlineAt less now.toDb()) }
            .orderBy(DailyAttemptsTable.deadlineAt to SortOrder.ASC)
            .limit(limit)
            .map { it[DailyAttemptsTable.id] }

    /** Users with an attempt (any status) for [dailyId] among [userIds]. */
    fun JdbcTransaction.usersWithAttempt(dailyId: UUID, userIds: Collection<UUID>): Set<UUID> {
        if (userIds.isEmpty()) return emptySet()
        return DailyAttemptsTable.select(DailyAttemptsTable.userId)
            .where { (DailyAttemptsTable.dailyId eq dailyId) and (DailyAttemptsTable.userId inList userIds) }
            .map { it[DailyAttemptsTable.userId] }
            .toSet()
    }

    private fun ResultRow.toAttempt() = AttemptRecord(
        id = this[DailyAttemptsTable.id],
        userId = this[DailyAttemptsTable.userId],
        dailyId = this[DailyAttemptsTable.dailyId],
        status = SessionStatus.fromDb(this[DailyAttemptsTable.status]),
        correctCount = this[DailyAttemptsTable.correctCount]?.toInt(),
        startedAt = this[DailyAttemptsTable.startedAt].toInstant(),
        deadlineAt = this[DailyAttemptsTable.deadlineAt].toInstant(),
        finishedAt = this[DailyAttemptsTable.finishedAt]?.toInstant(),
    )
}

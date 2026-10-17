package com.sabit.duelloquiz.backend.quiz

import com.sabit.duelloquiz.backend.common.I18nText
import com.sabit.duelloquiz.backend.common.db.ImagesTable
import com.sabit.duelloquiz.backend.common.db.OptionsTable
import com.sabit.duelloquiz.backend.common.db.QuestionsTable
import com.sabit.duelloquiz.backend.common.db.QuizAnswersTable
import com.sabit.duelloquiz.backend.common.db.QuizSessionQuestionsTable
import com.sabit.duelloquiz.backend.common.db.QuizSessionsTable
import com.sabit.duelloquiz.backend.common.db.toDb
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Random
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID

enum class SessionStatus(val db: String) {
    ACTIVE("active"), FINISHED("finished"), EXPIRED("expired");

    companion object {
        fun fromDb(value: String) = entries.first { it.db == value }
    }
}

data class SessionRecord(
    val id: UUID,
    val userId: UUID,
    val categoryId: Int,
    val status: SessionStatus,
    val correctCount: Int?,
    val startedAt: Instant,
    val deadlineAt: Instant,
    val finishedAt: Instant?,
)

data class OptionRecord(val id: UUID, val text: I18nText, val isCorrect: Boolean, val position: Int)

data class ImageRecord(
    val storageKey: String,
    val thumbKey: String,
    val width: Int,
    val height: Int,
    val dominantColor: String,
    val alt: I18nText?,
)

data class QuestionContent(
    val id: UUID,
    val text: I18nText,
    val explanation: I18nText?,
    val image: ImageRecord?,
    /** Ordered by stored position. */
    val options: List<OptionRecord>,
) {
    val correctOption: OptionRecord get() = options.first { it.isCorrect }
}

data class AnswerRecord(val questionId: UUID, val optionId: UUID?, val isCorrect: Boolean)

/**
 * Quiz persistence. Every function must run inside a transaction opened by the caller
 * ([com.sabit.duelloquiz.backend.common.Db.tx]) so the service controls locking.
 */
class QuizRepository {

    /** Up to [count] distinct random active questions of the category (OQ-021). */
    fun JdbcTransaction.randomActiveQuestionIds(categoryId: Int, count: Int): List<UUID> =
        QuestionsTable.select(QuestionsTable.id)
            .where { (QuestionsTable.categoryId eq categoryId) and (QuestionsTable.isActive eq true) }
            .orderBy(Random())
            .limit(count)
            .map { it[QuestionsTable.id] }

    fun JdbcTransaction.loadQuestions(ids: Collection<UUID>): Map<UUID, QuestionContent> {
        if (ids.isEmpty()) return emptyMap()
        val options = OptionsTable.selectAll()
            .where { OptionsTable.questionId inList ids }
            .orderBy(OptionsTable.position to SortOrder.ASC)
            .groupBy({ it[OptionsTable.questionId] }) {
                OptionRecord(
                    id = it[OptionsTable.id],
                    text = it[OptionsTable.textI18n],
                    isCorrect = it[OptionsTable.isCorrect],
                    position = it[OptionsTable.position].toInt(),
                )
            }
        return QuestionsTable
            .join(ImagesTable, JoinType.LEFT, QuestionsTable.imageId, ImagesTable.id)
            .selectAll()
            .where { QuestionsTable.id inList ids }
            .associate { row ->
                val id = row[QuestionsTable.id]
                id to QuestionContent(
                    id = id,
                    text = row[QuestionsTable.textI18n],
                    explanation = row[QuestionsTable.explanationI18n],
                    image = row.toImage(),
                    options = options[id].orEmpty(),
                )
            }
    }

    fun JdbcTransaction.insertSession(session: SessionRecord, questionIds: List<UUID>) {
        QuizSessionsTable.insert {
            it[id] = session.id
            it[userId] = session.userId
            it[categoryId] = session.categoryId
            it[status] = session.status.db
            it[startedAt] = session.startedAt.toDb()
            it[deadlineAt] = session.deadlineAt.toDb()
        }
        QuizSessionQuestionsTable.batchInsert(questionIds.withIndex()) { (index, questionId) ->
            this[QuizSessionQuestionsTable.sessionId] = session.id
            this[QuizSessionQuestionsTable.questionId] = questionId
            this[QuizSessionQuestionsTable.position] = (index + 1).toShort()
        }
    }

    fun JdbcTransaction.findSession(id: UUID, forUpdate: Boolean): SessionRecord? {
        val query = QuizSessionsTable.selectAll().where { QuizSessionsTable.id eq id }
        return (if (forUpdate) query.forUpdate() else query).singleOrNull()?.toSession()
    }

    /** Question ids of the session in position order. */
    fun JdbcTransaction.sessionQuestionIds(sessionId: UUID): List<UUID> =
        QuizSessionQuestionsTable.select(QuizSessionQuestionsTable.questionId)
            .where { QuizSessionQuestionsTable.sessionId eq sessionId }
            .orderBy(QuizSessionQuestionsTable.position to SortOrder.ASC)
            .map { it[QuizSessionQuestionsTable.questionId] }

    fun JdbcTransaction.answers(sessionId: UUID): Map<UUID, AnswerRecord> =
        QuizAnswersTable.selectAll()
            .where { QuizAnswersTable.sessionId eq sessionId }
            .associate { it[QuizAnswersTable.questionId] to it.toAnswer() }

    fun JdbcTransaction.insertAnswer(
        sessionId: UUID,
        questionId: UUID,
        optionId: UUID?,
        isCorrect: Boolean,
        answeredAt: Instant,
        elapsedMs: Int,
    ) {
        QuizAnswersTable.insert {
            it[QuizAnswersTable.sessionId] = sessionId
            it[QuizAnswersTable.questionId] = questionId
            it[QuizAnswersTable.optionId] = optionId
            it[QuizAnswersTable.isCorrect] = isCorrect
            it[QuizAnswersTable.answeredAt] = answeredAt.toDb()
            it[QuizAnswersTable.elapsedMs] = elapsedMs
        }
    }

    /** Closes an active session; returns false if it was already closed. */
    fun JdbcTransaction.closeSession(id: UUID, status: SessionStatus, correctCount: Int, finishedAt: Instant): Boolean =
        QuizSessionsTable.update({ (QuizSessionsTable.id eq id) and (QuizSessionsTable.status eq SessionStatus.ACTIVE.db) }) {
            it[QuizSessionsTable.status] = status.db
            it[QuizSessionsTable.correctCount] = correctCount.toShort()
            it[QuizSessionsTable.finishedAt] = finishedAt.toDb()
        } > 0

    fun JdbcTransaction.countCorrect(sessionId: UUID): Int =
        QuizAnswersTable.selectAll()
            .where { (QuizAnswersTable.sessionId eq sessionId) and (QuizAnswersTable.isCorrect eq true) }
            .count()
            .toInt()

    /** Active sessions whose deadline passed before [now] (for the expiry job), oldest first. */
    fun JdbcTransaction.overdueSessionIds(now: Instant, limit: Int): List<UUID> =
        QuizSessionsTable.select(QuizSessionsTable.id)
            .where { (QuizSessionsTable.status eq SessionStatus.ACTIVE.db) and (QuizSessionsTable.deadlineAt less now.toDb()) }
            .orderBy(QuizSessionsTable.deadlineAt to SortOrder.ASC)
            .limit(limit)
            .map { it[QuizSessionsTable.id] }

    private fun ResultRow.toSession() = SessionRecord(
        id = this[QuizSessionsTable.id],
        userId = this[QuizSessionsTable.userId],
        categoryId = this[QuizSessionsTable.categoryId],
        status = SessionStatus.fromDb(this[QuizSessionsTable.status]),
        correctCount = this[QuizSessionsTable.correctCount]?.toInt(),
        startedAt = this[QuizSessionsTable.startedAt].toInstant(),
        deadlineAt = this[QuizSessionsTable.deadlineAt].toInstant(),
        finishedAt = this[QuizSessionsTable.finishedAt]?.toInstant(),
    )

    private fun ResultRow.toAnswer() = AnswerRecord(
        questionId = this[QuizAnswersTable.questionId],
        optionId = this[QuizAnswersTable.optionId],
        isCorrect = this[QuizAnswersTable.isCorrect],
    )

    private fun ResultRow.toImage(): ImageRecord? {
        val key = this.getOrNull(ImagesTable.storageKey) ?: return null
        return ImageRecord(
            storageKey = key,
            thumbKey = this[ImagesTable.thumbKey],
            width = this[ImagesTable.width],
            height = this[ImagesTable.height],
            dominantColor = this[ImagesTable.dominantColor],
            // The question's own alt text wins over the image's default.
            alt = this[QuestionsTable.imageAltI18n] ?: this[ImagesTable.altI18n],
        )
    }
}

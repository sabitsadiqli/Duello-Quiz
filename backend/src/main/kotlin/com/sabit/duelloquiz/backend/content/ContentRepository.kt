package com.sabit.duelloquiz.backend.content

import com.sabit.duelloquiz.backend.category.CategoryRecord
import com.sabit.duelloquiz.backend.category.CategoryRepository.Companion.toCategory
import com.sabit.duelloquiz.backend.common.I18nText
import com.sabit.duelloquiz.backend.common.db.CategoriesTable
import com.sabit.duelloquiz.backend.common.db.OptionsTable
import com.sabit.duelloquiz.backend.common.db.QuestionsTable
import com.sabit.duelloquiz.backend.common.db.toDb
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID

data class StoredQuestion(val id: UUID, val contentHash: String)

/** A question as written by the importer; [options] are already in their stored positions (1..4). */
data class QuestionWrite(
    val externalId: String,
    val categoryId: Int,
    val difficulty: Int,
    val status: String,
    val isActive: Boolean,
    val text: I18nText,
    val explanation: I18nText?,
    val imageAlt: I18nText?,
    val contentHash: String,
    val options: List<ValidOption>,
)

/** Content persistence; every function runs inside the importer's transaction. */
class ContentRepository {

    fun JdbcTransaction.categoriesBySlug(): Map<String, CategoryRecord> =
        CategoriesTable.selectAll().map { it.toCategory() }.associateBy { it.slug }

    fun JdbcTransaction.insertCategory(category: ValidCategory, now: Instant): Int =
        CategoriesTable.insert {
            it[slug] = category.slug
            it[nameI18n] = category.name
            it[iconKey] = category.iconKey
            it[sortOrder] = category.sortOrder
            it[isActive] = category.active
            it[createdAt] = now.toDb()
            it[updatedAt] = now.toDb()
        }[CategoriesTable.id]

    fun JdbcTransaction.updateCategory(id: Int, category: ValidCategory, now: Instant) {
        CategoriesTable.update({ CategoriesTable.id eq id }) {
            it[nameI18n] = category.name
            it[iconKey] = category.iconKey
            it[sortOrder] = category.sortOrder
            it[isActive] = category.active
            it[updatedAt] = now.toDb()
        }
    }

    fun JdbcTransaction.questionsByExternalId(externalIds: Collection<String>): Map<String, StoredQuestion> {
        if (externalIds.isEmpty()) return emptyMap()
        return QuestionsTable.select(QuestionsTable.id, QuestionsTable.externalId, QuestionsTable.contentHash)
            .where { QuestionsTable.externalId inList externalIds }
            .associate {
                it[QuestionsTable.externalId] to StoredQuestion(it[QuestionsTable.id], it[QuestionsTable.contentHash])
            }
    }

    fun JdbcTransaction.insertQuestion(q: QuestionWrite, now: Instant) {
        val questionId = UUID.randomUUID()
        QuestionsTable.insert {
            it[id] = questionId
            it[externalId] = q.externalId
            it[categoryId] = q.categoryId
            it[difficulty] = q.difficulty.toShort()
            it[textI18n] = q.text
            it[explanationI18n] = q.explanation
            it[imageAltI18n] = q.imageAlt
            it[status] = q.status
            it[isActive] = q.isActive
            it[contentHash] = q.contentHash
            it[createdAt] = now.toDb()
            it[updatedAt] = now.toDb()
        }
        OptionsTable.batchInsert(q.options.withIndex()) { (index, option) ->
            this[OptionsTable.id] = UUID.randomUUID()
            this[OptionsTable.questionId] = questionId
            this[OptionsTable.textI18n] = option.text
            this[OptionsTable.isCorrect] = option.correct
            this[OptionsTable.position] = (index + 1).toShort()
        }
    }

    /**
     * Updates the question and its options in place (by position), so option ids stay
     * stable for stored quiz answers. Correct flags are cleared first to satisfy the
     * "one correct option" index while the correct answer moves.
     */
    fun JdbcTransaction.updateQuestion(id: UUID, q: QuestionWrite, now: Instant) {
        QuestionsTable.update({ QuestionsTable.id eq id }) {
            it[categoryId] = q.categoryId
            it[difficulty] = q.difficulty.toShort()
            it[textI18n] = q.text
            it[explanationI18n] = q.explanation
            it[imageAltI18n] = q.imageAlt
            it[status] = q.status
            it[isActive] = q.isActive
            it[contentHash] = q.contentHash
            it[updatedAt] = now.toDb()
        }
        OptionsTable.update({ OptionsTable.questionId eq id }) { it[isCorrect] = false }
        val existingPositions = OptionsTable.select(OptionsTable.position)
            .where { OptionsTable.questionId eq id }
            .map { it[OptionsTable.position].toInt() }
            .toSet()
        q.options.forEachIndexed { index, option ->
            val position = (index + 1).toShort()
            if (position.toInt() in existingPositions) {
                OptionsTable.update({ (OptionsTable.questionId eq id) and (OptionsTable.position eq position) }) {
                    it[textI18n] = option.text
                    it[isCorrect] = option.correct
                }
            } else {
                OptionsTable.insert {
                    it[OptionsTable.id] = UUID.randomUUID()
                    it[questionId] = id
                    it[textI18n] = option.text
                    it[isCorrect] = option.correct
                    it[OptionsTable.position] = position
                }
            }
        }
    }
}

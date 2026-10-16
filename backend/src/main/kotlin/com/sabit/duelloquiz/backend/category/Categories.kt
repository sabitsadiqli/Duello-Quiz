package com.sabit.duelloquiz.backend.category

import com.sabit.duelloquiz.backend.auth.authUser
import com.sabit.duelloquiz.backend.common.Db
import com.sabit.duelloquiz.backend.common.I18nText
import com.sabit.duelloquiz.backend.common.QuizConfig
import com.sabit.duelloquiz.backend.common.db.CategoriesTable
import com.sabit.duelloquiz.backend.common.db.QuestionsTable
import com.sabit.duelloquiz.backend.common.language
import com.sabit.duelloquiz.backend.common.resolve
import com.sabit.duelloquiz.backend.user.UserService
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

data class CategoryRecord(
    val id: Int,
    val slug: String,
    val name: I18nText,
    val iconKey: String,
    val sortOrder: Int,
    val isActive: Boolean,
)

class CategoryRepository(private val db: Db) {

    /** Active categories with at least [minActiveQuestions] active questions (OQ-013), sorted. */
    suspend fun listPlayable(minActiveQuestions: Int): List<CategoryRecord> = db.tx {
        val activeCount = QuestionsTable.id.count()
        CategoriesTable
            .join(
                QuestionsTable, JoinType.INNER, CategoriesTable.id, QuestionsTable.categoryId,
                additionalConstraint = { QuestionsTable.isActive eq true },
            )
            .select(CategoriesTable.columns + activeCount)
            .where { CategoriesTable.isActive eq true }
            .groupBy(*CategoriesTable.columns.toTypedArray())
            .having { activeCount greaterEq minActiveQuestions.toLong() }
            .orderBy(CategoriesTable.sortOrder to SortOrder.ASC, CategoriesTable.slug to SortOrder.ASC)
            .map { it.toCategory() }
    }

    suspend fun findById(id: Int): CategoryRecord? = db.tx {
        CategoriesTable.selectAll().where { CategoriesTable.id eq id }.singleOrNull()?.toCategory()
    }

    suspend fun countActiveQuestions(categoryId: Int): Long = db.tx {
        QuestionsTable.selectAll()
            .where { (QuestionsTable.categoryId eq categoryId) and (QuestionsTable.isActive eq true) }
            .count()
    }

    companion object {
        fun ResultRow.toCategory() = CategoryRecord(
            id = this[CategoriesTable.id],
            slug = this[CategoriesTable.slug],
            name = this[CategoriesTable.nameI18n],
            iconKey = this[CategoriesTable.iconKey],
            sortOrder = this[CategoriesTable.sortOrder],
            isActive = this[CategoriesTable.isActive],
        )
    }
}

class CategoryService(private val repository: CategoryRepository, private val quizConfig: QuizConfig) {
    suspend fun listPlayable(): List<CategoryRecord> = repository.listPlayable(quizConfig.questionsPerSession)
}

@Serializable
data class CategoryDto(val id: Int, val slug: String, val name: String, val iconKey: String)

@Serializable
data class CategoryListResponse(val items: List<CategoryDto>)

fun Route.categoryRoutes(service: CategoryService, users: UserService) {
    get("/categories") {
        users.getOrCreate(call.authUser)
        val language = call.language
        val items = service.listPlayable().map {
            CategoryDto(id = it.id, slug = it.slug, name = it.name.resolve(language), iconKey = it.iconKey)
        }
        call.respond(CategoryListResponse(items))
    }
}

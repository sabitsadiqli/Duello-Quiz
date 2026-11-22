package com.sabit.duelloquiz.backend.exam

import com.sabit.duelloquiz.backend.common.I18nText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Kinds of task groups (ADR 0002 §1). */
enum class GroupKind(val db: String) {
    READING("reading"), LISTENING("listening");

    companion object {
        fun of(db: String): GroupKind = entries.first { it.db == db }
    }
}

enum class TaskType(val db: String) {
    CLOSED_SINGLE("closed_single"), WRITTEN("written");

    companion object {
        fun of(db: String): TaskType = entries.first { it.db == db }
    }
}

@Serializable
data class ExamScoringConfig(
    /** `stage1`: score = maxScore · (writtenWeight · W + C) / rawMax, no penalty (mock-exams.md §2.1). */
    val rule: String,
    val writtenWeight: Int,
    val maxScore: Int,
)

@Serializable
data class ExamSectionTemplate(
    val id: String,
    val title: I18nText,
    val closed: Int,
    val written: Int,
    /** Kind of the one group every task of this section belongs to, or null (ungrouped tasks). */
    val group: String? = null,
) {
    val groupKind: GroupKind? get() = group?.let(GroupKind::of)
    val taskCount: Int get() = closed + written
}

/**
 * One DİM exam structure (glossary "Exam template"). The importer checks every variant
 * against it (EXAM-24); scoring reads the weights from it.
 */
@Serializable
data class ExamTemplate(
    val id: String,
    val title: I18nText,
    val stage: Int,
    val mode: String,
    val language: String,
    val scoring: ExamScoringConfig,
    /** OQ-113: options per closed task, 2–5. */
    val optionCount: Int,
    val sortOrder: Int,
    val sections: List<ExamSectionTemplate>,
) {
    init {
        require(optionCount in 2..5) { "optionCount must be 2..5" }
        require(scoring.rule == "stage1") { "unknown scoring rule ${scoring.rule}" }
        require(sections.isNotEmpty() && sections.map { it.id }.toSet().size == sections.size)
    }

    val taskCount: Int get() = sections.sumOf { it.taskCount }

    /** Stage 1: closed tasks count 1, written tasks `writtenWeight` (English: 23 + 2 · 7 = 37). */
    val rawMax: Int get() = sections.sumOf { it.closed + scoring.writtenWeight * it.written }

    fun section(id: String): ExamSectionTemplate? = sections.firstOrNull { it.id == id }
}

/** Registry of the templates in `resources/exams/templates.json`. */
class ExamTemplates(val all: List<ExamTemplate>) {
    private val byId = all.associateBy { it.id }

    operator fun get(id: String): ExamTemplate? = byId[id]

    companion object {
        @Serializable
        private data class File(val templates: List<ExamTemplate>)

        private val json = Json { ignoreUnknownKeys = true }

        fun load(): ExamTemplates {
            val text = ExamTemplates::class.java.getResourceAsStream("/exams/templates.json")
                ?.use { it.readBytes().decodeToString() }
                ?: error("exams/templates.json missing from the classpath")
            return ExamTemplates(json.decodeFromString(File.serializer(), text).templates)
        }
    }
}

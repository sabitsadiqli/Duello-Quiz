package com.sabit.duelloquiz.backend.common.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

/* Exposed mappings of V8__mock_exams.sql (docs/adr/0002-mock-exams-data-and-audio.md). */

private val examJson = Json { ignoreUnknownKeys = true }

object ExamTemplatesTable : Table("exam_templates") {
    val id = varchar("id", 64)
    val titleI18n = jsonb<Map<String, String>>("title_i18n", examJson)
    val stage = short("stage")
    val mode = varchar("mode", 16)
    val scoringRule = varchar("scoring_rule", 32)
    val accessTier = varchar("access_tier", 16).nullable()
    val sortOrder = integer("sort_order")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object ExamAudioTable : Table("exam_audio") {
    val id = javaUUID("id")
    val storageKey = varchar("storage_key", 255)
    val contentType = varchar("content_type", 32)
    val bytes = integer("bytes")
    val durationMs = integer("duration_ms")
    val sampleRate = integer("sample_rate")
    val channels = short("channels")
    val avgBitrate = integer("avg_bitrate")
    val sourceSha256 = varchar("source_sha256", 64)
    val fileSha256 = varchar("file_sha256", 64)
    val voices = jsonb<JsonElement>("voices", examJson)
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object ExamVariantsTable : Table("exam_variants") {
    val id = javaUUID("id")
    val externalId = varchar("external_id", 64)
    val templateId = varchar("template_id", 64)
    val number = integer("number")
    val status = varchar("status", 16)
    val currentVersionId = javaUUID("current_version_id").nullable()
    val publishApprovedBy = varchar("publish_approved_by", 64).nullable()
    val publishedAt = timestampWithTimeZone("published_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object ExamVariantVersionsTable : Table("exam_variant_versions") {
    val id = javaUUID("id")
    val variantId = javaUUID("variant_id")
    val version = integer("version")
    val contentHash = varchar("content_hash", 64)
    val language = varchar("language", 8)
    val durationMs = long("duration_ms")
    val graceMs = long("grace_ms")
    val packageBytes = long("package_bytes")
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object ExamGroupsTable : Table("exam_groups") {
    val id = javaUUID("id")
    val versionId = javaUUID("version_id")
    val externalId = varchar("external_id", 32)
    val section = varchar("section", 32)
    val kind = varchar("kind", 16)
    val position = short("position")
    val title = text("title").nullable()
    val instructionsI18n = jsonb<Map<String, String>>("instructions_i18n", examJson).nullable()
    val passage = text("passage").nullable()
    val licence = varchar("licence", 32)
    val author = text("author")
    val sourceUrl = text("source_url").nullable()
    val audioId = javaUUID("audio_id").nullable()
    val speakers = jsonb<JsonElement>("speakers", examJson).nullable()
    val script = jsonb<JsonElement>("script", examJson).nullable()
    override val primaryKey = PrimaryKey(id)
}

object ExamTasksTable : Table("exam_tasks") {
    val id = javaUUID("id")
    val versionId = javaUUID("version_id")
    val groupId = javaUUID("group_id").nullable()
    val externalId = varchar("external_id", 32)
    val section = varchar("section", 32)
    val position = short("position")
    val taskType = varchar("task_type", 16)
    val answerFormat = varchar("answer_format", 16).nullable()
    val prompt = text("prompt")
    val explanationI18n = jsonb<Map<String, String>>("explanation_i18n", examJson).nullable()
    val modelAnswer = text("model_answer").nullable()
    val criteria = jsonb<JsonElement>("criteria", examJson).nullable()
    val status = varchar("status", 16)
    val author = varchar("author", 64)
    val reviewedBy = varchar("reviewed_by", 64).nullable()
    override val primaryKey = PrimaryKey(id)
}

object ExamTaskOptionsTable : Table("exam_task_options") {
    val id = javaUUID("id")
    val taskId = javaUUID("task_id")
    val position = short("position")
    val text = text("text")
    val isCorrect = bool("is_correct")
    override val primaryKey = PrimaryKey(id)
}

object ExamAttemptsTable : Table("exam_attempts") {
    val id = javaUUID("id")
    val userId = javaUUID("user_id")
    val variantId = javaUUID("variant_id")
    val versionId = javaUUID("version_id")
    val mode = varchar("mode", 16)
    val status = varchar("status", 16)
    val isRetake = bool("is_retake")
    val skipListening = bool("skip_listening")
    val idempotencyKey = varchar("idempotency_key", 64)
    val holderDeviceId = varchar("holder_device_id", 64)
    val startedAt = timestampWithTimeZone("started_at")
    val startedDay = date("started_day")
    val endsAt = timestampWithTimeZone("ends_at")
    val deadlineAt = timestampWithTimeZone("deadline_at")
    val lateUntil = timestampWithTimeZone("late_until")
    val submittedAt = timestampWithTimeZone("submitted_at").nullable()
    val submitTrigger = varchar("submit_trigger", 8).nullable()
    val autoScore = decimal("auto_score", 5, 1).nullable()
    val writtenScore = decimal("written_score", 5, 1).nullable()
    val totalScore = decimal("total_score", 5, 1).nullable()
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object ExamAnswersTable : Table("exam_answers") {
    val attemptId = javaUUID("attempt_id")
    val taskId = javaUUID("task_id")
    val revision = integer("revision")
    val optionId = javaUUID("option_id").nullable()
    val answerText = varchar("answer_text", 1000).nullable()
    val flagged = bool("flagged")
    val savedAt = timestampWithTimeZone("saved_at")
    val receivedAt = timestampWithTimeZone("received_at")
    override val primaryKey = PrimaryKey(attemptId, taskId)
}

object ExamListeningPlaysTable : Table("exam_listening_plays") {
    val attemptId = javaUUID("attempt_id")
    val groupId = javaUUID("group_id")
    val playsUsed = short("plays_used")
    val audioFailed = bool("audio_failed")
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(attemptId, groupId)
}

object ExamSelfAssessmentsTable : Table("exam_self_assessments") {
    val attemptId = javaUUID("attempt_id")
    val taskId = javaUUID("task_id")
    val markSixths = short("mark_sixths")
    val assessedAt = timestampWithTimeZone("assessed_at")
    override val primaryKey = PrimaryKey(attemptId, taskId)
}

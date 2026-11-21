package com.sabit.duelloquiz.backend.exam

import com.sabit.duelloquiz.backend.common.I18nText
import com.sabit.duelloquiz.backend.common.db.ExamAnswersTable
import com.sabit.duelloquiz.backend.common.db.ExamAttemptsTable
import com.sabit.duelloquiz.backend.common.db.ExamAudioTable
import com.sabit.duelloquiz.backend.common.db.ExamGroupsTable
import com.sabit.duelloquiz.backend.common.db.ExamListeningPlaysTable
import com.sabit.duelloquiz.backend.common.db.ExamSelfAssessmentsTable
import com.sabit.duelloquiz.backend.common.db.ExamTaskOptionsTable
import com.sabit.duelloquiz.backend.common.db.ExamTasksTable
import com.sabit.duelloquiz.backend.common.db.ExamTemplatesTable
import com.sabit.duelloquiz.backend.common.db.ExamVariantVersionsTable
import com.sabit.duelloquiz.backend.common.db.ExamVariantsTable
import com.sabit.duelloquiz.backend.common.db.UsersTable
import com.sabit.duelloquiz.backend.common.db.toDb
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// ----------------------------------------------------------------------------- records

@Serializable
data class StoredSpeaker(val role: String, val name: String, val voice: String, val speakerId: Int? = null)

@Serializable
data class StoredLine(val speaker: String, val text: String)

@Serializable
data class StoredCriterion(val mark: String, val description: Map<String, String>)

@Serializable
data class StoredVoice(val role: String, val voice: String, val speakerId: Int? = null, val licence: String)

data class AudioRecord(
    val id: UUID,
    val storageKey: String,
    val contentType: String,
    val bytes: Int,
    val durationMs: Int,
    val sourceSha256: String,
    val fileSha256: String,
    val voices: List<StoredVoice>,
)

data class OptionRecord(val id: UUID, val position: Int, val text: String, val isCorrect: Boolean) {
    /** A–E (exam answer sheet letters). */
    val letter: String get() = ('A' + position - 1).toString()
}

data class GroupRecord(
    val id: UUID,
    val externalId: String,
    val section: String,
    val kind: GroupKind,
    val position: Int,
    val title: String?,
    val instructions: I18nText?,
    val passage: String?,
    val licence: String,
    val author: String,
    val sourceUrl: String?,
    val audio: AudioRecord?,
    val speakers: List<StoredSpeaker>,
    val script: List<StoredLine>,
)

data class TaskRecord(
    val id: UUID,
    val externalId: String,
    val groupId: UUID?,
    val section: String,
    val position: Int,
    val type: TaskType,
    val answerFormat: String?,
    val prompt: String,
    val explanation: I18nText?,
    val modelAnswer: String?,
    val criteria: List<StoredCriterion>,
    val status: String,
    val reviewedBy: String?,
    val options: List<OptionRecord>,
) {
    val correctOption: OptionRecord? get() = options.firstOrNull { it.isCorrect }
}

/** One immutable content version; cached by the service. */
data class VersionContent(
    val versionId: UUID,
    val variantId: UUID,
    val version: Int,
    val contentHash: String,
    val language: String,
    val durationMs: Long,
    val graceMs: Long,
    val packageBytes: Long,
    val groups: List<GroupRecord>,
    val tasks: List<TaskRecord>,
) {
    val tasksById: Map<UUID, TaskRecord> by lazy { tasks.associateBy { it.id } }
    val groupsById: Map<UUID, GroupRecord> by lazy { groups.associateBy { it.id } }
}

data class VariantRecord(
    val id: UUID,
    val externalId: String,
    val templateId: String,
    val number: Int,
    val status: String,
    val currentVersionId: UUID?,
    val publishApprovedBy: String?,
)

data class VersionRef(val id: UUID, val variantId: UUID, val version: Int, val contentHash: String, val packageBytes: Long)

enum class AttemptStatus(val db: String) {
    RUNNING("running"), AWAITING_UPLOAD("awaiting_upload"), SUBMITTED("submitted"), FINALIZED("finalized");

    val open: Boolean get() = this == RUNNING || this == AWAITING_UPLOAD

    companion object {
        fun of(db: String) = entries.first { it.db == db }
    }
}

data class ExamAttemptRecord(
    val id: UUID,
    val userId: UUID,
    val variantId: UUID,
    val versionId: UUID,
    val mode: String,
    val status: AttemptStatus,
    val isRetake: Boolean,
    val skipListening: Boolean,
    val idempotencyKey: String,
    val holderDeviceId: String,
    val startedAt: Instant,
    val startedDay: LocalDate,
    val endsAt: Instant,
    val deadlineAt: Instant,
    val lateUntil: Instant,
    val submittedAt: Instant?,
    val submitTrigger: String?,
    val autoScore: BigDecimal?,
    val writtenScore: BigDecimal?,
    val totalScore: BigDecimal?,
)

data class StoredAnswer(
    val taskId: UUID,
    val revision: Int,
    val optionId: UUID?,
    val text: String?,
    val flagged: Boolean,
    val savedAt: Instant,
)

data class StoredListening(val groupId: UUID, val playsUsed: Int, val audioFailed: Boolean)

/** Exam persistence; every function runs in the caller's transaction. */
class ExamRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    // -------------------------------------------------------------------- content

    fun JdbcTransaction.upsertTemplate(t: ExamTemplate, now: Instant) {
        val exists = ExamTemplatesTable.select(ExamTemplatesTable.id).where { ExamTemplatesTable.id eq t.id }.any()
        if (exists) {
            ExamTemplatesTable.update({ ExamTemplatesTable.id eq t.id }) {
                it[titleI18n] = t.title
                it[stage] = t.stage.toShort()
                it[mode] = t.mode
                it[scoringRule] = t.scoring.rule
                it[sortOrder] = t.sortOrder
                it[updatedAt] = now.toDb()
            }
        } else {
            ExamTemplatesTable.insert {
                it[id] = t.id
                it[titleI18n] = t.title
                it[stage] = t.stage.toShort()
                it[mode] = t.mode
                it[scoringRule] = t.scoring.rule
                it[accessTier] = null
                it[sortOrder] = t.sortOrder
                it[createdAt] = now.toDb()
                it[updatedAt] = now.toDb()
            }
        }
    }

    /** OQ-126: access tier per template (null = free). */
    fun JdbcTransaction.accessTiers(): Map<String, String?> =
        ExamTemplatesTable.select(ExamTemplatesTable.id, ExamTemplatesTable.accessTier)
            .associate { it[ExamTemplatesTable.id] to it[ExamTemplatesTable.accessTier] }

    fun JdbcTransaction.audioBySourceSha(shas: Collection<String>): Map<String, AudioRecord> {
        if (shas.isEmpty()) return emptyMap()
        return ExamAudioTable.selectAll().where { ExamAudioTable.sourceSha256 inList shas }
            .map { it.toAudio() }.associateBy { it.sourceSha256 }
    }

    fun JdbcTransaction.insertAudio(audio: AudioRecord, info: AudioInfo, now: Instant) {
        ExamAudioTable.insert {
            it[id] = audio.id
            it[storageKey] = audio.storageKey
            it[contentType] = audio.contentType
            it[bytes] = audio.bytes
            it[durationMs] = audio.durationMs
            it[sampleRate] = info.sampleRate
            it[channels] = info.channels.toShort()
            it[avgBitrate] = info.avgBitrate
            it[sourceSha256] = audio.sourceSha256
            it[fileSha256] = audio.fileSha256
            it[voices] = json.encodeToJsonElement(ListSerializer(StoredVoice.serializer()), audio.voices)
            it[createdAt] = now.toDb()
        }
    }

    fun JdbcTransaction.findVariantByExternalId(externalId: String): VariantRecord? =
        ExamVariantsTable.selectAll().where { ExamVariantsTable.externalId eq externalId }.forUpdate().singleOrNull()?.toVariant()

    /** External id of the variant that has [number] in [templateId], if any. */
    fun JdbcTransaction.findNumberOwner(templateId: String, number: Int): String? =
        ExamVariantsTable.select(ExamVariantsTable.externalId)
            .where { (ExamVariantsTable.templateId eq templateId) and (ExamVariantsTable.number eq number) }
            .singleOrNull()?.get(ExamVariantsTable.externalId)

    fun JdbcTransaction.findVariant(id: UUID): VariantRecord? =
        ExamVariantsTable.selectAll().where { ExamVariantsTable.id eq id }.singleOrNull()?.toVariant()

    fun JdbcTransaction.insertVariant(v: ValidVariant, id: UUID, now: Instant) {
        ExamVariantsTable.insert {
            it[ExamVariantsTable.id] = id
            it[externalId] = v.externalId
            it[templateId] = v.templateId
            it[number] = v.number
            it[status] = v.status
            it[publishApprovedBy] = v.publishApprovedBy
            it[publishedAt] = if (v.published) now.toDb() else null
            it[createdAt] = now.toDb()
            it[updatedAt] = now.toDb()
        }
    }

    fun JdbcTransaction.updateVariant(id: UUID, v: ValidVariant, currentVersionId: UUID, wasPublished: Boolean, now: Instant) {
        ExamVariantsTable.update({ ExamVariantsTable.id eq id }) {
            it[number] = v.number
            it[status] = v.status
            it[publishApprovedBy] = v.publishApprovedBy
            it[ExamVariantsTable.currentVersionId] = currentVersionId
            if (v.published && !wasPublished) it[publishedAt] = now.toDb()
            it[updatedAt] = now.toDb()
        }
    }

    fun JdbcTransaction.latestVersion(variantId: UUID): VersionRef? =
        ExamVariantVersionsTable.selectAll().where { ExamVariantVersionsTable.variantId eq variantId }
            .orderBy(ExamVariantVersionsTable.version to SortOrder.DESC).limit(1).singleOrNull()?.toVersionRef()

    fun JdbcTransaction.findVersion(variantId: UUID, version: Int): VersionRef? =
        ExamVariantVersionsTable.selectAll()
            .where { (ExamVariantVersionsTable.variantId eq variantId) and (ExamVariantVersionsTable.version eq version) }
            .singleOrNull()?.toVersionRef()

    fun JdbcTransaction.versionRef(versionId: UUID): VersionRef? =
        ExamVariantVersionsTable.selectAll().where { ExamVariantVersionsTable.id eq versionId }.singleOrNull()?.toVersionRef()

    /** Inserts a version with its groups, tasks and options; returns its id. */
    fun JdbcTransaction.insertVersion(
        variantId: UUID,
        version: Int,
        v: ValidVariant,
        contentHash: String,
        durationMs: Long,
        graceMs: Long,
        audioByFile: Map<String, AudioRecord>,
        now: Instant,
    ): UUID {
        val versionId = UUID.randomUUID()
        ExamVariantVersionsTable.insert {
            it[id] = versionId
            it[ExamVariantVersionsTable.variantId] = variantId
            it[ExamVariantVersionsTable.version] = version
            it[ExamVariantVersionsTable.contentHash] = contentHash
            it[language] = v.language
            it[ExamVariantVersionsTable.durationMs] = durationMs
            it[ExamVariantVersionsTable.graceMs] = graceMs
            it[packageBytes] = 0
            it[createdAt] = now.toDb()
        }
        val groupIds = v.groups.associate { it.id to UUID.randomUUID() }
        // Groups in the order of their first task.
        val groupOrder = v.tasks.mapNotNull { it.group }.distinct()
        val groups = v.groups.sortedBy { g -> groupOrder.indexOf(g.id).let { if (it < 0) Int.MAX_VALUE else it } }
        ExamGroupsTable.batchInsert(groups.withIndex()) { (index, g) ->
            this[ExamGroupsTable.id] = groupIds.getValue(g.id)
            this[ExamGroupsTable.versionId] = versionId
            this[ExamGroupsTable.externalId] = g.id
            this[ExamGroupsTable.section] = g.section
            this[ExamGroupsTable.kind] = g.kind.db
            this[ExamGroupsTable.position] = (index + 1).toShort()
            this[ExamGroupsTable.title] = g.title
            this[ExamGroupsTable.instructionsI18n] = g.instructions
            this[ExamGroupsTable.passage] = g.passage
            this[ExamGroupsTable.licence] = g.licence
            this[ExamGroupsTable.author] = g.author
            this[ExamGroupsTable.sourceUrl] = g.sourceUrl
            this[ExamGroupsTable.audioId] = g.audioFile?.let { audioByFile.getValue(it).id }
            this[ExamGroupsTable.speakers] = if (g.kind == GroupKind.LISTENING) {
                json.encodeToJsonElement(
                    ListSerializer(StoredSpeaker.serializer()),
                    g.speakers.map { StoredSpeaker(it.role, it.name, it.voice, it.speakerId) },
                )
            } else {
                null
            }
            this[ExamGroupsTable.script] = if (g.kind == GroupKind.LISTENING) {
                json.encodeToJsonElement(ListSerializer(StoredLine.serializer()), g.script.map { StoredLine(it.speaker, it.text) })
            } else {
                null
            }
        }
        val taskIds = v.tasks.map { UUID.randomUUID() }
        ExamTasksTable.batchInsert(v.tasks.withIndex()) { (index, t) ->
            this[ExamTasksTable.id] = taskIds[index]
            this[ExamTasksTable.versionId] = versionId
            this[ExamTasksTable.groupId] = t.group?.let { groupIds.getValue(it) }
            this[ExamTasksTable.externalId] = t.id
            this[ExamTasksTable.section] = t.section
            this[ExamTasksTable.position] = (index + 1).toShort()
            this[ExamTasksTable.taskType] = t.type.db
            this[ExamTasksTable.answerFormat] = t.answerFormat
            this[ExamTasksTable.prompt] = t.prompt
            this[ExamTasksTable.explanationI18n] = t.explanation
            this[ExamTasksTable.modelAnswer] = t.modelAnswer
            this[ExamTasksTable.criteria] = if (t.type == TaskType.WRITTEN) {
                json.encodeToJsonElement(
                    ListSerializer(StoredCriterion.serializer()),
                    t.criteria.map { StoredCriterion(it.mark.api, it.description) },
                )
            } else {
                null
            }
            this[ExamTasksTable.status] = t.status
            this[ExamTasksTable.author] = t.author
            this[ExamTasksTable.reviewedBy] = t.reviewedBy
        }
        val options = v.tasks.withIndex().flatMap { (index, t) -> t.options.withIndex().map { (i, o) -> Triple(taskIds[index], i + 1, o) } }
        ExamTaskOptionsTable.batchInsert(options) { (taskId, position, o) ->
            this[ExamTaskOptionsTable.id] = UUID.randomUUID()
            this[ExamTaskOptionsTable.taskId] = taskId
            this[ExamTaskOptionsTable.position] = position.toShort()
            this[ExamTaskOptionsTable.text] = o.text
            this[ExamTaskOptionsTable.isCorrect] = o.correct
        }
        return versionId
    }

    /** Status and reviewer are not content: updated in place on the current version. */
    fun JdbcTransaction.updateTaskReviews(versionId: UUID, v: ValidVariant): Int {
        var changed = 0
        val stored = ExamTasksTable.select(ExamTasksTable.externalId, ExamTasksTable.status, ExamTasksTable.reviewedBy)
            .where { ExamTasksTable.versionId eq versionId }
            .associate { it[ExamTasksTable.externalId] to (it[ExamTasksTable.status] to it[ExamTasksTable.reviewedBy]) }
        for (t in v.tasks) {
            if (stored[t.id] == (t.status to t.reviewedBy)) continue
            ExamTasksTable.update({ (ExamTasksTable.versionId eq versionId) and (ExamTasksTable.externalId eq t.id) }) {
                it[status] = t.status
                it[reviewedBy] = t.reviewedBy
                it[author] = t.author
            }
            changed++
        }
        return changed
    }

    fun JdbcTransaction.setPackageBytes(versionId: UUID, bytes: Long) {
        ExamVariantVersionsTable.update({ ExamVariantVersionsTable.id eq versionId }) { it[packageBytes] = bytes }
    }

    fun JdbcTransaction.loadVersion(versionId: UUID): VersionContent? {
        val ref = ExamVariantVersionsTable.selectAll().where { ExamVariantVersionsTable.id eq versionId }.singleOrNull() ?: return null
        val groupRows = ExamGroupsTable.selectAll().where { ExamGroupsTable.versionId eq versionId }
            .orderBy(ExamGroupsTable.position to SortOrder.ASC).toList()
        val audioIds = groupRows.mapNotNull { it[ExamGroupsTable.audioId] }
        val audio = if (audioIds.isEmpty()) {
            emptyMap()
        } else {
            ExamAudioTable.selectAll().where { ExamAudioTable.id inList audioIds }.map { it.toAudio() }.associateBy { it.id }
        }
        val groups = groupRows.map { r ->
            GroupRecord(
                id = r[ExamGroupsTable.id],
                externalId = r[ExamGroupsTable.externalId],
                section = r[ExamGroupsTable.section],
                kind = GroupKind.of(r[ExamGroupsTable.kind]),
                position = r[ExamGroupsTable.position].toInt(),
                title = r[ExamGroupsTable.title],
                instructions = r[ExamGroupsTable.instructionsI18n],
                passage = r[ExamGroupsTable.passage],
                licence = r[ExamGroupsTable.licence],
                author = r[ExamGroupsTable.author],
                sourceUrl = r[ExamGroupsTable.sourceUrl],
                audio = r[ExamGroupsTable.audioId]?.let { audio[it] },
                speakers = r[ExamGroupsTable.speakers]?.let { json.decodeFromJsonElement(ListSerializer(StoredSpeaker.serializer()), it) }.orEmpty(),
                script = r[ExamGroupsTable.script]?.let { json.decodeFromJsonElement(ListSerializer(StoredLine.serializer()), it) }.orEmpty(),
            )
        }
        val taskRows = ExamTasksTable.selectAll().where { ExamTasksTable.versionId eq versionId }
            .orderBy(ExamTasksTable.position to SortOrder.ASC).toList()
        val taskIds = taskRows.map { it[ExamTasksTable.id] }
        val options = if (taskIds.isEmpty()) {
            emptyMap()
        } else {
            ExamTaskOptionsTable.selectAll().where { ExamTaskOptionsTable.taskId inList taskIds }
                .orderBy(ExamTaskOptionsTable.position to SortOrder.ASC)
                .groupBy({ it[ExamTaskOptionsTable.taskId] }) {
                    OptionRecord(it[ExamTaskOptionsTable.id], it[ExamTaskOptionsTable.position].toInt(), it[ExamTaskOptionsTable.text], it[ExamTaskOptionsTable.isCorrect])
                }
        }
        val tasks = taskRows.map { r ->
            TaskRecord(
                id = r[ExamTasksTable.id],
                externalId = r[ExamTasksTable.externalId],
                groupId = r[ExamTasksTable.groupId],
                section = r[ExamTasksTable.section],
                position = r[ExamTasksTable.position].toInt(),
                type = TaskType.of(r[ExamTasksTable.taskType]),
                answerFormat = r[ExamTasksTable.answerFormat],
                prompt = r[ExamTasksTable.prompt],
                explanation = r[ExamTasksTable.explanationI18n],
                modelAnswer = r[ExamTasksTable.modelAnswer],
                criteria = r[ExamTasksTable.criteria]?.let { json.decodeFromJsonElement(ListSerializer(StoredCriterion.serializer()), it) }.orEmpty(),
                status = r[ExamTasksTable.status],
                reviewedBy = r[ExamTasksTable.reviewedBy],
                options = options[r[ExamTasksTable.id]].orEmpty(),
            )
        }
        return VersionContent(
            versionId = versionId,
            variantId = ref[ExamVariantVersionsTable.variantId],
            version = ref[ExamVariantVersionsTable.version],
            contentHash = ref[ExamVariantVersionsTable.contentHash],
            language = ref[ExamVariantVersionsTable.language],
            durationMs = ref[ExamVariantVersionsTable.durationMs],
            graceMs = ref[ExamVariantVersionsTable.graceMs],
            packageBytes = ref[ExamVariantVersionsTable.packageBytes],
            groups = groups,
            tasks = tasks,
        )
    }

    /** Published variants with their current version, catalogue order. */
    fun JdbcTransaction.publishedVariants(): List<Pair<VariantRecord, VersionRef>> {
        val variants = ExamVariantsTable.selectAll()
            .where { (ExamVariantsTable.status eq ExamValidator.PUBLISHED) and ExamVariantsTable.currentVersionId.isNotNull() }
            .orderBy(ExamVariantsTable.templateId to SortOrder.ASC, ExamVariantsTable.number to SortOrder.ASC)
            .map { it.toVariant() }
        if (variants.isEmpty()) return emptyList()
        val versions = ExamVariantVersionsTable.selectAll()
            .where { ExamVariantVersionsTable.id inList variants.map { it.currentVersionId!! } }
            .associate { it[ExamVariantVersionsTable.id] to it.toVersionRef() }
        return variants.mapNotNull { v -> versions[v.currentVersionId]?.let { v to it } }
    }

    // -------------------------------------------------------------------- attempts

    /** Serializes starts of one user (one running attempt, daily limit). */
    fun JdbcTransaction.lockUser(userId: UUID): Boolean =
        UsersTable.select(UsersTable.id).where { UsersTable.id eq userId }.forUpdate().any()

    fun JdbcTransaction.findAttempt(id: UUID, forUpdate: Boolean = false): ExamAttemptRecord? {
        val query = ExamAttemptsTable.selectAll().where { ExamAttemptsTable.id eq id }
        return (if (forUpdate) query.forUpdate() else query).singleOrNull()?.toAttempt()
    }

    fun JdbcTransaction.findByIdempotencyKey(userId: UUID, key: String): ExamAttemptRecord? =
        ExamAttemptsTable.selectAll()
            .where { (ExamAttemptsTable.userId eq userId) and (ExamAttemptsTable.idempotencyKey eq key) }
            .singleOrNull()?.toAttempt()

    /** Running or awaiting upload, newest first. */
    fun JdbcTransaction.openAttempts(userId: UUID): List<ExamAttemptRecord> =
        ExamAttemptsTable.selectAll()
            .where { (ExamAttemptsTable.userId eq userId) and (ExamAttemptsTable.status inList listOf(AttemptStatus.RUNNING.db, AttemptStatus.AWAITING_UPLOAD.db)) }
            .orderBy(ExamAttemptsTable.startedAt to SortOrder.DESC)
            .map { it.toAttempt() }

    /** `running` attempts of [userId] whose deadline passed become `awaiting_upload`. */
    fun JdbcTransaction.markOverdue(userId: UUID, now: Instant): Int =
        ExamAttemptsTable.update({
            (ExamAttemptsTable.userId eq userId) and (ExamAttemptsTable.status eq AttemptStatus.RUNNING.db) and
                (ExamAttemptsTable.deadlineAt less now.toDb())
        }) {
            it[status] = AttemptStatus.AWAITING_UPLOAD.db
            it[updatedAt] = now.toDb()
        }

    fun JdbcTransaction.countStarts(userId: UUID, day: LocalDate): Long =
        ExamAttemptsTable.select(ExamAttemptsTable.id)
            .where { (ExamAttemptsTable.userId eq userId) and (ExamAttemptsTable.startedDay eq day) }
            .count()

    fun JdbcTransaction.hasFinished(userId: UUID, variantId: UUID): Boolean =
        ExamAttemptsTable.select(ExamAttemptsTable.id)
            .where {
                (ExamAttemptsTable.userId eq userId) and (ExamAttemptsTable.variantId eq variantId) and
                    (ExamAttemptsTable.status inList listOf(AttemptStatus.SUBMITTED.db, AttemptStatus.FINALIZED.db))
            }.any()

    fun JdbcTransaction.hasAttemptOnVersion(userId: UUID, versionId: UUID): Boolean =
        ExamAttemptsTable.select(ExamAttemptsTable.id)
            .where { (ExamAttemptsTable.userId eq userId) and (ExamAttemptsTable.versionId eq versionId) }.any()

    fun JdbcTransaction.attemptsOf(userId: UUID, limit: Int?): List<ExamAttemptRecord> {
        val query = ExamAttemptsTable.selectAll().where { ExamAttemptsTable.userId eq userId }
            .orderBy(ExamAttemptsTable.startedAt to SortOrder.DESC, ExamAttemptsTable.id to SortOrder.ASC)
        return (if (limit != null) query.limit(limit) else query).map { it.toAttempt() }
    }

    fun JdbcTransaction.insertAttempt(a: ExamAttemptRecord, now: Instant) {
        ExamAttemptsTable.insert {
            it[id] = a.id
            it[userId] = a.userId
            it[variantId] = a.variantId
            it[versionId] = a.versionId
            it[mode] = a.mode
            it[status] = a.status.db
            it[isRetake] = a.isRetake
            it[skipListening] = a.skipListening
            it[idempotencyKey] = a.idempotencyKey
            it[holderDeviceId] = a.holderDeviceId
            it[startedAt] = a.startedAt.toDb()
            it[startedDay] = a.startedDay
            it[endsAt] = a.endsAt.toDb()
            it[deadlineAt] = a.deadlineAt.toDb()
            it[lateUntil] = a.lateUntil.toDb()
            it[updatedAt] = now.toDb()
        }
    }

    fun JdbcTransaction.setHolder(attemptId: UUID, deviceId: String, now: Instant) {
        ExamAttemptsTable.update({ ExamAttemptsTable.id eq attemptId }) {
            it[holderDeviceId] = deviceId
            it[updatedAt] = now.toDb()
        }
    }

    fun JdbcTransaction.setStatus(attemptId: UUID, status: AttemptStatus, now: Instant) {
        ExamAttemptsTable.update({ ExamAttemptsTable.id eq attemptId }) {
            it[ExamAttemptsTable.status] = status.db
            it[updatedAt] = now.toDb()
        }
    }

    fun JdbcTransaction.close(attemptId: UUID, status: AttemptStatus, trigger: String, submittedAt: Instant, score: ExamScore) {
        ExamAttemptsTable.update({ ExamAttemptsTable.id eq attemptId }) {
            it[ExamAttemptsTable.status] = status.db
            it[submitTrigger] = trigger
            it[ExamAttemptsTable.submittedAt] = submittedAt.toDb()
            it[autoScore] = score.autoScore
            it[writtenScore] = score.writtenScore
            it[totalScore] = score.total
            it[updatedAt] = submittedAt.toDb()
        }
    }

    fun JdbcTransaction.storeScore(attemptId: UUID, score: ExamScore, now: Instant) {
        ExamAttemptsTable.update({ ExamAttemptsTable.id eq attemptId }) {
            it[autoScore] = score.autoScore
            it[writtenScore] = score.writtenScore
            it[totalScore] = score.total
            it[updatedAt] = now.toDb()
        }
    }

    /** Open attempts whose late-upload window ended (sweep job). */
    fun JdbcTransaction.pastWindow(now: Instant, limit: Int): List<UUID> =
        ExamAttemptsTable.select(ExamAttemptsTable.id)
            .where {
                (ExamAttemptsTable.status inList listOf(AttemptStatus.RUNNING.db, AttemptStatus.AWAITING_UPLOAD.db)) and
                    (ExamAttemptsTable.lateUntil lessEq now.toDb())
            }
            .limit(limit)
            .map { it[ExamAttemptsTable.id] }

    fun JdbcTransaction.answers(attemptId: UUID): Map<UUID, StoredAnswer> =
        ExamAnswersTable.selectAll().where { ExamAnswersTable.attemptId eq attemptId }.associate {
            it[ExamAnswersTable.taskId] to StoredAnswer(
                taskId = it[ExamAnswersTable.taskId],
                revision = it[ExamAnswersTable.revision],
                optionId = it[ExamAnswersTable.optionId],
                text = it[ExamAnswersTable.answerText],
                flagged = it[ExamAnswersTable.flagged],
                savedAt = it[ExamAnswersTable.savedAt].toInstant(),
            )
        }

    fun JdbcTransaction.writeAnswer(attemptId: UUID, answer: StoredAnswer, exists: Boolean, now: Instant) {
        if (exists) {
            ExamAnswersTable.update({ (ExamAnswersTable.attemptId eq attemptId) and (ExamAnswersTable.taskId eq answer.taskId) }) {
                it[revision] = answer.revision
                it[optionId] = answer.optionId
                it[answerText] = answer.text
                it[flagged] = answer.flagged
                it[savedAt] = answer.savedAt.toDb()
                it[receivedAt] = now.toDb()
            }
        } else {
            ExamAnswersTable.insert {
                it[ExamAnswersTable.attemptId] = attemptId
                it[taskId] = answer.taskId
                it[revision] = answer.revision
                it[optionId] = answer.optionId
                it[answerText] = answer.text
                it[flagged] = answer.flagged
                it[savedAt] = answer.savedAt.toDb()
                it[receivedAt] = now.toDb()
            }
        }
    }

    fun JdbcTransaction.listening(attemptId: UUID): Map<UUID, StoredListening> =
        ExamListeningPlaysTable.selectAll().where { ExamListeningPlaysTable.attemptId eq attemptId }.associate {
            it[ExamListeningPlaysTable.groupId] to StoredListening(
                it[ExamListeningPlaysTable.groupId], it[ExamListeningPlaysTable.playsUsed].toInt(), it[ExamListeningPlaysTable.audioFailed],
            )
        }

    fun JdbcTransaction.writeListening(attemptId: UUID, state: StoredListening, exists: Boolean, now: Instant) {
        if (exists) {
            ExamListeningPlaysTable.update({ (ExamListeningPlaysTable.attemptId eq attemptId) and (ExamListeningPlaysTable.groupId eq state.groupId) }) {
                it[playsUsed] = state.playsUsed.toShort()
                it[audioFailed] = state.audioFailed
                it[updatedAt] = now.toDb()
            }
        } else {
            ExamListeningPlaysTable.insert {
                it[ExamListeningPlaysTable.attemptId] = attemptId
                it[groupId] = state.groupId
                it[playsUsed] = state.playsUsed.toShort()
                it[audioFailed] = state.audioFailed
                it[updatedAt] = now.toDb()
            }
        }
    }

    fun JdbcTransaction.marks(attemptId: UUID): Map<UUID, Mark> =
        ExamSelfAssessmentsTable.selectAll().where { ExamSelfAssessmentsTable.attemptId eq attemptId }.associate {
            it[ExamSelfAssessmentsTable.taskId] to Mark.ofSixths(it[ExamSelfAssessmentsTable.markSixths].toInt())!!
        }

    fun JdbcTransaction.setMark(attemptId: UUID, taskId: UUID, mark: Mark?, now: Instant) {
        ExamSelfAssessmentsTable.deleteWhere { (ExamSelfAssessmentsTable.attemptId eq attemptId) and (ExamSelfAssessmentsTable.taskId eq taskId) }
        if (mark != null) {
            ExamSelfAssessmentsTable.insert {
                it[ExamSelfAssessmentsTable.attemptId] = attemptId
                it[ExamSelfAssessmentsTable.taskId] = taskId
                it[markSixths] = mark.sixths.toShort()
                it[assessedAt] = now.toDb()
            }
        }
    }

    // -------------------------------------------------------------------- mapping

    private fun ResultRow.toAudio() = AudioRecord(
        id = this[ExamAudioTable.id],
        storageKey = this[ExamAudioTable.storageKey],
        contentType = this[ExamAudioTable.contentType],
        bytes = this[ExamAudioTable.bytes],
        durationMs = this[ExamAudioTable.durationMs],
        sourceSha256 = this[ExamAudioTable.sourceSha256],
        fileSha256 = this[ExamAudioTable.fileSha256],
        voices = json.decodeFromJsonElement(ListSerializer(StoredVoice.serializer()), this[ExamAudioTable.voices]),
    )

    private fun ResultRow.toVariant() = VariantRecord(
        id = this[ExamVariantsTable.id],
        externalId = this[ExamVariantsTable.externalId],
        templateId = this[ExamVariantsTable.templateId],
        number = this[ExamVariantsTable.number],
        status = this[ExamVariantsTable.status],
        currentVersionId = this[ExamVariantsTable.currentVersionId],
        publishApprovedBy = this[ExamVariantsTable.publishApprovedBy],
    )

    private fun ResultRow.toVersionRef() = VersionRef(
        id = this[ExamVariantVersionsTable.id],
        variantId = this[ExamVariantVersionsTable.variantId],
        version = this[ExamVariantVersionsTable.version],
        contentHash = this[ExamVariantVersionsTable.contentHash],
        packageBytes = this[ExamVariantVersionsTable.packageBytes],
    )

    private fun ResultRow.toAttempt() = ExamAttemptRecord(
        id = this[ExamAttemptsTable.id],
        userId = this[ExamAttemptsTable.userId],
        variantId = this[ExamAttemptsTable.variantId],
        versionId = this[ExamAttemptsTable.versionId],
        mode = this[ExamAttemptsTable.mode],
        status = AttemptStatus.of(this[ExamAttemptsTable.status]),
        isRetake = this[ExamAttemptsTable.isRetake],
        skipListening = this[ExamAttemptsTable.skipListening],
        idempotencyKey = this[ExamAttemptsTable.idempotencyKey],
        holderDeviceId = this[ExamAttemptsTable.holderDeviceId],
        startedAt = this[ExamAttemptsTable.startedAt].toInstant(),
        startedDay = this[ExamAttemptsTable.startedDay],
        endsAt = this[ExamAttemptsTable.endsAt].toInstant(),
        deadlineAt = this[ExamAttemptsTable.deadlineAt].toInstant(),
        lateUntil = this[ExamAttemptsTable.lateUntil].toInstant(),
        submittedAt = this[ExamAttemptsTable.submittedAt]?.toInstant(),
        submitTrigger = this[ExamAttemptsTable.submitTrigger],
        autoScore = this[ExamAttemptsTable.autoScore],
        writtenScore = this[ExamAttemptsTable.writtenScore],
        totalScore = this[ExamAttemptsTable.totalScore],
    )
}

package com.sabit.duelloquiz.backend.exam

import com.sabit.duelloquiz.backend.common.Db
import com.sabit.duelloquiz.backend.content.ImportIssue
import com.sabit.duelloquiz.backend.content.ImportValidationException
import com.sabit.duelloquiz.backend.image.ImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.slf4j.LoggerFactory
import java.io.IOException
import java.security.MessageDigest
import java.time.Clock
import java.util.UUID

/** An audio file after the checks: stripped bytes, ready to store. */
internal class PreparedAudio(val sourceSha: String, val bytes: ByteArray, val fileSha: String, val info: AudioInfo, val voices: List<StoredVoice>)

/** Reads a file of a variant directory (e.g. `audio/listening-1.m4a`); null if it does not exist. */
fun interface VariantFiles {
    /** Blocking; called on the IO dispatcher. */
    fun read(relativePath: String): ByteArray?
}

data class ExamVariantInput(val ref: String, val element: JsonElement, val files: VariantFiles)

data class ExamImportInput(val variants: List<ExamVariantInput>, val preIssues: List<ImportIssue> = emptyList())

@Serializable
data class ExamImportReport(
    val variantsCreated: Int,
    val versionsCreated: Int,
    /** Only status, reviewer or publish approval changed (no new version). */
    val statusUpdated: Int,
    val unchanged: Int,
    val published: Int,
    val audioCreated: Int,
)

/**
 * Imports mock-exam variants (EXAM-24, ADR 0002 §2). All-or-nothing: every variant is
 * validated against its template and every audio file is checked and stripped before
 * anything is written; one problem rejects the whole run. Idempotent: an unchanged
 * variant writes nothing, a content change creates a new immutable version, and an audio
 * original stored before is reused.
 */
class ExamImporter(
    private val db: Db,
    private val repository: ExamRepository,
    private val storage: ImageStorage,
    private val templates: ExamTemplates,
    private val views: ExamViews,
    private val config: ExamConfig,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(ExamImporter::class.java)
    private val validator = ExamValidator(templates, config)

    class PreparedVariant internal constructor(val valid: ValidVariant, internal val audioShaByFile: Map<String, String>)

    /** Result of [validate]: safe to [write]. */
    class Prepared internal constructor(val variants: List<PreparedVariant>, internal val audio: Map<String, PreparedAudio>)

    suspend fun import(input: ExamImportInput): ExamImportReport = write(validate(input))

    /** @throws ImportValidationException with every problem; nothing has been written. */
    suspend fun validate(input: ExamImportInput): Prepared {
        val issues = input.preIssues.toMutableList()
        val parsed = input.variants.mapNotNull { item -> validator.validate(item.element, item.ref, issues)?.let { item to it } }
        parsed.groupBy { it.second.externalId }.filterValues { it.size > 1 }.keys.forEach {
            issues += ImportIssue(it, "duplicate_id", "Variant id appears more than once")
        }
        parsed.groupBy { it.second.templateId to it.second.number }.filterValues { it.size > 1 }.forEach { (key, list) ->
            list.forEach { issues += ImportIssue(it.second.externalId, "duplicate_number", "Number ${key.second} is used twice in ${key.first}") }
        }
        if (parsed.isNotEmpty()) {
            db.tx {
                parsed.forEach { (_, v) ->
                    val clash = repository.run { findNumberOwner(v.templateId, v.number) }
                    if (clash != null && clash != v.externalId) {
                        issues += ImportIssue(v.externalId, "duplicate_number", "Number ${v.number} of ${v.templateId} belongs to $clash")
                    }
                }
            }
        }

        val audio = LinkedHashMap<String, PreparedAudio>()
        val prepared = mutableListOf<PreparedVariant>()
        for ((item, variant) in parsed) {
            val shaByFile = LinkedHashMap<String, String>()
            for (group in variant.groups.filter { it.audioFile != null }) {
                val file = group.audioFile!!
                val ref = "${variant.externalId}/${group.id}"
                val bytes = try {
                    withContext(Dispatchers.IO) { item.files.read(file) }
                } catch (e: IOException) {
                    null
                }
                if (bytes == null) {
                    issues += ImportIssue(ref, "audio_missing", "Audio file $file not found next to variant.json")
                    continue
                }
                if (bytes.size > config.audio.maxBytes) {
                    issues += ImportIssue(ref, Mp4Audio.Codes.TOO_LARGE, "$file has ${bytes.size} bytes (max ${config.audio.maxBytes})")
                    continue
                }
                val sourceSha = sha256Hex(bytes)
                shaByFile[file] = sourceSha
                if (sourceSha in audio) continue
                try {
                    val stripped = Mp4Audio.stripMetadata(bytes)
                    val info = Mp4Audio.probe(stripped, config.audio)
                    val voices = group.speakers.map {
                        StoredVoice(it.role, it.voice, it.speakerId, ExamValidator.VOICE_LICENCES.getValue(it.voice))
                    }
                    audio[sourceSha] = PreparedAudio(sourceSha, stripped, sha256Hex(stripped), info, voices)
                } catch (e: AudioRejectedException) {
                    issues += ImportIssue(ref, e.code, "$file: ${e.message}")
                }
            }
            prepared += PreparedVariant(variant, shaByFile)
        }
        if (issues.isNotEmpty()) throw ImportValidationException(issues)
        return Prepared(prepared, audio)
    }

    suspend fun write(prepared: Prepared): ExamImportReport {
        val audio = prepared.audio
        val now = clock.instant()
        val known = db.tx { repository.run { audioBySourceSha(audio.keys) } }
        // New files first (immutable keys); removed again if the transaction fails.
        val written = mutableListOf<AudioRecord>()
        val toInsert = mutableListOf<Pair<AudioRecord, AudioInfo>>()
        try {
            for (a in audio.values) {
                if (a.sourceSha in known) continue
                val id = UUID.randomUUID()
                val record = AudioRecord(
                    id = id,
                    storageKey = audioKey(id),
                    contentType = Mp4Audio.CONTENT_TYPE,
                    bytes = a.bytes.size,
                    durationMs = a.info.durationMs.toInt(),
                    sourceSha256 = a.sourceSha,
                    fileSha256 = a.fileSha,
                    voices = a.voices,
                )
                storage.put(record.storageKey, a.bytes, Mp4Audio.CONTENT_TYPE)
                written += record
                toInsert += record to a.info
            }
            val report = db.tx { writeAll(prepared.variants, known, toInsert, now) }
            log.info(
                "exam_import variantsCreated={} versionsCreated={} statusUpdated={} unchanged={} published={} audioCreated={}",
                report.variantsCreated, report.versionsCreated, report.statusUpdated, report.unchanged, report.published, report.audioCreated,
            )
            return report
        } catch (e: Exception) {
            written.forEach { runCatching { storage.delete(it.storageKey) } }
            throw e
        }
    }

    private fun JdbcTransaction.writeAll(
        variants: List<PreparedVariant>,
        known: Map<String, AudioRecord>,
        toInsert: List<Pair<AudioRecord, AudioInfo>>,
        now: java.time.Instant,
    ): ExamImportReport = with(repository) {
        variants.map { it.valid.templateId }.distinct().forEach { upsertTemplate(templates[it]!!, now) }
        toInsert.forEach { (record, info) -> insertAudio(record, info, now) }
        val audioBySha = known + toInsert.associate { it.first.sourceSha256 to it.first }

        var created = 0
        var versions = 0
        var statusOnly = 0
        var unchanged = 0
        for (p in variants) {
            val v = p.valid
            val template = templates[v.templateId]!!
            val duration = config.durationFor(template.mode)
            val hash = contentHash(v, p.audioShaByFile, duration, config.graceMs)
            val audioByFile = p.audioShaByFile.mapValues { audioBySha.getValue(it.value) }
            val existing = findVariantByExternalId(v.externalId)
            if (existing == null) {
                val id = UUID.randomUUID()
                insertVariant(v, id, now)
                val versionId = insertVersion(id, 1, v, hash, duration, config.graceMs, audioByFile, now)
                updateVariant(id, v, versionId, wasPublished = v.published, now)
                setPackageBytes(versionId, views.packageBytes(template, loadVersion(versionId)!!))
                created++
                versions++
                continue
            }
            val latest = latestVersion(existing.id)
            if (latest != null && latest.contentHash == hash && existing.currentVersionId == latest.id) {
                val reviewsChanged = updateTaskReviews(latest.id, v)
                if (existing.status != v.status || existing.publishApprovedBy != v.publishApprovedBy || existing.number != v.number || reviewsChanged > 0) {
                    updateVariant(existing.id, v, latest.id, wasPublished = existing.status == ExamValidator.PUBLISHED, now)
                    statusOnly++
                } else {
                    unchanged++
                }
                continue
            }
            // EXAM-24: an edited variant gets a new version; attempts keep theirs.
            val versionId = insertVersion(existing.id, (latest?.version ?: 0) + 1, v, hash, duration, config.graceMs, audioByFile, now)
            updateVariant(existing.id, v, versionId, wasPublished = existing.status == ExamValidator.PUBLISHED, now)
            setPackageBytes(versionId, views.packageBytes(template, loadVersion(versionId)!!))
            versions++
        }
        ExamImportReport(
            variantsCreated = created,
            versionsCreated = versions,
            statusUpdated = statusOnly,
            unchanged = unchanged,
            published = variants.count { it.valid.published },
            audioCreated = toInsert.size,
        )
    }

    companion object {
        private val canonical = Json { prettyPrint = false }

        fun audioKey(id: UUID) = "audio/$id.m4a"

        /**
         * SHA-256 over the content that answers and scores depend on (texts, keys,
         * structure, audio originals, timing). Status, authors, reviewers and the
         * publish approval are not content.
         */
        fun contentHash(v: ValidVariant, audioShaByFile: Map<String, String>, durationMs: Long, graceMs: Long): String {
            fun i18n(map: Map<String, String>?): JsonElement =
                map?.let { m -> JsonObject(m.toSortedMap().mapValues { JsonPrimitive(it.value) }) } ?: JsonPrimitive(null as String?)
            val root = buildJsonObject {
                put("template", v.templateId)
                put("number", v.number)
                put("language", v.language)
                put("durationMs", durationMs)
                put("graceMs", graceMs)
                put(
                    "groups",
                    buildJsonArray {
                        v.groups.sortedBy { it.id }.forEach { g ->
                            add(
                                buildJsonObject {
                                    put("id", g.id)
                                    put("kind", g.kind.db)
                                    put("section", g.section)
                                    put("title", g.title)
                                    put("instructions", i18n(g.instructions))
                                    put("passage", g.passage)
                                    put("licence", g.licence)
                                    put("author", g.author)
                                    put("sourceUrl", g.sourceUrl)
                                    put("audio", g.audioFile?.let { audioShaByFile[it] })
                                    put("speakers", buildJsonArray { g.speakers.forEach { s -> add(JsonPrimitive("${s.role}|${s.name}|${s.voice}|${s.speakerId}")) } })
                                    put("script", buildJsonArray { g.script.forEach { l -> add(JsonPrimitive("${l.speaker}|${l.text}")) } })
                                },
                            )
                        }
                    },
                )
                put(
                    "tasks",
                    buildJsonArray {
                        v.tasks.forEach { t ->
                            add(
                                buildJsonObject {
                                    put("id", t.id)
                                    put("section", t.section)
                                    put("group", t.group)
                                    put("type", t.type.db)
                                    put("answerFormat", t.answerFormat)
                                    put("prompt", t.prompt)
                                    put("options", buildJsonArray { t.options.forEach { o -> add(JsonPrimitive("${o.correct}|${o.text}")) } })
                                    put("explanation", i18n(t.explanation))
                                    put("modelAnswer", t.modelAnswer)
                                    put("criteria", buildJsonArray { t.criteria.forEach { c -> add(buildJsonObject { put("mark", c.mark.api); put("description", i18n(c.description)) }) } })
                                },
                            )
                        }
                    },
                )
            }
            return sha256Hex(canonical.encodeToString(JsonElement.serializer(), root).encodeToByteArray())
        }

        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

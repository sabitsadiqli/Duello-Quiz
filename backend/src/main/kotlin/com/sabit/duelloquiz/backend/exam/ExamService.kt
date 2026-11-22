package com.sabit.duelloquiz.backend.exam

import com.sabit.duelloquiz.backend.common.ApiException
import com.sabit.duelloquiz.backend.common.BakuTime
import com.sabit.duelloquiz.backend.common.Db
import com.sabit.duelloquiz.backend.common.ErrorCodes
import com.sabit.duelloquiz.backend.common.badRequest
import com.sabit.duelloquiz.backend.common.conflict
import com.sabit.duelloquiz.backend.common.notFound
import com.sabit.duelloquiz.backend.common.resolve
import com.sabit.duelloquiz.backend.common.validationFailed
import com.sabit.duelloquiz.backend.progress.AwardRequest
import com.sabit.duelloquiz.backend.progress.ProgressDto
import com.sabit.duelloquiz.backend.progress.ProgressService
import com.sabit.duelloquiz.backend.progress.SourceType
import com.sabit.duelloquiz.backend.user.UserRecord
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Error codes of the exam endpoints (docs/api/openapi.yaml) — never rename one. */
object ExamErrorCodes {
    const val VARIANT_NOT_FOUND = "exam_variant_not_found"
    const val ATTEMPT_NOT_FOUND = "exam_attempt_not_found"
    const val VARIANT_UNAVAILABLE = "variant_unavailable"
    const val VERSION_MISMATCH = "variant_version_mismatch"
    const val ATTEMPT_RUNNING = "exam_attempt_running"
    const val ATTEMPT_CLOSED = "attempt_closed"
    const val ATTEMPT_NOT_FINISHED = "attempt_not_finished"
    const val TASK_NOT_IN_ATTEMPT = "task_not_in_attempt"
    const val GROUP_NOT_IN_ATTEMPT = "group_not_in_attempt"
}

/**
 * Mock-exam attempts (ADR 0002 §5–§6): the server owns start, deadlines, keys and
 * scoring; answers are synced offline-first by revision; written marks are the only
 * user-supplied score input. Written answer texts are personal data and never logged.
 */
class ExamService(
    private val db: Db,
    private val repository: ExamRepository,
    private val templates: ExamTemplates,
    private val views: ExamViews,
    private val progress: ProgressService,
    private val config: ExamConfig,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(ExamService::class.java)

    /** Versions are immutable: cached for the process lifetime. */
    private val contentCache = ConcurrentHashMap<UUID, VersionContent>()

    private val idPattern = Regex("^[A-Za-z0-9_-]{8,64}$")

    // -------------------------------------------------------------------- content

    private suspend fun content(versionId: UUID): VersionContent =
        contentCache[versionId] ?: db.tx { with(repository) { loadVersion(versionId) } }!!.also { contentCache[versionId] = it }

    private fun templateOf(variant: VariantRecord): ExamTemplate =
        templates[variant.templateId] ?: error("template ${variant.templateId} is not in exams/templates.json")

    private fun variantNotFound() = notFound(ExamErrorCodes.VARIANT_NOT_FOUND, "Exam variant not found")

    private fun attemptNotFound() = notFound(ExamErrorCodes.ATTEMPT_NOT_FOUND, "Exam attempt not found")

    /** EXAM-01: templates with published variants and the caller's last attempt per variant. */
    suspend fun catalog(user: UserRecord, language: String): ExamCatalogResponse {
        val now = clock.instant()
        val (published, tiers, attempts) = db.tx {
            with(repository) { Triple(publishedVariants(), accessTiers(), attemptsOf(user.id, 200)) }
        }
        val lastByVariant = attempts.groupBy { it.variantId }.mapValues { it.value.first() }
        val variantsById = published.associate { it.first.id to it.first }
        val list = published.filter { templates[it.first.templateId] != null }
            .groupBy { it.first.templateId }
            .map { (templateId, variants) ->
                val template = templates[templateId]!!
                ExamTemplateSummaryDto(
                    id = template.id,
                    title = template.title.resolve(language),
                    stage = template.stage,
                    mode = template.mode,
                    language = template.language,
                    durationMs = config.durationFor(template.mode),
                    taskCount = template.taskCount,
                    accessTier = tiers[template.id],
                    variants = variants.map { (variant, version) ->
                        ExamVariantSummaryDto(
                            id = variant.id.toString(),
                            number = variant.number,
                            version = version.version,
                            taskCount = template.taskCount,
                            packageBytes = version.packageBytes,
                            lastAttempt = lastByVariant[variant.id]?.let { summary(it, variantsById, language, now) },
                        )
                    },
                ) to template.sortOrder
            }
            .sortedBy { it.second }
            .map { it.first }
        return ExamCatalogResponse(now.toEpochMilli(), list)
    }

    /** Published variant (or 404). */
    private suspend fun publishedVariant(variantId: UUID): Pair<VariantRecord, VersionRef> {
        val (variant, ref) = db.tx {
            with(repository) {
                val v = findVariant(variantId)
                v to v?.currentVersionId?.let { versionRef(it) }
            }
        }
        if (variant == null || variant.status != ExamValidator.PUBLISHED || ref == null || templates[variant.templateId] == null) {
            throw variantNotFound()
        }
        return variant to ref
    }

    /** EXAM-02. */
    suspend fun variantInfo(user: UserRecord, variantId: UUID, language: String): ExamVariantInfoResponse {
        val now = clock.instant()
        val (variant, ref) = publishedVariant(variantId)
        val template = templateOf(variant)
        val content = content(ref.id)
        val (tiers, attempts) = db.tx { with(repository) { accessTiers() to attemptsOf(user.id, null).filter { it.variantId == variantId } } }
        return ExamVariantInfoResponse(
            id = variant.id.toString(),
            number = variant.number,
            version = ref.version,
            template = ExamTemplateRefDto(template.id, template.title.resolve(language), template.stage, tiers[template.id]),
            language = content.language,
            mode = template.mode,
            durationMs = content.durationMs,
            graceMs = content.graceMs,
            lateUploadWindowMs = config.lateUploadWindowMs,
            listeningMaxPlays = config.listeningMaxPlays,
            listeningPlayCountsAfterMs = config.listeningPlayCountsAfterMs,
            scoring = ExamScoringRuleDto(template.scoring.rule, template.scoring.writtenWeight, template.rawMax, template.scoring.maxScore.toDouble()),
            sections = template.sections.mapIndexed { i, s ->
                ExamSectionInfoDto(s.id, s.title.resolve(language), i + 1, s.closed, s.written, s.group)
            },
            taskCount = content.tasks.size,
            hasListening = content.groups.any { it.kind == GroupKind.LISTENING },
            hasWritten = content.tasks.any { it.type == TaskType.WRITTEN },
            packageBytes = ref.packageBytes,
            attemptCount = attempts.size,
            lastAttempt = attempts.firstOrNull()?.let { summary(it, mapOf(variant.id to variant), language, now) },
        )
    }

    /** EXAM-03: the current published version, or an older one the caller has an attempt on. */
    suspend fun packageOf(user: UserRecord, variantId: UUID, version: Int?, language: String): ExamPackageResponse {
        val (variant, ref) = db.tx {
            with(repository) {
                val v = findVariant(variantId) ?: return@tx null to null
                val r = when (version) {
                    null -> v.currentVersionId?.let { versionRef(it) }
                    else -> findVersion(variantId, version)
                }
                val allowed = r != null && (
                    (v.status == ExamValidator.PUBLISHED && r.id == v.currentVersionId) || hasAttemptOnVersion(user.id, r.id)
                    )
                v to r?.takeIf { allowed }
            }
        }
        if (variant == null || ref == null || templates[variant.templateId] == null) throw variantNotFound()
        return views.packageOf(templateOf(variant), content(ref.id), language)
    }

    // -------------------------------------------------------------------- attempts

    /** EXAM-04: creates the attempt (true) or returns the existing one (false). */
    suspend fun start(user: UserRecord, request: StartExamAttemptRequest): Pair<Boolean, ExamAttemptResponse> {
        val variantId = uuid(request.variantId, "variantId")
        checkDeviceId(request.deviceId)
        if (!idPattern.matches(request.idempotencyKey)) throw validationFailed("idempotencyKey must match ^[A-Za-z0-9_-]{8,64}$")
        if (request.mode != ExamConfig.MODE_SUBJECT) throw validationFailed("mode must be subject (Phase 1)")
        if (request.version < 1) throw validationFailed("version must be >= 1")

        val variant = db.tx { with(repository) { findVariant(variantId) } }
        if (variant == null || variant.status == "draft" || templates[variant.templateId] == null) throw variantNotFound()
        val template = templateOf(variant)
        val now = clock.instant()

        val (created, attempt) = db.tx {
            with(repository) {
                lockUser(user.id)
                findByIdempotencyKey(user.id, request.idempotencyKey)?.let { return@tx false to it }
                markOverdue(user.id, now)
                val running = openAttempts(user.id).firstOrNull { it.status == AttemptStatus.RUNNING }
                if (running != null) {
                    if (running.variantId == variantId) return@tx false to running
                    throw conflict(ExamErrorCodes.ATTEMPT_RUNNING, "Another exam attempt is running")
                }
                if (variant.status != ExamValidator.PUBLISHED || variant.currentVersionId == null) {
                    throw conflict(ExamErrorCodes.VARIANT_UNAVAILABLE, "Variant is not published")
                }
                val ref = versionRef(variant.currentVersionId)!!
                if (ref.version != request.version) {
                    throw conflict(ExamErrorCodes.VERSION_MISMATCH, "Package version ${request.version} is outdated (current ${ref.version})")
                }
                val day = BakuTime.day(now)
                if (countStarts(user.id, day) >= config.startsPerDay) {
                    val seconds = maxOf(1L, (BakuTime.nextReset(now).toEpochMilli() - now.toEpochMilli() + 999) / 1000)
                    log.warn("exam_rate_limited userId={}", user.id)
                    throw ApiException(
                        HttpStatusCode.TooManyRequests, ErrorCodes.RATE_LIMITED, "Too many exam attempts started today",
                        headers = mapOf(HttpHeaders.RetryAfter to seconds.toString()),
                    )
                }
                val versionDuration = ExamVariantDurations(config.durationFor(template.mode), config.graceMs)
                val endsAt = now.plusMillis(versionDuration.durationMs)
                val deadline = endsAt.plusMillis(versionDuration.graceMs)
                val record = ExamAttemptRecord(
                    id = UUID.randomUUID(),
                    userId = user.id,
                    variantId = variantId,
                    versionId = ref.id,
                    mode = request.mode,
                    status = AttemptStatus.RUNNING,
                    isRetake = hasFinished(user.id, variantId),
                    skipListening = request.skipListening,
                    idempotencyKey = request.idempotencyKey,
                    holderDeviceId = request.deviceId,
                    startedAt = now,
                    startedDay = day,
                    endsAt = endsAt,
                    deadlineAt = deadline,
                    lateUntil = deadline.plusMillis(config.lateUploadWindowMs),
                    submittedAt = null,
                    submitTrigger = null,
                    autoScore = null,
                    writtenScore = null,
                    totalScore = null,
                )
                insertAttempt(record, now)
                true to record
            }
        }
        if (created) {
            log.info("exam_attempt_started userId={} attemptId={} variant={} retake={}", user.id, attempt.id, variant.externalId, attempt.isRetake)
        }
        return created to attemptResponse(attempt, resumed = !created, now)
    }

    private data class ExamVariantDurations(val durationMs: Long, val graceMs: Long)

    suspend fun current(user: UserRecord): CurrentExamAttemptResponse {
        val now = clock.instant()
        val open = db.tx { with(repository) { openAttempts(user.id) } }
        open.filter { now.isAfter(it.lateUntil) }.forEach { finalizeIfDue(it.id) }
        val current = open.firstOrNull { !now.isAfter(it.lateUntil) } ?: return CurrentExamAttemptResponse(null)
        return CurrentExamAttemptResponse(attemptResponse(current, resumed = true, now))
    }

    suspend fun attempt(user: UserRecord, attemptId: UUID): ExamAttemptResponse {
        val attempt = ownAttempt(user, attemptId).let { if (it.status.open) finalizeIfDue(it.id) ?: it else it }
        return attemptResponse(attempt, resumed = true, clock.instant())
    }

    suspend fun history(user: UserRecord, limit: Int, language: String): ExamAttemptListResponse {
        if (limit !in 1..50) throw validationFailed("limit must be 1..50")
        val now = clock.instant()
        var attempts = db.tx { with(repository) { attemptsOf(user.id, limit) } }
        if (attempts.any { it.status.open && now.isAfter(it.lateUntil) }) {
            attempts.filter { it.status.open && now.isAfter(it.lateUntil) }.forEach { finalizeIfDue(it.id) }
            attempts = db.tx { with(repository) { attemptsOf(user.id, limit) } }
        }
        val variants = db.tx { with(repository) { attempts.map { it.variantId }.distinct().mapNotNull { findVariant(it) } } }.associateBy { it.id }
        return ExamAttemptListResponse(attempts.map { summary(it, variants, language, now) })
    }

    private suspend fun ownAttempt(user: UserRecord, attemptId: UUID): ExamAttemptRecord {
        val attempt = db.tx { with(repository) { findAttempt(attemptId) } }
        if (attempt == null || attempt.userId != user.id) throw attemptNotFound()
        return attempt
    }

    /** Status as the client must see it now (a passed deadline is `awaiting_upload`). */
    private fun effectiveStatus(a: ExamAttemptRecord, now: Instant): AttemptStatus =
        if (a.status == AttemptStatus.RUNNING && now.isAfter(a.deadlineAt)) AttemptStatus.AWAITING_UPLOAD else a.status

    private suspend fun attemptResponse(a: ExamAttemptRecord, resumed: Boolean, now: Instant): ExamAttemptResponse {
        val content = content(a.versionId)
        val variant = db.tx { with(repository) { findVariant(a.variantId) } }!!
        val (answers, listening) = db.tx { with(repository) { answers(a.id) to listening(a.id) } }
        return ExamAttemptResponse(
            id = a.id.toString(),
            variantId = a.variantId.toString(),
            version = content.version,
            templateId = variant.templateId,
            mode = a.mode,
            status = effectiveStatus(a, now).db,
            isRetake = a.isRetake,
            skipListening = a.skipListening,
            resumed = resumed,
            startedAtEpochMs = a.startedAt.toEpochMilli(),
            endsAtEpochMs = a.endsAt.toEpochMilli(),
            deadlineEpochMs = a.deadlineAt.toEpochMilli(),
            lateUploadUntilEpochMs = a.lateUntil.toEpochMilli(),
            serverNowEpochMs = now.toEpochMilli(),
            submittedAtEpochMs = a.submittedAt?.toEpochMilli(),
            answers = content.tasks.mapNotNull { answers[it.id] }.map {
                ExamAnswerDto(it.taskId.toString(), it.revision, it.optionId?.toString(), it.text, it.flagged, it.savedAt.toEpochMilli())
            },
            listening = content.groups.filter { it.kind == GroupKind.LISTENING }.map { g -> listeningDto(g.id, listening[g.id]) },
        )
    }

    private fun listeningDto(groupId: UUID, state: StoredListening?): ExamListeningStateDto {
        val used = state?.playsUsed ?: 0
        return ExamListeningStateDto(
            groupId.toString(), used, maxOf(0, config.listeningMaxPlays - used), state?.audioFailed ?: false, state?.positionMs ?: 0,
        )
    }

    // -------------------------------------------------------------------- writes

    private sealed interface WriteGate {
        data class Open(val attempt: ExamAttemptRecord, val late: Boolean) : WriteGate
        data object Closed : WriteGate
    }

    /**
     * Locks the attempt and applies the timing and device rules of OQ-118. A window that
     * ended is finalized here (committed) before `attempt_closed` is returned.
     */
    private suspend fun <T> write(
        user: UserRecord,
        attemptId: UUID,
        deviceId: String,
        block: JdbcTransaction.(attempt: ExamAttemptRecord, content: VersionContent, late: Boolean, now: Instant) -> T,
    ): T {
        checkDeviceId(deviceId)
        val content = content(ownAttempt(user, attemptId).versionId)
        val now = clock.instant()
        var result: T? = null
        val gate = db.tx {
            with(repository) {
                val a = findAttempt(attemptId, forUpdate = true) ?: return@tx WriteGate.Closed
                if (!a.status.open) return@tx WriteGate.Closed
                if (now.isAfter(a.lateUntil)) {
                    finalizeLocked(a, content, TRIGGER_SERVER, now)
                    return@tx WriteGate.Closed
                }
                val late = now.isAfter(a.deadlineAt)
                if (late && a.status == AttemptStatus.RUNNING) setStatus(a.id, AttemptStatus.AWAITING_UPLOAD, now)
                if (late && deviceId != a.holderDeviceId) return@tx WriteGate.Closed
                result = block(a, content, late, now)
                if (!late && deviceId != a.holderDeviceId) setHolder(a.id, deviceId, now)
                WriteGate.Open(a, late)
            }
        }
        if (gate is WriteGate.Closed) throw conflict(ExamErrorCodes.ATTEMPT_CLOSED, "Attempt is closed for this device")
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    /** OQ-118: highest revision per task wins; validation is all-or-nothing (EXAM-21). */
    suspend fun writeAnswers(user: UserRecord, attemptId: UUID, request: ExamAnswerBatchRequest): ExamAnswerBatchResponse {
        if (request.answers.isEmpty() || request.answers.size > config.maxBatchSize) {
            throw validationFailed("answers must have 1..${config.maxBatchSize} entries")
        }
        val parsed = request.answers.map { it to uuid(it.taskId, "taskId") }
        if (parsed.map { it.second }.toSet().size != parsed.size) throw validationFailed("a task appears more than once in the batch")
        return write(user, attemptId, request.deviceId) { attempt, content, late, now ->
            val writes = parsed.map { (w, taskId) -> validateWrite(w, taskId, content) }
            with(repository) {
                val stored = answers(attempt.id)
                val accepted = mutableListOf<AcceptedAnswerDto>()
                val ignored = mutableListOf<IgnoredAnswerDto>()
                for (w in writes) {
                    val existing = stored[w.taskId]
                    when {
                        late && w.savedAt.isAfter(attempt.deadlineAt) ->
                            ignored += IgnoredAnswerDto(w.taskId.toString(), w.revision, "saved_after_deadline")
                        existing != null && w.revision < existing.revision ->
                            ignored += IgnoredAnswerDto(w.taskId.toString(), w.revision, "stale_revision")
                        existing != null && w.revision == existing.revision ->
                            accepted += AcceptedAnswerDto(w.taskId.toString(), w.revision)
                        else -> {
                            writeAnswer(attempt.id, w, exists = existing != null, now)
                            accepted += AcceptedAnswerDto(w.taskId.toString(), w.revision)
                        }
                    }
                }
                ExamAnswerBatchResponse(
                    status = (if (late) AttemptStatus.AWAITING_UPLOAD else AttemptStatus.RUNNING).db,
                    serverNowEpochMs = now.toEpochMilli(),
                    accepted = accepted,
                    ignored = ignored,
                )
            }
        }
    }

    suspend fun writeAnswer(user: UserRecord, attemptId: UUID, taskId: String, request: ExamSingleAnswerRequest) =
        writeAnswers(
            user, attemptId,
            ExamAnswerBatchRequest(
                request.deviceId,
                listOf(ExamAnswerWriteDto(taskId, request.revision, request.optionId, request.text, request.flagged, request.savedAtEpochMs)),
            ),
        )

    private fun validateWrite(w: ExamAnswerWriteDto, taskId: UUID, content: VersionContent): StoredAnswer {
        val task = content.tasksById[taskId] ?: throw badRequest("Task is not part of this attempt", ExamErrorCodes.TASK_NOT_IN_ATTEMPT)
        if (w.revision !in 1..1_000_000) throw validationFailed("revision must be 1..1000000")
        if (w.savedAtEpochMs <= 0) throw validationFailed("savedAtEpochMs must be positive")
        val optionId = w.optionId?.let { uuid(it, "optionId") }
        val text = w.text?.takeIf { it.isNotEmpty() }
        when (task.type) {
            TaskType.CLOSED_SINGLE -> {
                if (text != null) throw validationFailed("text is only allowed on written tasks")
                if (optionId != null && task.options.none { it.id == optionId }) {
                    throw badRequest("Option does not belong to the task", ErrorCodes.INVALID_OPTION)
                }
            }
            TaskType.WRITTEN -> {
                if (optionId != null) throw validationFailed("optionId is only allowed on closed tasks")
                if (text != null && text.codePointCount(0, text.length) > config.maxTextLength) {
                    throw validationFailed("text is longer than ${config.maxTextLength} characters")
                }
            }
        }
        return StoredAnswer(taskId, w.revision, optionId, text, w.flagged, Instant.ofEpochMilli(w.savedAtEpochMs))
    }

    /** OQ-119: keeps the maximum of plays used; an audio failure stays reported. */
    suspend fun updateListening(user: UserRecord, attemptId: UUID, groupIdRaw: String, request: ExamListeningUpdateRequest): ExamListeningStateDto {
        val groupId = runCatching { UUID.fromString(groupIdRaw) }.getOrNull()
            ?: throw badRequest("Group is not part of this attempt", ExamErrorCodes.GROUP_NOT_IN_ATTEMPT)
        if (request.playsUsed !in 0..config.listeningMaxPlays) throw validationFailed("playsUsed must be 0..${config.listeningMaxPlays}")
        if (request.savedAtEpochMs <= 0) throw validationFailed("savedAtEpochMs must be positive")
        if (request.positionMs < 0) throw validationFailed("positionMs must not be negative")
        return write(user, attemptId, request.deviceId) { attempt, content, late, now ->
            val group = content.groupsById[groupId]?.takeIf { it.kind == GroupKind.LISTENING }
                ?: throw badRequest("Group is not part of this attempt", ExamErrorCodes.GROUP_NOT_IN_ATTEMPT)
            with(repository) {
                val stored = listening(attempt.id)[group.id]
                if (late && Instant.ofEpochMilli(request.savedAtEpochMs).isAfter(attempt.deadlineAt)) {
                    return@write listeningDto(group.id, stored)
                }
                val duration = group.audio?.durationMs
                if (duration != null && request.positionMs > duration) throw validationFailed("positionMs must be 0..$duration")
                val merged = mergeListening(group.id, stored, request)
                if (merged != stored) writeListening(attempt.id, merged, exists = stored != null, now)
                listeningDto(group.id, merged)
            }
        }
    }

    /**
     * OQ-119 / OQ-D43: plays used keep their maximum, an audio failure stays reported. The
     * position follows the newest play: a higher play count always takes the position;
     * with the same count the later `savedAtEpochMs` wins (last write); an older count
     * never moves it.
     */
    private fun mergeListening(groupId: UUID, stored: StoredListening?, request: ExamListeningUpdateRequest): StoredListening {
        val savedAt = Instant.ofEpochMilli(request.savedAtEpochMs)
        val storedPlays = stored?.playsUsed ?: 0
        val takePosition = stored == null || request.playsUsed > storedPlays ||
            (request.playsUsed == storedPlays && (stored.positionSavedAt == null || !savedAt.isBefore(stored.positionSavedAt)))
        return StoredListening(
            groupId = groupId,
            playsUsed = maxOf(storedPlays, request.playsUsed),
            audioFailed = (stored?.audioFailed ?: false) || request.audioFailed,
            positionMs = if (takePosition) request.positionMs else stored.positionMs,
            positionSavedAt = if (takePosition) savedAt else stored.positionSavedAt,
        )
    }

    // -------------------------------------------------------------------- submit, result

    /** EXAM-14: idempotent; after the deadline only from the holding device. */
    suspend fun submit(user: UserRecord, attemptId: UUID, request: SubmitExamRequest, language: String): ExamResultResponse {
        checkDeviceId(request.deviceId)
        if (request.trigger != TRIGGER_USER && request.trigger != TRIGGER_DEADLINE) throw validationFailed("trigger must be user or deadline")
        val content = content(ownAttempt(user, attemptId).versionId)
        val now = clock.instant()
        val closed = db.tx {
            with(repository) {
                val a = findAttempt(attemptId, forUpdate = true)!!
                when {
                    !a.status.open -> a
                    now.isAfter(a.lateUntil) -> finalizeLocked(a, content, TRIGGER_SERVER, now)
                    now.isAfter(a.deadlineAt) && request.deviceId != a.holderDeviceId -> null
                    else -> {
                        val trigger = if (request.trigger == TRIGGER_DEADLINE || now.isAfter(a.endsAt)) TRIGGER_DEADLINE else TRIGGER_USER
                        finalizeLocked(a, content, trigger, now)
                    }
                }
            }
        } ?: throw conflict(ExamErrorCodes.ATTEMPT_CLOSED, "Attempt is closed for this device")
        return resultOf(closed, content, language)
    }

    suspend fun result(user: UserRecord, attemptId: UUID, language: String): ExamResultResponse {
        val attempt = closedAttempt(user, attemptId)
        return resultOf(attempt, content(attempt.versionId), language)
    }

    /** EXAM-16: marks only on written tasks of a finished attempt; recomputes the estimate. */
    suspend fun selfAssess(user: UserRecord, attemptId: UUID, request: SelfAssessmentRequest, language: String): ExamResultResponse {
        if (request.marks.isEmpty() || request.marks.size > 50) throw validationFailed("marks must have 1..50 entries")
        val attempt = closedAttempt(user, attemptId)
        val content = content(attempt.versionId)
        val entries = request.marks.map { e ->
            val taskId = uuid(e.taskId, "taskId")
            val task = content.tasksById[taskId] ?: throw badRequest("Task is not part of this attempt", ExamErrorCodes.TASK_NOT_IN_ATTEMPT)
            if (task.type != TaskType.WRITTEN) throw validationFailed("only written tasks are self-assessed")
            val mark = e.mark?.let { Mark.ofApi(it) ?: throw validationFailed("mark must be 0, 1/3, 1/2, 2/3, 1 or null") }
            taskId to mark
        }
        if (entries.map { it.first }.toSet().size != entries.size) throw validationFailed("a task appears more than once")
        val now = clock.instant()
        val updated = db.tx {
            with(repository) {
                val a = findAttempt(attemptId, forUpdate = true)!!
                entries.forEach { (taskId, mark) -> setMark(a.id, taskId, mark, now) }
                val score = score(a, content)
                storeScore(a.id, score, now)
                a.copy(autoScore = score.autoScore, writtenScore = score.writtenScore, totalScore = score.total)
            }
        }
        log.info("exam_self_assessed attemptId={} entries={}", attemptId, entries.size)
        return resultOf(updated, content, language)
    }

    /** EXAM-18: only after submit or finalization. */
    suspend fun review(user: UserRecord, attemptId: UUID, language: String): ExamReviewResponse {
        val attempt = closedAttempt(user, attemptId)
        val content = content(attempt.versionId)
        val variant = db.tx { with(repository) { findVariant(attempt.variantId) } }!!
        val template = templateOf(variant)
        val (answers, listening, marks) = db.tx { with(repository) { Triple(answers(attempt.id), listening(attempt.id), marks(attempt.id)) } }
        val excluded = excludedGroups(attempt, content, listening).first
        return ExamReviewResponse(
            attemptId = attempt.id.toString(),
            variantId = attempt.variantId.toString(),
            version = content.version,
            language = content.language,
            sections = views.sections(template, language),
            groups = content.groups.map { g ->
                val names = g.speakers.associate { it.role to it.name }
                ExamReviewGroupDto(
                    id = g.id.toString(),
                    kind = g.kind.db,
                    sectionId = g.section,
                    position = g.position,
                    title = g.title,
                    instructions = g.instructions?.resolve(language),
                    passage = g.passage,
                    audio = g.audio?.let(views::media),
                    transcript = g.script.map { TranscriptLineDto(it.speaker, names[it.speaker].orEmpty(), it.text) },
                    voices = g.audio?.voices.orEmpty().map { VoiceCreditDto(it.role, it.voice, it.speakerId, it.licence) },
                )
            },
            tasks = content.tasks.map { t ->
                val answer = answers[t.id]
                val mark = marks[t.id]
                val outcome = when {
                    t.groupId in excluded -> "excluded"
                    t.type == TaskType.WRITTEN -> if (mark != null) "assessed" else "not_assessed"
                    answer?.optionId == null -> "blank"
                    answer.optionId == t.correctOption?.id -> "correct"
                    else -> "wrong"
                }
                ExamReviewTaskDto(
                    id = t.id.toString(),
                    position = t.position,
                    sectionId = t.section,
                    groupId = t.groupId?.toString(),
                    type = t.type.db,
                    prompt = t.prompt,
                    options = t.options.map { ExamReviewOptionDto(it.id.toString(), it.letter, it.text, it.isCorrect) },
                    selectedOptionId = answer?.optionId?.toString(),
                    correctOptionId = t.correctOption?.id?.toString(),
                    outcome = outcome,
                    text = answer?.text,
                    flagged = answer?.flagged ?: false,
                    explanation = t.explanation?.resolve(language),
                    modelAnswer = t.modelAnswer,
                    criteria = t.criteria.map { CriterionDto(it.mark, it.description.resolve(language)) },
                    selfMark = mark?.api,
                )
            },
        )
    }

    /** Finished attempt of the caller; finalizes one whose window ended; else 409. */
    private suspend fun closedAttempt(user: UserRecord, attemptId: UUID): ExamAttemptRecord {
        val attempt = ownAttempt(user, attemptId)
        if (!attempt.status.open) return attempt
        return finalizeIfDue(attemptId)
            ?: throw conflict(ExamErrorCodes.ATTEMPT_NOT_FINISHED, "Attempt is not submitted yet")
    }

    /** Finalizes the attempt if its late-upload window ended; returns it closed, else null. */
    private suspend fun finalizeIfDue(attemptId: UUID): ExamAttemptRecord? {
        val versionId = db.tx { with(repository) { findAttempt(attemptId) } }?.versionId ?: return null
        val content = content(versionId)
        val now = clock.instant()
        return db.tx {
            with(repository) {
                val a = findAttempt(attemptId, forUpdate = true) ?: return@tx null
                when {
                    !a.status.open -> a
                    now.isAfter(a.lateUntil) -> finalizeLocked(a, content, TRIGGER_SERVER, now)
                    else -> null
                }
            }
        }
    }

    /** Sweep job: finalizes attempts whose late-upload window ended (EXAM-14). */
    suspend fun finalizeOverdue(): Int {
        val ids = db.tx { with(repository) { pastWindow(clock.instant(), 100) } }
        var done = 0
        for (id in ids) {
            if (finalizeIfDue(id)?.submitTrigger == TRIGGER_SERVER) done++
        }
        return done
    }

    private fun excludedGroups(
        a: ExamAttemptRecord,
        content: VersionContent,
        listening: Map<UUID, StoredListening>,
    ): Pair<Set<UUID>, ListeningExclusion?> {
        val listeningGroups = content.groups.filter { it.kind == GroupKind.LISTENING }
        if (a.skipListening) return listeningGroups.map { it.id }.toSet() to ListeningExclusion.SKIPPED
        val failed = listeningGroups.filter { listening[it.id]?.audioFailed == true }.map { it.id }.toSet()
        return failed to (if (failed.isEmpty()) null else ListeningExclusion.AUDIO_FAILED)
    }

    private fun JdbcTransaction.scoredTasks(a: ExamAttemptRecord, content: VersionContent): List<ScoredTask> = with(repository) {
        val answers = answers(a.id)
        val marks = marks(a.id)
        val excluded = excludedGroups(a, content, listening(a.id)).first
        content.tasks.map { t ->
            val answer = answers[t.id]
            ScoredTask(
                section = t.section,
                type = t.type,
                correct = t.type == TaskType.CLOSED_SINGLE && answer?.optionId != null && answer.optionId == t.correctOption?.id,
                answered = if (t.type == TaskType.CLOSED_SINGLE) answer?.optionId != null else !answer?.text.isNullOrBlank(),
                mark = if (t.type == TaskType.WRITTEN) marks[t.id] else null,
                excluded = t.groupId in excluded,
            )
        }
    }

    private fun JdbcTransaction.score(a: ExamAttemptRecord, content: VersionContent): ExamScore {
        val variant = with(repository) { findVariant(a.variantId) }!!
        return ExamScoring.score(templateOf(variant), scoredTasks(a, content))
    }

    /**
     * Scores and closes a locked open attempt. A client submit with an answer makes the
     * Baku day of the submit active (OQ-125, OQ-132: not the start day); a server
     * finalization never counts.
     */
    private fun JdbcTransaction.finalizeLocked(a: ExamAttemptRecord, content: VersionContent, trigger: String, now: Instant): ExamAttemptRecord {
        val score = score(a, content)
        val status = if (trigger == TRIGGER_SERVER) AttemptStatus.FINALIZED else AttemptStatus.SUBMITTED
        with(repository) { close(a.id, status, trigger, now, score) }
        if (trigger != TRIGGER_SERVER) {
            val answered = with(repository) { answers(a.id) }.values.any { it.optionId != null || !it.text.isNullOrBlank() }
            with(progress) {
                // OQ-132: the activity counts for the day it was submitted.
                award(AwardRequest(a.userId, SourceType.EXAM, a.id, startedAt = now, eligible = answered, items = emptyList()))
            }
        }
        log.info("exam_attempt_closed attemptId={} trigger={} total={}", a.id, trigger, score.total)
        return a.copy(
            status = status, submittedAt = now, submitTrigger = trigger,
            autoScore = score.autoScore, writtenScore = score.writtenScore, totalScore = score.total,
        )
    }

    private suspend fun resultOf(a: ExamAttemptRecord, content: VersionContent, language: String): ExamResultResponse {
        val variant = db.tx { with(repository) { findVariant(a.variantId) } }!!
        val template = templateOf(variant)
        data class Parts(val score: ExamScore, val exclusion: ListeningExclusion?, val answered: Int, val flagged: Int, val progress: ProgressDto?)
        val parts = db.tx {
            with(repository) {
                val answers = answers(a.id)
                val tasks = scoredTasks(a, content)
                Parts(
                    score = ExamScoring.score(template, tasks),
                    exclusion = excludedGroups(a, content, listening(a.id)).second,
                    answered = tasks.count { it.answered },
                    flagged = answers.values.count { it.flagged },
                    progress = with(progress) { storedAward(a.userId, SourceType.EXAM, a.id) },
                )
            }
        }
        val s = parts.score
        return ExamResultResponse(
            attemptId = a.id.toString(),
            variantId = a.variantId.toString(),
            version = content.version,
            templateId = template.id,
            mode = a.mode,
            status = a.status.db,
            isRetake = a.isRetake,
            submitTrigger = a.submitTrigger!!,
            submittedAtEpochMs = a.submittedAt!!.toEpochMilli(),
            estimate = ExamEstimateDto(
                total = s.total.toDouble(),
                autoScore = s.autoScore.toDouble(),
                writtenScore = s.writtenScore.toDouble(),
                maxScore = s.achievableMax.toDouble(),
                rawMax = s.rawMax,
                listeningExcluded = parts.exclusion != null,
                listeningExclusionReason = parts.exclusion?.api,
            ),
            closed = ClosedSummaryDto(s.closedCorrect, s.closedWrong, s.closedBlank, s.closedTotal),
            written = WrittenSummaryDto(s.writtenAssessed, s.writtenTotal, ExamScoring.marks(s.writtenSixths), s.writtenTotal),
            sections = s.sections.map { sec ->
                ExamSectionResultDto(
                    id = sec.id,
                    title = template.section(sec.id)?.title?.resolve(language) ?: sec.id,
                    closedCorrect = sec.closedCorrect,
                    closedWrong = sec.closedWrong,
                    closedBlank = sec.closedBlank,
                    closedTotal = sec.closedTotal,
                    writtenAssessed = sec.writtenAssessed,
                    writtenTotal = sec.writtenTotal,
                    writtenMarks = ExamScoring.marks(sec.writtenSixths),
                    excluded = sec.excluded,
                )
            },
            answeredCount = parts.answered,
            flaggedCount = parts.flagged,
            progress = parts.progress,
        )
    }

    private suspend fun summary(a: ExamAttemptRecord, variants: Map<UUID, VariantRecord>, language: String, now: Instant): ExamAttemptSummaryDto {
        val variant = variants[a.variantId] ?: db.tx { with(repository) { findVariant(a.variantId) } }!!
        val template = templateOf(variant)
        val content = content(a.versionId)
        val (assessed, max) = if (a.status.open) {
            0 to template.scoring.maxScore.toDouble()
        } else {
            db.tx { marksAndMax(a, content, template) }
        }
        return ExamAttemptSummaryDto(
            id = a.id.toString(),
            variantId = a.variantId.toString(),
            variantNumber = variant.number,
            templateId = template.id,
            templateTitle = template.title.resolve(language),
            mode = a.mode,
            status = effectiveStatus(a, now).db,
            isRetake = a.isRetake,
            dayKey = BakuTime.key(a.startedDay),
            startedAtEpochMs = a.startedAt.toEpochMilli(),
            endsAtEpochMs = a.endsAt.toEpochMilli(),
            submittedAtEpochMs = a.submittedAt?.toEpochMilli(),
            estimateTotal = a.totalScore?.toDouble(),
            maxScore = max,
            writtenAssessed = assessed,
            writtenTotal = content.tasks.count { it.type == TaskType.WRITTEN },
        )
    }

    private fun JdbcTransaction.marksAndMax(a: ExamAttemptRecord, content: VersionContent, template: ExamTemplate): Pair<Int, Double> =
        with(repository) { marks(a.id).size } to ExamScoring.score(template, scoredTasks(a, content)).achievableMax.toDouble()

    private fun checkDeviceId(deviceId: String) {
        if (!idPattern.matches(deviceId)) throw validationFailed("deviceId must match ^[A-Za-z0-9_-]{8,64}$")
    }

    private fun uuid(raw: String, field: String): UUID =
        runCatching { UUID.fromString(raw) }.getOrNull() ?: throw validationFailed("$field must be a UUID")

    companion object {
        const val TRIGGER_USER = "user"
        const val TRIGGER_DEADLINE = "deadline"
        const val TRIGGER_SERVER = "server"
    }
}

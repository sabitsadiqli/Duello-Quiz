package com.sabit.duelloquiz.core.network.fake

import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.exam.ExamAnswer
import com.sabit.duelloquiz.core.model.exam.ExamAnswerBatchResult
import com.sabit.duelloquiz.core.model.exam.ExamAnswerWrite
import com.sabit.duelloquiz.core.model.exam.ExamAttempt
import com.sabit.duelloquiz.core.model.exam.ExamAttemptStatus
import com.sabit.duelloquiz.core.model.exam.ExamAttemptSummary
import com.sabit.duelloquiz.core.model.exam.ExamCatalog
import com.sabit.duelloquiz.core.model.exam.ExamClosedCounts
import com.sabit.duelloquiz.core.model.exam.ExamCriterion
import com.sabit.duelloquiz.core.model.exam.ExamEstimate
import com.sabit.duelloquiz.core.model.exam.ExamGroup
import com.sabit.duelloquiz.core.model.exam.ExamGroupKind
import com.sabit.duelloquiz.core.model.exam.ExamListeningState
import com.sabit.duelloquiz.core.model.exam.ExamMark
import com.sabit.duelloquiz.core.model.exam.ExamMedia
import com.sabit.duelloquiz.core.model.exam.ExamMode
import com.sabit.duelloquiz.core.model.exam.ExamOption
import com.sabit.duelloquiz.core.model.exam.ExamOutcome
import com.sabit.duelloquiz.core.model.exam.ExamPackage
import com.sabit.duelloquiz.core.model.exam.ExamResult
import com.sabit.duelloquiz.core.model.exam.ExamReview
import com.sabit.duelloquiz.core.model.exam.ExamReviewGroup
import com.sabit.duelloquiz.core.model.exam.ExamReviewOption
import com.sabit.duelloquiz.core.model.exam.ExamReviewTask
import com.sabit.duelloquiz.core.model.exam.ExamScoringRule
import com.sabit.duelloquiz.core.model.exam.ExamSection
import com.sabit.duelloquiz.core.model.exam.ExamSectionInfo
import com.sabit.duelloquiz.core.model.exam.ExamSectionResult
import com.sabit.duelloquiz.core.model.exam.ExamStartOutcome
import com.sabit.duelloquiz.core.model.exam.ExamSubmitTrigger
import com.sabit.duelloquiz.core.model.exam.ExamTask
import com.sabit.duelloquiz.core.model.exam.ExamTaskType
import com.sabit.duelloquiz.core.model.exam.ExamTemplateSummary
import com.sabit.duelloquiz.core.model.exam.ExamTranscriptLine
import com.sabit.duelloquiz.core.model.exam.ExamVariantInfo
import com.sabit.duelloquiz.core.model.exam.ExamVariantSummary
import com.sabit.duelloquiz.core.model.exam.ExamVoiceCredit
import com.sabit.duelloquiz.core.model.exam.ExamWrittenCounts
import com.sabit.duelloquiz.core.model.exam.ListeningExclusion
import com.sabit.duelloquiz.core.network.ExamNetworkDataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sin

/**
 * Debug only: an in-process exam server for builds without Firebase. One "Buraxılış — İngilis
 * dili" variant in the Phase 1 structure (listening 3 + 3, language 16, reading 4 + 4) with
 * placeholder tasks whose right option is always "A" (the placeholders say so), a generated WAV
 * tone as the listening audio and the stage 1 formula 100/37 · (2·W + C). Like a real server it
 * keeps the keys to itself: the package and attempt responses never carry them.
 */
internal class FakeExamNetworkDataSource(private val clock: WallClock) : ExamNetworkDataSource {

    private val mutex = Mutex()
    private val attempts = mutableMapOf<String, FakeAttempt>()
    private val idempotency = mutableMapOf<String, String>()

    private class FakeAttempt(
        val id: String,
        val isRetake: Boolean,
        val skipListening: Boolean,
        val startedAt: Long,
        var status: ExamAttemptStatus = ExamAttemptStatus.RUNNING,
        var submittedAt: Long? = null,
        var trigger: ExamSubmitTrigger = ExamSubmitTrigger.USER,
        val answers: MutableMap<String, ExamAnswer> = mutableMapOf(),
        val listening: MutableMap<String, ExamListeningState> = mutableMapOf(),
        val marks: MutableMap<String, ExamMark> = mutableMapOf(),
    )

    override suspend fun getCatalog(): DataResult<ExamCatalog> = mutex.withLock {
        delay(LATENCY_MS)
        DataResult.Success(
            ExamCatalog(
                templates = listOf(
                    ExamTemplateSummary(
                        id = TEMPLATE_ID,
                        title = TEMPLATE_TITLE,
                        stage = 1,
                        mode = ExamMode.SUBJECT,
                        language = "en",
                        durationMs = DURATION_MS,
                        taskCount = tasks.size,
                        variants = listOf(
                            ExamVariantSummary(VARIANT_ID, 1, VERSION, tasks.size, packageBytes, latestSummary()),
                        ),
                    ),
                ),
            ),
        )
    }

    override suspend fun getVariant(variantId: String): DataResult<ExamVariantInfo> = mutex.withLock {
        delay(LATENCY_MS)
        if (variantId != VARIANT_ID) return@withLock notFound(ApiErrorCode.ExamVariantNotFound)
        DataResult.Success(
            ExamVariantInfo(
                id = VARIANT_ID,
                number = 1,
                version = VERSION,
                templateId = TEMPLATE_ID,
                templateTitle = TEMPLATE_TITLE,
                stage = 1,
                language = "en",
                mode = ExamMode.SUBJECT,
                durationMs = DURATION_MS,
                graceMs = GRACE_MS,
                lateUploadWindowMs = LATE_MS,
                listeningMaxPlays = 2,
                listeningPlayCountsAfterMs = 10_000,
                scoring = ExamScoringRule("stage1", 2, RAW_MAX, 100.0),
                sections = sections.map { section ->
                    val inSection = tasks.filter { it.sectionId == section.id }
                    ExamSectionInfo(
                        id = section.id,
                        title = section.title,
                        position = section.position,
                        closedCount = inSection.count { it.type == ExamTaskType.CLOSED_SINGLE },
                        writtenCount = inSection.count { it.type == ExamTaskType.WRITTEN },
                        groupKind = groups.firstOrNull { it.sectionId == section.id }?.kind,
                    )
                },
                taskCount = tasks.size,
                hasListening = true,
                hasWritten = true,
                packageBytes = packageBytes,
                attemptCount = attempts.size,
                lastAttempt = latestSummary(),
            ),
        )
    }

    override suspend fun getPackage(variantId: String, version: Int?): DataResult<ExamPackage> {
        delay(LATENCY_MS)
        if (variantId != VARIANT_ID) return notFound(ApiErrorCode.ExamVariantNotFound)
        return DataResult.Success(
            ExamPackage(
                variantId = VARIANT_ID,
                version = VERSION,
                templateId = TEMPLATE_ID,
                language = "en",
                durationMs = DURATION_MS,
                graceMs = GRACE_MS,
                optionCount = 5,
                listeningMaxPlays = 2,
                listeningPlayCountsAfterMs = 10_000,
                maxTextLength = 1_000,
                sections = sections,
                groups = groups,
                tasks = tasks,
                media = listOf(audioMedia),
                packageBytes = packageBytes,
            ),
        )
    }

    override suspend fun startAttempt(
        variantId: String,
        version: Int,
        deviceId: String,
        idempotencyKey: String,
        skipListening: Boolean,
    ): DataResult<ExamStartOutcome> = mutex.withLock {
        delay(LATENCY_MS)
        if (variantId != VARIANT_ID) return@withLock notFound(ApiErrorCode.ExamVariantNotFound)
        if (version != VERSION) return@withLock notFound(ApiErrorCode.VariantVersionMismatch)
        idempotency[idempotencyKey]?.let { id ->
            return@withLock DataResult.Success(ExamStartOutcome.Started(attempts.getValue(id).toAttempt(resumed = true)))
        }
        attempts.values.firstOrNull { it.status == ExamAttemptStatus.RUNNING }?.let { running ->
            return@withLock DataResult.Success(ExamStartOutcome.Started(running.toAttempt(resumed = true)))
        }
        val attempt = FakeAttempt(
            id = UUID.randomUUID().toString(),
            isRetake = attempts.values.any { it.status.isFinished },
            skipListening = skipListening,
            startedAt = clock.nowMs(),
        )
        attempts[attempt.id] = attempt
        idempotency[idempotencyKey] = attempt.id
        DataResult.Success(ExamStartOutcome.Started(attempt.toAttempt(resumed = false)))
    }

    override suspend fun listAttempts(limit: Int): DataResult<List<ExamAttemptSummary>> = mutex.withLock {
        delay(LATENCY_MS)
        DataResult.Success(attempts.values.sortedByDescending { it.startedAt }.take(limit).map { it.toSummary() })
    }

    override suspend fun getCurrentAttempt(): DataResult<ExamAttempt?> = mutex.withLock {
        DataResult.Success(attempts.values.firstOrNull { it.status == ExamAttemptStatus.RUNNING }?.toAttempt(resumed = true))
    }

    override suspend fun getAttempt(attemptId: String): DataResult<ExamAttempt> = mutex.withLock {
        val attempt = attempts[attemptId] ?: return@withLock notFound(ApiErrorCode.ExamAttemptNotFound)
        DataResult.Success(attempt.toAttempt(resumed = true))
    }

    override suspend fun putAnswers(
        attemptId: String,
        deviceId: String,
        answers: List<ExamAnswerWrite>,
    ): DataResult<ExamAnswerBatchResult> = mutex.withLock {
        val attempt = attempts[attemptId] ?: return@withLock notFound(ApiErrorCode.ExamAttemptNotFound)
        if (attempt.status.isFinished) return@withLock notFound(ApiErrorCode.AttemptClosed)
        val accepted = mutableListOf<Pair<String, Int>>()
        val ignored = mutableListOf<Pair<String, Int>>()
        answers.forEach { write ->
            val stored = attempt.answers[write.taskId]
            if (stored != null && stored.revision > write.revision) {
                ignored += write.taskId to write.revision
            } else {
                attempt.answers[write.taskId] =
                    ExamAnswer(write.taskId, write.revision, write.optionId, write.text, write.flagged, write.savedAtEpochMs)
                accepted += write.taskId to write.revision
            }
        }
        DataResult.Success(ExamAnswerBatchResult(attempt.status, accepted, ignored))
    }

    override suspend fun putListening(
        attemptId: String,
        groupId: String,
        deviceId: String,
        playsUsed: Int,
        audioFailed: Boolean,
        positionMs: Long,
        savedAtEpochMs: Long,
    ): DataResult<ExamListeningState> = mutex.withLock {
        val attempt = attempts[attemptId] ?: return@withLock notFound(ApiErrorCode.ExamAttemptNotFound)
        val stored = attempt.listening[groupId] ?: ExamListeningState(groupId, 0, false, 0)
        val next = ExamListeningState(
            groupId = groupId,
            playsUsed = maxOf(stored.playsUsed, playsUsed.coerceIn(0, 2)),
            audioFailed = stored.audioFailed || audioFailed,
            positionMs = if (playsUsed >= stored.playsUsed) positionMs else stored.positionMs,
        )
        attempt.listening[groupId] = next
        DataResult.Success(next)
    }

    override suspend fun submit(attemptId: String, deviceId: String, trigger: ExamSubmitTrigger): DataResult<ExamResult> =
        mutex.withLock {
            delay(LATENCY_MS)
            val attempt = attempts[attemptId] ?: return@withLock notFound(ApiErrorCode.ExamAttemptNotFound)
            if (!attempt.status.isFinished) {
                attempt.status = ExamAttemptStatus.SUBMITTED
                attempt.submittedAt = clock.nowMs()
                attempt.trigger = if (clock.nowMs() >= attempt.startedAt + DURATION_MS) ExamSubmitTrigger.DEADLINE else trigger
            }
            DataResult.Success(attempt.toResult())
        }

    override suspend fun getResult(attemptId: String): DataResult<ExamResult> = mutex.withLock {
        val attempt = attempts[attemptId] ?: return@withLock notFound(ApiErrorCode.ExamAttemptNotFound)
        if (!attempt.status.isFinished) return@withLock notFound(ApiErrorCode.AttemptNotFinished)
        DataResult.Success(attempt.toResult())
    }

    override suspend fun putSelfAssessment(attemptId: String, marks: List<Pair<String, ExamMark?>>): DataResult<ExamResult> =
        mutex.withLock {
            val attempt = attempts[attemptId] ?: return@withLock notFound(ApiErrorCode.ExamAttemptNotFound)
            if (!attempt.status.isFinished) return@withLock notFound(ApiErrorCode.AttemptNotFinished)
            marks.forEach { (taskId, mark) -> if (mark == null) attempt.marks.remove(taskId) else attempt.marks[taskId] = mark }
            DataResult.Success(attempt.toResult())
        }

    override suspend fun getReview(attemptId: String): DataResult<ExamReview> = mutex.withLock {
        delay(LATENCY_MS)
        val attempt = attempts[attemptId] ?: return@withLock notFound(ApiErrorCode.ExamAttemptNotFound)
        if (!attempt.status.isFinished) return@withLock notFound(ApiErrorCode.AttemptNotFinished)
        DataResult.Success(attempt.toReview())
    }

    override suspend fun downloadMedia(url: String, destination: File, onProgress: (Long) -> Unit): DataResult<Unit> {
        destination.parentFile?.mkdirs()
        val bytes = audioBytes
        destination.outputStream().use { out ->
            var written = 0
            while (written < bytes.size) {
                val n = minOf(CHUNK, bytes.size - written)
                out.write(bytes, written, n)
                written += n
                onProgress(written.toLong())
                delay(CHUNK_DELAY_MS)
            }
        }
        return DataResult.Success(Unit)
    }

    private fun latestSummary(): ExamAttemptSummary? = attempts.values.maxByOrNull { it.startedAt }?.toSummary()

    private fun FakeAttempt.toAttempt(resumed: Boolean) = ExamAttempt(
        id = id,
        variantId = VARIANT_ID,
        version = VERSION,
        templateId = TEMPLATE_ID,
        mode = ExamMode.SUBJECT,
        status = status,
        isRetake = isRetake,
        skipListening = skipListening,
        resumed = resumed,
        startedAtEpochMs = startedAt,
        endsAtEpochMs = startedAt + DURATION_MS,
        deadlineEpochMs = startedAt + DURATION_MS + GRACE_MS,
        lateUploadUntilEpochMs = startedAt + DURATION_MS + GRACE_MS + LATE_MS,
        submittedAtEpochMs = submittedAt,
        answers = answers.values.toList(),
        listening = listening.values.toList(),
        clockOffsetMs = 0,
    )

    private fun FakeAttempt.toSummary(): ExamAttemptSummary {
        val result = if (status.isFinished) toResult() else null
        return ExamAttemptSummary(
            id = id,
            variantId = VARIANT_ID,
            variantNumber = 1,
            templateId = TEMPLATE_ID,
            templateTitle = TEMPLATE_TITLE,
            mode = ExamMode.SUBJECT,
            status = status,
            isRetake = isRetake,
            dayKey = "2026-11-21",
            startedAtEpochMs = startedAt,
            endsAtEpochMs = startedAt + DURATION_MS,
            submittedAtEpochMs = submittedAt,
            estimateTotal = result?.estimate?.total,
            maxScore = result?.estimate?.maxScore ?: 100.0,
            writtenAssessed = result?.written?.assessed ?: 0,
            writtenTotal = tasks.count { it.type == ExamTaskType.WRITTEN },
        )
    }

    private fun FakeAttempt.excluded(task: ExamTask): Boolean = task.sectionId == LISTENING && excludedReason() != null

    private fun FakeAttempt.excludedReason(): ListeningExclusion? = when {
        skipListening -> ListeningExclusion.SKIPPED
        listening.values.any { it.audioFailed } -> ListeningExclusion.AUDIO_FAILED
        else -> null
    }

    private fun FakeAttempt.toResult(): ExamResult {
        val counted = tasks.filterNot { excluded(it) }
        val closed = counted.filter { it.type == ExamTaskType.CLOSED_SINGLE }
        val written = counted.filter { it.type == ExamTaskType.WRITTEN }
        val correct = closed.count { answers[it.id]?.optionId == it.options.first().id }
        val blank = closed.count { answers[it.id]?.optionId == null }
        val sixths = written.sumOf { marks[it.id]?.sixths ?: 0 }
        val reason = excludedReason()
        val rawMax = if (reason != null) RAW_MAX - LISTENING_RAW else RAW_MAX
        fun score(raw: BigDecimal) = raw.multiply(BigDecimal(100)).divide(BigDecimal(RAW_MAX), 1, RoundingMode.HALF_UP).toDouble()
        val writtenRaw = BigDecimal(sixths * 2).divide(BigDecimal(6), 10, RoundingMode.HALF_UP)
        return ExamResult(
            attemptId = id,
            variantId = VARIANT_ID,
            version = VERSION,
            templateId = TEMPLATE_ID,
            mode = ExamMode.SUBJECT,
            status = status,
            isRetake = isRetake,
            submitTrigger = trigger,
            submittedAtEpochMs = submittedAt ?: startedAt,
            estimate = ExamEstimate(
                total = score(BigDecimal(correct) + writtenRaw),
                autoScore = score(BigDecimal(correct)),
                writtenScore = score(writtenRaw),
                maxScore = score(BigDecimal(rawMax)),
                rawMax = rawMax,
                listeningExcluded = reason != null,
                exclusion = reason,
            ),
            closed = ExamClosedCounts(correct, closed.size - correct - blank, blank, closed.size),
            written = ExamWrittenCounts(
                assessed = written.count { it.id in marks },
                total = written.size,
                marks = BigDecimal(sixths).divide(BigDecimal(6), 2, RoundingMode.HALF_UP).toDouble(),
                maxMarks = written.size,
            ),
            sections = sections.map { section ->
                val inSection = tasks.filter { it.sectionId == section.id }
                val sectionClosed = inSection.filter { it.type == ExamTaskType.CLOSED_SINGLE }
                val sectionWritten = inSection.filter { it.type == ExamTaskType.WRITTEN }
                val sectionCorrect = sectionClosed.count { answers[it.id]?.optionId == it.options.first().id }
                val sectionBlank = sectionClosed.count { answers[it.id]?.optionId == null }
                ExamSectionResult(
                    id = section.id,
                    title = section.title,
                    closedCorrect = sectionCorrect,
                    closedWrong = sectionClosed.size - sectionCorrect - sectionBlank,
                    closedBlank = sectionBlank,
                    closedTotal = sectionClosed.size,
                    writtenAssessed = sectionWritten.count { it.id in marks },
                    writtenTotal = sectionWritten.size,
                    writtenMarks = sectionWritten.sumOf { (marks[it.id]?.sixths ?: 0) / 6.0 },
                    excluded = section.id == LISTENING && reason != null,
                )
            },
            answeredCount = answers.values.count { it.optionId != null || !it.text.isNullOrBlank() },
            flaggedCount = answers.values.count { it.flagged },
            progress = null,
        )
    }

    private fun FakeAttempt.toReview() = ExamReview(
        attemptId = id,
        variantId = VARIANT_ID,
        version = VERSION,
        language = "en",
        sections = sections,
        groups = groups.map { group ->
            ExamReviewGroup(
                id = group.id,
                kind = group.kind,
                sectionId = group.sectionId,
                position = group.position,
                title = group.title,
                instructions = group.instructions,
                passage = group.passage,
                audio = group.audio,
                transcript = if (group.kind == ExamGroupKind.LISTENING) transcript else emptyList(),
                voices = if (group.kind == ExamGroupKind.LISTENING) voices else emptyList(),
            )
        },
        tasks = tasks.map { task ->
            val answer = answers[task.id]
            val correctId = task.options.firstOrNull()?.id
            val outcome = when {
                excluded(task) -> ExamOutcome.EXCLUDED
                task.type == ExamTaskType.WRITTEN -> if (task.id in marks) ExamOutcome.ASSESSED else ExamOutcome.NOT_ASSESSED
                answer?.optionId == null -> ExamOutcome.BLANK
                answer.optionId == correctId -> ExamOutcome.CORRECT
                else -> ExamOutcome.WRONG
            }
            ExamReviewTask(
                id = task.id,
                position = task.position,
                sectionId = task.sectionId,
                groupId = task.groupId,
                type = task.type,
                prompt = task.prompt,
                options = task.options.map { ExamReviewOption(it.id, it.letter, it.text, it.id == correctId) },
                selectedOptionId = answer?.optionId,
                correctOptionId = correctId,
                outcome = outcome,
                text = answer?.text,
                flagged = answer?.flagged == true,
                explanation = "Debug: the right option of every placeholder task is A.",
                modelAnswer = if (task.type == ExamTaskType.WRITTEN) "A short model answer for task ${task.position}." else null,
                criteria = if (task.type == ExamTaskType.WRITTEN) criteria else emptyList(),
                selfMark = marks[task.id],
            )
        },
    )

    private fun <T> notFound(code: ApiErrorCode): DataResult<T> = DataResult.Failure(DataError.Api(code))

    private companion object {
        const val TEMPLATE_ID = "buraxilis-ingilis"
        const val TEMPLATE_TITLE = "Buraxılış — İngilis dili"
        const val VARIANT_ID = "00000000-0000-4000-8000-00000000e001"
        const val VERSION = 1
        const val DURATION_MS = 60 * 60_000L
        const val GRACE_MS = 2 * 60_000L
        const val LATE_MS = 24 * 60 * 60_000L
        const val RAW_MAX = 37
        const val LISTENING_RAW = 9
        const val LISTENING = "listening"
        const val LATENCY_MS = 300L
        const val CHUNK = 16 * 1024
        const val CHUNK_DELAY_MS = 40L
        const val SAMPLE_RATE = 8_000
        const val AUDIO_SECONDS = 40

        val sections = listOf(
            ExamSection(LISTENING, "Dinləmə", 1),
            ExamSection("language", "Qrammatika və lüğət", 2),
            ExamSection("reading", "Oxu", 3),
        )

        val audioBytes: ByteArray by lazy { toneWav() }

        val audioMedia: ExamMedia by lazy {
            ExamMedia(
                url = "https://fake.invalid/media/audio/listening-1.wav",
                bytes = audioBytes.size.toLong(),
                sha256 = MessageDigest.getInstance("SHA-256").digest(audioBytes).joinToString("") { "%02x".format(it) },
                contentType = "audio/wav",
                durationMs = AUDIO_SECONDS * 1_000L,
            )
        }

        val packageBytes: Long get() = audioMedia.bytes + 24_000

        val groups: List<ExamGroup> by lazy {
            listOf(
                ExamGroup(
                    id = "00000000-0000-4000-8000-0000000a0001",
                    kind = ExamGroupKind.LISTENING,
                    sectionId = LISTENING,
                    position = 1,
                    title = "At the town library",
                    instructions = "Dialoqu dinlə və tapşırıqları yerinə yetir.",
                    passage = null,
                    audio = audioMedia,
                ),
                ExamGroup(
                    id = "00000000-0000-4000-8000-0000000a0002",
                    kind = ExamGroupKind.READING,
                    sectionId = "reading",
                    position = 2,
                    title = "A garden on the roof",
                    instructions = "Mətni oxu və tapşırıqları yerinə yetir.",
                    passage = "Three years ago the roof of our school was empty and grey. Today it is a small garden " +
                        "with tomatoes, beans and herbs.\n\nThe idea came from a group of students in the tenth grade. " +
                        "They wanted a place to learn about plants **without leaving the school**. The head teacher " +
                        "agreed, but only if the students looked after the garden *every week*, including the summer " +
                        "holidays.\n\nNow younger pupils water the plants in the morning, and older ones keep a diary " +
                        "of the harvest. Last autumn the school canteen used __forty kilograms__ of tomatoes from the roof.",
                    audio = null,
                ),
            )
        }

        val tasks: List<ExamTask> by lazy {
            val layout = buildList {
                repeat(3) { add(Triple(LISTENING, groups[0].id, ExamTaskType.CLOSED_SINGLE)) }
                repeat(3) { add(Triple(LISTENING, groups[0].id, ExamTaskType.WRITTEN)) }
                repeat(16) { add(Triple("language", null, ExamTaskType.CLOSED_SINGLE)) }
                repeat(4) { add(Triple("reading", groups[1].id, ExamTaskType.CLOSED_SINGLE)) }
                repeat(4) { add(Triple("reading", groups[1].id, ExamTaskType.WRITTEN)) }
            }
            layout.mapIndexed { index, (section, group, type) ->
                val position = index + 1
                ExamTask(
                    id = "00000000-0000-4000-8000-%012d".format(position),
                    position = position,
                    sectionId = section,
                    groupId = group,
                    type = type,
                    prompt = if (type == ExamTaskType.CLOSED_SINGLE) {
                        "Debug task $position: she {gap} to school every day. (The right option is A.)"
                    } else {
                        "Debug task $position: answer in one or two sentences."
                    },
                    options = if (type == ExamTaskType.CLOSED_SINGLE) {
                        listOf("goes", "go", "going", "gone", "is go").mapIndexed { i, text ->
                            ExamOption("00000000-0000-4000-8001-%08d%04d".format(position, i), ('A' + i).toString(), text)
                        }
                    } else {
                        emptyList()
                    },
                )
            }
        }

        val transcript = listOf(
            ExamTranscriptLine("A", "Leyla", "Hi Tom. Is the library open on Sunday this week?"),
            ExamTranscriptLine("B", "Tom", "Not this Sunday, I'm afraid. It's closed for painting until Monday."),
        )

        val voices = listOf(
            ExamVoiceCredit("A", "en_GB-alba-medium", null, "CC-BY-4.0"),
            ExamVoiceCredit("B", "en_US-libritts_r-medium", 452, "CC-BY-4.0"),
        )

        val criteria = listOf(
            ExamCriterion(ExamMark.ONE, "Fikir tam və düzgün ifadə olunub."),
            ExamCriterion(ExamMark.HALF, "Fikir qismən ifadə olunub."),
            ExamCriterion(ExamMark.ZERO, "Cavab yoxdur və ya mövzuya aid deyil."),
        )

        /** A quiet 440 Hz tone, mono 8-bit PCM WAV, so ExoPlayer has something real to play. */
        fun toneWav(): ByteArray {
            val samples = SAMPLE_RATE * AUDIO_SECONDS
            val out = ByteArray(44 + samples)
            fun int(at: Int, value: Int) {
                for (i in 0..3) out[at + i] = (value shr (8 * i)).toByte()
            }
            fun short(at: Int, value: Int) {
                out[at] = value.toByte()
                out[at + 1] = (value shr 8).toByte()
            }
            "RIFF".toByteArray().copyInto(out, 0)
            int(4, 36 + samples)
            "WAVEfmt ".toByteArray().copyInto(out, 8)
            int(16, 16)
            short(20, 1)
            short(22, 1)
            int(24, SAMPLE_RATE)
            int(28, SAMPLE_RATE)
            short(32, 1)
            short(34, 8)
            "data".toByteArray().copyInto(out, 36)
            int(40, samples)
            for (i in 0 until samples) {
                out[44 + i] = (128 + 20 * sin(2 * PI * 440 * i / SAMPLE_RATE)).toInt().toByte()
            }
            return out
        }
    }
}

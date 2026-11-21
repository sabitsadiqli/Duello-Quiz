package com.sabit.duelloquiz.core.model.exam

import androidx.compose.runtime.Immutable
import com.sabit.duelloquiz.core.model.ProgressGain

/*
 * Mock exams "Sınaqlar" (ADR 0002, docs/api/openapi.yaml tag `exams`, API 0.5.0). Every score,
 * maximum and outcome is the server's; the client never derives correctness (EXAM-21).
 */

enum class ExamMode(val wire: String) {
    SUBJECT("subject"),
    FULL("full"),
    ;

    companion object {
        fun fromWire(value: String?): ExamMode = entries.firstOrNull { it.wire == value } ?: SUBJECT
    }
}

enum class ExamAttemptStatus(val wire: String) {
    RUNNING("running"),
    AWAITING_UPLOAD("awaiting_upload"),
    SUBMITTED("submitted"),
    FINALIZED("finalized"),
    ;

    val isFinished: Boolean get() = this == SUBMITTED || this == FINALIZED

    companion object {
        fun fromWire(value: String?): ExamAttemptStatus = entries.firstOrNull { it.wire == value } ?: RUNNING
    }
}

enum class ExamTaskType(val wire: String) {
    CLOSED_SINGLE("closed_single"),
    WRITTEN("written"),
    ;

    companion object {
        fun fromWire(value: String?): ExamTaskType = entries.firstOrNull { it.wire == value } ?: CLOSED_SINGLE
    }
}

enum class ExamGroupKind(val wire: String) {
    READING("reading"),
    LISTENING("listening"),
    ;

    companion object {
        fun fromWire(value: String?): ExamGroupKind? = entries.firstOrNull { it.wire == value }
    }
}

enum class ExamSubmitTrigger(val wire: String) {
    USER("user"),
    DEADLINE("deadline"),
    SERVER("server"),
    ;

    companion object {
        fun fromWire(value: String?): ExamSubmitTrigger = entries.firstOrNull { it.wire == value } ?: USER
    }
}

enum class ListeningExclusion(val wire: String) {
    SKIPPED("skipped"),
    AUDIO_FAILED("audio_failed"),
    ;

    companion object {
        fun fromWire(value: String?): ListeningExclusion? = entries.firstOrNull { it.wire == value }
    }
}

/** Self-assessed mark of a written task (OQ-112): 0, ⅓, ½, ⅔, 1; [sixths] as stored by the server. */
enum class ExamMark(val wire: String, val sixths: Int) {
    ZERO("0", 0),
    ONE_THIRD("1/3", 2),
    HALF("1/2", 3),
    TWO_THIRDS("2/3", 4),
    ONE("1", 6),
    ;

    companion object {
        fun fromWire(value: String?): ExamMark? = entries.firstOrNull { it.wire == value }
    }
}

/** Review outcome of a task (`ExamReviewTask.outcome`), the server's classification. */
enum class ExamOutcome(val wire: String) {
    CORRECT("correct"),
    WRONG("wrong"),
    BLANK("blank"),
    ASSESSED("assessed"),
    NOT_ASSESSED("not_assessed"),
    EXCLUDED("excluded"),
    ;

    companion object {
        fun fromWire(value: String?): ExamOutcome = entries.firstOrNull { it.wire == value } ?: BLANK
    }
}

/** `GET /v1/exams`. Empty [templates] = hide the "Sınaqlar" entry (EXAM-01). */
@Immutable
data class ExamCatalog(
    val templates: List<ExamTemplateSummary>,
    /** server clock − local wall clock, measured around the request. */
    val clockOffsetMs: Long = 0,
) {
    val variantCount: Int get() = templates.sumOf { it.variants.size }
}

@Immutable
data class ExamTemplateSummary(
    val id: String,
    /** Server i18n, e.g. "Buraxılış — İngilis dili"; the client adds "(DİM formatında)". */
    val title: String,
    val stage: Int,
    val mode: ExamMode,
    val language: String,
    val durationMs: Long,
    val taskCount: Int,
    val variants: List<ExamVariantSummary>,
)

@Immutable
data class ExamVariantSummary(
    val id: String,
    /** Shown as "Sınaq №N" (OQ-D49). */
    val number: Int,
    val version: Int,
    val taskCount: Int,
    val packageBytes: Long,
    val lastAttempt: ExamAttemptSummary?,
)

@Immutable
data class ExamAttemptSummary(
    val id: String,
    val variantId: String,
    val variantNumber: Int,
    val templateId: String,
    val templateTitle: String,
    val mode: ExamMode,
    val status: ExamAttemptStatus,
    val isRetake: Boolean,
    /** Baku day of the start, `YYYY-MM-DD`. */
    val dayKey: String,
    val startedAtEpochMs: Long,
    val endsAtEpochMs: Long,
    val submittedAtEpochMs: Long?,
    /** Rounded to 0.1 by the server; null until submitted or finalized. */
    val estimateTotal: Double?,
    val maxScore: Double,
    val writtenAssessed: Int,
    val writtenTotal: Int,
)

@Immutable
data class ExamScoringRule(val rule: String, val writtenWeight: Int, val rawMax: Int, val maxScore: Double)

@Immutable
data class ExamSectionInfo(
    val id: String,
    val title: String,
    val position: Int,
    val closedCount: Int,
    val writtenCount: Int,
    val groupKind: ExamGroupKind?,
)

/** `GET /v1/exams/variants/{id}` (EXAM-02). */
@Immutable
data class ExamVariantInfo(
    val id: String,
    val number: Int,
    val version: Int,
    val templateId: String,
    val templateTitle: String,
    val stage: Int,
    val language: String,
    val mode: ExamMode,
    val durationMs: Long,
    val graceMs: Long,
    val lateUploadWindowMs: Long,
    val listeningMaxPlays: Int,
    val listeningPlayCountsAfterMs: Long,
    val scoring: ExamScoringRule,
    val sections: List<ExamSectionInfo>,
    val taskCount: Int,
    val hasListening: Boolean,
    val hasWritten: Boolean,
    val packageBytes: Long,
    val attemptCount: Int,
    val lastAttempt: ExamAttemptSummary?,
)

@Immutable
data class ExamMedia(
    val url: String,
    val bytes: Long,
    /** Lower-case hex SHA-256 of the file (EXAM-03 verification). */
    val sha256: String,
    val contentType: String,
    val durationMs: Long?,
)

@Immutable
data class ExamSection(val id: String, val title: String, val position: Int)

@Immutable
data class ExamGroup(
    val id: String,
    val kind: ExamGroupKind,
    val sectionId: String,
    val position: Int,
    val title: String?,
    val instructions: String?,
    /** Reading groups: restricted markup (paragraphs, `**bold**`, `*italic*`, `__underline__`). */
    val passage: String?,
    /** Listening groups: the downloaded audio file, never streamed. */
    val audio: ExamMedia?,
)

@Immutable
data class ExamOption(val id: String, val letter: String, val text: String)

/** A task of the package: no correctness data at all (EXAM-21). */
@Immutable
data class ExamTask(
    val id: String,
    /** 1-based number in the exam ("14 / 30"). */
    val position: Int,
    val sectionId: String,
    val groupId: String?,
    val type: ExamTaskType,
    val prompt: String,
    val options: List<ExamOption>,
)

/** `GET /v1/exams/variants/{id}/package` (EXAM-03): everything needed offline, no keys. */
@Immutable
data class ExamPackage(
    val variantId: String,
    val version: Int,
    val templateId: String,
    val language: String,
    val durationMs: Long,
    val graceMs: Long,
    val optionCount: Int,
    val listeningMaxPlays: Int,
    val listeningPlayCountsAfterMs: Long,
    val maxTextLength: Int,
    val sections: List<ExamSection>,
    val groups: List<ExamGroup>,
    val tasks: List<ExamTask>,
    val media: List<ExamMedia>,
    val packageBytes: Long,
)

/** One task's synced state: the latest revision wins (EXAM-13). */
@Immutable
data class ExamAnswer(
    val taskId: String,
    val revision: Int,
    val optionId: String?,
    val text: String?,
    val flagged: Boolean,
    val savedAtEpochMs: Long,
)

@Immutable
data class ExamListeningState(
    val groupId: String,
    val playsUsed: Int,
    val audioFailed: Boolean,
    val positionMs: Long,
)

/** `POST /v1/exams/attempts`, `GET …/attempts/{id}`, `…/current` (EXAM-04, EXAM-13). */
@Immutable
data class ExamAttempt(
    val id: String,
    val variantId: String,
    val version: Int,
    val templateId: String,
    val mode: ExamMode,
    val status: ExamAttemptStatus,
    val isRetake: Boolean,
    val skipListening: Boolean,
    val resumed: Boolean,
    val startedAtEpochMs: Long,
    /** Countdown target and auto-submit moment (OQ-D41). */
    val endsAtEpochMs: Long,
    /** endsAt + grace; server side only, never displayed. */
    val deadlineEpochMs: Long,
    val lateUploadUntilEpochMs: Long,
    val submittedAtEpochMs: Long?,
    val answers: List<ExamAnswer>,
    val listening: List<ExamListeningState>,
    /** server clock − local wall clock, measured around the request (DUEL-12 method). */
    val clockOffsetMs: Long = 0,
)

/** One entry of `PUT …/answers`: the full state of a task at a client revision. */
@Immutable
data class ExamAnswerWrite(
    val taskId: String,
    val revision: Int,
    val optionId: String?,
    val text: String?,
    val flagged: Boolean,
    val savedAtEpochMs: Long,
)

enum class ExamIgnoredReason(val wire: String) {
    STALE_REVISION("stale_revision"),
    SAVED_AFTER_DEADLINE("saved_after_deadline"),
    ;

    companion object {
        fun fromWire(value: String?): ExamIgnoredReason = entries.firstOrNull { it.wire == value } ?: STALE_REVISION
    }
}

@Immutable
data class ExamAnswerBatchResult(
    val status: ExamAttemptStatus,
    val accepted: List<Pair<String, Int>>,
    val ignored: List<Pair<String, Int>>,
    val clockOffsetMs: Long = 0,
)

@Immutable
data class ExamEstimate(
    val total: Double,
    val autoScore: Double,
    val writtenScore: Double,
    /** Reachable maximum: 100 unless listening is excluded (OQ-D42). */
    val maxScore: Double,
    val rawMax: Int,
    val listeningExcluded: Boolean,
    val exclusion: ListeningExclusion?,
)

@Immutable
data class ExamClosedCounts(val correct: Int, val wrong: Int, val blank: Int, val total: Int)

@Immutable
data class ExamWrittenCounts(val assessed: Int, val total: Int, val marks: Double, val maxMarks: Int) {
    val unassessed: Int get() = (total - assessed).coerceAtLeast(0)
}

@Immutable
data class ExamSectionResult(
    val id: String,
    val title: String,
    val closedCorrect: Int,
    val closedWrong: Int,
    val closedBlank: Int,
    val closedTotal: Int,
    val writtenAssessed: Int,
    val writtenTotal: Int,
    val writtenMarks: Double,
    val excluded: Boolean,
)

/** `…/submit`, `…/result`, `…/self-assessment` (EXAM-15 … EXAM-17). */
@Immutable
data class ExamResult(
    val attemptId: String,
    val variantId: String,
    val version: Int,
    val templateId: String,
    val mode: ExamMode,
    val status: ExamAttemptStatus,
    val isRetake: Boolean,
    val submitTrigger: ExamSubmitTrigger,
    val submittedAtEpochMs: Long,
    val estimate: ExamEstimate,
    val closed: ExamClosedCounts,
    val written: ExamWrittenCounts,
    val sections: List<ExamSectionResult>,
    val answeredCount: Int,
    val flaggedCount: Int,
    /** Streak change of this submit (no XP, OQ-125); null for a server finalization. */
    val progress: ProgressGain?,
)

@Immutable
data class ExamTranscriptLine(val speaker: String, val speakerName: String, val text: String)

@Immutable
data class ExamVoiceCredit(val role: String, val voice: String, val speakerId: Int?, val licence: String)

@Immutable
data class ExamReviewGroup(
    val id: String,
    val kind: ExamGroupKind,
    val sectionId: String,
    val position: Int,
    val title: String?,
    val instructions: String?,
    val passage: String?,
    val audio: ExamMedia?,
    val transcript: List<ExamTranscriptLine>,
    val voices: List<ExamVoiceCredit>,
)

@Immutable
data class ExamReviewOption(val id: String, val letter: String, val text: String, val correct: Boolean)

@Immutable
data class ExamCriterion(val mark: ExamMark, val description: String)

@Immutable
data class ExamReviewTask(
    val id: String,
    val position: Int,
    val sectionId: String,
    val groupId: String?,
    val type: ExamTaskType,
    val prompt: String,
    val options: List<ExamReviewOption>,
    val selectedOptionId: String?,
    val correctOptionId: String?,
    val outcome: ExamOutcome,
    val text: String?,
    val flagged: Boolean,
    val explanation: String?,
    val modelAnswer: String?,
    val criteria: List<ExamCriterion>,
    val selfMark: ExamMark?,
)

/** `GET …/review` (EXAM-18): keys, explanations, model answers and transcripts after submit only. */
@Immutable
data class ExamReview(
    val attemptId: String,
    val variantId: String,
    val version: Int,
    val language: String,
    val sections: List<ExamSection>,
    val groups: List<ExamReviewGroup>,
    val tasks: List<ExamReviewTask>,
)

/** Result of `POST /v1/exams/attempts`. */
sealed interface ExamStartOutcome {
    data class Started(val attempt: ExamAttempt) : ExamStartOutcome

    /** 409 `exam_attempt_running`: another variant runs; [running] from `GET …/current` when known. */
    data class OtherRunning(val running: ExamAttempt?) : ExamStartOutcome
}

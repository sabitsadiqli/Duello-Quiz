package com.sabit.duelloquiz.backend.exam

import com.sabit.duelloquiz.backend.progress.ProgressDto
import kotlinx.serialization.Serializable

/* Wire format of /v1/exams* (docs/api/openapi.yaml, tag `exams`). */

@Serializable
data class ExamCatalogResponse(val serverNowEpochMs: Long, val templates: List<ExamTemplateSummaryDto>)

@Serializable
data class ExamTemplateSummaryDto(
    val id: String,
    val title: String,
    val stage: Int,
    val mode: String,
    val language: String,
    val durationMs: Long,
    val taskCount: Int,
    val accessTier: String?,
    val variants: List<ExamVariantSummaryDto>,
)

@Serializable
data class ExamVariantSummaryDto(
    val id: String,
    val number: Int,
    val version: Int,
    val taskCount: Int,
    val packageBytes: Long,
    val lastAttempt: ExamAttemptSummaryDto?,
)

@Serializable
data class ExamAttemptSummaryDto(
    val id: String,
    val variantId: String,
    val variantNumber: Int,
    val templateId: String,
    val templateTitle: String,
    val mode: String,
    val status: String,
    val isRetake: Boolean,
    val dayKey: String,
    val startedAtEpochMs: Long,
    val endsAtEpochMs: Long,
    val submittedAtEpochMs: Long?,
    val estimateTotal: Double?,
    val maxScore: Double,
    val writtenAssessed: Int,
    val writtenTotal: Int,
)

@Serializable
data class ExamAttemptListResponse(val attempts: List<ExamAttemptSummaryDto>)

@Serializable
data class ExamTemplateRefDto(val id: String, val title: String, val stage: Int, val accessTier: String?)

@Serializable
data class ExamScoringRuleDto(val rule: String, val writtenWeight: Int, val rawMax: Int, val maxScore: Double)

@Serializable
data class ExamSectionInfoDto(
    val id: String,
    val title: String,
    val position: Int,
    val closedCount: Int,
    val writtenCount: Int,
    val groupKind: String?,
)

@Serializable
data class ExamVariantInfoResponse(
    val id: String,
    val number: Int,
    val version: Int,
    val template: ExamTemplateRefDto,
    val language: String,
    val mode: String,
    val durationMs: Long,
    val graceMs: Long,
    val lateUploadWindowMs: Long,
    val listeningMaxPlays: Int,
    val listeningPlayCountsAfterMs: Long,
    val scoring: ExamScoringRuleDto,
    val sections: List<ExamSectionInfoDto>,
    val taskCount: Int,
    val hasListening: Boolean,
    val hasWritten: Boolean,
    val packageBytes: Long,
    val attemptCount: Int,
    val lastAttempt: ExamAttemptSummaryDto?,
)

@Serializable
data class ExamSectionDto(val id: String, val title: String, val position: Int)

@Serializable
data class ExamMediaDto(
    val url: String,
    val bytes: Int,
    val sha256: String,
    val contentType: String,
    val durationMs: Int?,
)

@Serializable
data class ExamPackageGroupDto(
    val id: String,
    val kind: String,
    val sectionId: String,
    val position: Int,
    val title: String?,
    val instructions: String?,
    val passage: String?,
    val audio: ExamMediaDto?,
)

@Serializable
data class ExamPackageOptionDto(val id: String, val letter: String, val text: String)

@Serializable
data class ExamPackageTaskDto(
    val id: String,
    val position: Int,
    val sectionId: String,
    val groupId: String?,
    val type: String,
    val answerFormat: String?,
    val prompt: String,
    val options: List<ExamPackageOptionDto>,
)

/** Offline package: never keys, explanations, model answers, criteria, speakers or scripts (EXAM-21). */
@Serializable
data class ExamPackageResponse(
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
    val sections: List<ExamSectionDto>,
    val groups: List<ExamPackageGroupDto>,
    val tasks: List<ExamPackageTaskDto>,
    val media: List<ExamMediaDto>,
    val packageBytes: Long,
)

@Serializable
data class StartExamAttemptRequest(
    val variantId: String,
    val version: Int,
    val deviceId: String,
    val idempotencyKey: String,
    val mode: String = ExamConfig.MODE_SUBJECT,
    val skipListening: Boolean = false,
)

@Serializable
data class ExamAnswerDto(
    val taskId: String,
    val revision: Int,
    val optionId: String?,
    val text: String?,
    val flagged: Boolean,
    val savedAtEpochMs: Long,
)

@Serializable
data class ExamListeningStateDto(val groupId: String, val playsUsed: Int, val playsLeft: Int, val audioFailed: Boolean)

@Serializable
data class ExamAttemptResponse(
    val id: String,
    val variantId: String,
    val version: Int,
    val templateId: String,
    val mode: String,
    val status: String,
    val isRetake: Boolean,
    val skipListening: Boolean,
    val resumed: Boolean,
    val startedAtEpochMs: Long,
    val endsAtEpochMs: Long,
    val deadlineEpochMs: Long,
    val lateUploadUntilEpochMs: Long,
    val serverNowEpochMs: Long,
    val submittedAtEpochMs: Long?,
    val answers: List<ExamAnswerDto>,
    val listening: List<ExamListeningStateDto>,
)

@Serializable
data class CurrentExamAttemptResponse(val attempt: ExamAttemptResponse?)

@Serializable
data class ExamAnswerWriteDto(
    val taskId: String,
    val revision: Int,
    val optionId: String? = null,
    val text: String? = null,
    val flagged: Boolean = false,
    val savedAtEpochMs: Long,
)

@Serializable
data class ExamAnswerBatchRequest(val deviceId: String, val answers: List<ExamAnswerWriteDto>)

@Serializable
data class ExamSingleAnswerRequest(
    val deviceId: String,
    val revision: Int,
    val optionId: String? = null,
    val text: String? = null,
    val flagged: Boolean = false,
    val savedAtEpochMs: Long,
)

@Serializable
data class AcceptedAnswerDto(val taskId: String, val revision: Int)

@Serializable
data class IgnoredAnswerDto(val taskId: String, val revision: Int, val reason: String)

@Serializable
data class ExamAnswerBatchResponse(
    val status: String,
    val serverNowEpochMs: Long,
    val accepted: List<AcceptedAnswerDto>,
    val ignored: List<IgnoredAnswerDto>,
)

@Serializable
data class ExamListeningUpdateRequest(
    val deviceId: String,
    val playsUsed: Int,
    val audioFailed: Boolean = false,
    val savedAtEpochMs: Long,
)

@Serializable
data class SubmitExamRequest(val deviceId: String, val trigger: String = "user")

@Serializable
data class ExamEstimateDto(
    val total: Double,
    val autoScore: Double,
    val writtenScore: Double,
    val maxScore: Double,
    val rawMax: Int,
    val listeningExcluded: Boolean,
    val listeningExclusionReason: String?,
)

@Serializable
data class ClosedSummaryDto(val correct: Int, val wrong: Int, val blank: Int, val total: Int)

@Serializable
data class WrittenSummaryDto(val assessed: Int, val total: Int, val marks: Double, val maxMarks: Int)

@Serializable
data class ExamSectionResultDto(
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

@Serializable
data class ExamResultResponse(
    val attemptId: String,
    val variantId: String,
    val version: Int,
    val templateId: String,
    val mode: String,
    val status: String,
    val isRetake: Boolean,
    val submitTrigger: String,
    val submittedAtEpochMs: Long,
    val estimate: ExamEstimateDto,
    val closed: ClosedSummaryDto,
    val written: WrittenSummaryDto,
    val sections: List<ExamSectionResultDto>,
    val answeredCount: Int,
    val flaggedCount: Int,
    val progress: ProgressDto?,
)

@Serializable
data class SelfAssessmentEntryDto(val taskId: String, val mark: String?)

@Serializable
data class SelfAssessmentRequest(val marks: List<SelfAssessmentEntryDto>)

@Serializable
data class TranscriptLineDto(val speaker: String, val speakerName: String, val text: String)

@Serializable
data class VoiceCreditDto(val role: String, val voice: String, val speakerId: Int?, val licence: String)

@Serializable
data class ExamReviewGroupDto(
    val id: String,
    val kind: String,
    val sectionId: String,
    val position: Int,
    val title: String?,
    val instructions: String?,
    val passage: String?,
    val audio: ExamMediaDto?,
    val transcript: List<TranscriptLineDto>,
    val voices: List<VoiceCreditDto>,
)

@Serializable
data class ExamReviewOptionDto(val id: String, val letter: String, val text: String, val correct: Boolean)

@Serializable
data class CriterionDto(val mark: String, val description: String)

@Serializable
data class ExamReviewTaskDto(
    val id: String,
    val position: Int,
    val sectionId: String,
    val groupId: String?,
    val type: String,
    val prompt: String,
    val options: List<ExamReviewOptionDto>,
    val selectedOptionId: String?,
    val correctOptionId: String?,
    val outcome: String,
    val text: String?,
    val flagged: Boolean,
    val explanation: String?,
    val modelAnswer: String?,
    val criteria: List<CriterionDto>,
    val selfMark: String?,
)

@Serializable
data class ExamReviewResponse(
    val attemptId: String,
    val variantId: String,
    val version: Int,
    val language: String,
    val sections: List<ExamSectionDto>,
    val groups: List<ExamReviewGroupDto>,
    val tasks: List<ExamReviewTaskDto>,
)

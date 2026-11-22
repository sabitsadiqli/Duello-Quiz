package com.sabit.duelloquiz.core.network.model

import kotlinx.serialization.Serializable

// Wire types of the `exams` tag (docs/api/openapi.yaml 0.5.0, ADR 0002). Internal to :core:network.

@Serializable
internal data class NetworkExamCatalog(
    val serverNowEpochMs: Long,
    val templates: List<NetworkExamTemplateSummary> = emptyList(),
)

@Serializable
internal data class NetworkExamTemplateSummary(
    val id: String,
    val title: String,
    val stage: Int = 1,
    val mode: String = "subject",
    val language: String = "en",
    val durationMs: Long,
    val taskCount: Int,
    val accessTier: String? = null,
    val variants: List<NetworkExamVariantSummary> = emptyList(),
)

@Serializable
internal data class NetworkExamVariantSummary(
    val id: String,
    val number: Int,
    val version: Int,
    val taskCount: Int,
    val packageBytes: Long,
    val lastAttempt: NetworkExamAttemptSummary? = null,
)

@Serializable
internal data class NetworkExamAttemptSummary(
    val id: String,
    val variantId: String,
    val variantNumber: Int,
    val templateId: String,
    val templateTitle: String,
    val mode: String = "subject",
    val status: String,
    val isRetake: Boolean = false,
    val dayKey: String,
    val startedAtEpochMs: Long,
    val endsAtEpochMs: Long,
    val submittedAtEpochMs: Long? = null,
    val estimateTotal: Double? = null,
    val maxScore: Double = 100.0,
    val writtenAssessed: Int = 0,
    val writtenTotal: Int = 0,
)

@Serializable
internal data class NetworkExamAttemptList(val attempts: List<NetworkExamAttemptSummary> = emptyList())

@Serializable
internal data class NetworkExamTemplateRef(
    val id: String,
    val title: String,
    val stage: Int = 1,
    val accessTier: String? = null,
)

@Serializable
internal data class NetworkExamScoringRule(
    val rule: String,
    val writtenWeight: Int,
    val rawMax: Int,
    val maxScore: Double,
)

@Serializable
internal data class NetworkExamSectionInfo(
    val id: String,
    val title: String,
    val position: Int,
    val closedCount: Int = 0,
    val writtenCount: Int = 0,
    val groupKind: String? = null,
)

@Serializable
internal data class NetworkExamVariantInfo(
    val id: String,
    val number: Int,
    val version: Int,
    val template: NetworkExamTemplateRef,
    val language: String = "en",
    val mode: String = "subject",
    val durationMs: Long,
    val graceMs: Long = 0,
    val lateUploadWindowMs: Long = 0,
    val listeningMaxPlays: Int = 2,
    val listeningPlayCountsAfterMs: Long = 10_000,
    val scoring: NetworkExamScoringRule,
    val sections: List<NetworkExamSectionInfo> = emptyList(),
    val taskCount: Int,
    val hasListening: Boolean = false,
    val hasWritten: Boolean = false,
    val packageBytes: Long,
    val attemptCount: Int = 0,
    val lastAttempt: NetworkExamAttemptSummary? = null,
)

@Serializable
internal data class NetworkExamMedia(
    val url: String,
    val bytes: Long,
    val sha256: String,
    val contentType: String,
    val durationMs: Long? = null,
)

@Serializable
internal data class NetworkExamSection(val id: String, val title: String, val position: Int)

@Serializable
internal data class NetworkExamPackageGroup(
    val id: String,
    val kind: String,
    val sectionId: String,
    val position: Int,
    val title: String? = null,
    val instructions: String? = null,
    val passage: String? = null,
    val audio: NetworkExamMedia? = null,
)

@Serializable
internal data class NetworkExamOption(val id: String, val letter: String, val text: String)

@Serializable
internal data class NetworkExamPackageTask(
    val id: String,
    val position: Int,
    val sectionId: String,
    val groupId: String? = null,
    val type: String,
    val answerFormat: String? = null,
    val prompt: String,
    val options: List<NetworkExamOption> = emptyList(),
)

@Serializable
internal data class NetworkExamPackage(
    val variantId: String,
    val version: Int,
    val templateId: String,
    val language: String = "en",
    val durationMs: Long,
    val graceMs: Long = 0,
    val optionCount: Int = 5,
    val listeningMaxPlays: Int = 2,
    val listeningPlayCountsAfterMs: Long = 10_000,
    val maxTextLength: Int = 1_000,
    val sections: List<NetworkExamSection> = emptyList(),
    val groups: List<NetworkExamPackageGroup> = emptyList(),
    val tasks: List<NetworkExamPackageTask> = emptyList(),
    val media: List<NetworkExamMedia> = emptyList(),
    val packageBytes: Long = 0,
)

@Serializable
internal data class NetworkStartExamAttemptRequest(
    val variantId: String,
    val version: Int,
    val deviceId: String,
    val idempotencyKey: String,
    val mode: String = "subject",
    val skipListening: Boolean = false,
)

@Serializable
internal data class NetworkExamAnswer(
    val taskId: String,
    val revision: Int,
    val optionId: String? = null,
    val text: String? = null,
    val flagged: Boolean = false,
    val savedAtEpochMs: Long,
)

@Serializable
internal data class NetworkExamListeningState(
    val groupId: String,
    val playsUsed: Int = 0,
    val playsLeft: Int = 0,
    val audioFailed: Boolean = false,
    val positionMs: Long = 0,
)

@Serializable
internal data class NetworkExamAttempt(
    val id: String,
    val variantId: String,
    val version: Int,
    val templateId: String,
    val mode: String = "subject",
    val status: String,
    val isRetake: Boolean = false,
    val skipListening: Boolean = false,
    val resumed: Boolean = false,
    val startedAtEpochMs: Long,
    val endsAtEpochMs: Long,
    val deadlineEpochMs: Long,
    val lateUploadUntilEpochMs: Long,
    val serverNowEpochMs: Long,
    val submittedAtEpochMs: Long? = null,
    val answers: List<NetworkExamAnswer> = emptyList(),
    val listening: List<NetworkExamListeningState> = emptyList(),
)

@Serializable
internal data class NetworkCurrentExamAttempt(val attempt: NetworkExamAttempt? = null)

@Serializable
internal data class NetworkExamAnswerWrite(
    val taskId: String,
    val revision: Int,
    // Explicit nulls: `optionId: null` clears a closed task, `text: null` empties a written one.
    val optionId: String?,
    val text: String?,
    val flagged: Boolean,
    val savedAtEpochMs: Long,
)

@Serializable
internal data class NetworkExamAnswerBatch(val deviceId: String, val answers: List<NetworkExamAnswerWrite>)

@Serializable
internal data class NetworkExamRevisionRef(val taskId: String, val revision: Int, val reason: String? = null)

@Serializable
internal data class NetworkExamAnswerBatchResult(
    val status: String,
    val serverNowEpochMs: Long,
    val accepted: List<NetworkExamRevisionRef> = emptyList(),
    val ignored: List<NetworkExamRevisionRef> = emptyList(),
)

@Serializable
internal data class NetworkExamListeningUpdate(
    val deviceId: String,
    val playsUsed: Int,
    val audioFailed: Boolean,
    val savedAtEpochMs: Long,
    val positionMs: Long,
)

@Serializable
internal data class NetworkSubmitExamRequest(val deviceId: String, val trigger: String)

@Serializable
internal data class NetworkExamEstimate(
    val total: Double,
    val autoScore: Double,
    val writtenScore: Double,
    val maxScore: Double,
    val rawMax: Int,
    val listeningExcluded: Boolean = false,
    val listeningExclusionReason: String? = null,
)

@Serializable
internal data class NetworkExamClosedCounts(val correct: Int, val wrong: Int, val blank: Int, val total: Int)

@Serializable
internal data class NetworkExamWrittenCounts(val assessed: Int, val total: Int, val marks: Double, val maxMarks: Int)

@Serializable
internal data class NetworkExamSectionResult(
    val id: String,
    val title: String,
    val closedCorrect: Int = 0,
    val closedWrong: Int = 0,
    val closedBlank: Int = 0,
    val closedTotal: Int = 0,
    val writtenAssessed: Int = 0,
    val writtenTotal: Int = 0,
    val writtenMarks: Double = 0.0,
    val excluded: Boolean = false,
)

@Serializable
internal data class NetworkExamResult(
    val attemptId: String,
    val variantId: String,
    val version: Int,
    val templateId: String,
    val mode: String = "subject",
    val status: String,
    val isRetake: Boolean = false,
    val submitTrigger: String = "user",
    val submittedAtEpochMs: Long,
    val estimate: NetworkExamEstimate,
    val closed: NetworkExamClosedCounts,
    val written: NetworkExamWrittenCounts,
    val sections: List<NetworkExamSectionResult> = emptyList(),
    val answeredCount: Int = 0,
    val flaggedCount: Int = 0,
    val progress: NetworkProgress? = null,
)

@Serializable
internal data class NetworkSelfAssessmentMark(val taskId: String, val mark: String?)

@Serializable
internal data class NetworkSelfAssessmentRequest(val marks: List<NetworkSelfAssessmentMark>)

@Serializable
internal data class NetworkExamTranscriptLine(val speaker: String, val speakerName: String, val text: String)

@Serializable
internal data class NetworkExamVoiceCredit(
    val role: String,
    val voice: String,
    val speakerId: Int? = null,
    val licence: String,
)

@Serializable
internal data class NetworkExamReviewGroup(
    val id: String,
    val kind: String,
    val sectionId: String,
    val position: Int,
    val title: String? = null,
    val instructions: String? = null,
    val passage: String? = null,
    val audio: NetworkExamMedia? = null,
    val transcript: List<NetworkExamTranscriptLine> = emptyList(),
    val voices: List<NetworkExamVoiceCredit> = emptyList(),
)

@Serializable
internal data class NetworkExamReviewOption(val id: String, val letter: String, val text: String, val correct: Boolean = false)

@Serializable
internal data class NetworkExamCriterion(val mark: String, val description: String)

@Serializable
internal data class NetworkExamReviewTask(
    val id: String,
    val position: Int,
    val sectionId: String,
    val groupId: String? = null,
    val type: String,
    val prompt: String,
    val options: List<NetworkExamReviewOption> = emptyList(),
    val selectedOptionId: String? = null,
    val correctOptionId: String? = null,
    val outcome: String,
    val text: String? = null,
    val flagged: Boolean = false,
    val explanation: String? = null,
    val modelAnswer: String? = null,
    val criteria: List<NetworkExamCriterion> = emptyList(),
    val selfMark: String? = null,
)

@Serializable
internal data class NetworkExamReview(
    val attemptId: String,
    val variantId: String,
    val version: Int,
    val language: String = "en",
    val sections: List<NetworkExamSection> = emptyList(),
    val groups: List<NetworkExamReviewGroup> = emptyList(),
    val tasks: List<NetworkExamReviewTask> = emptyList(),
)

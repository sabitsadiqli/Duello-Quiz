package com.sabit.duelloquiz.core.network.model

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

internal fun NetworkExamCatalog.asExternalModel(clockOffsetMs: Long) = ExamCatalog(
    templates = templates.map { it.asExternalModel() },
    clockOffsetMs = clockOffsetMs,
)

internal fun NetworkExamTemplateSummary.asExternalModel() = ExamTemplateSummary(
    id = id,
    title = title,
    stage = stage,
    mode = ExamMode.fromWire(mode),
    language = language,
    durationMs = durationMs,
    taskCount = taskCount,
    variants = variants.map { it.asExternalModel() },
)

internal fun NetworkExamVariantSummary.asExternalModel() = ExamVariantSummary(
    id = id,
    number = number,
    version = version,
    taskCount = taskCount,
    packageBytes = packageBytes,
    lastAttempt = lastAttempt?.asExternalModel(),
)

internal fun NetworkExamAttemptSummary.asExternalModel() = ExamAttemptSummary(
    id = id,
    variantId = variantId,
    variantNumber = variantNumber,
    templateId = templateId,
    templateTitle = templateTitle,
    mode = ExamMode.fromWire(mode),
    status = ExamAttemptStatus.fromWire(status),
    isRetake = isRetake,
    dayKey = dayKey,
    startedAtEpochMs = startedAtEpochMs,
    endsAtEpochMs = endsAtEpochMs,
    submittedAtEpochMs = submittedAtEpochMs,
    estimateTotal = estimateTotal,
    maxScore = maxScore,
    writtenAssessed = writtenAssessed,
    writtenTotal = writtenTotal,
)

internal fun NetworkExamVariantInfo.asExternalModel() = ExamVariantInfo(
    id = id,
    number = number,
    version = version,
    templateId = template.id,
    templateTitle = template.title,
    stage = template.stage,
    language = language,
    mode = ExamMode.fromWire(mode),
    durationMs = durationMs,
    graceMs = graceMs,
    lateUploadWindowMs = lateUploadWindowMs,
    listeningMaxPlays = listeningMaxPlays,
    listeningPlayCountsAfterMs = listeningPlayCountsAfterMs,
    scoring = ExamScoringRule(scoring.rule, scoring.writtenWeight, scoring.rawMax, scoring.maxScore),
    sections = sections.map {
        ExamSectionInfo(it.id, it.title, it.position, it.closedCount, it.writtenCount, ExamGroupKind.fromWire(it.groupKind))
    }.sortedBy { it.position },
    taskCount = taskCount,
    hasListening = hasListening,
    hasWritten = hasWritten,
    packageBytes = packageBytes,
    attemptCount = attemptCount,
    lastAttempt = lastAttempt?.asExternalModel(),
)

internal fun NetworkExamMedia.asExternalModel() = ExamMedia(
    url = url,
    bytes = bytes,
    sha256 = sha256.lowercase(),
    contentType = contentType,
    durationMs = durationMs,
)

/**
 * Groups of an unknown kind and tasks of an unknown type are dropped (a newer server); nothing
 * else is interpreted.
 */
internal fun NetworkExamPackage.asExternalModel() = ExamPackage(
    variantId = variantId,
    version = version,
    templateId = templateId,
    language = language,
    durationMs = durationMs,
    graceMs = graceMs,
    optionCount = optionCount,
    listeningMaxPlays = listeningMaxPlays,
    listeningPlayCountsAfterMs = listeningPlayCountsAfterMs,
    maxTextLength = maxTextLength,
    sections = sections.map { ExamSection(it.id, it.title, it.position) }.sortedBy { it.position },
    groups = groups.mapNotNull { group ->
        val kind = ExamGroupKind.fromWire(group.kind) ?: return@mapNotNull null
        ExamGroup(
            id = group.id,
            kind = kind,
            sectionId = group.sectionId,
            position = group.position,
            title = group.title,
            instructions = group.instructions,
            passage = group.passage,
            audio = group.audio?.asExternalModel(),
        )
    },
    tasks = tasks.filter { it.type in KnownTaskTypes }.map { task ->
        ExamTask(
            id = task.id,
            position = task.position,
            sectionId = task.sectionId,
            groupId = task.groupId,
            type = ExamTaskType.fromWire(task.type),
            prompt = task.prompt,
            options = task.options.map { ExamOption(it.id, it.letter, it.text) },
        )
    }.sortedBy { it.position },
    media = media.map { it.asExternalModel() },
    packageBytes = packageBytes,
)

private val KnownTaskTypes = ExamTaskType.entries.map { it.wire }.toSet()

internal fun NetworkExamAttempt.asExternalModel(clockOffsetMs: Long) = ExamAttempt(
    id = id,
    variantId = variantId,
    version = version,
    templateId = templateId,
    mode = ExamMode.fromWire(mode),
    status = ExamAttemptStatus.fromWire(status),
    isRetake = isRetake,
    skipListening = skipListening,
    resumed = resumed,
    startedAtEpochMs = startedAtEpochMs,
    endsAtEpochMs = endsAtEpochMs,
    deadlineEpochMs = deadlineEpochMs,
    lateUploadUntilEpochMs = lateUploadUntilEpochMs,
    submittedAtEpochMs = submittedAtEpochMs,
    answers = answers.map { ExamAnswer(it.taskId, it.revision, it.optionId, it.text, it.flagged, it.savedAtEpochMs) },
    listening = listening.map { ExamListeningState(it.groupId, it.playsUsed, it.audioFailed, it.positionMs) },
    clockOffsetMs = clockOffsetMs,
)

internal fun ExamAnswerWrite.asNetworkModel() = NetworkExamAnswerWrite(
    taskId = taskId,
    revision = revision,
    optionId = optionId,
    text = text,
    flagged = flagged,
    savedAtEpochMs = savedAtEpochMs,
)

internal fun NetworkExamAnswerBatchResult.asExternalModel(clockOffsetMs: Long) = ExamAnswerBatchResult(
    status = ExamAttemptStatus.fromWire(status),
    accepted = accepted.map { it.taskId to it.revision },
    ignored = ignored.map { it.taskId to it.revision },
    clockOffsetMs = clockOffsetMs,
)

internal fun NetworkExamListeningState.asExternalModel() = ExamListeningState(groupId, playsUsed, audioFailed, positionMs)

internal fun NetworkExamResult.asExternalModel() = ExamResult(
    attemptId = attemptId,
    variantId = variantId,
    version = version,
    templateId = templateId,
    mode = ExamMode.fromWire(mode),
    status = ExamAttemptStatus.fromWire(status),
    isRetake = isRetake,
    submitTrigger = ExamSubmitTrigger.fromWire(submitTrigger),
    submittedAtEpochMs = submittedAtEpochMs,
    estimate = ExamEstimate(
        total = estimate.total,
        autoScore = estimate.autoScore,
        writtenScore = estimate.writtenScore,
        maxScore = estimate.maxScore,
        rawMax = estimate.rawMax,
        listeningExcluded = estimate.listeningExcluded,
        exclusion = ListeningExclusion.fromWire(estimate.listeningExclusionReason),
    ),
    closed = ExamClosedCounts(closed.correct, closed.wrong, closed.blank, closed.total),
    written = ExamWrittenCounts(written.assessed, written.total, written.marks, written.maxMarks),
    sections = sections.map {
        ExamSectionResult(
            id = it.id,
            title = it.title,
            closedCorrect = it.closedCorrect,
            closedWrong = it.closedWrong,
            closedBlank = it.closedBlank,
            closedTotal = it.closedTotal,
            writtenAssessed = it.writtenAssessed,
            writtenTotal = it.writtenTotal,
            writtenMarks = it.writtenMarks,
            excluded = it.excluded,
        )
    },
    answeredCount = answeredCount,
    flaggedCount = flaggedCount,
    progress = progress?.asExternalModel(),
)

internal fun NetworkExamReview.asExternalModel() = ExamReview(
    attemptId = attemptId,
    variantId = variantId,
    version = version,
    language = language,
    sections = sections.map { ExamSection(it.id, it.title, it.position) }.sortedBy { it.position },
    groups = groups.mapNotNull { group ->
        val kind = ExamGroupKind.fromWire(group.kind) ?: return@mapNotNull null
        ExamReviewGroup(
            id = group.id,
            kind = kind,
            sectionId = group.sectionId,
            position = group.position,
            title = group.title,
            instructions = group.instructions,
            passage = group.passage,
            audio = group.audio?.asExternalModel(),
            transcript = group.transcript.map { ExamTranscriptLine(it.speaker, it.speakerName, it.text) },
            voices = group.voices.map { ExamVoiceCredit(it.role, it.voice, it.speakerId, it.licence) },
        )
    },
    tasks = tasks.filter { it.type in KnownTaskTypes }.map { task ->
        ExamReviewTask(
            id = task.id,
            position = task.position,
            sectionId = task.sectionId,
            groupId = task.groupId,
            type = ExamTaskType.fromWire(task.type),
            prompt = task.prompt,
            options = task.options.map { ExamReviewOption(it.id, it.letter, it.text, it.correct) },
            selectedOptionId = task.selectedOptionId,
            correctOptionId = task.correctOptionId,
            outcome = ExamOutcome.fromWire(task.outcome),
            text = task.text,
            flagged = task.flagged,
            explanation = task.explanation,
            modelAnswer = task.modelAnswer,
            criteria = task.criteria.mapNotNull { c -> ExamMark.fromWire(c.mark)?.let { ExamCriterion(it, c.description) } },
            selfMark = ExamMark.fromWire(task.selfMark),
        )
    }.sortedBy { it.position },
)

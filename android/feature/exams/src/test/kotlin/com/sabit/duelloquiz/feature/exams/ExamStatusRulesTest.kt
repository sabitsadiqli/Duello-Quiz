package com.sabit.duelloquiz.feature.exams

import com.sabit.duelloquiz.core.data.exam.ExamDownloadState
import com.sabit.duelloquiz.core.data.exam.LocalAttemptPhase
import com.sabit.duelloquiz.core.data.exam.LocalExamAttempt
import com.sabit.duelloquiz.core.model.exam.ExamAttemptStatus
import com.sabit.duelloquiz.core.model.exam.ExamAttemptSummary
import com.sabit.duelloquiz.core.model.exam.ExamMode
import com.sabit.duelloquiz.core.model.exam.ExamScoringRule
import com.sabit.duelloquiz.core.model.exam.ExamVariantInfo
import com.sabit.duelloquiz.core.model.exam.ExamVariantSummary
import com.sabit.duelloquiz.feature.exams.catalog.VariantStatus
import com.sabit.duelloquiz.feature.exams.catalog.variantStatus
import com.sabit.duelloquiz.feature.exams.info.InfoAction
import com.sabit.duelloquiz.feature.exams.info.infoAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Catalogue status priority (exam-catalogue.md) and the info action table (exam-info.md). */
class ExamStatusRulesTest {

    private fun summary(status: ExamAttemptStatus, estimate: Double? = 67.7, assessed: Int = 7, ends: Long = 5_000_000) = ExamAttemptSummary(
        id = "a1", variantId = "v1", variantNumber = 1, templateId = "t", templateTitle = "T", mode = ExamMode.SUBJECT,
        status = status, isRetake = false, dayKey = "2026-11-21", startedAtEpochMs = 0, endsAtEpochMs = ends,
        submittedAtEpochMs = null, estimateTotal = estimate, maxScore = 100.0, writtenAssessed = assessed, writtenTotal = 7,
    )

    private fun variant(last: ExamAttemptSummary? = null) = ExamVariantSummary("v1", 1, 2, 30, 3_400_000, last)

    private val local = LocalExamAttempt(
        "a1", "v1", 1, 2, "t", "T", false, false, 0, 3_600_000, 3_720_000, 90_000_000, 0,
        LocalAttemptPhase.RUNNING, null, null, emptySet(), false,
    )

    @Test
    fun statusPriority() {
        assertEquals(VariantStatus.Running(42), variantStatus(variant(), local, ExamDownloadState.Idle, 2, 0, 3_600_000 - 42 * 60_000L + 1))
        assertEquals(VariantStatus.Downloading(50), variantStatus(variant(), null, ExamDownloadState.Running(50, 100), null, 0, 0))
        assertEquals(VariantStatus.Pending, variantStatus(variant(summary(ExamAttemptStatus.SUBMITTED, estimate = null)), null, ExamDownloadState.Idle, null, 0, 0))
        assertEquals(VariantStatus.NeedsAssessment, variantStatus(variant(summary(ExamAttemptStatus.SUBMITTED, assessed = 3)), null, ExamDownloadState.Idle, 2, 0, 0))
        assertEquals(VariantStatus.UpdateNeeded, variantStatus(variant(), null, ExamDownloadState.Idle, 1, 0, 0))
        assertEquals(VariantStatus.Score("67,7"), variantStatus(variant(summary(ExamAttemptStatus.SUBMITTED)), null, ExamDownloadState.Idle, 2, 0, 0))
        assertEquals(VariantStatus.Downloaded, variantStatus(variant(), null, ExamDownloadState.Done(2), 2, 0, 0))
        assertEquals(VariantStatus.NotStarted, variantStatus(variant(), null, ExamDownloadState.Idle, null, 0, 0))
    }

    private val info = ExamVariantInfo(
        "v1", 1, 2, "t", "T", 1, "en", ExamMode.SUBJECT, 3_600_000, 120_000, 86_400_000, 2, 10_000,
        ExamScoringRule("stage1", 2, 37, 100.0), emptyList(), 30, true, true, 3_400_000, 0, null,
    )

    @Test
    fun infoActions() {
        fun action(running: LocalExamAttempt? = null, download: ExamDownloadState = ExamDownloadState.Idle, version: Int? = null, online: Boolean = true) =
            infoAction(info, running, download, version, online, notificationsOff = true, versionMismatch = false, nowMs = 0, sizeMb = "3,4")
        assertEquals(InfoAction.Download("3,4", offline = false, updated = false), action())
        assertEquals(InfoAction.Download("3,4", offline = false, updated = true), action(version = 1))
        assertEquals(InfoAction.Start(offline = true, notificationsOff = true, retake = false, lastResultAttemptId = null), action(version = 2, online = false))
        assertTrue(action(download = ExamDownloadState.Running(1, 2)) is InfoAction.Downloading)
        assertEquals(InfoAction.Resume(60, packageMissing = true, sizeMb = "3,4"), action(running = local))
        assertEquals(InfoAction.Resume(60, packageMissing = false, sizeMb = "3,4"), action(running = local, version = 2))
    }
}

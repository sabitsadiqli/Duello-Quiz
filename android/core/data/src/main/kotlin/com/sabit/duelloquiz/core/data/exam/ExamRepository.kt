package com.sabit.duelloquiz.core.data.exam

import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.exam.ExamAttemptSummary
import com.sabit.duelloquiz.core.model.exam.ExamCatalog
import com.sabit.duelloquiz.core.model.exam.ExamMark
import com.sabit.duelloquiz.core.model.exam.ExamMedia
import com.sabit.duelloquiz.core.model.exam.ExamPackage
import com.sabit.duelloquiz.core.model.exam.ExamResult
import com.sabit.duelloquiz.core.model.exam.ExamReview
import com.sabit.duelloquiz.core.model.exam.ExamStartOutcome
import com.sabit.duelloquiz.core.model.exam.ExamSubmitTrigger
import com.sabit.duelloquiz.core.model.exam.ExamVariantInfo
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Mock exams "Sınaqlar" (ADR 0002, docs/requirements/mock-exams.md). Single source of truth for
 * the catalogue cache, packages on disk, the local attempt (Room) with its offline answer queue,
 * submit, self-assessed marks and the result / review caches. Every score and outcome comes from
 * the server; nothing here derives correctness (EXAM-21). Data of a signed-out account is deleted
 * by [clearAccountData] (EXAM-23); packages stay.
 */
interface ExamRepository {

    /** Last catalogue of the signed-in account (null = never loaded). */
    val catalog: Flow<ExamCatalog?>

    suspend fun refreshCatalog(): DataResult<ExamCatalog>

    /** The running (or submit-pending) attempt on this device: Oyna banner and row (HOME-10). */
    val activeAttempt: Flow<LocalExamAttempt?>

    /** History "Sınaqlarım", newest first (cached). */
    val history: Flow<List<ExamAttemptSummary>?>

    suspend fun refreshHistory(limit: Int = HISTORY_PAGE): DataResult<List<ExamAttemptSummary>>

    fun cachedVariant(variantId: String): Flow<ExamVariantInfo?>

    suspend fun refreshVariant(variantId: String): DataResult<ExamVariantInfo>

    // ------------------------------------------------------------------ package (EXAM-03)

    /** Live download state merged with what is on disk. */
    fun downloadState(variantId: String): Flow<ExamDownloadState>

    /** The verified local version of [variantId], or null. */
    suspend fun downloadedVersion(variantId: String): Int?

    /** Starts (or keeps) the background download; [version] null = current. */
    fun startDownload(variantId: String, version: Int?, expectedBytes: Long)

    /** Stops the download; verified files are kept (EXAM-03). */
    fun cancelDownload(variantId: String)

    suspend fun localPackage(variantId: String, version: Int?): ExamPackage?

    /** The verified local file of [media], or null. */
    suspend fun localMedia(media: ExamMedia): File?

    /** EXAM-10 / EXAM-26: (re-)downloads one media file with the hash check; null on failure. */
    suspend fun fetchMedia(media: ExamMedia, forceRedownload: Boolean): File?

    /** Unpublished variant: the local package goes (EXAM-03). */
    suspend fun deleteLocalPackage(variantId: String)

    // ------------------------------------------------------------------ attempt

    /**
     * `POST /v1/exams/attempts` (EXAM-04); on success the attempt is stored locally (Room) with the
     * server answers. One [idempotencyKey] per tap.
     */
    suspend fun start(variant: ExamVariantInfo, version: Int, skipListening: Boolean, idempotencyKey: String): DataResult<ExamStartOutcome>

    /** Stores a running attempt that came from the server (`…/current`, other device). */
    suspend fun adoptServerAttempt(attemptId: String, variantNumber: Int, templateTitle: String): DataResult<LocalExamAttempt>

    fun attemptSnapshot(attemptId: String): Flow<ExamAttemptSnapshot?>

    suspend fun localAttempt(attemptId: String): LocalExamAttempt?

    /**
     * `GET …/attempts/{id}` merged into Room by per-task revision (EXAM-13 other device). Returns
     * true when answers of another device changed local ones.
     */
    suspend fun refreshAttempt(attemptId: String): DataResult<Boolean>

    /** EXAM-06: new revision; null clears the answer. Ignored once the attempt is read-only. */
    suspend fun selectOption(attemptId: String, taskId: String, optionId: String?)

    /** EXAM-11: written text as typed (UTF-8, no normalisation); blank = empty. */
    suspend fun setText(attemptId: String, taskId: String, text: String)

    /** EXAM-12: flag toggle, same revision and sync as answers. */
    suspend fun setFlag(attemptId: String, taskId: String, flagged: Boolean)

    /** EXAM-10 / OQ-D43: plays, position (saved every 1 s) and audio failure. */
    suspend fun saveListening(attemptId: String, state: LocalListening)

    suspend fun setCurrentTask(attemptId: String, taskId: String)

    suspend fun markGroupVisited(attemptId: String, groupId: String)

    suspend fun passageOffsets(attemptId: String): Map<String, Int>

    suspend fun savePassageOffset(attemptId: String, groupId: String, charOffset: Int)

    /** Uploads the queue now (answers in batches of 100, then listening state). */
    suspend fun sync(attemptId: String): ExamSyncOutcome

    /** EXAM-14: read-only at once, flush the queue, submit; offline → queued for 24 h. */
    suspend fun submit(attemptId: String, trigger: ExamSubmitTrigger): ExamSubmitOutcome

    /** EXAM-27: the reminder was posted for this attempt (at most once). */
    suspend fun markReminderPosted(attemptId: String)

    // ------------------------------------------------------------------ result, review, marks

    fun cachedResult(attemptId: String): Flow<ExamResult?>

    suspend fun refreshResult(attemptId: String): DataResult<ExamResult>

    fun cachedReview(attemptId: String): Flow<ExamReview?>

    suspend fun refreshReview(attemptId: String): DataResult<ExamReview>

    /** Local marks (pending or confirmed) that override the review's `selfMark`. */
    fun localMarks(attemptId: String): Flow<Map<String, LocalExamMark>>

    /** EXAM-16: saved at once (Room), then sent; returns the recomputed result when online. */
    suspend fun setMark(attemptId: String, taskId: String, mark: ExamMark?): ExamMarkOutcome

    /** The background worker: every queued answer, listening state, submit and mark. True = done. */
    suspend fun syncAllPending(): Boolean

    /** EXAM-23: sign-out / account switch / deletion. */
    suspend fun clearAccountData()

    companion object {
        const val HISTORY_PAGE = 20
    }
}

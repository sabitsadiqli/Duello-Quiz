package com.sabit.duelloquiz.core.network

import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.exam.ExamAnswerBatchResult
import com.sabit.duelloquiz.core.model.exam.ExamAnswerWrite
import com.sabit.duelloquiz.core.model.exam.ExamAttempt
import com.sabit.duelloquiz.core.model.exam.ExamAttemptSummary
import com.sabit.duelloquiz.core.model.exam.ExamCatalog
import com.sabit.duelloquiz.core.model.exam.ExamListeningState
import com.sabit.duelloquiz.core.model.exam.ExamMark
import com.sabit.duelloquiz.core.model.exam.ExamPackage
import com.sabit.duelloquiz.core.model.exam.ExamResult
import com.sabit.duelloquiz.core.model.exam.ExamReview
import com.sabit.duelloquiz.core.model.exam.ExamStartOutcome
import com.sabit.duelloquiz.core.model.exam.ExamSubmitTrigger
import com.sabit.duelloquiz.core.model.exam.ExamVariantInfo
import java.io.File

/**
 * The `exams` tag of `docs/api/openapi.yaml` (API 0.5.0, ADR 0002). Same auth, language, timeout
 * and error rules as [DuelloNetworkDataSource]; DTOs never leave this module. Responses with
 * `serverNowEpochMs` carry the measured clock offset.
 */
interface ExamNetworkDataSource {

    /** `GET /v1/exams` (EXAM-01). */
    suspend fun getCatalog(): DataResult<ExamCatalog>

    /** `GET /v1/exams/variants/{id}` (EXAM-02). */
    suspend fun getVariant(variantId: String): DataResult<ExamVariantInfo>

    /** `GET /v1/exams/variants/{id}/package?version=` (EXAM-03); never contains keys. */
    suspend fun getPackage(variantId: String, version: Int?): DataResult<ExamPackage>

    /**
     * `POST /v1/exams/attempts` (EXAM-04). 201/200 → [ExamStartOutcome.Started]; 409
     * `exam_attempt_running` → [ExamStartOutcome.OtherRunning] with `GET …/current`. Other
     * 409s (`variant_unavailable`, `variant_version_mismatch`), 404 and 429 are failures.
     */
    suspend fun startAttempt(
        variantId: String,
        version: Int,
        deviceId: String,
        idempotencyKey: String,
        skipListening: Boolean,
    ): DataResult<ExamStartOutcome>

    /** `GET /v1/exams/attempts?limit=` newest first (EXAM-20). */
    suspend fun listAttempts(limit: Int): DataResult<List<ExamAttemptSummary>>

    /** `GET /v1/exams/attempts/current`: running or awaiting upload, else null. */
    suspend fun getCurrentAttempt(): DataResult<ExamAttempt?>

    /** `GET /v1/exams/attempts/{id}` (resume, other device). */
    suspend fun getAttempt(attemptId: String): DataResult<ExamAttempt>

    /** `PUT /v1/exams/attempts/{id}/answers`, up to 100 entries (EXAM-13). */
    suspend fun putAnswers(attemptId: String, deviceId: String, answers: List<ExamAnswerWrite>): DataResult<ExamAnswerBatchResult>

    /** `PUT /v1/exams/attempts/{id}/listening/{groupId}` (EXAM-10, OQ-D43). */
    suspend fun putListening(
        attemptId: String,
        groupId: String,
        deviceId: String,
        playsUsed: Int,
        audioFailed: Boolean,
        positionMs: Long,
        savedAtEpochMs: Long,
    ): DataResult<ExamListeningState>

    /** `POST /v1/exams/attempts/{id}/submit` (idempotent, EXAM-14). */
    suspend fun submit(attemptId: String, deviceId: String, trigger: ExamSubmitTrigger): DataResult<ExamResult>

    /** `GET /v1/exams/attempts/{id}/result` (EXAM-17). */
    suspend fun getResult(attemptId: String): DataResult<ExamResult>

    /** `PUT /v1/exams/attempts/{id}/self-assessment`; null mark clears it (EXAM-16). */
    suspend fun putSelfAssessment(attemptId: String, marks: List<Pair<String, ExamMark?>>): DataResult<ExamResult>

    /** `GET /v1/exams/attempts/{id}/review` (EXAM-18). */
    suspend fun getReview(attemptId: String): DataResult<ExamReview>

    /**
     * Downloads a media file of the package manifest to [destination], resuming an existing
     * partial file with a `Range` request. [onProgress] gets the bytes on disk so far. The
     * caller verifies size and SHA-256.
     */
    suspend fun downloadMedia(url: String, destination: File, onProgress: (Long) -> Unit): DataResult<Unit>
}

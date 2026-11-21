package com.sabit.duelloquiz.core.network.ktor

import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DataError
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
import com.sabit.duelloquiz.core.network.ExamNetworkDataSource
import com.sabit.duelloquiz.core.network.model.NetworkCurrentExamAttempt
import com.sabit.duelloquiz.core.network.model.NetworkExamAnswerBatch
import com.sabit.duelloquiz.core.network.model.NetworkExamAnswerBatchResult
import com.sabit.duelloquiz.core.network.model.NetworkExamAttempt
import com.sabit.duelloquiz.core.network.model.NetworkExamAttemptList
import com.sabit.duelloquiz.core.network.model.NetworkExamCatalog
import com.sabit.duelloquiz.core.network.model.NetworkExamListeningState
import com.sabit.duelloquiz.core.network.model.NetworkExamListeningUpdate
import com.sabit.duelloquiz.core.network.model.NetworkExamPackage
import com.sabit.duelloquiz.core.network.model.NetworkExamResult
import com.sabit.duelloquiz.core.network.model.NetworkExamReview
import com.sabit.duelloquiz.core.network.model.NetworkExamVariantInfo
import com.sabit.duelloquiz.core.network.model.NetworkSelfAssessmentMark
import com.sabit.duelloquiz.core.network.model.NetworkSelfAssessmentRequest
import com.sabit.duelloquiz.core.network.model.NetworkStartExamAttemptRequest
import com.sabit.duelloquiz.core.network.model.NetworkSubmitExamRequest
import com.sabit.duelloquiz.core.network.model.asExternalModel
import com.sabit.duelloquiz.core.network.model.asNetworkModel
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.FileOutputStream

/** [ExamNetworkDataSource] over Ktor ([createDuelloHttpClient] headers and auth). */
internal class KtorExamNetworkDataSource(private val api: ApiCaller) : ExamNetworkDataSource {

    override suspend fun getCatalog(): DataResult<ExamCatalog> = api.timed { measure ->
        api.call({ get("v1/exams") }) { response ->
            val catalog = response.body<NetworkExamCatalog>()
            catalog.asExternalModel(clockOffsetMs = measure(catalog.serverNowEpochMs))
        }
    }

    override suspend fun getVariant(variantId: String): DataResult<ExamVariantInfo> =
        api.call({ get("v1/exams/variants/${variantId.encodeURLPathPart()}") }) {
            it.body<NetworkExamVariantInfo>().asExternalModel()
        }

    override suspend fun getPackage(variantId: String, version: Int?): DataResult<ExamPackage> = api.call({
        get("v1/exams/variants/${variantId.encodeURLPathPart()}/package") {
            if (version != null) parameter("version", version)
            // A package of 30 tasks with passages is small, but a slow 2G link should not fail at 15 s.
            timeout { requestTimeoutMillis = PACKAGE_TIMEOUT_MS }
        }
    }) { it.body<NetworkExamPackage>().asExternalModel() }

    override suspend fun startAttempt(
        variantId: String,
        version: Int,
        deviceId: String,
        idempotencyKey: String,
        skipListening: Boolean,
    ): DataResult<ExamStartOutcome> = api.timed { measure ->
        val started: DataResult<ExamStartOutcome> = api.call({
            post("v1/exams/attempts") {
                contentType(ContentType.Application.Json)
                setBody(
                    NetworkStartExamAttemptRequest(
                        variantId = variantId,
                        version = version,
                        deviceId = deviceId,
                        idempotencyKey = idempotencyKey,
                        skipListening = skipListening,
                    ),
                )
            }
        }) { response ->
            val attempt = response.body<NetworkExamAttempt>()
            ExamStartOutcome.Started(attempt.asExternalModel(clockOffsetMs = measure(attempt.serverNowEpochMs)))
        }
        val error = (started as? DataResult.Failure)?.error
        if (error is DataError.Api && error.code == ApiErrorCode.ExamAttemptRunning) {
            val running = (getCurrentAttempt() as? DataResult.Success)?.value
            DataResult.Success(ExamStartOutcome.OtherRunning(running))
        } else {
            started
        }
    }

    override suspend fun listAttempts(limit: Int): DataResult<List<ExamAttemptSummary>> =
        api.call({ get("v1/exams/attempts") { parameter("limit", limit) } }) { response ->
            response.body<NetworkExamAttemptList>().attempts.map { it.asExternalModel() }
        }

    override suspend fun getCurrentAttempt(): DataResult<ExamAttempt?> = api.timed { measure ->
        api.call({ get("v1/exams/attempts/current") }) { response ->
            response.body<NetworkCurrentExamAttempt>().attempt?.let {
                it.asExternalModel(clockOffsetMs = measure(it.serverNowEpochMs))
            }
        }
    }

    override suspend fun getAttempt(attemptId: String): DataResult<ExamAttempt> = api.timed { measure ->
        api.call({ get("v1/exams/attempts/${attemptId.encodeURLPathPart()}") }) { response ->
            val attempt = response.body<NetworkExamAttempt>()
            attempt.asExternalModel(clockOffsetMs = measure(attempt.serverNowEpochMs))
        }
    }

    override suspend fun putAnswers(
        attemptId: String,
        deviceId: String,
        answers: List<ExamAnswerWrite>,
    ): DataResult<ExamAnswerBatchResult> = api.timed { measure ->
        api.call({
            put("v1/exams/attempts/${attemptId.encodeURLPathPart()}/answers") {
                contentType(ContentType.Application.Json)
                setBody(NetworkExamAnswerBatch(deviceId = deviceId, answers = answers.map { it.asNetworkModel() }))
            }
        }) { response ->
            val result = response.body<NetworkExamAnswerBatchResult>()
            result.asExternalModel(clockOffsetMs = measure(result.serverNowEpochMs))
        }
    }

    override suspend fun putListening(
        attemptId: String,
        groupId: String,
        deviceId: String,
        playsUsed: Int,
        audioFailed: Boolean,
        positionMs: Long,
        savedAtEpochMs: Long,
    ): DataResult<ExamListeningState> = api.call({
        put("v1/exams/attempts/${attemptId.encodeURLPathPart()}/listening/${groupId.encodeURLPathPart()}") {
            contentType(ContentType.Application.Json)
            setBody(
                NetworkExamListeningUpdate(
                    deviceId = deviceId,
                    playsUsed = playsUsed,
                    audioFailed = audioFailed,
                    savedAtEpochMs = savedAtEpochMs,
                    positionMs = positionMs,
                ),
            )
        }
    }) { it.body<NetworkExamListeningState>().asExternalModel() }

    override suspend fun submit(attemptId: String, deviceId: String, trigger: ExamSubmitTrigger): DataResult<ExamResult> =
        api.call({
            post("v1/exams/attempts/${attemptId.encodeURLPathPart()}/submit") {
                contentType(ContentType.Application.Json)
                setBody(NetworkSubmitExamRequest(deviceId = deviceId, trigger = trigger.wire))
            }
        }) { it.body<NetworkExamResult>().asExternalModel() }

    override suspend fun getResult(attemptId: String): DataResult<ExamResult> =
        api.call({ get("v1/exams/attempts/${attemptId.encodeURLPathPart()}/result") }) {
            it.body<NetworkExamResult>().asExternalModel()
        }

    override suspend fun putSelfAssessment(attemptId: String, marks: List<Pair<String, ExamMark?>>): DataResult<ExamResult> =
        api.call({
            put("v1/exams/attempts/${attemptId.encodeURLPathPart()}/self-assessment") {
                contentType(ContentType.Application.Json)
                setBody(NetworkSelfAssessmentRequest(marks.map { (taskId, mark) -> NetworkSelfAssessmentMark(taskId, mark?.wire) }))
            }
        }) { it.body<NetworkExamResult>().asExternalModel() }

    override suspend fun getReview(attemptId: String): DataResult<ExamReview> =
        api.call({ get("v1/exams/attempts/${attemptId.encodeURLPathPart()}/review") }) {
            it.body<NetworkExamReview>().asExternalModel()
        }

    override suspend fun downloadMedia(url: String, destination: File, onProgress: (Long) -> Unit): DataResult<Unit> =
        api.guarded {
            destination.parentFile?.mkdirs()
            val existing = if (destination.exists()) destination.length() else 0L
            api.client.prepareGet(url) {
                if (existing > 0) header(HttpHeaders.Range, "bytes=$existing-")
                timeout {
                    requestTimeoutMillis = MEDIA_TIMEOUT_MS
                    socketTimeoutMillis = REQUEST_TIMEOUT_MS
                }
            }.execute { response ->
                val append = when (response.status) {
                    HttpStatusCode.PartialContent -> true
                    HttpStatusCode.OK -> false
                    // The partial file is already complete (or bigger than the file): verify decides.
                    HttpStatusCode.RequestedRangeNotSatisfiable -> return@execute DataResult.Success(Unit)
                    else -> return@execute DataResult.Failure(api.errorOf(response))
                }
                var written = if (append) existing else 0L
                onProgress(written)
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(BUFFER_BYTES)
                FileOutputStream(destination, append).use { out ->
                    while (true) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read == -1) break
                        if (read > 0) {
                            out.write(buffer, 0, read)
                            written += read
                            onProgress(written)
                        }
                    }
                }
                DataResult.Success(Unit)
            }
        }

    private companion object {
        const val PACKAGE_TIMEOUT_MS = 60_000L
        const val MEDIA_TIMEOUT_MS = 10 * 60_000L
        const val BUFFER_BYTES = 16 * 1024
    }
}

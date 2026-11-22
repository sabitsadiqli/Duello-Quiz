package com.sabit.duelloquiz.core.network.ktor

import com.sabit.duelloquiz.core.common.DeviceConnectivity
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.exam.ExamAnswerWrite
import com.sabit.duelloquiz.core.model.exam.ExamAttemptStatus
import com.sabit.duelloquiz.core.model.exam.ExamGroupKind
import com.sabit.duelloquiz.core.model.exam.ExamMark
import com.sabit.duelloquiz.core.model.exam.ExamOutcome
import com.sabit.duelloquiz.core.model.exam.ExamStartOutcome
import com.sabit.duelloquiz.core.model.exam.ExamSubmitTrigger
import com.sabit.duelloquiz.core.model.exam.ExamTaskType
import com.sabit.duelloquiz.core.model.exam.ListeningExclusion
import com.sabit.duelloquiz.core.network.auth.IdTokenProvider
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Exam payloads of openapi.yaml 0.5.0 (tag `exams`). */
class ExamApiTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val requests = mutableListOf<HttpRequestData>()
    private var now = 1_000_000L

    private fun dataSource(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): KtorExamNetworkDataSource {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        val client = createDuelloHttpClient(engine, "http://test/", IdTokenProvider { "token" }) { "az" }
        return KtorExamNetworkDataSource(ApiCaller(client, DeviceConnectivity { false }) { now })
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "application/json"))

    private fun HttpRequestData.bodyJson() = Json.parseToJsonElement(
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
    ).jsonObject

    private val attemptJson = """
        {"id": "a1", "variantId": "v1", "version": 2, "templateId": "buraxilis-ingilis", "mode": "subject",
         "status": "running", "isRetake": false, "skipListening": true, "resumed": false,
         "startedAtEpochMs": 1000000, "endsAtEpochMs": 4600000, "deadlineEpochMs": 4720000,
         "lateUploadUntilEpochMs": 91120000, "serverNowEpochMs": 1000550, "submittedAtEpochMs": null,
         "answers": [{"taskId": "t1", "revision": 3, "optionId": "o2", "text": null, "flagged": true, "savedAtEpochMs": 1000400}],
         "listening": [{"groupId": "g1", "playsUsed": 1, "playsLeft": 1, "audioFailed": false, "positionMs": 72000}]}
    """.trimIndent()

    private val resultJson = """
        {"attemptId": "a1", "variantId": "v1", "version": 2, "templateId": "buraxilis-ingilis", "mode": "subject",
         "status": "submitted", "isRetake": true, "submitTrigger": "deadline", "submittedAtEpochMs": 4600100,
         "estimate": {"total": 58.4, "autoScore": 40.5, "writtenScore": 18.0, "maxScore": 75.7, "rawMax": 28,
                      "listeningExcluded": true, "listeningExclusionReason": "skipped"},
         "closed": {"correct": 15, "wrong": 4, "blank": 1, "total": 20},
         "written": {"assessed": 2, "total": 4, "marks": 1.33, "maxMarks": 4},
         "sections": [{"id": "listening", "title": "Dinləmə", "closedCorrect": 0, "closedWrong": 0, "closedBlank": 3,
                       "closedTotal": 3, "writtenAssessed": 0, "writtenTotal": 3, "writtenMarks": 0, "excluded": true}],
         "answeredCount": 22, "flaggedCount": 3, "progress": null}
    """.trimIndent()

    @Test
    fun catalogMeasuresClockOffsetAndKeepsVariants() = runTest {
        val source = dataSource {
            now += 100
            json(
                """{"serverNowEpochMs": 1000550, "templates": [{"id": "buraxilis-ingilis", "title": "Buraxılış — İngilis dili",
                    "stage": 1, "mode": "subject", "language": "en", "durationMs": 3600000, "taskCount": 30, "accessTier": null,
                    "variants": [{"id": "v1", "number": 1, "version": 2, "taskCount": 30, "packageBytes": 3400000,
                      "lastAttempt": null}]}]}""",
            )
        }

        val catalog = (source.getCatalog() as DataResult.Success).value

        assertEquals(500, catalog.clockOffsetMs) // 1_000_550 − midpoint 1_000_050
        assertEquals(1, catalog.variantCount)
        assertEquals(1, catalog.templates.single().variants.single().number)
        assertEquals(3_400_000L, catalog.templates.single().variants.single().packageBytes)
    }

    @Test
    fun packageHasNoKeysAndDropsUnknownTaskTypes() = runTest {
        val source = dataSource {
            json(
                """{"variantId": "v1", "version": 2, "templateId": "buraxilis-ingilis", "language": "en",
                    "durationMs": 3600000, "graceMs": 120000, "optionCount": 5, "listeningMaxPlays": 2,
                    "listeningPlayCountsAfterMs": 10000, "maxTextLength": 1000,
                    "sections": [{"id": "reading", "title": "Oxu", "position": 3}, {"id": "listening", "title": "Dinləmə", "position": 1}],
                    "groups": [{"id": "g1", "kind": "listening", "sectionId": "listening", "position": 1, "title": null,
                      "instructions": null, "passage": null,
                      "audio": {"url": "https://m/a.m4a", "bytes": 1500000, "sha256": "${"AB".repeat(32)}",
                                "contentType": "audio/mp4", "durationMs": 185000}},
                      {"id": "g9", "kind": "video", "sectionId": "x", "position": 9, "title": null, "instructions": null,
                       "passage": null, "audio": null}],
                    "tasks": [{"id": "t2", "position": 2, "sectionId": "listening", "groupId": "g1", "type": "written",
                               "answerFormat": "short_text", "prompt": "Why?", "options": []},
                              {"id": "t1", "position": 1, "sectionId": "listening", "groupId": "g1", "type": "closed_single",
                               "answerFormat": null, "prompt": "What?",
                               "options": [{"id": "o1", "letter": "A", "text": "x"}, {"id": "o2", "letter": "B", "text": "y"}]},
                              {"id": "t3", "position": 3, "sectionId": "x", "groupId": null, "type": "coded_numeric",
                               "answerFormat": null, "prompt": "?", "options": []}],
                    "media": [{"url": "https://m/a.m4a", "bytes": 1500000, "sha256": "${"ab".repeat(32)}",
                               "contentType": "audio/mp4", "durationMs": 185000}],
                    "packageBytes": 1530000}""",
            )
        }

        val pkg = (source.getPackage("v1", version = 2) as DataResult.Success).value

        assertEquals("version=2", requests.single().url.encodedQuery)
        assertEquals(listOf("listening", "reading"), pkg.sections.map { it.id })
        assertEquals(listOf(ExamGroupKind.LISTENING), pkg.groups.map { it.kind })
        assertEquals(listOf("t1", "t2"), pkg.tasks.map { it.id })
        assertEquals(ExamTaskType.CLOSED_SINGLE, pkg.tasks.first().type)
        assertEquals("ab".repeat(32), pkg.groups.single().audio?.sha256)
    }

    @Test
    fun startSendsDeviceSkipAndIdempotencyAndReadsTimes() = runTest {
        val source = dataSource {
            now += 100
            json(attemptJson, HttpStatusCode.Created)
        }

        val outcome = source.startAttempt("v1", 2, "device-123", "key-12345", skipListening = true)

        val attempt = ((outcome as DataResult.Success).value as ExamStartOutcome.Started).attempt
        assertEquals(4_600_000L, attempt.endsAtEpochMs)
        assertEquals(4_720_000L, attempt.deadlineEpochMs)
        assertEquals(500L, attempt.clockOffsetMs)
        assertTrue(attempt.skipListening)
        assertEquals(3, attempt.answers.single().revision)
        assertEquals(72_000L, attempt.listening.single().positionMs)
        val body = requests.single().bodyJson()
        assertEquals("device-123", body["deviceId"]?.jsonPrimitive?.content)
        assertEquals("key-12345", body["idempotencyKey"]?.jsonPrimitive?.content)
        assertEquals("true", body["skipListening"]?.jsonPrimitive?.content)
        assertEquals("subject", body["mode"]?.jsonPrimitive?.content)
    }

    @Test
    fun startWhileAnotherRunsReturnsTheRunningAttempt() = runTest {
        val source = dataSource { request ->
            if (request.method == HttpMethod.Post) {
                json("""{"code": "exam_attempt_running", "message": "x"}""", HttpStatusCode.Conflict)
            } else {
                json("""{"attempt": $attemptJson}""")
            }
        }

        val outcome = (source.startAttempt("v2", 1, "device-123", "key-12345", false) as DataResult.Success).value

        assertEquals("a1", (outcome as ExamStartOutcome.OtherRunning).running?.id)
        assertEquals("/v1/exams/attempts/current", requests.last().url.encodedPath)
    }

    @Test
    fun startVersionMismatchIsAnApiError() = runTest {
        val source = dataSource { json("""{"code": "variant_version_mismatch", "message": "x"}""", HttpStatusCode.Conflict) }

        val result = source.startAttempt("v1", 1, "device-123", "key-12345", false)

        assertEquals(DataResult.Failure(DataError.Api(ApiErrorCode.VariantVersionMismatch)), result)
    }

    @Test
    fun startRateLimitedKeepsRetryAfter() = runTest {
        val source = dataSource {
            respond(
                content = """{"code": "rate_limited", "message": "x"}""",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.RetryAfter to listOf("3600")),
            )
        }

        assertEquals(DataResult.Failure(DataError.RateLimited(3600)), source.startAttempt("v1", 1, "device-123", "key-12345", false))
    }

    @Test
    fun answersBatchSendsExplicitNullsAndReadsIgnored() = runTest {
        val source = dataSource {
            json(
                """{"status": "running", "serverNowEpochMs": 2000000,
                    "accepted": [{"taskId": "t1", "revision": 4}],
                    "ignored": [{"taskId": "t2", "revision": 1, "reason": "stale_revision"}]}""",
            )
        }

        val result = source.putAnswers(
            "a1",
            "device-123",
            listOf(ExamAnswerWrite("t1", 4, optionId = null, text = null, flagged = false, savedAtEpochMs = 1_999_000)),
        )

        val batch = (result as DataResult.Success).value
        assertEquals(ExamAttemptStatus.RUNNING, batch.status)
        assertEquals(listOf("t1" to 4), batch.accepted)
        assertEquals(listOf("t2" to 1), batch.ignored)
        val entry = requests.single().bodyJson()["answers"]!!.jsonArray.single().jsonObject
        assertEquals(JsonNull, entry["optionId"])
        assertEquals(JsonNull, entry["text"])
        assertEquals(HttpMethod.Put, requests.single().method)
    }

    @Test
    fun closedAttemptIsAttemptClosed() = runTest {
        val source = dataSource { json("""{"code": "attempt_closed", "message": "x"}""", HttpStatusCode.Conflict) }

        val result = source.putAnswers("a1", "d", listOf(ExamAnswerWrite("t1", 1, "o1", null, false, 1)))

        assertEquals(DataResult.Failure(DataError.Api(ApiErrorCode.AttemptClosed)), result)
    }

    @Test
    fun listeningUpdateSendsPositionAndPlays() = runTest {
        val source = dataSource {
            json("""{"groupId": "g1", "playsUsed": 2, "playsLeft": 0, "audioFailed": false, "positionMs": 3000}""")
        }

        val state = (source.putListening("a1", "g1", "device-123", 2, false, 3_000, 5_000) as DataResult.Success).value

        assertEquals(2, state.playsUsed)
        val body = requests.single().bodyJson()
        assertEquals("3000", body["positionMs"]?.jsonPrimitive?.content)
        assertEquals("2", body["playsUsed"]?.jsonPrimitive?.content)
        assertEquals("/v1/exams/attempts/a1/listening/g1", requests.single().url.encodedPath)
    }

    @Test
    fun submitResultWithReducedMaximum() = runTest {
        val source = dataSource { json(resultJson) }

        val result = (source.submit("a1", "device-123", ExamSubmitTrigger.DEADLINE) as DataResult.Success).value

        assertEquals("deadline", requests.single().bodyJson()["trigger"]?.jsonPrimitive?.content)
        assertEquals(58.4, result.estimate.total, 0.0)
        assertEquals(75.7, result.estimate.maxScore, 0.0)
        assertEquals(ListeningExclusion.SKIPPED, result.estimate.exclusion)
        assertEquals(ExamSubmitTrigger.DEADLINE, result.submitTrigger)
        assertEquals(2, result.written.unassessed)
        assertTrue(result.sections.single().excluded)
        assertNull(result.progress)
    }

    @Test
    fun selfAssessmentSendsMarkWireValues() = runTest {
        val source = dataSource { json(resultJson) }

        source.putSelfAssessment("a1", listOf("t5" to ExamMark.TWO_THIRDS, "t6" to null))

        val marks = requests.single().bodyJson()["marks"]!!.jsonArray
        assertEquals("2/3", marks[0].jsonObject["mark"]?.jsonPrimitive?.content)
        assertEquals(JsonNull, marks[1].jsonObject["mark"])
    }

    @Test
    fun resultBeforeSubmitIsAttemptNotFinished() = runTest {
        val source = dataSource { json("""{"code": "attempt_not_finished", "message": "x"}""", HttpStatusCode.Conflict) }

        assertEquals(DataResult.Failure(DataError.Api(ApiErrorCode.AttemptNotFinished)), source.getResult("a1"))
    }

    @Test
    fun reviewReadsKeysCriteriaAndTranscript() = runTest {
        val source = dataSource {
            json(
                """{"attemptId": "a1", "variantId": "v1", "version": 2, "language": "en",
                    "sections": [{"id": "listening", "title": "Dinləmə", "position": 1}],
                    "groups": [{"id": "g1", "kind": "listening", "sectionId": "listening", "position": 1, "title": null,
                      "instructions": null, "passage": null, "audio": null,
                      "transcript": [{"speaker": "A", "speakerName": "Leyla", "text": "Hi"}],
                      "voices": [{"role": "A", "voice": "en_GB-alba-medium", "speakerId": null, "licence": "CC-BY-4.0"}]}],
                    "tasks": [{"id": "t1", "position": 1, "sectionId": "listening", "groupId": "g1", "type": "written",
                      "prompt": "Why?", "options": [], "selectedOptionId": null, "correctOptionId": null,
                      "outcome": "not_assessed", "text": "Because", "flagged": true, "explanation": null,
                      "modelAnswer": "Since", "criteria": [{"mark": "1", "description": "Full"}, {"mark": "0.7", "description": "?"}],
                      "selfMark": null}]}""",
            )
        }

        val review = (source.getReview("a1") as DataResult.Success).value

        val task = review.tasks.single()
        assertEquals(ExamOutcome.NOT_ASSESSED, task.outcome)
        assertEquals(listOf(ExamMark.ONE), task.criteria.map { it.mark })
        assertEquals("Leyla", review.groups.single().transcript.single().speakerName)
        assertFalse(task.selfMark != null)
    }

    @Test
    fun mediaDownloadResumesWithRange() = runTest {
        val file = tmp.newFile("a.m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val progress = mutableListOf<Long>()
        val source = dataSource { request ->
            assertEquals("bytes=3-", request.headers[HttpHeaders.Range])
            respond(content = byteArrayOf(4, 5), status = HttpStatusCode.PartialContent)
        }

        val result = source.downloadMedia("https://media.test/a.m4a", file) { progress += it }

        assertEquals(DataResult.Success(Unit), result)
        assertEquals(listOf<Byte>(1, 2, 3, 4, 5), file.readBytes().toList())
        assertEquals(5L, progress.last())
    }

    @Test
    fun mediaDownloadRestartsWhenTheServerIgnoresRange() = runTest {
        val file = tmp.newFile("b.m4a").apply { writeBytes(byteArrayOf(9, 9)) }
        val source = dataSource { respond(content = byteArrayOf(1, 2, 3), status = HttpStatusCode.OK) }

        source.downloadMedia("https://media.test/b.m4a", file) {}

        assertEquals(listOf<Byte>(1, 2, 3), file.readBytes().toList())
    }
}

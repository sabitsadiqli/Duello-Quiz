package com.sabit.duelloquiz.backend.exam

import com.sabit.duelloquiz.backend.common.ErrorResponse
import com.sabit.duelloquiz.backend.common.db.ExamAnswersTable
import com.sabit.duelloquiz.backend.common.db.ExamAttemptsTable
import com.sabit.duelloquiz.backend.common.db.ExamSelfAssessmentsTable
import com.sabit.duelloquiz.backend.common.db.UserActiveDaysTable
import com.sabit.duelloquiz.backend.testsupport.DbTest
import com.sabit.duelloquiz.backend.testsupport.MutableClock
import com.sabit.duelloquiz.backend.testsupport.TestEnv
import com.sabit.duelloquiz.backend.testsupport.TestExams
import com.sabit.duelloquiz.backend.testsupport.TestExams.mapTasks
import com.sabit.duelloquiz.backend.testsupport.TestExams.with
import com.sabit.duelloquiz.backend.testsupport.auth
import com.sabit.duelloquiz.backend.testsupport.testConfig
import com.sabit.duelloquiz.backend.testsupport.withTestApp
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExamRoutesTest : DbTest() {
    private val deviceA = "device-aaaa-1111"
    private val deviceB = "device-bbbb-2222"
    private var keys = 0

    private fun seed(env: TestEnv, vararg variants: kotlinx.serialization.json.JsonObject) = runBlocking {
        env.examImporter.import(TestExams.input(*(if (variants.isEmpty()) arrayOf(TestExams.published()) else variants)))
    }

    private fun second() = TestExams.published(TestExams.sample().with("id", "sample-ingilis-002").with("number", JsonPrimitive(2)))

    private suspend inline fun <reified T> HttpClient.putJson(path: String, token: String, body: T): HttpResponse =
        put(path) { auth(token); contentType(ContentType.Application.Json); setBody(body) }

    private suspend inline fun <reified T> HttpClient.postJson(path: String, token: String, body: T): HttpResponse =
        post(path) { auth(token); contentType(ContentType.Application.Json); setBody(body) }

    private suspend fun HttpClient.catalog(token: String) = get("/v1/exams") { auth(token) }.body<ExamCatalogResponse>()

    private suspend fun HttpClient.pkg(token: String, variantId: String, version: Int? = null) =
        get("/v1/exams/variants/$variantId/package" + (version?.let { "?version=$it" } ?: "")) { auth(token) }

    private suspend fun HttpClient.start(token: String, variantId: String, version: Int = 1, key: String = "key-${++keys}-xxxxxx", skip: Boolean = false, device: String = deviceA) =
        postJson("/v1/exams/attempts", token, StartExamAttemptRequest(variantId, version, device, key, skipListening = skip))

    private suspend fun HttpClient.answers(token: String, id: String, vararg writes: ExamAnswerWriteDto, device: String = deviceA) =
        putJson("/v1/exams/attempts/$id/answers", token, ExamAnswerBatchRequest(device, writes.toList()))

    private suspend fun HttpClient.submit(token: String, id: String, device: String = deviceA, trigger: String = "user") =
        postJson("/v1/exams/attempts/$id/submit", token, SubmitExamRequest(device, trigger))

    private suspend fun HttpResponse.code() = body<ErrorResponse>().code

    private fun ExamPackageResponse.task(position: Int) = tasks.single { it.position == position }

    private fun ExamPackageResponse.option(position: Int, text: String) = task(position).options.single { it.text == text }.id

    private fun ExamPackageResponse.wrong(position: Int, correctText: String) = task(position).options.first { it.text != correctText }.id

    // Sample keys (content/samples/exams/.../variant.json).
    private val l1 = "Put them in the return box on Saturday."
    private val g1 = "rains"
    private val r1 = "A group of tenth-grade students'."

    @Test
    fun `catalogue, package without keys, start, answer, submit, self-assess and review`() {
        val env = TestEnv(db)
        seed(env)
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            val catalog = client.catalog(token)
            val template = catalog.templates.single()
            assertEquals("sample-ingilis", template.id)
            assertNull(template.accessTier)
            val variant = template.variants.single()
            assertNull(variant.lastAttempt)
            assertTrue(variant.packageBytes > 0)

            val info = client.get("/v1/exams/variants/${variant.id}") { auth(token) }.body<ExamVariantInfoResponse>()
            assertEquals(5, info.scoring.rawMax)
            assertEquals(3_600_000, info.durationMs)
            assertTrue(info.hasListening && info.hasWritten)

            val raw = client.pkg(token, variant.id).bodyAsText()
            listOf(
                "\"correct\"", "isCorrect", "correctOption", "explanation", "İzah", "Leyla deyir", "modelAnswer",
                "Because the students", "criteria", "\"script\"", "speakers", "transcript", "return box next to the front door",
                "en_GB-alba",
            ).forEach { assertFalse(it in raw, "package leaks '$it'") }
            val pkg = client.pkg(token, variant.id).body<ExamPackageResponse>()
            assertEquals(4, pkg.tasks.size)
            assertEquals(listOf("A", "B", "C", "D", "E"), pkg.task(1).options.map { it.letter })
            val audio = pkg.groups.single { it.kind == "listening" }.audio!!
            assertTrue(audio.url.startsWith("https://media.example.test/media/audio/") && audio.url.endsWith(".m4a"))
            assertEquals(listOf(audio), pkg.media)

            val created = client.start(token, variant.id, key = "start-key-0001")
            assertEquals(HttpStatusCode.Created, created.status)
            val attempt = created.body<ExamAttemptResponse>()
            assertEquals(env.clock.millis() + 3_600_000, attempt.endsAtEpochMs)
            assertEquals(attempt.endsAtEpochMs + 120_000, attempt.deadlineEpochMs)
            assertEquals(attempt.deadlineEpochMs + 86_400_000, attempt.lateUploadUntilEpochMs)
            assertEquals("running", attempt.status)
            assertFalse(attempt.isRetake)
            // Double tap and resume give the same attempt.
            val again = client.start(token, variant.id, key = "start-key-0001")
            assertEquals(HttpStatusCode.OK, again.status)
            assertEquals(attempt.id, again.body<ExamAttemptResponse>().id)
            assertEquals(attempt.id, client.start(token, variant.id).body<ExamAttemptResponse>().id)

            val now = env.clock.millis()
            val batch = client.answers(
                token, attempt.id,
                ExamAnswerWriteDto(pkg.task(1).id, 1, pkg.option(1, l1), savedAtEpochMs = now),
                ExamAnswerWriteDto(pkg.task(2).id, 1, pkg.option(2, g1), flagged = true, savedAtEpochMs = now),
                ExamAnswerWriteDto(pkg.task(3).id, 2, pkg.wrong(3, r1), savedAtEpochMs = now),
                ExamAnswerWriteDto(pkg.task(4).id, 1, text = "They must water it every week.", savedAtEpochMs = now),
            ).body<ExamAnswerBatchResponse>()
            assertEquals(4, batch.accepted.size)
            // Out of order: a lower revision is ignored, the same one is a no-op.
            val stale = client.answers(token, attempt.id, ExamAnswerWriteDto(pkg.task(3).id, 1, pkg.option(3, r1), savedAtEpochMs = now))
                .body<ExamAnswerBatchResponse>()
            assertEquals(listOf("stale_revision"), stale.ignored.map { it.reason })

            val state = client.get("/v1/exams/attempts/${attempt.id}") { auth(token) }
            val stateRaw = state.bodyAsText()
            listOf("\"correct\"", "correctOption", "outcome").forEach { assertFalse(it in stateRaw, "attempt state leaks $it") }
            assertEquals(4, state.body<ExamAttemptResponse>().answers.size)
            assertEquals(HttpStatusCode.Conflict, client.get("/v1/exams/attempts/${attempt.id}/review") { auth(token) }.status)
            assertEquals("attempt_not_finished", client.get("/v1/exams/attempts/${attempt.id}/result") { auth(token) }.code())
            assertEquals(attempt.id, client.get("/v1/exams/attempts/current") { auth(token) }.body<CurrentExamAttemptResponse>().attempt?.id)

            // Another user sees nothing.
            val other = env.verifier.tokenFor("u2")
            assertEquals("exam_attempt_not_found", client.get("/v1/exams/attempts/${attempt.id}") { auth(other) }.code())

            env.clock.advance(Duration.ofMinutes(20))
            val result = client.submit(token, attempt.id).body<ExamResultResponse>()
            assertEquals("submitted", result.status)
            assertEquals("user", result.submitTrigger)
            // C = 2 of raw max 5 (3 closed + 2 · 1 written) → 40.0.
            assertEquals(40.0, result.estimate.total)
            assertEquals(100.0, result.estimate.maxScore)
            assertEquals(ClosedSummaryDto(2, 1, 0, 3), result.closed)
            assertEquals(0, result.written.assessed)
            assertEquals(1, result.flaggedCount)
            assertEquals(0, result.progress!!.xpGained, "OQ-125: no XP")
            assertTrue(result.progress!!.streakExtendedToday, "OQ-125: active day")
            assertEquals(result, client.submit(token, attempt.id).body<ExamResultResponse>(), "idempotent submit")

            val assessed = client.putJson(
                "/v1/exams/attempts/${attempt.id}/self-assessment", token,
                SelfAssessmentRequest(listOf(SelfAssessmentEntryDto(pkg.task(4).id, "1/2"))),
            ).body<ExamResultResponse>()
            assertEquals(60.0, assessed.estimate.total)
            assertEquals(40.0, assessed.estimate.autoScore)
            assertEquals(20.0, assessed.estimate.writtenScore)
            // OQ-D47: marks can be changed later.
            val changed = client.putJson(
                "/v1/exams/attempts/${attempt.id}/self-assessment", token,
                SelfAssessmentRequest(listOf(SelfAssessmentEntryDto(pkg.task(4).id, "1"))),
            ).body<ExamResultResponse>()
            assertEquals(80.0, changed.estimate.total)
            listOf("0.5", "1/4").forEach { mark ->
                val bad = client.putJson("/v1/exams/attempts/${attempt.id}/self-assessment", token, SelfAssessmentRequest(listOf(SelfAssessmentEntryDto(pkg.task(4).id, mark))))
                assertEquals(HttpStatusCode.BadRequest, bad.status, mark)
            }
            val closedMark = client.putJson("/v1/exams/attempts/${attempt.id}/self-assessment", token, SelfAssessmentRequest(listOf(SelfAssessmentEntryDto(pkg.task(1).id, "1"))))
            assertEquals("validation_failed", closedMark.code())

            val review = client.get("/v1/exams/attempts/${attempt.id}/review") { auth(token) }.body<ExamReviewResponse>()
            assertEquals(listOf("correct", "correct", "wrong", "assessed"), review.tasks.map { it.outcome })
            assertEquals(pkg.option(3, r1), review.tasks[2].correctOptionId)
            assertNotNull(review.tasks[3].modelAnswer)
            assertEquals("1", review.tasks[3].selfMark)
            val listening = review.groups.single { it.kind == "listening" }
            assertEquals("Leyla", listening.transcript.first().speakerName)
            assertNotNull(listening.audio, "OQ-D44: audio for free replay in the review")
            assertEquals(setOf("en_GB-alba-medium", "en_US-libritts_r-medium"), listening.voices.map { it.voice }.toSet())

            // Retake.
            val retake = client.start(token, variant.id).body<ExamAttemptResponse>()
            assertTrue(retake.isRetake)
            val history = client.get("/v1/exams/attempts") { auth(token) }.body<ExamAttemptListResponse>()
            assertEquals(listOf(retake.id, attempt.id), history.attempts.map { it.id })
            assertEquals(80.0, history.attempts[1].estimateTotal)
            assertEquals(1, history.attempts[1].writtenAssessed)
        }
    }

    @Test
    fun `answer validation is all or nothing`() {
        val env = TestEnv(db)
        seed(env)
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            val variant = client.catalog(token).templates.single().variants.single()
            val pkg = client.pkg(token, variant.id).body<ExamPackageResponse>()
            val id = client.start(token, variant.id).body<ExamAttemptResponse>().id
            val now = env.clock.millis()
            val good = ExamAnswerWriteDto(pkg.task(2).id, 1, pkg.option(2, g1), savedAtEpochMs = now)
            assertEquals("invalid_option", client.answers(token, id, good, ExamAnswerWriteDto(pkg.task(1).id, 1, pkg.option(2, g1), savedAtEpochMs = now)).code())
            assertEquals("validation_failed", client.answers(token, id, good, ExamAnswerWriteDto(pkg.task(1).id, 1, text = "x", savedAtEpochMs = now)).code())
            assertEquals("validation_failed", client.answers(token, id, good, ExamAnswerWriteDto(pkg.task(4).id, 1, text = "x".repeat(1001), savedAtEpochMs = now)).code())
            assertEquals("validation_failed", client.answers(token, id, good, good).code(), "duplicate task")
            assertEquals("task_not_in_attempt", client.answers(token, id, good, ExamAnswerWriteDto(java.util.UUID.randomUUID().toString(), 1, savedAtEpochMs = now)).code())
            assertEquals(0L, db.tx { ExamAnswersTable.selectAll().count() }, "nothing stored")
            assertEquals("validation_failed", client.answers(token, id, good, device = "short").code())
        }
    }

    @Test
    fun `deadline, grace, late upload from the holding device and server finalization`() {
        val env = TestEnv(db)
        seed(env)
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            val variant = client.catalog(token).templates.single().variants.single()
            val pkg = client.pkg(token, variant.id).body<ExamPackageResponse>()
            val attempt = client.start(token, variant.id).body<ExamAttemptResponse>()
            // Device B writes during the attempt and becomes the holder.
            client.answers(token, attempt.id, ExamAnswerWriteDto(pkg.task(2).id, 1, pkg.option(2, g1), savedAtEpochMs = env.clock.millis()), device = deviceB)
            // Inside the grace period (after endsAt, before deadline) writes are normal.
            env.clock.advance(Duration.ofMinutes(61))
            assertEquals(HttpStatusCode.OK, client.answers(token, attempt.id, ExamAnswerWriteDto(pkg.task(1).id, 1, pkg.option(1, l1), savedAtEpochMs = env.clock.millis()), device = deviceB).status)
            val savedBeforeDeadline = env.clock.millis()
            env.clock.advance(Duration.ofMinutes(5))
            assertEquals("awaiting_upload", client.get("/v1/exams/attempts/${attempt.id}") { auth(token) }.body<ExamAttemptResponse>().status)
            // Late window: other device rejected, holder accepted only for entries saved before the deadline.
            assertEquals("attempt_closed", client.answers(token, attempt.id, ExamAnswerWriteDto(pkg.task(3).id, 1, pkg.option(3, r1), savedAtEpochMs = savedBeforeDeadline)).code())
            val late = client.answers(
                token, attempt.id,
                ExamAnswerWriteDto(pkg.task(3).id, 1, pkg.option(3, r1), savedAtEpochMs = savedBeforeDeadline),
                ExamAnswerWriteDto(pkg.task(4).id, 1, text = "late", savedAtEpochMs = env.clock.millis()),
                device = deviceB,
            ).body<ExamAnswerBatchResponse>()
            assertEquals(listOf(pkg.task(3).id), late.accepted.map { it.taskId })
            assertEquals(listOf("saved_after_deadline"), late.ignored.map { it.reason })
            assertEquals("awaiting_upload", late.status)
            assertEquals("attempt_closed", client.submit(token, attempt.id, device = deviceA).code())
            val result = client.submit(token, attempt.id, device = deviceB).body<ExamResultResponse>()
            assertEquals("deadline", result.submitTrigger)
            assertEquals(60.0, result.estimate.total, "3 closed correct of raw 5")

            // An attempt never submitted is finalized by the server after the window.
            val second = client.start(token, variant.id).body<ExamAttemptResponse>()
            client.answers(token, second.id, ExamAnswerWriteDto(pkg.task(1).id, 1, pkg.option(1, l1), savedAtEpochMs = env.clock.millis()))
            env.clock.advance(Duration.ofMinutes(62).plusHours(24).plusSeconds(1))
            val finalized = client.get("/v1/exams/attempts/${second.id}/result") { auth(token) }.body<ExamResultResponse>()
            assertEquals("finalized", finalized.status)
            assertEquals("server", finalized.submitTrigger)
            assertEquals(20.0, finalized.estimate.total)
            assertNull(finalized.progress, "no active day for an abandoned attempt")
            assertEquals("attempt_closed", client.answers(token, second.id, ExamAnswerWriteDto(pkg.task(2).id, 1, savedAtEpochMs = env.clock.millis())).code())
        }
    }

    @Test
    fun `listening play counter, audio failure and skip listening`() {
        val env = TestEnv(db)
        seed(env)
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            val variant = client.catalog(token).templates.single().variants.single()
            val pkg = client.pkg(token, variant.id).body<ExamPackageResponse>()
            val group = pkg.groups.single { it.kind == "listening" }.id
            val attempt = client.start(token, variant.id).body<ExamAttemptResponse>()
            assertEquals(listOf(ExamListeningStateDto(group, 0, 2, false)), attempt.listening)
            val path = "/v1/exams/attempts/${attempt.id}/listening/$group"
            val now = env.clock.millis()
            assertEquals(1, client.putJson(path, token, ExamListeningUpdateRequest(deviceA, 1, savedAtEpochMs = now)).body<ExamListeningStateDto>().playsLeft)
            assertEquals(1, client.putJson(path, token, ExamListeningUpdateRequest(deviceA, 0, savedAtEpochMs = now)).body<ExamListeningStateDto>().playsUsed, "max is kept")
            assertEquals(HttpStatusCode.BadRequest, client.putJson(path, token, ExamListeningUpdateRequest(deviceA, 3, savedAtEpochMs = now)).status)
            val readingGroup = pkg.groups.single { it.kind == "reading" }.id
            assertEquals("group_not_in_attempt", client.putJson("/v1/exams/attempts/${attempt.id}/listening/$readingGroup", token, ExamListeningUpdateRequest(deviceA, 1, savedAtEpochMs = now)).code())
            client.putJson(path, token, ExamListeningUpdateRequest(deviceA, 1, audioFailed = true, savedAtEpochMs = now))
            client.answers(token, attempt.id, ExamAnswerWriteDto(pkg.task(1).id, 1, pkg.option(1, l1), savedAtEpochMs = now), ExamAnswerWriteDto(pkg.task(2).id, 1, pkg.option(2, g1), savedAtEpochMs = now))
            val result = client.submit(token, attempt.id).body<ExamResultResponse>()
            assertTrue(result.estimate.listeningExcluded)
            assertEquals("audio_failed", result.estimate.listeningExclusionReason)
            // OQ-D42: the listening point is not counted and the maximum drops to 100 · 4/5.
            assertEquals(20.0, result.estimate.total)
            assertEquals(80.0, result.estimate.maxScore)

            val skipped = client.start(token, variant.id, skip = true).body<ExamAttemptResponse>()
            assertTrue(skipped.skipListening)
            val r2 = client.submit(token, skipped.id).body<ExamResultResponse>()
            assertEquals("skipped", r2.estimate.listeningExclusionReason)
            assertNull(r2.progress?.takeIf { it.streakExtendedToday }, "an empty submit is not a new active day")
        }
    }

    @Test
    fun `listening position resumes with the newest play (OQ-D43)`() {
        val env = TestEnv(db)
        seed(env)
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            val variant = client.catalog(token).templates.single().variants.single()
            val pkg = client.pkg(token, variant.id).body<ExamPackageResponse>()
            val listeningGroup = pkg.groups.single { it.kind == "listening" }
            val group = listeningGroup.id
            val duration = listeningGroup.audio!!.durationMs!!
            val attempt = client.start(token, variant.id).body<ExamAttemptResponse>()
            val path = "/v1/exams/attempts/${attempt.id}/listening/$group"
            val t0 = env.clock.millis()
            suspend fun put(plays: Int, position: Int, savedAt: Long) =
                client.putJson(path, token, ExamListeningUpdateRequest(deviceA, plays, savedAtEpochMs = savedAt, positionMs = position))

            assertEquals(12_000, put(1, 12_000, t0 + 1_000).body<ExamListeningStateDto>().positionMs)
            assertEquals(20_000, put(1, 20_000, t0 + 2_000).body<ExamListeningStateDto>().positionMs, "last write wins")
            assertEquals(20_000, put(1, 5_000, t0 + 1_500).body<ExamListeningStateDto>().positionMs, "an older write is ignored")
            // Second play started, not counted yet (same count, later write), then counted.
            assertEquals(3_000, put(1, 3_000, t0 + 3_000).body<ExamListeningStateDto>().positionMs)
            assertEquals(11_000, put(2, 11_000, t0 + 4_000).body<ExamListeningStateDto>().positionMs)
            val stale = put(1, 30_000, t0 + 5_000).body<ExamListeningStateDto>()
            assertEquals(ExamListeningStateDto(group, 2, 0, false, 11_000), stale, "a lower play count never moves the position")

            assertEquals("validation_failed", put(2, -1, t0 + 6_000).code())
            assertEquals("validation_failed", put(2, duration + 1, t0 + 6_000).code())
            assertEquals(duration, put(2, duration, t0 + 6_000).body<ExamListeningStateDto>().positionMs, "the end is allowed")

            // Resume after an app kill.
            val resumed = client.get("/v1/exams/attempts/${attempt.id}") { auth(token) }.body<ExamAttemptResponse>()
            assertEquals(listOf(ExamListeningStateDto(group, 2, 0, false, duration)), resumed.listening)
        }
    }

    @Test
    fun `a submitted attempt counts for the Baku day of the submit (OQ-132)`() {
        // 2026-10-16 23:40 Baku = 19:40 UTC.
        val env = TestEnv(db, clock = MutableClock(Instant.parse("2026-10-16T19:40:00Z")))
        seed(env)
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            val variant = client.catalog(token).templates.single().variants.single()
            val pkg = client.pkg(token, variant.id).body<ExamPackageResponse>()
            val attempt = client.start(token, variant.id).body<ExamAttemptResponse>()
            client.answers(token, attempt.id, ExamAnswerWriteDto(pkg.task(1).id, 1, pkg.option(1, l1), savedAtEpochMs = env.clock.millis()))
            env.clock.advance(Duration.ofMinutes(50)) // 00:30 Baku, 2026-10-17
            val result = client.submit(token, attempt.id).body<ExamResultResponse>()
            assertEquals(LocalDate.parse("2026-10-17").toString(), result.progress!!.dayKey)
            assertTrue(result.progress!!.streakExtendedToday)
            assertEquals(listOf(LocalDate.parse("2026-10-17")), activeDays())
            val summary = client.get("/v1/exams/attempts") { auth(token) }.body<ExamAttemptListResponse>().attempts.single()
            assertEquals("2026-10-16", summary.dayKey, "the history keeps the start day (OQ-124 start limit)")

            // A second attempt the server finalizes (never submitted) marks nothing.
            val second = client.start(token, variant.id).body<ExamAttemptResponse>()
            client.answers(token, second.id, ExamAnswerWriteDto(pkg.task(1).id, 1, pkg.option(1, l1), savedAtEpochMs = env.clock.millis()))
            env.clock.advance(Duration.ofMinutes(62).plusHours(24).plusSeconds(1))
            val finalized = client.get("/v1/exams/attempts/${second.id}/result") { auth(token) }.body<ExamResultResponse>()
            assertEquals("finalized", finalized.status)
            assertNull(finalized.progress)
            assertEquals(listOf(LocalDate.parse("2026-10-17")), activeDays(), "a finalized attempt makes no day active")
        }
    }

    private suspend fun activeDays(): List<LocalDate> =
        db.tx { UserActiveDaysTable.selectAll().map { it[UserActiveDaysTable.day] }.sorted() }

    @Test
    fun `one running attempt, version mismatch, unpublished variant and 10 starts per day`() {
        val env = TestEnv(db)
        seed(env, TestExams.published(), second())
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            val (v1, v2) = client.catalog(token).templates.single().variants
            val running = client.start(token, v1.id).body<ExamAttemptResponse>()
            assertEquals("exam_attempt_running", client.start(token, v2.id).code())
            client.submit(token, running.id)

            // An edit creates version 2: an old package cannot start, but its owner can still download it.
            env.examImporter.import(TestExams.input(TestExams.published().mapTasks { if (it["id"]!!.jsonPrimitive.content == "g1") it.with("prompt", "New prompt") else it }))
            assertEquals("variant_version_mismatch", client.start(token, v1.id, version = 1).code())
            assertEquals(HttpStatusCode.OK, client.pkg(token, v1.id, version = 1).status)
            assertEquals("exam_variant_not_found", client.pkg(env.verifier.tokenFor("u3"), v1.id, version = 1).code())
            assertEquals(2, client.pkg(token, v1.id).body<ExamPackageResponse>().version)

            // Retired variant.
            env.examImporter.import(TestExams.input(second().with("status", "retired")))
            assertEquals("variant_unavailable", client.start(token, v2.id).code())
            assertEquals("exam_variant_not_found", client.get("/v1/exams/variants/${v2.id}") { auth(token) }.code())

            // 1 start so far today; 9 more are allowed.
            repeat(9) {
                val a = client.start(token, v1.id, version = 2)
                assertEquals(HttpStatusCode.Created, a.status, "start ${it + 2}")
                client.submit(token, a.body<ExamAttemptResponse>().id)
            }
            val limited = client.start(token, v1.id, version = 2)
            assertEquals(HttpStatusCode.TooManyRequests, limited.status)
            assertEquals("rate_limited", limited.code())
            assertNotNull(limited.headers[HttpHeaders.RetryAfter])
        }
    }

    @Test
    fun `account deletion removes attempts, answers, written texts and marks`() {
        val env = TestEnv(db)
        seed(env)
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            val variant = client.catalog(token).templates.single().variants.single()
            val pkg = client.pkg(token, variant.id).body<ExamPackageResponse>()
            val id = client.start(token, variant.id).body<ExamAttemptResponse>().id
            client.answers(token, id, ExamAnswerWriteDto(pkg.task(4).id, 1, text = "Personal answer text", savedAtEpochMs = env.clock.millis()))
            client.submit(token, id)
            client.putJson("/v1/exams/attempts/$id/self-assessment", token, SelfAssessmentRequest(listOf(SelfAssessmentEntryDto(pkg.task(4).id, "1"))))
            assertEquals(1L, db.tx { ExamAnswersTable.selectAll().count() })
            assertEquals(HttpStatusCode.NoContent, client.delete("/v1/me") { auth(token) }.status)
            assertEquals(0L, db.tx { ExamAttemptsTable.selectAll().count() })
            assertEquals(0L, db.tx { ExamAnswersTable.selectAll().count() })
            assertEquals(0L, db.tx { ExamSelfAssessmentsTable.selectAll().count() })
        }
    }

    @Test
    fun `feature flag off hides every exam endpoint`() {
        val env = TestEnv(db, config = testConfig(exams = ExamConfig(enabled = false, finalizeSweepIntervalMs = 0)))
        val token = env.verifier.tokenFor("u1")
        withTestApp(env) { client ->
            val response = client.get("/v1/exams") { auth(token) }
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals("not_found", response.code())
        }
    }
}

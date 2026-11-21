package com.sabit.duelloquiz.backend.exam

import com.sabit.duelloquiz.backend.auth.authUser
import com.sabit.duelloquiz.backend.common.language
import com.sabit.duelloquiz.backend.common.notFound
import com.sabit.duelloquiz.backend.common.validationFailed
import com.sabit.duelloquiz.backend.user.UserService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.util.UUID

/** `/v1/exams*` (docs/api/openapi.yaml, tag `exams`). Not registered when the feature is off. */
fun Route.examRoutes(service: ExamService, users: UserService) {
    route("/exams") {
        get {
            call.respond(service.catalog(users.getOrCreate(call.authUser), call.language))
        }
        route("/variants/{variantId}") {
            get {
                val id = variantId(call.parameters["variantId"])
                call.respond(service.variantInfo(users.getOrCreate(call.authUser), id, call.language))
            }
            get("/package") {
                val id = variantId(call.parameters["variantId"])
                val version = call.request.queryParameters["version"]?.let {
                    it.toIntOrNull()?.takeIf { v -> v >= 1 } ?: throw validationFailed("version must be a positive integer")
                }
                call.respond(service.packageOf(users.getOrCreate(call.authUser), id, version, call.language))
            }
        }
        route("/attempts") {
            post {
                val request = call.receive<StartExamAttemptRequest>()
                val (created, attempt) = service.start(users.getOrCreate(call.authUser), request)
                call.respond(if (created) HttpStatusCode.Created else HttpStatusCode.OK, attempt)
            }
            get {
                val limit = call.request.queryParameters["limit"]?.let {
                    it.toIntOrNull() ?: throw validationFailed("limit must be an integer")
                } ?: 20
                call.respond(service.history(users.getOrCreate(call.authUser), limit, call.language))
            }
            get("/current") {
                call.respond(service.current(users.getOrCreate(call.authUser)))
            }
            route("/{examAttemptId}") {
                get {
                    call.respond(service.attempt(users.getOrCreate(call.authUser), attemptId(call.parameters["examAttemptId"])))
                }
                put("/answers") {
                    val id = attemptId(call.parameters["examAttemptId"])
                    val request = call.receive<ExamAnswerBatchRequest>()
                    call.respond(service.writeAnswers(users.getOrCreate(call.authUser), id, request))
                }
                put("/answers/{taskId}") {
                    val id = attemptId(call.parameters["examAttemptId"])
                    val request = call.receive<ExamSingleAnswerRequest>()
                    call.respond(service.writeAnswer(users.getOrCreate(call.authUser), id, call.parameters["taskId"]!!, request))
                }
                put("/listening/{groupId}") {
                    val id = attemptId(call.parameters["examAttemptId"])
                    val request = call.receive<ExamListeningUpdateRequest>()
                    call.respond(service.updateListening(users.getOrCreate(call.authUser), id, call.parameters["groupId"]!!, request))
                }
                post("/submit") {
                    val id = attemptId(call.parameters["examAttemptId"])
                    val request = call.receive<SubmitExamRequest>()
                    call.respond(service.submit(users.getOrCreate(call.authUser), id, request, call.language))
                }
                get("/result") {
                    call.respond(service.result(users.getOrCreate(call.authUser), attemptId(call.parameters["examAttemptId"]), call.language))
                }
                put("/self-assessment") {
                    val id = attemptId(call.parameters["examAttemptId"])
                    val request = call.receive<SelfAssessmentRequest>()
                    call.respond(service.selfAssess(users.getOrCreate(call.authUser), id, request, call.language))
                }
                get("/review") {
                    call.respond(service.review(users.getOrCreate(call.authUser), attemptId(call.parameters["examAttemptId"]), call.language))
                }
            }
        }
    }
}

/** A malformed id cannot name an existing variant or attempt: 404 like an unknown one. */
private fun variantId(raw: String?): UUID =
    raw?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: throw notFound(ExamErrorCodes.VARIANT_NOT_FOUND, "Exam variant not found")

private fun attemptId(raw: String?): UUID =
    raw?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: throw notFound(ExamErrorCodes.ATTEMPT_NOT_FOUND, "Exam attempt not found")

package com.sabit.duelloquiz.backend.user

import com.sabit.duelloquiz.backend.auth.authUser
import com.sabit.duelloquiz.backend.common.validationFailed
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

@Serializable
data class ProfileResponse(
    val id: String,
    val displayName: String,
    val avatarUrl: String?,
    val rating: Int,
    val createdAt: String,
)

@Serializable
data class UpdateProfileRequest(val displayName: String)

@Serializable
data class StatsResponse(val record: Record, val calibration: List<CalibrationLevel>)

@Serializable
data class Record(val wins: Int, val losses: Int, val draws: Int, val total: Int)

@Serializable
data class CalibrationLevel(val level: Int, val picks: Int, val correct: Int)

@Serializable
data class MatchHistoryResponse(val items: List<MatchSummary>)

@Serializable
data class MatchSummary(
    val id: String,
    val opponent: OpponentDto,
    val result: String,
    val endReason: String,
    val scoreOwn: Int,
    val scoreOpponent: Int,
    val ratingChange: Int,
    val finishedAt: String,
)

@Serializable
data class OpponentDto(val displayName: String?, val avatarUrl: String?, val deleted: Boolean)

const val MAX_RECENT_MATCHES = 20

fun UserRecord.toResponse() = ProfileResponse(
    id = id.toString(),
    displayName = displayName,
    avatarUrl = avatarUrl,
    rating = rating,
    createdAt = createdAt.toString(),
)

fun Route.userRoutes(service: UserService) {
    route("/me") {
        get {
            call.respond(service.getOrCreate(call.authUser).toResponse())
        }
        patch {
            val request = call.receive<UpdateProfileRequest>()
            call.respond(service.updateDisplayName(call.authUser, request.displayName).toResponse())
        }
        delete {
            service.deleteAccount(call.authUser)
            call.respond(HttpStatusCode.NoContent)
        }
        get("/stats") {
            call.respond(service.stats(call.authUser))
        }
        get("/matches") {
            val raw = call.request.queryParameters["limit"]
            val limit = if (raw == null) MAX_RECENT_MATCHES else raw.toIntOrNull()
            if (limit == null || limit !in 1..MAX_RECENT_MATCHES) {
                throw validationFailed("limit must be an integer between 1 and $MAX_RECENT_MATCHES")
            }
            call.respond(service.recentMatches(call.authUser, limit))
        }
    }
}

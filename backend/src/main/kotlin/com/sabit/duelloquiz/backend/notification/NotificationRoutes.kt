package com.sabit.duelloquiz.backend.notification

import com.sabit.duelloquiz.backend.auth.authUser
import com.sabit.duelloquiz.backend.user.UserService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

@Serializable
data class PushTokenRequest(val token: String, val locale: String? = null)

@Serializable
data class NotificationSettingsDto(val dailyReminder: Boolean, val streakReminder: Boolean)

fun Route.notificationRoutes(service: NotificationService, users: UserService) {
    route("/me/push-tokens") {
        put {
            val request = call.receive<PushTokenRequest>()
            val user = users.getOrCreate(call.authUser)
            service.registerToken(user.id, request.token, request.locale)
            call.respond(HttpStatusCode.NoContent)
        }
        delete("/{token}") {
            val user = users.getOrCreate(call.authUser)
            call.parameters["token"]?.let { service.unregisterToken(user.id, it) }
            call.respond(HttpStatusCode.NoContent)
        }
    }
    route("/me/notification-settings") {
        get {
            val user = users.getOrCreate(call.authUser)
            call.respond(service.settings(user.id).toDto())
        }
        put {
            val request = call.receive<NotificationSettingsDto>()
            val user = users.getOrCreate(call.authUser)
            call.respond(service.updateSettings(user.id, NotificationSettings(request.dailyReminder, request.streakReminder)).toDto())
        }
    }
}

private fun NotificationSettings.toDto() = NotificationSettingsDto(dailyReminder, streakReminder)

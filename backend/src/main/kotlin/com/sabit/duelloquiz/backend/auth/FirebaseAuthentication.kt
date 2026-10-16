package com.sabit.duelloquiz.backend.auth

import com.sabit.duelloquiz.backend.common.ApiException
import com.sabit.duelloquiz.backend.common.ErrorCodes
import com.sabit.duelloquiz.backend.common.ErrorResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.principal
import io.ktor.server.response.respond

const val FIREBASE_AUTH = "firebase"

/**
 * Ktor authentication provider: `Authorization: Bearer <Firebase ID token>` verified by
 * a [TokenVerifier]; the principal is an [AuthUser]. Failures answer 401 `unauthorized`.
 */
class FirebaseAuthenticationProvider(config: Config) : AuthenticationProvider(config) {
    private val verifier: TokenVerifier = config.verifier

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val token = bearerToken(context.call)
        val cause = if (token == null) {
            AuthenticationFailedCause.NoCredentials
        } else {
            try {
                context.principal(verifier.verify(token))
                return
            } catch (e: InvalidTokenException) {
                AuthenticationFailedCause.InvalidCredentials
            }
        }
        context.challenge(CHALLENGE_KEY, cause) { challenge, call ->
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse(ErrorCodes.UNAUTHORIZED, "Missing or invalid bearer token"),
            )
            challenge.complete()
        }
    }

    class Config(name: String?) : AuthenticationProvider.Config(name, null) {
        lateinit var verifier: TokenVerifier
    }

    private companion object {
        const val CHALLENGE_KEY = "FirebaseIdToken"
    }
}

fun AuthenticationConfig.firebase(name: String? = FIREBASE_AUTH, configure: FirebaseAuthenticationProvider.Config.() -> Unit) {
    register(FirebaseAuthenticationProvider(FirebaseAuthenticationProvider.Config(name).apply(configure)))
}

private fun bearerToken(call: ApplicationCall): String? {
    val header = call.request.headers[HttpHeaders.Authorization] ?: return null
    val parts = header.trim().split(' ', limit = 2)
    if (parts.size != 2 || !parts[0].equals("Bearer", ignoreCase = true)) return null
    return parts[1].trim().takeIf { it.isNotEmpty() }
}

/** The verified caller; only valid inside `authenticate(FIREBASE_AUTH) { }`. */
val ApplicationCall.authUser: AuthUser
    get() = principal<AuthUser>()
        ?: throw ApiException(HttpStatusCode.Unauthorized, ErrorCodes.UNAUTHORIZED, "Not authenticated")

/** Admin routes: custom claim `admin=true`, else 403 `forbidden`. */
fun ApplicationCall.requireAdmin(): AuthUser {
    val user = authUser
    if (!user.isAdmin) throw ApiException(HttpStatusCode.Forbidden, ErrorCodes.FORBIDDEN, "Admin claim required")
    return user
}

package com.sabit.duelloquiz.backend.common

import io.ktor.server.config.ApplicationConfig
import java.time.Duration

/** Typed view of `application.conf` (values may come from environment variables). */
data class AppConfig(
    val db: DbConfig,
    val firebase: FirebaseConfig,
    val media: MediaConfig,
    val quiz: QuizConfig,
    val displayNameBlocklistFile: String?,
    val importMaxBodyBytes: Long,
) {
    companion object {
        fun from(config: ApplicationConfig): AppConfig {
            fun str(path: String) = config.property(path).getString()
            fun strOrNull(path: String) = config.propertyOrNull(path)?.getString()?.takeIf { it.isNotBlank() }
            fun int(path: String) = str(path).toInt()
            fun long(path: String) = str(path).toLong()
            return AppConfig(
                db = DbConfig(
                    url = str("duello.db.url"),
                    user = str("duello.db.user"),
                    password = config.propertyOrNull("duello.db.password")?.getString().orEmpty(),
                    maxPoolSize = int("duello.db.maxPoolSize"),
                ),
                firebase = FirebaseConfig(
                    serviceAccountFile = strOrNull("duello.firebase.serviceAccountFile"),
                    projectId = strOrNull("duello.firebase.projectId"),
                ),
                media = MediaConfig(
                    dir = str("duello.media.dir"),
                    baseUrl = str("duello.media.baseUrl"),
                ),
                quiz = QuizConfig(
                    questionsPerSession = int("duello.quiz.questionsPerSession"),
                    questionTimeMs = long("duello.quiz.questionTimeMs"),
                    revealMs = long("duello.quiz.revealMs"),
                    graceMs = long("duello.quiz.graceMs"),
                    sessionsPerHour = int("duello.quiz.sessionsPerHour"),
                    expirySweepIntervalMs = long("duello.quiz.expirySweepIntervalMs"),
                ),
                displayNameBlocklistFile = strOrNull("duello.displayName.blocklistFile"),
                importMaxBodyBytes = long("duello.import.maxBodyBytes"),
            )
        }
    }
}

data class DbConfig(val url: String, val user: String, val password: String, val maxPoolSize: Int)

data class FirebaseConfig(val serviceAccountFile: String?, val projectId: String?)

data class MediaConfig(val dir: String, val baseUrl: String)

/**
 * Quiz numbers live here once (docs/requirements/quiz.md §3). The session deadline is
 * derived: questions × (question time + reveal) + grace = 10 × 21.5 s + 30 s = 245 s.
 */
data class QuizConfig(
    val questionsPerSession: Int = 10,
    val questionTimeMs: Long = 20_000,
    val revealMs: Long = 1_500,
    val graceMs: Long = 30_000,
    val sessionsPerHour: Int = 30,
    val expirySweepIntervalMs: Long = 60_000,
) {
    val sessionDuration: Duration
        get() = Duration.ofMillis(questionsPerSession * (questionTimeMs + revealMs) + graceMs)
}

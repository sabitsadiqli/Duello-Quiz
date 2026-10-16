package com.sabit.duelloquiz.backend

import com.sabit.duelloquiz.backend.auth.AuthAccountDeleter
import com.sabit.duelloquiz.backend.auth.FIREBASE_AUTH
import com.sabit.duelloquiz.backend.auth.FirebaseAccountDeleter
import com.sabit.duelloquiz.backend.auth.FirebaseTokenVerifier
import com.sabit.duelloquiz.backend.auth.RejectingTokenVerifier
import com.sabit.duelloquiz.backend.auth.TokenVerifier
import com.sabit.duelloquiz.backend.auth.UnconfiguredAccountDeleter
import com.sabit.duelloquiz.backend.auth.createFirebaseAuth
import com.sabit.duelloquiz.backend.auth.firebase
import com.sabit.duelloquiz.backend.category.CategoryRepository
import com.sabit.duelloquiz.backend.category.CategoryService
import com.sabit.duelloquiz.backend.category.categoryRoutes
import com.sabit.duelloquiz.backend.common.AppConfig
import com.sabit.duelloquiz.backend.common.Db
import com.sabit.duelloquiz.backend.common.configureErrorHandling
import com.sabit.duelloquiz.backend.common.healthRoutes
import com.sabit.duelloquiz.backend.common.notFoundFallback
import com.sabit.duelloquiz.backend.content.ContentImporter
import com.sabit.duelloquiz.backend.content.ContentRepository
import com.sabit.duelloquiz.backend.content.adminContentRoutes
import com.sabit.duelloquiz.backend.image.ImageStorage
import com.sabit.duelloquiz.backend.image.LocalDiskImageStorage
import com.sabit.duelloquiz.backend.image.MediaUrls
import com.sabit.duelloquiz.backend.quiz.InMemorySlidingWindowRateLimiter
import com.sabit.duelloquiz.backend.quiz.QuizRepository
import com.sabit.duelloquiz.backend.quiz.QuizService
import com.sabit.duelloquiz.backend.quiz.RateLimiter
import com.sabit.duelloquiz.backend.quiz.quizRoutes
import com.sabit.duelloquiz.backend.user.DisplayNamePolicy
import com.sabit.duelloquiz.backend.user.UserRepository
import com.sabit.duelloquiz.backend.user.UserService
import com.sabit.duelloquiz.backend.user.userRoutes
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.KoinIsolated
import org.koin.logger.slf4jLogger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.nio.file.Path
import java.time.Clock
import java.time.Duration

val ApiJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true
}

/** Ktor module referenced from application.conf. */
fun Application.module() {
    val config = AppConfig.from(environment.config)
    install(KoinIsolated) {
        slf4jLogger()
        modules(infrastructureModule(config), featureModule(config))
    }
    configureApp()
}

/** Database, clock, Firebase and storage: replaced by fakes in tests. */
fun infrastructureModule(config: AppConfig) = module {
    single { config }
    single { config.quiz }
    single<Clock> { Clock.systemUTC() }
    single { Db(Db.dataSource(config.db).also(Db::migrate)) }
    single { FirebaseHolder(createFirebaseAuth(config.firebase)) }
    single<TokenVerifier> { get<FirebaseHolder>().auth?.let(::FirebaseTokenVerifier) ?: RejectingTokenVerifier }
    single<AuthAccountDeleter> { get<FirebaseHolder>().auth?.let(::FirebaseAccountDeleter) ?: UnconfiguredAccountDeleter }
    single<ImageStorage> { LocalDiskImageStorage(Path.of(config.media.dir)) }
}

class FirebaseHolder(val auth: com.google.firebase.auth.FirebaseAuth?)

/** Repositories and services; the same in production and tests. */
fun featureModule(config: AppConfig) = module {
    single { MediaUrls(config.media.baseUrl) }
    single { DisplayNamePolicy(DisplayNamePolicy.loadBlocklist(config.displayNameBlocklistFile?.let(Path::of))) }
    single<RateLimiter> { InMemorySlidingWindowRateLimiter(config.quiz.sessionsPerHour, Duration.ofHours(1)) }
    single { UserRepository(get()) }
    single { UserService(get(), get(), get(), get()) }
    single { CategoryRepository(get()) }
    single { CategoryService(get(), get()) }
    single { QuizRepository() }
    single { QuizService(get(), get(), get(), get(), get(), get(), get()) }
    single { ContentRepository() }
    single { ContentImporter(get(), get(), get()) }
}

/** Plugins, routes and background jobs; expects Koin to be installed. */
fun Application.configureApp() {
    val config = get<AppConfig>()
    val db = get<Db>()

    install(ContentNegotiation) { json(ApiJson) }
    install(CallLogging) {
        level = Level.INFO
        filter { it.request.path() != "/health" }
    }
    configureErrorHandling()
    install(Authentication) {
        firebase { verifier = this@configureApp.get() }
    }

    val users = get<UserService>()
    routing {
        healthRoutes(db)
        authenticate(FIREBASE_AUTH) {
            route("/v1") {
                userRoutes(users)
                categoryRoutes(get(), users)
                quizRoutes(get(), users)
                adminContentRoutes(get(), config.importMaxBodyBytes)
            }
        }
        notFoundFallback()
    }

    startSessionExpiryJob(get(), config.quiz.expirySweepIntervalMs)
    monitor.subscribe(ApplicationStopped) { db.close() }
}

private fun Application.startSessionExpiryJob(service: QuizService, intervalMs: Long) {
    if (intervalMs <= 0) return
    val log = LoggerFactory.getLogger("com.sabit.duelloquiz.backend.quiz.SessionExpiryJob")
    launch {
        while (isActive) {
            delay(intervalMs)
            try {
                val expired = service.expireOverdueSessions()
                if (expired > 0) log.info("quiz_sessions_expired count={}", expired)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                log.error("quiz_session_expiry_failed", e)
            }
        }
    }
}

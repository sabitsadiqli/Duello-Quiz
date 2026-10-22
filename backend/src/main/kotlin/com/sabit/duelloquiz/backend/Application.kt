package com.sabit.duelloquiz.backend

import com.sabit.duelloquiz.backend.auth.AuthAccountDeleter
import com.sabit.duelloquiz.backend.auth.FIREBASE_AUTH
import com.sabit.duelloquiz.backend.auth.FirebaseAccountDeleter
import com.sabit.duelloquiz.backend.auth.FirebaseTokenVerifier
import com.sabit.duelloquiz.backend.auth.RejectingTokenVerifier
import com.sabit.duelloquiz.backend.auth.TokenVerifier
import com.sabit.duelloquiz.backend.auth.UnconfiguredAccountDeleter
import com.sabit.duelloquiz.backend.auth.createFirebaseApp
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
import com.sabit.duelloquiz.backend.daily.DailyRepository
import com.sabit.duelloquiz.backend.daily.DailyService
import com.sabit.duelloquiz.backend.daily.dailyRoutes
import com.sabit.duelloquiz.backend.duel.DbDuelStore
import com.sabit.duelloquiz.backend.duel.DuelClock
import com.sabit.duelloquiz.backend.duel.DuelService
import com.sabit.duelloquiz.backend.duel.DuelStore
import com.sabit.duelloquiz.backend.duel.InMemoryMatchmakingQueue
import com.sabit.duelloquiz.backend.duel.MatchmakingQueue
import com.sabit.duelloquiz.backend.duel.duelRoutes
import com.sabit.duelloquiz.backend.image.ImageProcessor
import com.sabit.duelloquiz.backend.image.ImageRepository
import com.sabit.duelloquiz.backend.image.ImageService
import com.sabit.duelloquiz.backend.image.ImageStorage
import com.sabit.duelloquiz.backend.image.adminImageRoutes
import com.sabit.duelloquiz.backend.image.LocalDiskImageStorage
import com.sabit.duelloquiz.backend.image.MediaUrls
import com.sabit.duelloquiz.backend.notification.DisabledPushSender
import com.sabit.duelloquiz.backend.notification.FcmPushSender
import com.sabit.duelloquiz.backend.notification.NotificationRepository
import com.sabit.duelloquiz.backend.notification.NotificationService
import com.sabit.duelloquiz.backend.notification.NotificationTexts
import com.sabit.duelloquiz.backend.notification.PushSender
import com.sabit.duelloquiz.backend.notification.notificationRoutes
import com.sabit.duelloquiz.backend.progress.ProgressRepository
import com.sabit.duelloquiz.backend.progress.ProgressService
import com.sabit.duelloquiz.backend.progress.progressRoutes
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
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
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
    // Duel timers use their own clock so tests can run them on virtual or real time.
    single<DuelClock> { DuelClock.System }
    // Closed by Koin when the application stops.
    single { Db(Db.dataSource(config.db).also(Db::migrate)) } withOptions { onClose { it?.close() } }
    single { FirebaseHolder(createFirebaseApp(config.firebase)) }
    single<TokenVerifier> { get<FirebaseHolder>().auth?.let(::FirebaseTokenVerifier) ?: RejectingTokenVerifier }
    single<AuthAccountDeleter> { get<FirebaseHolder>().auth?.let(::FirebaseAccountDeleter) ?: UnconfiguredAccountDeleter }
    single<ImageStorage> { LocalDiskImageStorage(Path.of(config.media.dir)) }
    single<PushSender> { get<FirebaseHolder>().messaging?.let(::FcmPushSender) ?: DisabledPushSender }
}

class FirebaseHolder(app: com.google.firebase.FirebaseApp?) {
    val auth: com.google.firebase.auth.FirebaseAuth? = app?.let(com.google.firebase.auth.FirebaseAuth::getInstance)
    val messaging: com.google.firebase.messaging.FirebaseMessaging? =
        app?.let(com.google.firebase.messaging.FirebaseMessaging::getInstance)
}

/** Repositories and services; the same in production and tests. */
fun featureModule(config: AppConfig) = module {
    single { MediaUrls(config.media.baseUrl) }
    single { DisplayNamePolicy(DisplayNamePolicy.loadBlocklist(config.displayNameBlocklistFile?.let(Path::of))) }
    single<RateLimiter> { InMemorySlidingWindowRateLimiter(config.quiz.sessionsPerHour, Duration.ofHours(1)) }
    single { config.duel }
    single { UserRepository(get()) }
    single { UserService(get(), get(), get(), get(), listOf(get<DuelService>())) }
    single { CategoryRepository(get()) }
    single { CategoryService(get(), get()) }
    single { QuizRepository() }
    single { ProgressRepository() }
    single { ProgressService(get(), get(), config.progress, get()) }
    single { QuizService(get(), get(), get(), get(), get(), get(), get(), get()) }
    single { DailyRepository() }
    single { DailyService(get(), get(), get(), get(), config.daily, config.quiz, get(), get()) }
    single { NotificationRepository() }
    single { NotificationTexts.load() }
    single { NotificationService(get(), get(), get(), get(), get(), get(), get(), config.notifications, get()) }
    single { ContentRepository() }
    single { ImageRepository() }
    single { ImageProcessor(config.images) }
    single { ImageService(get(), get(), get(), get(), config.images, get()) }
    single { ContentImporter(get(), get(), get(), get(), get()) }
    single<DuelStore> { DbDuelStore(get(), get(), config.duel.eloK, get()) }
    single<MatchmakingQueue> { InMemoryMatchmakingQueue() }
    single { DuelScope(CoroutineScope(SupervisorJob() + Dispatchers.Default)) } withOptions { onClose { it?.scope?.cancel() } }
    single { DuelService(get<DuelScope>().scope, config.duel, get(), get(), get(), get()) }
}

/** Scope of duel matches and queue timers; cancelled when the application stops. */
class DuelScope(val scope: CoroutineScope)

/** Plugins, routes and background jobs; expects Koin to be installed. */
fun Application.configureApp() {
    val config = get<AppConfig>()
    val db = get<Db>()

    install(ContentNegotiation) { json(ApiJson) }
    install(CallLogging) {
        level = Level.INFO
        disableDefaultColors()
        filter { it.request.path() != "/health" }
    }
    configureErrorHandling()
    install(WebSockets) {
        // Protocol limit is 4096 bytes (checked per message); larger frames close the socket.
        // Heartbeat is the protocol's own ping/pong (15 s timeout in DuelRoutes).
        maxFrameSize = 65_536
    }
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
                adminImageRoutes(get(), get())
                duelRoutes(get(), users, config.duel, get())
                dailyRoutes(get(), users)
                progressRoutes(get(), get(), users)
                notificationRoutes(get(), users)
            }
        }
        notFoundFallback()
    }

    startSessionExpiryJob(get(), get(), config.quiz.expirySweepIntervalMs)
    startNotificationScheduler(get(), get(), config.notifications)
    val duel = get<DuelService>()
    launch {
        try {
            duel.abandonLostMatches()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            LoggerFactory.getLogger(DuelService::class.java).error("duel_abandon_on_startup_failed", e)
        }
    }
}

private fun Application.startSessionExpiryJob(service: QuizService, daily: DailyService, intervalMs: Long) {
    if (intervalMs <= 0) return
    val log = LoggerFactory.getLogger("com.sabit.duelloquiz.backend.quiz.SessionExpiryJob")
    launch {
        while (isActive) {
            delay(intervalMs)
            try {
                val expired = service.expireOverdueSessions()
                if (expired > 0) log.info("quiz_sessions_expired count={}", expired)
                val expiredDaily = daily.expireOverdueAttempts()
                if (expiredDaily > 0) log.info("daily_attempts_expired count={}", expiredDaily)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                log.error("quiz_session_expiry_failed", e)
            }
        }
    }
}

/**
 * Push reminder scheduler (docs/mvp-spec.md §11): checks every tick whether a reminder
 * window (10:00 / 20:00 Baku + 60 min) is open and runs that job once per day. Runs in
 * this single replica (OQ-070); the per-user send log makes a restart safe.
 */
private fun Application.startNotificationScheduler(
    service: NotificationService,
    sender: PushSender,
    config: com.sabit.duelloquiz.backend.notification.NotificationConfig,
) {
    val log = LoggerFactory.getLogger("com.sabit.duelloquiz.backend.notification.NotificationScheduler")
    if (!config.enabled || config.tickIntervalMs <= 0) return
    if (!sender.available) log.warn("push_not_configured: reminders are not sent (no Firebase service account)")
    launch {
        while (isActive) {
            try {
                service.runDue()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                log.error("notification_job_failed", e)
            }
            delay(config.tickIntervalMs)
        }
    }
}

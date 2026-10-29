package com.sabit.duelloquiz.backend.common

import com.sabit.duelloquiz.backend.daily.DailyConfig
import com.sabit.duelloquiz.backend.duel.BotConfig
import com.sabit.duelloquiz.backend.duel.DuelConfig
import com.sabit.duelloquiz.backend.notification.NotificationConfig
import com.sabit.duelloquiz.backend.progress.ProgressConfig
import io.ktor.server.config.ApplicationConfig
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/** Typed view of `application.conf` (values may come from environment variables). */
data class AppConfig(
    val db: DbConfig,
    val firebase: FirebaseConfig,
    val media: MediaConfig,
    val quiz: QuizConfig,
    val displayNameBlocklistFile: String?,
    val importMaxBodyBytes: Long,
    val duel: DuelConfig = DuelConfig(),
    val images: ImageConfig = ImageConfig(),
    val daily: DailyConfig = DailyConfig(),
    val progress: ProgressConfig = ProgressConfig(),
    val notifications: NotificationConfig = NotificationConfig(),
) {
    companion object {
        fun from(config: ApplicationConfig): AppConfig {
            fun str(path: String) = config.property(path).getString()
            fun strOrNull(path: String) = config.propertyOrNull(path)?.getString()?.takeIf { it.isNotBlank() }
            fun int(path: String) = str(path).toInt()
            fun long(path: String) = str(path).toLong()
            fun bool(path: String) = str(path).toBooleanStrict()
            fun time(path: String) = LocalTime.parse(str(path))
            fun double(path: String) = str(path).toDouble()
            fun doubles(path: String) = config.property(path).getList().map { it.toDouble() }
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
                duel = DuelConfig(
                    regularRounds = int("duello.duel.regularRounds"),
                    maxSuddenDeath = int("duello.duel.maxSuddenDeath"),
                    introMs = long("duello.duel.introMs"),
                    questionMs = long("duello.duel.questionMs"),
                    confidenceMs = long("duello.duel.confidenceMs"),
                    answerMs = long("duello.duel.answerMs"),
                    resultMs = long("duello.duel.resultMs"),
                    graceMs = long("duello.duel.graceMs"),
                    queueTimeoutMs = long("duello.duel.queueTimeoutMs"),
                    reconnectWindowMs = long("duello.duel.reconnectWindowMs"),
                    maxBet3PerMatch = int("duello.duel.maxBet3PerMatch"),
                    missedRoundsToForfeit = int("duello.duel.missedRoundsToForfeit"),
                    eloK = int("duello.duel.eloK"),
                    heartbeatIntervalMs = long("duello.duel.heartbeatIntervalMs"),
                    heartbeatTimeoutMs = long("duello.duel.heartbeatTimeoutMs"),
                    bot = BotConfig(
                        enabled = bool("duello.duel.bot.enabled"),
                        waitMs = long("duello.duel.bot.waitMs"),
                        baseP = double("duello.duel.bot.baseP"),
                        perRatingPoint = double("duello.duel.bot.perRatingPoint"),
                        ratingPivot = int("duello.duel.bot.ratingPivot"),
                        difficultyAdjust = doubles("duello.duel.bot.difficultyAdjust"),
                        formAdjust = double("duello.duel.bot.formAdjust"),
                        formWindow = int("duello.duel.bot.formWindow"),
                        formHighWins = int("duello.duel.bot.formHighWins"),
                        formLowWins = int("duello.duel.bot.formLowWins"),
                        minP = double("duello.duel.bot.minP"),
                        maxP = double("duello.duel.bot.maxP"),
                        knowsBetWeights = doubles("duello.duel.bot.knowsBetWeights"),
                        notKnowsBetWeights = doubles("duello.duel.bot.notKnowsBetWeights"),
                        allInKnowsBet3 = double("duello.duel.bot.allInKnowsBet3"),
                        pickMinMs = long("duello.duel.bot.pickMinMs"),
                        pickMaxMs = long("duello.duel.bot.pickMaxMs"),
                        answerMinMs = long("duello.duel.bot.answerMinMs"),
                        answerMaxMs = long("duello.duel.bot.answerMaxMs"),
                    ),
                ),
                images = ImageConfig(
                    maxUploadBytes = long("duello.images.maxUploadBytes"),
                    maxSourcePixels = long("duello.images.maxSourcePixels"),
                    maxLongSide = int("duello.images.maxLongSide"),
                    thumbLongSide = int("duello.images.thumbLongSide"),
                    quality = int("duello.images.quality"),
                    thumbQuality = int("duello.images.thumbQuality"),
                    minQuality = int("duello.images.minQuality"),
                    qualityStep = int("duello.images.qualityStep"),
                    targetBytes = int("duello.images.targetBytes"),
                    thumbTargetBytes = int("duello.images.thumbTargetBytes"),
                    parallelism = int("duello.images.parallelism"),
                ),
                daily = DailyConfig(
                    questions = int("duello.daily.questions"),
                    questionTimeMs = long("duello.daily.questionTimeMs"),
                    revealMs = long("duello.daily.revealMs"),
                    graceMs = long("duello.daily.graceMs"),
                    firstDay = LocalDate.parse(str("duello.daily.firstDay")),
                    reuseWindowDays = int("duello.daily.reuseWindowDays"),
                ),
                progress = ProgressConfig(
                    quizXpPerCorrect = int("duello.progress.quizXpPerCorrect"),
                    dailyXpPerCorrect = int("duello.progress.dailyXpPerCorrect"),
                    dailyCompletionBonus = int("duello.progress.dailyCompletionBonus"),
                    duelPlayedXp = int("duello.progress.duelPlayedXp"),
                    duelWinXp = int("duello.progress.duelWinXp"),
                    duelDrawXp = int("duello.progress.duelDrawXp"),
                    duelBotPlayedXp = int("duello.progress.duelBotPlayedXp"),
                    duelBotWinXp = int("duello.progress.duelBotWinXp"),
                    duelBotDrawXp = int("duello.progress.duelBotDrawXp"),
                    quizSessionsPerDay = int("duello.progress.quizSessionsPerDay"),
                    duelMatchesPerDay = int("duello.progress.duelMatchesPerDay"),
                    duelSameOpponentPerDay = int("duello.progress.duelSameOpponentPerDay"),
                    freezeEveryDays = int("duello.progress.freezeEveryDays"),
                ),
                notifications = NotificationConfig(
                    enabled = bool("duello.notifications.enabled"),
                    tickIntervalMs = long("duello.notifications.tickIntervalMs"),
                    dailyReminderAt = time("duello.notifications.dailyReminderAt"),
                    streakReminderAt = time("duello.notifications.streakReminderAt"),
                    sendWindowMinutes = long("duello.notifications.sendWindowMinutes"),
                    quietStart = time("duello.notifications.quietStart"),
                    quietEnd = time("duello.notifications.quietEnd"),
                    inactiveAfterDays = long("duello.notifications.inactiveAfterDays"),
                    minStreak = int("duello.notifications.minStreak"),
                    tokenStaleDays = long("duello.notifications.tokenStaleDays"),
                    logRetentionDays = long("duello.notifications.logRetentionDays"),
                    maxTokensPerUser = int("duello.notifications.maxTokensPerUser"),
                ),
            )
        }
    }
}

data class DbConfig(val url: String, val user: String, val password: String, val maxPoolSize: Int)

data class FirebaseConfig(val serviceAccountFile: String?, val projectId: String?)

data class MediaConfig(val dir: String, val baseUrl: String)

/** Image pipeline numbers (docs/mvp-spec.md §5); defaults = application.conf. */
data class ImageConfig(
    val maxUploadBytes: Long = 10L * 1024 * 1024,
    val maxSourcePixels: Long = 100_000_000,
    val maxLongSide: Int = 1024,
    val thumbLongSide: Int = 400,
    val quality: Int = 75,
    val thumbQuality: Int = 70,
    val minQuality: Int = 50,
    val qualityStep: Int = 5,
    val targetBytes: Int = 80_000,
    val thumbTargetBytes: Int = 20_000,
    val parallelism: Int = 1,
) {
    init {
        require(thumbLongSide in 1..maxLongSide) { "thumbLongSide must be within 1..maxLongSide" }
        require(minQuality in 1..minOf(quality, thumbQuality)) { "minQuality must not exceed the start qualities" }
        require(qualityStep > 0 && parallelism > 0) { "qualityStep and parallelism must be positive" }
    }
}

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

package com.sabit.duelloquiz.backend.notification

import com.sabit.duelloquiz.backend.common.BakuTime
import com.sabit.duelloquiz.backend.common.Db
import com.sabit.duelloquiz.backend.common.ErrorCodes
import com.sabit.duelloquiz.backend.common.badRequest
import com.sabit.duelloquiz.backend.daily.DailyRecord
import com.sabit.duelloquiz.backend.daily.DailyRepository
import com.sabit.duelloquiz.backend.daily.DailyService
import com.sabit.duelloquiz.backend.progress.ProgressService
import com.sabit.duelloquiz.backend.progress.Streaks
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class JobReport(val sent: Int, val failed: Int, val skipped: Map<String, Int>, val invalidTokens: Int)

/**
 * Push reminders (docs/mvp-spec.md §11, NOTIF-01 … NOTIF-08) and the token/switch
 * endpoints. The jobs run inside the single backend replica (OQ-070); a per-(user,
 * type, day) row in `notification_log` is claimed before sending, so a retry or a
 * restart never sends twice. Running several replicas would additionally need a job
 * lock (e.g. a PostgreSQL advisory lock) to avoid duplicated work; duplicates are
 * already prevented by the claim.
 */
class NotificationService(
    private val db: Db,
    private val repository: NotificationRepository,
    private val dailyRepository: DailyRepository,
    private val daily: DailyService,
    private val progress: ProgressService,
    private val texts: NotificationTexts,
    private val sender: PushSender,
    private val config: NotificationConfig,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(NotificationService::class.java)
    private val rules = ReminderRules(config)
    private val completed = HashSet<Pair<NotificationType, LocalDate>>()
    private var lastCleanup: LocalDate? = null

    // -----------------------------------------------------------------------
    // Endpoints
    // -----------------------------------------------------------------------

    suspend fun registerToken(userId: UUID, rawToken: String, rawLocale: String?) {
        val token = rawToken.trim()
        if (token.isEmpty() || token.length > MAX_TOKEN_LENGTH || token.any { it.isWhitespace() || it.isISOControl() }) {
            throw badRequest("token must be 1-$MAX_TOKEN_LENGTH characters without whitespace", ErrorCodes.INVALID_PUSH_TOKEN)
        }
        val locale = rawLocale?.trim()?.takeIf { it.isNotEmpty() } ?: "az"
        if (!LOCALE.matches(locale)) throw badRequest("locale must be a language tag like az or az-AZ", ErrorCodes.VALIDATION_FAILED)
        db.tx { with(repository) { upsertToken(userId, token, locale.lowercase(), clock.instant(), config.maxTokensPerUser) } }
    }

    suspend fun unregisterToken(userId: UUID, token: String) {
        db.tx { with(repository) { deleteToken(userId, token.trim()) } }
    }

    suspend fun settings(userId: UUID): NotificationSettings = db.tx { with(repository) { settings(userId) } }

    suspend fun updateSettings(userId: UUID, settings: NotificationSettings): NotificationSettings = db.tx {
        with(repository) {
            saveSettings(userId, settings, clock.instant())
            settings(userId)
        }
    }

    // -----------------------------------------------------------------------
    // Jobs
    // -----------------------------------------------------------------------

    /** Called by the scheduler every tick: runs every job whose window is open, once per day. */
    suspend fun runDue(now: Instant = clock.instant()): List<JobReport> {
        val today = BakuTime.day(now)
        val reports = mutableListOf<JobReport>()
        if (lastCleanup != today) {
            cleanup(now)
            lastCleanup = today
        }
        if (!sender.available) return reports
        for (type in NotificationType.entries) {
            val key = type to today
            if (key in completed || !rules.inSendWindow(type, now)) continue
            reports += runJob(type, now)
            completed += key
            completed.removeIf { it.second.isBefore(today.minusDays(2)) }
        }
        return reports
    }

    /** Sends [type] to every eligible user at [now]. Safe to run again: claimed users are skipped. */
    suspend fun runJob(type: NotificationType, now: Instant = clock.instant()): JobReport {
        val today = BakuTime.day(now)
        val todayDaily: DailyRecord? = if (type == NotificationType.DAILY_REMINDER) daily.ensureToday(now) else null
        val snapshot = db.tx {
            val tokens = with(repository) { allTokens() }.groupBy { it.userId }
            val users = tokens.keys
            Snapshot(
                tokens = tokens,
                settings = with(repository) { settings(users) },
                streaks = with(progress) { streakStates(users) },
                createdAt = with(repository) { createdAt(users) },
                started = todayDaily?.let { with(dailyRepository) { usersWithAttempt(it.id, users) } } ?: emptySet(),
            )
        }
        var sent = 0
        var failed = 0
        var invalid = 0
        val skipped = HashMap<String, Int>()
        fun skip(userId: UUID, reason: String) {
            skipped.merge(reason, 1, Int::plus)
            log.info("notification_skipped userId={} type={} reason={}", userId, type.db, reason)
        }
        for ((userId, userTokens) in snapshot.tokens) {
            val created = snapshot.createdAt[userId] ?: continue
            val streak = snapshot.streaks[userId]
            val view = streak?.let { Streaks.view(it, today) }
            val ctx = ReminderContext(
                type = type,
                now = now,
                enabledByUser = (snapshot.settings[userId] ?: NotificationSettings()).enabled(type),
                hasTokens = userTokens.isNotEmpty(),
                referenceDay = streak?.lastActiveDay ?: BakuTime.day(created),
                dailyAvailable = todayDaily != null,
                dailyStarted = userId in snapshot.started,
                streak = view?.current ?: 0,
                todayActive = view?.todayActive ?: false,
            )
            val decision = rules.decide(ctx)
            if (decision is ReminderDecision.Skip) {
                skip(userId, decision.reason)
                continue
            }
            if (!db.tx { with(repository) { claim(userId, type, today, now) } }) {
                skip(userId, "limit")
                continue
            }
            val params = when (type) {
                NotificationType.DAILY_REMINDER -> mapOf("dailyNumber" to todayDaily!!.number.toString())
                NotificationType.STREAK_REMINDER -> mapOf("streak" to ctx.streak.toString())
            }
            var anySent = false
            for (token in userTokens) {
                val text = texts.render(type, token.locale, params)
                val result = try {
                    sender.send(
                        PushMessage(token.token, type, text.title, text.body, mapOf("type" to type.db, "dayKey" to BakuTime.key(today))),
                    )
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    PushResult.Failed(e.javaClass.simpleName)
                }
                when (result) {
                    PushResult.Sent -> anySent = true
                    PushResult.InvalidToken -> {
                        invalid++
                        db.tx { with(repository) { deleteToken(token.token) } }
                        log.info("push_token_invalid userId={}", userId)
                    }
                    is PushResult.Failed -> log.warn("notification_send_failed userId={} type={} reason={}", userId, type.db, result.reason)
                }
            }
            val outcome = if (anySent) NotificationRepository.OUTCOME_SENT else NotificationRepository.OUTCOME_FAILED
            db.tx { with(repository) { setOutcome(userId, type, today, outcome) } }
            if (anySent) {
                sent++
                log.info("notification_sent userId={} type={}", userId, type.db)
            } else {
                failed++
            }
        }
        val report = JobReport(sent, failed, skipped, invalid)
        log.info("notification_job_done type={} day={} sent={} failed={} invalidTokens={} skipped={}", type.db, today, sent, failed, invalid, skipped)
        return report
    }

    /** Daily housekeeping: stale tokens (NOTIF-08) and old send-log rows. */
    suspend fun cleanup(now: Instant = clock.instant()) {
        val (tokens, logs) = db.tx {
            with(repository) {
                deleteStaleTokens(now.minus(java.time.Duration.ofDays(config.tokenStaleDays))) to
                    deleteLogBefore(BakuTime.day(now).minusDays(config.logRetentionDays))
            }
        }
        if (tokens > 0 || logs > 0) log.info("notification_cleanup staleTokens={} logRows={}", tokens, logs)
    }

    private class Snapshot(
        val tokens: Map<UUID, List<PushTokenRecord>>,
        val settings: Map<UUID, NotificationSettings>,
        val streaks: Map<UUID, com.sabit.duelloquiz.backend.progress.StreakState>,
        val createdAt: Map<UUID, Instant>,
        val started: Set<UUID>,
    )

    private companion object {
        const val MAX_TOKEN_LENGTH = 1024
        val LOCALE = Regex("^[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,8}){0,2}$")
    }
}

package com.sabit.duelloquiz.backend.notification

import com.sabit.duelloquiz.backend.common.BakuTime
import com.sabit.duelloquiz.backend.common.I18n
import com.sabit.duelloquiz.backend.common.I18nText
import com.sabit.duelloquiz.backend.common.resolve
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Reminder types; `db` is stored and sent as the FCM data `type` (and Android channel id / tag). */
enum class NotificationType(val db: String) {
    DAILY_REMINDER("daily_reminder"), STREAK_REMINDER("streak_reminder");
}

/** Push numbers (docs/mvp-spec.md §11, OQ-088, OQ-089, OQ-092). */
data class NotificationConfig(
    /** Off: no scheduler (tests; no Firebase configured). */
    val enabled: Boolean = true,
    /** How often the scheduler checks for due jobs. 0 disables it. */
    val tickIntervalMs: Long = 60_000,
    val dailyReminderAt: LocalTime = LocalTime.of(10, 0),
    val streakReminderAt: LocalTime = LocalTime.of(20, 0),
    /** A job that could not run at its time (restart) still runs within this window, later it is skipped. */
    val sendWindowMinutes: Long = 60,
    val quietStart: LocalTime = LocalTime.of(22, 0),
    val quietEnd: LocalTime = LocalTime.of(9, 0),
    /** OQ-092: nothing after this many days without an active day. */
    val inactiveAfterDays: Long = 7,
    /** Streak reminder only from this streak on (OQ-088). */
    val minStreak: Int = 2,
    /** Tokens not refreshed for this many days are deleted. */
    val tokenStaleDays: Long = 60,
    /** Send log rows older than this are deleted. */
    val logRetentionDays: Long = 90,
    val maxTokensPerUser: Int = 10,
) {
    fun sendTime(type: NotificationType): LocalTime = when (type) {
        NotificationType.DAILY_REMINDER -> dailyReminderAt
        NotificationType.STREAK_REMINDER -> streakReminderAt
    }
}

/** Everything the server knows about one user when a reminder job runs. */
data class ReminderContext(
    val type: NotificationType,
    val now: Instant,
    val enabledByUser: Boolean,
    val hasTokens: Boolean,
    /** Last active day, or the account's creation day if it never played. */
    val referenceDay: LocalDate,
    /** Daily reminder: today's challenge exists. */
    val dailyAvailable: Boolean = false,
    /** Daily reminder: the user has an attempt today (started or finished). */
    val dailyStarted: Boolean = false,
    /** Streak reminder: the streak as the user sees it today. */
    val streak: Int = 0,
    val todayActive: Boolean = false,
)

sealed interface ReminderDecision {
    data object Send : ReminderDecision
    data class Skip(val reason: String) : ReminderDecision
}

/** Eligibility per OQ-088, OQ-089, OQ-091, OQ-092; pure. */
class ReminderRules(private val config: NotificationConfig = NotificationConfig()) {

    fun inQuietHours(time: LocalTime): Boolean =
        if (config.quietStart > config.quietEnd) {
            time >= config.quietStart || time < config.quietEnd
        } else {
            time >= config.quietStart && time < config.quietEnd
        }

    /** The job of [type] may run at [now]: from its time until the window ends, never in quiet hours. */
    fun inSendWindow(type: NotificationType, now: Instant): Boolean {
        val time = BakuTime.localTime(now)
        val start = config.sendTime(type)
        val end = start.plusMinutes(config.sendWindowMinutes)
        val inWindow = if (end > start) time >= start && time < end else time >= start || time < end
        return inWindow && !inQuietHours(time)
    }

    fun decide(ctx: ReminderContext): ReminderDecision {
        val today = BakuTime.day(ctx.now)
        return when {
            !inSendWindow(ctx.type, ctx.now) -> ReminderDecision.Skip(
                if (inQuietHours(BakuTime.localTime(ctx.now))) "quiet_hours" else "outside_window",
            )
            !ctx.enabledByUser -> ReminderDecision.Skip("off")
            !ctx.hasTokens -> ReminderDecision.Skip("no_token")
            BakuTime.daysBetween(ctx.referenceDay, today) > config.inactiveAfterDays -> ReminderDecision.Skip("inactive")
            ctx.type == NotificationType.DAILY_REMINDER && !ctx.dailyAvailable -> ReminderDecision.Skip("unavailable")
            ctx.type == NotificationType.DAILY_REMINDER && ctx.dailyStarted -> ReminderDecision.Skip("played")
            ctx.type == NotificationType.STREAK_REMINDER && ctx.todayActive -> ReminderDecision.Skip("played")
            ctx.type == NotificationType.STREAK_REMINDER && ctx.streak < config.minStreak -> ReminderDecision.Skip("no_streak")
            else -> ReminderDecision.Send
        }
    }
}

data class NotificationText(val title: String, val body: String)

/** Reminder texts from backend i18n JSON (`i18n/notifications.json`), resolved by the token's locale. */
class NotificationTexts(private val texts: Map<String, Pair<I18nText, I18nText>>) {

    fun render(type: NotificationType, locale: String?, params: Map<String, String>): NotificationText {
        val (title, body) = texts[type.db] ?: error("No notification text for ${type.db}")
        val language = I18n.resolveLanguage(locale)
        fun fill(text: String) = params.entries.fold(text) { acc, (k, v) -> acc.replace("{$k}", v) }
        return NotificationText(fill(title.resolve(language)), fill(body.resolve(language)))
    }

    companion object {
        fun load(resource: String = "/i18n/notifications.json"): NotificationTexts {
            val raw = NotificationTexts::class.java.getResourceAsStream(resource)?.use { it.readBytes().decodeToString() }
                ?: error("Missing resource $resource")
            val root = Json.parseToJsonElement(raw).jsonObject
            val texts = NotificationType.entries.associate { type ->
                val entry = root[type.db]?.jsonObject ?: error("$resource has no ${type.db}")
                fun i18n(key: String): I18nText = (entry[key] as? JsonObject ?: error("${type.db}.$key missing"))
                    .mapValues { it.value.jsonPrimitive.content }
                    .also { require(I18n.FALLBACK_LANGUAGE in it) { "${type.db}.$key needs az" } }
                type.db to (i18n("title") to i18n("body"))
            }
            return NotificationTexts(texts)
        }
    }
}

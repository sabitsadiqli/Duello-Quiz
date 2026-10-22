package com.sabit.duelloquiz.core.model

import androidx.compose.runtime.Immutable

/**
 * What one finalized quiz session, daily attempt or duel did to the player's progress (schema
 * `Progress` of docs/api/openapi.yaml; also `match.result.progress`). Every value is the server's:
 * the client never computes XP, levels or the streak (docs/mvp-spec.md §10).
 */
@Immutable
data class ProgressGain(
    /** Baku day the activity counted for ("2026-10-22"). */
    val dayKey: String,
    val xpGained: Int,
    /** XP was withheld by a daily cap (OQ-082); show the cap note instead of "+XP". */
    val xpCapped: Boolean,
    val xpBreakdown: List<XpBreakdownItem>,
    val xpTotal: Long,
    val levelBefore: Int,
    val levelAfter: Int,
    val xpIntoLevelBefore: Long,
    val xpForNextLevelBefore: Long,
    val xpIntoLevel: Long,
    val xpForNextLevel: Long,
    val categoryLevelUps: List<CategoryLevelUp>,
    val streakCurrent: Int,
    val streakBest: Int,
    /** This result made its day active for the first time (shown once). */
    val streakExtendedToday: Boolean,
    val freezeAvailable: Boolean,
) {
    val leveledUp: Boolean get() = levelAfter > levelBefore
}

@Immutable
data class XpBreakdownItem(
    /** `correct_answers`, `daily_bonus`, `duel_played`, `duel_win`, `duel_draw` (unknown kept as sent). */
    val reason: String,
    val categoryId: Int?,
    val amount: Int,
)

@Immutable
data class CategoryLevelUp(val categoryId: Int, val levelBefore: Int, val levelAfter: Int)

/** `GET /v1/me/progress` (PROG-03 … PROG-08). */
@Immutable
data class ProgressSummary(
    val xpTotal: Long,
    val level: Int,
    val xpIntoLevel: Long,
    val xpForNextLevel: Long,
    val streakCurrent: Int,
    val streakBest: Int,
    /** Today (Baku) already counts for the streak. */
    val todayActive: Boolean,
    val freezeAvailable: Boolean,
    /** One-time home message; null when there is none. */
    val streakEvent: StreakEvent?,
    /** Today (Baku) when the server answered. */
    val dayKey: String,
    /** Next 00:00 Baku on the server clock. */
    val nextResetEpochMs: Long,
    val categories: List<CategoryProgress>,
    /** server clock − local wall clock, measured around the request. */
    val clockOffsetMs: Long = 0,
) {
    /**
     * True once Baku midnight has passed since the server answered (offline cache after
     * midnight): the "today" state must not be claimed any more (PROG-06).
     */
    fun isFromEarlierDay(localNowMs: Long): Boolean = localNowMs + clockOffsetMs >= nextResetEpochMs
}

@Immutable
data class CategoryProgress(
    val categoryId: Int,
    val slug: String,
    val name: String,
    val iconKey: String,
    val xp: Long,
    val level: Int,
    val xpIntoLevel: Long,
    val xpForNextLevel: Long,
)

enum class StreakEventType {
    /** A held freeze covered the missed day [StreakEvent.dayKey] (PROG-07). */
    FREEZE_USED,

    /** The streak ended; [StreakEvent.dayKey] is the first missed day (PROG-08). */
    STREAK_LOST,
}

@Immutable
data class StreakEvent(val type: StreakEventType, val dayKey: String, val previousStreak: Int)

/** `GET/PUT /v1/me/notification-settings` (NOTIF-06, OQ-091). */
@Immutable
data class NotificationSettings(val dailyReminder: Boolean, val streakReminder: Boolean)

/** `data.type` of a reminder push; also the Android channel id and notification tag (OQ-096). */
enum class ReminderType(val wire: String) {
    DAILY("daily_reminder"),
    STREAK("streak_reminder"),
    ;

    companion object {
        fun fromWire(value: String?): ReminderType? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * What the phone allows for the app's notifications (NOTIF-06). On Android 12 and lower
 * [permissionGranted] is always true (no runtime permission).
 */
@Immutable
data class SystemNotificationState(
    val permissionGranted: Boolean,
    /** App notifications are on in the system settings. */
    val appNotificationsEnabled: Boolean,
    val dailyChannelEnabled: Boolean,
    val streakChannelEnabled: Boolean,
) {
    /** The phone blocks every notification of the app (permission denied or app switched off). */
    val blocked: Boolean get() = !permissionGranted || !appNotificationsEnabled

    fun channelEnabled(type: ReminderType): Boolean = when (type) {
        ReminderType.DAILY -> dailyChannelEnabled
        ReminderType.STREAK -> streakChannelEnabled
    }

    /**
     * The switch value shown in settings: the server value AND the permission AND the app's
     * notifications AND the channel (notifications.md B). The server value itself never changes
     * because of the phone state.
     */
    fun effective(type: ReminderType, serverValue: Boolean): Boolean = serverValue && !blocked && channelEnabled(type)

    companion object {
        val AllAllowed = SystemNotificationState(true, true, true, true)
    }
}

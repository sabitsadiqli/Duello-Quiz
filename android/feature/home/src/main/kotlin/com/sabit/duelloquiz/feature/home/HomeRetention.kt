package com.sabit.duelloquiz.feature.home

import androidx.compose.runtime.Immutable
import com.sabit.duelloquiz.core.designsystem.component.DailyCardUi
import com.sabit.duelloquiz.core.designsystem.component.HomeMessageVariant
import com.sabit.duelloquiz.core.designsystem.component.StreakBadgeState
import com.sabit.duelloquiz.core.designsystem.daily.ResultSquareState
import com.sabit.duelloquiz.core.designsystem.util.RetentionFormat
import com.sabit.duelloquiz.core.model.DailySquare
import com.sabit.duelloquiz.core.model.DailyStatus
import com.sabit.duelloquiz.core.model.DailyStatusType
import com.sabit.duelloquiz.core.model.ProgressSummary
import com.sabit.duelloquiz.core.model.StreakEvent
import com.sabit.duelloquiz.core.model.StreakEventType

/** Level and streak in the home header (HOME-08, components.md §24 / §25). */
@Immutable
sealed interface HeaderProgressUi {
    /** No progress and no cache after a failed load: never show "0" as a guess. */
    data object Hidden : HeaderProgressUi

    data object Skeleton : HeaderProgressUi

    data class Content(
        val level: Int,
        val fraction: Float,
        val streak: Int,
        val badge: StreakBadgeState,
        val freezeAvailable: Boolean,
    ) : HeaderProgressUi
}

/** The one home message banner (components.md §29). [days] = best streak for "streak ended". */
@Immutable
data class HomeMessageUi(val variant: HomeMessageVariant, val days: Int = 0)

/** What the home card needs to know about the daily status request. */
internal data class DailyLoad(
    val loading: Boolean = false,
    /** The request completed at least once (success or failure). */
    val attempted: Boolean = false,
    val offline: Boolean = false,
    val failed: Boolean = false,
    val starting: Boolean = false,
)

/**
 * The home daily card from the cached status (DAILY-01, daily-challenge.md A). Pure, unit-tested.
 * A status from an earlier Baku day (`nextResetEpochMs` passed on the server clock) is never shown
 * as today's: finished → "Yeni çağırış yüklənir…" while online, else the offline/unknown face.
 */
internal fun dailyCardUi(status: DailyStatus?, load: DailyLoad, online: Boolean, localNowMs: Long): DailyCardUi {
    if (status == null) {
        return when {
            !load.attempted || load.loading -> DailyCardUi.Skeleton
            load.offline || !online -> DailyCardUi.OfflineUnknown
            else -> DailyCardUi.Error
        }
    }
    val remaining = RetentionFormat.remainingMs(status.nextResetEpochMs, localNowMs, status.clockOffsetMs)
    val stale = remaining <= 0
    if (stale) {
        return when {
            (online && !load.offline) && status.status == DailyStatusType.FINISHED -> status.finishedCard(countdownMs = null)
            online && !load.offline && !load.failed -> DailyCardUi.Skeleton
            load.failed && online && !load.offline -> DailyCardUi.Error
            else -> DailyCardUi.OfflineUnknown
        }
    }
    val number = status.number ?: 0
    return when (status.status) {
        DailyStatusType.AVAILABLE -> DailyCardUi.Available(number, status.questionCount, starting = load.starting)
        DailyStatusType.IN_PROGRESS -> DailyCardUi.InProgress(number, starting = load.starting)
        DailyStatusType.FINISHED -> status.finishedCard(countdownMs = remaining)
        DailyStatusType.UNAVAILABLE -> DailyCardUi.Unavailable
    }
}

private fun DailyStatus.finishedCard(countdownMs: Long?): DailyCardUi.Finished {
    val summary = result
    val (hours, minutes) = RetentionFormat.hoursAndMinutes(countdownMs ?: 0)
    return DailyCardUi.Finished(
        number = number ?: 0,
        correctCount = summary?.correctCount ?: 0,
        total = summary?.total ?: questionCount,
        squares = summary?.squares?.map { it.toSquareState() },
        countdown = countdownMs?.let(RetentionFormat::countdown),
        remainingHours = hours,
        remainingMinutes = minutes,
        shareEnabled = summary != null,
    )
}

internal fun DailySquare.toSquareState(): ResultSquareState = when (this) {
    DailySquare.CORRECT -> ResultSquareState.Correct
    DailySquare.WRONG -> ResultSquareState.Wrong
    DailySquare.UNANSWERED -> ResultSquareState.None
}

/**
 * Header level and streak (HOME-08, PROG-06). After Baku midnight a cached progress no longer
 * claims "today" (outlined flame) until the server confirms it.
 */
internal fun headerProgressUi(progress: ProgressSummary?, loading: Boolean, attempted: Boolean, localNowMs: Long): HeaderProgressUi {
    if (progress == null) return if (!attempted || loading) HeaderProgressUi.Skeleton else HeaderProgressUi.Hidden
    val today = progress.todayActive && !progress.isFromEarlierDay(localNowMs)
    return HeaderProgressUi.Content(
        level = progress.level,
        fraction = RetentionFormat.fraction(progress.xpIntoLevel, progress.xpForNextLevel),
        streak = progress.streakCurrent,
        badge = if (today) StreakBadgeState.ActiveToday else StreakBadgeState.NotYetToday,
        freezeAvailable = progress.freezeAvailable,
    )
}

/**
 * The home message by priority (HOME-08): streak ended → freeze used → streak hint. [event] is
 * the one-time event shown in this home session (already deduplicated by the repository); the
 * hint needs a streak ≥ 1, today not active, not dismissed today, and a progress of today.
 */
internal fun homeMessageUi(
    event: StreakEvent?,
    progress: ProgressSummary?,
    hintDismissedDay: String?,
    localNowMs: Long,
): HomeMessageUi? {
    when (event?.type) {
        StreakEventType.STREAK_LOST -> return HomeMessageUi(HomeMessageVariant.StreakLost, progress?.streakBest ?: event.previousStreak)
        StreakEventType.FREEZE_USED -> return HomeMessageUi(HomeMessageVariant.FreezeUsed)
        null -> Unit
    }
    val summary = progress ?: return null
    val showHint = summary.streakCurrent >= 1 && !summary.todayActive &&
        !summary.isFromEarlierDay(localNowMs) && hintDismissedDay != summary.dayKey
    return if (showHint) HomeMessageUi(HomeMessageVariant.StreakHint) else null
}

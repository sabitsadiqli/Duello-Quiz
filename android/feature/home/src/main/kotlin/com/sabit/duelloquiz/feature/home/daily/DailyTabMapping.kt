package com.sabit.duelloquiz.feature.home.daily

import androidx.compose.runtime.Immutable
import com.sabit.duelloquiz.core.data.repository.DailyProgress
import com.sabit.duelloquiz.core.designsystem.component.AnswerBoxState
import com.sabit.duelloquiz.core.designsystem.component.DailyCardUi
import com.sabit.duelloquiz.core.designsystem.component.StreakMessageVariant
import com.sabit.duelloquiz.core.designsystem.component.WeekDayUi
import com.sabit.duelloquiz.core.designsystem.component.WeekDotState
import com.sabit.duelloquiz.core.designsystem.daily.ResultSquareState
import com.sabit.duelloquiz.core.designsystem.util.RetentionFormat
import com.sabit.duelloquiz.core.model.DailySquare
import com.sabit.duelloquiz.core.model.DailyStatus
import com.sabit.duelloquiz.core.model.DailyStatusType
import com.sabit.duelloquiz.core.model.ProgressSummary
import com.sabit.duelloquiz.core.model.StreakEvent
import com.sabit.duelloquiz.core.model.StreakEventType
import com.sabit.duelloquiz.feature.home.BakuDay

/** What the card needs to know about the `GET /v1/daily` request. */
internal data class DailyLoad(
    val loading: Boolean = false,
    /** The request completed at least once (success or failure). */
    val attempted: Boolean = false,
    val offline: Boolean = false,
    val failed: Boolean = false,
    val starting: Boolean = false,
)

/** The streak section of the Gündəlik tab (daily-tab.md B). */
@Immutable
sealed interface StreakSectionUi {
    /** First load without cache: title skeleton, 7 hairline dots, no message. */
    data class Loading(val week: List<WeekDayUi>) : StreakSectionUi

    /** Failed without cache: `profile_section_error` + retry, height kept. */
    data object Error : StreakSectionUi

    data class Content(
        val current: Int,
        val best: Int,
        /** Today (Baku) counts; never claimed from a cache of an earlier day (PROG-06). */
        val todayActive: Boolean,
        val freezeAvailable: Boolean,
        /** 0 / 0: `profile_streak_empty` instead of the title and best line. */
        val isNew: Boolean,
        val week: List<WeekDayUi>,
        /** Baku day of the progress; the dot fill and flame ignite run once per day. */
        val dayKey: String,
    ) : StreakSectionUi
}

/** The one streak message (components.md §29 v3). [days] = best streak for "streak ended". */
@Immutable
data class StreakMessageUi(val variant: StreakMessageVariant, val days: Int = 0)

/**
 * The challenge card from the cached status (daily-tab.md A, DAILY-01). Pure, unit-tested.
 * A status from an earlier Baku day (`nextResetEpochMs` passed on the server clock) is never shown
 * as today's: finished → "Yeni çağırış yüklənir…" while online, else the offline/unknown face.
 * [inProgressBoxes] fills the in-progress boxes from the in-memory attempt (null = all dashed).
 */
internal fun dailyCardUi(
    status: DailyStatus?,
    load: DailyLoad,
    online: Boolean,
    localNowMs: Long,
    inProgressBoxes: List<AnswerBoxState>? = null,
): DailyCardUi {
    if (status == null) {
        return when {
            !load.attempted || load.loading -> DailyCardUi.Skeleton
            load.offline || !online -> DailyCardUi.OfflineUnknown
            else -> DailyCardUi.Error
        }
    }
    val remaining = RetentionFormat.remainingMs(status.nextResetEpochMs, localNowMs, status.clockOffsetMs)
    if (remaining <= 0) {
        val reachable = online && !load.offline
        return when {
            reachable && status.status == DailyStatusType.FINISHED -> status.finishedCard(countdownMs = null)
            reachable && !load.failed -> DailyCardUi.Skeleton
            reachable -> DailyCardUi.Error
            else -> DailyCardUi.OfflineUnknown
        }
    }
    val number = status.number ?: 0
    return when (status.status) {
        DailyStatusType.AVAILABLE -> DailyCardUi.Available(number, status.questionCount, starting = load.starting)
        DailyStatusType.IN_PROGRESS -> DailyCardUi.InProgress(
            number = number,
            boxes = inProgressBoxes?.takeIf { it.size == status.questionCount }
                ?: List(status.questionCount) { AnswerBoxState.NotPlayed },
            starting = load.starting,
        )
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
 * In-progress answer boxes from the attempt kept in memory (daily-tab.md A): the server verdicts
 * filled (correct / wrong / no answer), answers sent offline as pending, the rest dashed. The
 * client never decides correctness; `GET /v1/daily` has no per-question results, so without the
 * in-memory attempt (process death, another device) every box stays dashed. Null = no attempt.
 */
internal fun inProgressBoxes(progress: DailyProgress?): List<AnswerBoxState>? {
    val value = progress ?: return null
    return value.attempt.questions.sortedBy { it.position }.map { question ->
        val verdict = value.resolved[question.id]
        when {
            verdict != null && verdict.optionId == null -> AnswerBoxState.None
            verdict != null -> if (verdict.correct) AnswerBoxState.Correct else AnswerBoxState.Wrong
            question.id in value.queued -> AnswerBoxState.Pending
            else -> AnswerBoxState.NotPlayed
        }
    }
}

/**
 * The streak section (daily-tab.md B, PROG-06). [localNowMs] is the wall clock; the Baku "today"
 * follows the server clock of the progress. The week row is the OQ-D35 fallback of
 * [BakuDay.fallbackWeek]. Pure, unit-tested.
 */
internal fun streakSectionUi(progress: ProgressSummary?, loading: Boolean, attempted: Boolean, localNowMs: Long): StreakSectionUi {
    if (progress == null) {
        if (attempted && !loading) return StreakSectionUi.Error
        val today = BakuDay.of(localNowMs).epochDay
        val week = BakuDay.fallbackWeek(today, progressEpochDay = null, streakCurrent = 0, todayActive = false)
            .map { it.copy(state = WeekDotState.Unknown) }
        return StreakSectionUi.Loading(week)
    }
    val stale = progress.isFromEarlierDay(localNowMs)
    val today = BakuDay.of(localNowMs + progress.clockOffsetMs).epochDay
    val progressDay = BakuDay.parse(progress.dayKey)?.epochDay
    val event = progress.streakEvent
    val week = BakuDay.fallbackWeek(
        todayEpochDay = today,
        progressEpochDay = progressDay,
        streakCurrent = progress.streakCurrent,
        todayActive = progress.todayActive,
        freezeEpochDay = event?.takeIf { it.type == StreakEventType.FREEZE_USED }?.let { BakuDay.parse(it.dayKey)?.epochDay },
        missedEpochDay = event?.takeIf { it.type == StreakEventType.STREAK_LOST }?.let { BakuDay.parse(it.dayKey)?.epochDay },
    )
    return StreakSectionUi.Content(
        current = progress.streakCurrent,
        best = progress.streakBest,
        todayActive = progress.todayActive && !stale,
        freezeAvailable = progress.freezeAvailable,
        isNew = progress.streakCurrent == 0 && progress.streakBest == 0,
        week = week,
        dayKey = progress.dayKey,
    )
}

/**
 * The streak message by priority (PROG-06 … PROG-08, same rules as v2 home): streak ended →
 * freeze used → streak hint. [event] is the one-time event shown in this tab session (already
 * deduplicated by the repository); the hint needs a streak ≥ 1, today not active, not dismissed
 * today, and a progress of today (an older cache cannot know).
 */
internal fun streakMessageUi(
    event: StreakEvent?,
    progress: ProgressSummary?,
    hintDismissedDay: String?,
    localNowMs: Long,
): StreakMessageUi? {
    when (event?.type) {
        StreakEventType.STREAK_LOST -> return StreakMessageUi(StreakMessageVariant.StreakLost, progress?.streakBest ?: event.previousStreak)
        StreakEventType.FREEZE_USED -> return StreakMessageUi(StreakMessageVariant.FreezeUsed)
        null -> Unit
    }
    val summary = progress ?: return null
    val showHint = summary.streakCurrent >= 1 && !summary.todayActive &&
        !summary.isFromEarlierDay(localNowMs) && hintDismissedDay != summary.dayKey
    return if (showHint) StreakMessageUi(StreakMessageVariant.StreakHint) else null
}

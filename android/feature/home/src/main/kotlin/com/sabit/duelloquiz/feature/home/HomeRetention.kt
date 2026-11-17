package com.sabit.duelloquiz.feature.home

import androidx.compose.runtime.Immutable
import com.sabit.duelloquiz.core.designsystem.component.StreakDayState
import com.sabit.duelloquiz.core.model.ProgressSummary

/** The streak chip in the home top row (HOME-08, components.md §35). */
@Immutable
sealed interface StreakChipUi {
    /** No progress and no cache after a failed load: never show "0" as a guess. */
    data object Hidden : StreakChipUi

    data object Skeleton : StreakChipUi

    data class Content(
        val days: Int,
        val state: StreakDayState,
        val freezeAvailable: Boolean,
        /** Baku day of the progress; the flame ignites once per day. */
        val dayKey: String,
    ) : StreakChipUi
}

/**
 * The streak chip (HOME-08, PROG-06). After Baku midnight a cached progress no longer claims
 * "today" (outlined flame) until the server confirms it. Pure, unit-tested.
 */
internal fun streakChipUi(progress: ProgressSummary?, loading: Boolean, attempted: Boolean, localNowMs: Long): StreakChipUi {
    if (progress == null) return if (!attempted || loading) StreakChipUi.Skeleton else StreakChipUi.Hidden
    val today = progress.todayActive && !progress.isFromEarlierDay(localNowMs)
    return StreakChipUi.Content(
        days = progress.streakCurrent,
        state = if (today) StreakDayState.ActiveToday else StreakDayState.NotYetToday,
        freezeAvailable = progress.freezeAvailable,
        dayKey = progress.dayKey,
    )
}

/**
 * The daily-rotating greeting pool (home.md, copy/az.md `home_greeting_1` … `_8`). Lines 1, 3
 * and 5 mention the duel or Əminlik and are skipped while the duel flag is off (OQ-020).
 */
internal object GreetingPool {
    const val SIZE = 8
    val DUEL_LINES: Set<Int> = setOf(1, 3, 5)

    /** Pool line number (1-based) for the Baku day [epochDay]; the same all day, the next one tomorrow. */
    fun lineFor(epochDay: Long, duelEnabled: Boolean): Int {
        val pool = (1..SIZE).filter { duelEnabled || it !in DUEL_LINES }
        return pool[epochDay.mod(pool.size)]
    }

    /** "Aysel Məmmədova" → "Aysel"; a one-word name as is; null when unknown or blank. */
    fun firstName(displayName: String?): String? =
        displayName?.trim()?.split(WHITESPACE)?.firstOrNull()?.takeIf { it.isNotEmpty() }

    private val WHITESPACE = Regex("\\s+")
}

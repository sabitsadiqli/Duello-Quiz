package com.sabit.duelloquiz.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.data.repository.DailyRepository
import com.sabit.duelloquiz.core.designsystem.util.RetentionFormat
import com.sabit.duelloquiz.core.model.DailyStatus
import com.sabit.duelloquiz.core.model.DailyStatusType
import com.sabit.duelloquiz.core.model.ReminderType
import com.sabit.duelloquiz.core.notifications.ReminderOpens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * State of the tab scaffold (HOME-09): the Gündəlik badge dot and the reminder tap waiting to be
 * handled (NOTIF-05). The badge reads only the cached daily status (no extra request); home
 * refreshes it with its own refresh and the Gündəlik tab on return from the daily flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainTabsViewModel @Inject constructor(
    dailyRepository: DailyRepository,
    private val reminderOpens: ReminderOpens,
    private val clock: WallClock,
) : ViewModel() {

    /** True while today's challenge is `available` or `in_progress` (daily-tab.md decisions). */
    val dailyBadge: StateFlow<Boolean> = dailyRepository.status
        .flatMapLatest { status ->
            flow {
                emit(dailyBadgeVisible(status, clock.nowMs()))
                if (status != null) {
                    // At Baku midnight (server clock) the cached status stops being today's.
                    val remaining = RetentionFormat.remainingMs(status.nextResetEpochMs, clock.nowMs(), status.clockOffsetMs)
                    if (remaining > 0) {
                        delay(remaining)
                        emit(dailyBadgeVisible(status, clock.nowMs()))
                    }
                }
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initialValue = false)

    /** The reminder the app was opened from, until [consumeReminder]. */
    val pendingReminder: StateFlow<ReminderType?> = reminderOpens.pending

    fun consumeReminder(): ReminderType? = reminderOpens.consume()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * The Gündəlik badge (components.md §33): shown for `available` and `in_progress` of today;
 * hidden for `finished`, `unavailable`, no status (unknown / error) and a cache of an earlier
 * Baku day (offline after midnight). Pure, unit-tested.
 */
internal fun dailyBadgeVisible(status: DailyStatus?, localNowMs: Long): Boolean {
    if (status == null) return false
    if (RetentionFormat.remainingMs(status.nextResetEpochMs, localNowMs, status.clockOffsetMs) <= 0) return false
    return status.status == DailyStatusType.AVAILABLE || status.status == DailyStatusType.IN_PROGRESS
}

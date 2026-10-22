package com.sabit.duelloquiz.core.notifications

import com.sabit.duelloquiz.core.model.ReminderType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The reminder the app was opened from (NOTIF-05), handed from the activity to home: a daily
 * reminder scrolls the daily card into view once; nothing starts by itself. Only the type is
 * used; the payload's day is never trusted (home shows the current server state).
 */
@Singleton
class ReminderOpens @Inject constructor() {
    private val _pending = MutableStateFlow<ReminderType?>(null)
    val pending: StateFlow<ReminderType?> = _pending.asStateFlow()

    fun onOpened(type: ReminderType) {
        _pending.value = type
    }

    /** Returns and clears the pending reminder. */
    fun consume(): ReminderType? = _pending.value.also { _pending.value = null }
}

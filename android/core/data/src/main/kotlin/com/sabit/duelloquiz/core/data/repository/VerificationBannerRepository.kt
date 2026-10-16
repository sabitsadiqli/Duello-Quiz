package com.sabit.duelloquiz.core.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dismissal of the email-verification banner (AUTH-08). Per OQ-043 it lasts until the next cold
 * start, so it is kept in memory only. It is stored per account uid, so another account signing
 * in on the same device still sees its own banner.
 */
interface VerificationBannerRepository {
    /** Uids whose banner was dismissed in this process. */
    val dismissedUids: StateFlow<Set<String>>

    fun dismiss(uid: String)
}

@Singleton
internal class InMemoryVerificationBannerRepository @Inject constructor() : VerificationBannerRepository {
    private val state = MutableStateFlow<Set<String>>(emptySet())

    override val dismissedUids: StateFlow<Set<String>> = state.asStateFlow()

    override fun dismiss(uid: String) {
        state.value += uid
    }
}

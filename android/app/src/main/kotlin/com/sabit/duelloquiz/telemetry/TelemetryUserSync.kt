package com.sabit.duelloquiz.telemetry

import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.auth.AuthState
import com.sabit.duelloquiz.core.common.Telemetry
import com.sabit.duelloquiz.core.common.di.ApplicationScope
import com.sabit.duelloquiz.core.data.repository.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the telemetry user ID equal to the backend `users.id` of the signed-in account
 * (auth.md §5): set once the profile is known, cleared on sign-out and while a new account's
 * profile is still unknown, so reports never carry the previous account's ID.
 */
@Singleton
class TelemetryUserSync @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val telemetry: Telemetry,
    @param:ApplicationScope private val appScope: CoroutineScope,
) {
    fun start() {
        appScope.launch {
            combine(authRepository.authState, profileRepository.profile) { state, profile ->
                if (state is AuthState.SignedIn) profile?.id else null
            }
                .distinctUntilChanged()
                .collect { telemetry.setUserId(it) }
        }
    }
}

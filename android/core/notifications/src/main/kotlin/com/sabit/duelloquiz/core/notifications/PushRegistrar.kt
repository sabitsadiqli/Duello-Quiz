package com.sabit.duelloquiz.core.notifications

import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.auth.AuthState
import com.sabit.duelloquiz.core.common.di.ApplicationScope
import com.sabit.duelloquiz.core.data.repository.PushTokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NOTIF-02: keeps this device's token registered for the signed-in account. Runs at app start
 * and whenever the account or the phone permission changes: allowed → `PUT` (at most once a day),
 * revoked → `DELETE`. Never asks for the permission itself (NOTIF-01).
 */
@Singleton
class PushRegistrar @Inject constructor(
    private val authRepository: AuthRepository,
    private val pushTokens: PushTokenRepository,
    private val systemNotifications: SystemNotifications,
    @param:ApplicationScope private val appScope: CoroutineScope,
) {
    fun start() {
        appScope.launch {
            combine(
                authRepository.authState.map { (it as? AuthState.SignedIn)?.user?.uid }.distinctUntilChanged(),
                systemNotifications.state.map { !it.blocked }.distinctUntilChanged(),
            ) { uid, allowed -> uid to allowed }
                .collect { (uid, allowed) -> if (uid != null) pushTokens.sync(allowed) }
        }
    }
}

package com.sabit.duelloquiz.core.network.fake

import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.network.DuelloNetworkDataSource
import com.sabit.duelloquiz.core.network.duel.DuelSocketFactory

/** Debug only: the fake backend used when Firebase is not configured (see NetworkModule). */
internal object DebugNetworkFakes {
    fun create(authRepository: AuthRepository, clock: WallClock): DuelloNetworkDataSource? =
        FakeDuelloNetworkDataSource(authRepository, clock)

    fun createDuel(authRepository: AuthRepository, clock: WallClock): DuelSocketFactory? = null
}

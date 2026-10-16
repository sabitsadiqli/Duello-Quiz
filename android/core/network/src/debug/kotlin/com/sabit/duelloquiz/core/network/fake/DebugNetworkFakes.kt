package com.sabit.duelloquiz.core.network.fake

import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.network.DuelloNetworkDataSource

/** Debug only: the fake backend used when Firebase is not configured (see NetworkModule). */
internal object DebugNetworkFakes {
    fun create(authRepository: AuthRepository, clock: WallClock): DuelloNetworkDataSource? =
        FakeDuelloNetworkDataSource(authRepository, clock)
}

package com.sabit.duelloquiz.core.network.fake

import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.network.DuelloNetworkDataSource

/** Release builds have no fake backend; the debug source set provides one. */
internal object DebugNetworkFakes {
    @Suppress("UNUSED_PARAMETER", "FunctionOnlyReturningConstant")
    fun create(authRepository: AuthRepository, clock: WallClock): DuelloNetworkDataSource? = null
}

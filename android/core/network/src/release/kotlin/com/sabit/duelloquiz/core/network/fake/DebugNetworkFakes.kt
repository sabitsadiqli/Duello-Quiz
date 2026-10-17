package com.sabit.duelloquiz.core.network.fake

import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.network.DuelloNetworkDataSource
import com.sabit.duelloquiz.core.network.duel.DuelSocketFactory

/** Release builds have no fake backend; the debug source set provides one. */
@Suppress("UNUSED_PARAMETER", "FunctionOnlyReturningConstant")
internal object DebugNetworkFakes {
    fun create(authRepository: AuthRepository, clock: WallClock): DuelloNetworkDataSource? = null

    fun createDuel(authRepository: AuthRepository, clock: WallClock): DuelSocketFactory? = null
}

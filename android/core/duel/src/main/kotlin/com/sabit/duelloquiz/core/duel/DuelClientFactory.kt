package com.sabit.duelloquiz.core.duel

import com.sabit.duelloquiz.core.common.MonotonicClock
import com.sabit.duelloquiz.core.network.duel.DuelSocketFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/**
 * Creates a [DuelClient] bound to a screen's scope (the duel ViewModel): the socket lives as long
 * as the duel screen, survives configuration changes and is closed when the user leaves the flow.
 */
class DuelClientFactory @Inject constructor(
    private val socketFactory: DuelSocketFactory,
    private val store: ActiveDuelStore,
    private val clock: MonotonicClock,
) {
    fun create(scope: CoroutineScope, config: DuelClientConfig = DuelClientConfig()): DuelClient =
        DuelClient(socketFactory, store, clock, scope, config)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DuelModule {
    @Binds
    abstract fun bindActiveDuelStore(impl: DataStoreActiveDuelStore): ActiveDuelStore
}

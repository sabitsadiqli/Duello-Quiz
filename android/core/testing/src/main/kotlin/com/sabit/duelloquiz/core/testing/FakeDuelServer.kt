package com.sabit.duelloquiz.core.testing

import com.sabit.duelloquiz.core.duel.ActiveDuel
import com.sabit.duelloquiz.core.duel.ActiveDuelStore
import com.sabit.duelloquiz.core.model.duel.DuelClientMessage
import com.sabit.duelloquiz.core.network.duel.DuelConnectError
import com.sabit.duelloquiz.core.network.duel.DuelInbound
import com.sabit.duelloquiz.core.network.duel.DuelOpenResult
import com.sabit.duelloquiz.core.network.duel.DuelSocket
import com.sabit.duelloquiz.core.network.duel.DuelSocketFactory
import com.sabit.duelloquiz.core.model.duel.DuelServerMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Scriptable duel server for tests: every [open] creates a [FakeDuelSocket] the test drives
 * ([FakeDuelSocket.emit], [FakeDuelSocket.serverClose]) and inspects ([FakeDuelSocket.sent]).
 * Queue handshake failures with [failNextOpens]. [onOpen] can greet automatically (e.g. `hello`).
 */
class FakeDuelServer : DuelSocketFactory {

    val sockets = mutableListOf<FakeDuelSocket>()
    val openFailures = ArrayDeque<DuelConnectError>()
    var onOpen: (FakeDuelSocket) -> Unit = {}

    /** When set, every `ping` is answered with a `pong` carrying this server time. */
    var autoPong: (() -> Long)? = null

    val openCount: Int get() = sockets.size + failedOpens
    private var failedOpens = 0

    /** The newest socket. */
    val socket: FakeDuelSocket get() = sockets.last()

    fun failNextOpens(vararg errors: DuelConnectError) {
        openFailures.addAll(errors)
    }

    override suspend fun open(): DuelOpenResult {
        openFailures.removeFirstOrNull()?.let {
            failedOpens++
            return DuelOpenResult.Failed(it)
        }
        val socket = FakeDuelSocket(autoPong)
        sockets += socket
        onOpen(socket)
        return DuelOpenResult.Opened(socket)
    }

    /** Every message the client sent on any socket, without pings. */
    val sentWithoutPings: List<DuelClientMessage>
        get() = sockets.flatMap { it.sent }.filter { it != DuelClientMessage.Ping }
}

class FakeDuelSocket(private val autoPong: (() -> Long)? = null) : DuelSocket {
    private val channel = Channel<DuelInbound>(Channel.UNLIMITED)
    override val incoming: ReceiveChannel<DuelInbound> = channel

    val sent = mutableListOf<DuelClientMessage>()
    var closedByClientWith: Int? = null
        private set
    var isOpen = true
        private set

    fun emit(vararg messages: DuelServerMessage) {
        messages.forEach { channel.trySend(DuelInbound.Message(it)) }
    }

    fun emitMalformed() {
        channel.trySend(DuelInbound.Malformed)
    }

    /** The server closes with [code]; null = the connection broke. */
    fun serverClose(code: Int?) {
        if (!isOpen) return
        isOpen = false
        channel.trySend(DuelInbound.Closed(code))
        channel.close()
    }

    override suspend fun send(message: DuelClientMessage): Boolean {
        if (!isOpen) return false
        sent += message
        if (message == DuelClientMessage.Ping) autoPong?.let { emit(DuelServerMessage.Pong(0, it())) }
        return true
    }

    override suspend fun close(code: Int) {
        if (!isOpen) return
        closedByClientWith = code
        isOpen = false
        channel.trySend(DuelInbound.Closed(code))
        channel.close()
    }
}

/** [ActiveDuelStore] in memory. */
class InMemoryActiveDuelStore(initial: ActiveDuel? = null, left: String? = null) : ActiveDuelStore {
    val duel = MutableStateFlow(initial)
    val left = MutableStateFlow(left)

    override val activeMatchId: Flow<String?> = duel.map { it?.matchId }

    override suspend fun activeDuel(): ActiveDuel? = duel.value

    override suspend fun setActiveDuel(duel: ActiveDuel?) {
        this.duel.value = duel
    }

    override suspend fun leftMatchId(): String? = left.value

    override suspend fun setLeftMatchId(matchId: String?) {
        left.value = matchId
    }
}

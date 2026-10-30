package com.sabit.duelloquiz.core.duel

import android.util.Log
import com.sabit.duelloquiz.core.common.MonotonicClock
import com.sabit.duelloquiz.core.model.duel.DuelClientMessage
import com.sabit.duelloquiz.core.model.duel.DuelCloseCode
import com.sabit.duelloquiz.core.model.duel.DuelErrorCode
import com.sabit.duelloquiz.core.model.duel.DuelServerMessage
import com.sabit.duelloquiz.core.network.duel.DuelConnectError
import com.sabit.duelloquiz.core.network.duel.DuelInbound
import com.sabit.duelloquiz.core.network.duel.DuelOpenResult
import com.sabit.duelloquiz.core.network.duel.DuelSocket
import com.sabit.duelloquiz.core.network.duel.DuelSocketFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Connection of a [DuelClient] (what the screen shows about its own socket). */
sealed interface DuelConnectionState {
    /** Not connected and not trying (never started, or closed by the app). */
    data object Idle : DuelConnectionState

    /** First handshake running. */
    data object Connecting : DuelConnectionState

    /** `hello` received. */
    data object Connected : DuelConnectionState

    /**
     * The socket dropped during a match; reconnecting with backoff (DUEL-13). [sinceElapsedMs] is
     * when the drop was noticed (the overlay switches to "Bağlantı yoxdur" after 10 s).
     */
    data class Reconnecting(val sinceElapsedMs: Long, val attempt: Int) : DuelConnectionState

    /** The socket dropped outside a match; no automatic reconnect (DUEL-03 "Bağlantı kəsildi"). */
    data class Lost(val closeCode: Int?) : DuelConnectionState

    /** The first handshake failed (DUEL-01). */
    data class Failed(val error: DuelConnectError) : DuelConnectionState

    /** `upgrade_required` / close 4000: never reconnect (OQ-029). */
    data object UpgradeRequired : DuelConnectionState

    /** `session_replaced` / close 4001: never reconnect (DUEL-04). */
    data object SessionReplaced : DuelConnectionState
}

/** Timing of the client side of the protocol (duel-protocol.md §1.3, DUEL-13). */
data class DuelClientConfig(
    /** Default until `hello.heartbeatIntervalMs` arrives. */
    val pingIntervalMs: Long = 5_000,
    /** No server message for this long = the connection is treated as dropped (DUEL-13). */
    val silenceTimeoutMs: Long = 15_000,
    /** Pause between reconnect attempts after the first, immediate one. */
    val reconnectDelayMs: Long = 1_000,
)

/**
 * The duel WebSocket client (docs/api/duel-protocol.md). It owns everything below the screen:
 *
 * - connect with the Bearer token (in [DuelSocketFactory]), `ping` every heartbeat interval, and a
 *   watchdog that treats 15 s without any server message as a drop;
 * - the clock offset from every `serverNowEpochMs` ([clock]);
 * - `seq` ordering of the match stream: duplicates and stale messages are ignored, a gap sends
 *   `match.rejoin` and drops the stream until `match.state` (or `match.result`) resets it (§1.4);
 * - `hello.activeMatchId` + `match.state` resync, and `match.rejoin` for a saved match the server
 *   did not report (process death, server restart → `match_not_found`, DUEL-13/16);
 * - close codes: no reconnect after 4000/4001; during a match every other drop reconnects with
 *   backoff (first try at once, then every [DuelClientConfig.reconnectDelayMs]); outside a match a
 *   drop is reported as [DuelConnectionState.Lost];
 * - the saved [ActiveDuel] from `match.found` until `match.result` / `match.cancelled` /
 *   `match_not_found`, and the "left while offline" marker: such a match is left automatically
 *   when the server reports it again (OQ-058);
 * - a late `queue.leave` answered with `already_in_match` rejoins the running match (OQ-073): a
 *   forfeit always needs the explicit DUEL-15 confirmation.
 *
 * Applied server messages are delivered in order through [messages] (single collector). Not
 * thread-safe: call it from the [scope]'s dispatcher (the main thread in the app).
 */
@Suppress("TooManyFunctions")
class DuelClient(
    private val socketFactory: DuelSocketFactory,
    private val store: ActiveDuelStore,
    private val monotonicClock: MonotonicClock,
    private val scope: CoroutineScope,
    private val config: DuelClientConfig = DuelClientConfig(),
) {
    private val _connection = MutableStateFlow<DuelConnectionState>(DuelConnectionState.Idle)
    val connection: StateFlow<DuelConnectionState> = _connection.asStateFlow()

    private val _messages = Channel<DuelServerMessage>(Channel.UNLIMITED)

    /** Server messages after `seq` filtering, in order. `pong` and unknown types are not delivered. */
    val messages: Flow<DuelServerMessage> = _messages.receiveAsFlow()

    /** Server clock estimate (DUEL-12). */
    val clock = DuelClockSync()

    private var runJob: Job? = null
    private var socket: DuelSocket? = null
    private val kick = Channel<Unit>(Channel.CONFLATED)
    private var closedByApp = false
    private var fatal: DuelConnectionState? = null
    private var heartbeatMs = config.pingIntervalMs
    private var lastServerMessageAt = 0L

    // Match stream state (§1.4).
    private var lastSeq = 0L
    private var awaitingSnapshot = false
    private var ignoredMatchId: String? = null
    private var active: ActiveDuel? = null
    private var activeLoaded = false

    /** The running match this client follows, or null (searching, finished, idle). */
    val activeMatchId: String? get() = active?.matchId

    /** Players of the current or last match (from `match.found` / `match.state` / the store). */
    var lastPlayers: ActiveDuel? = null
        private set

    /** Server time now in epoch ms. */
    fun serverNowMs(): Long = clock.serverNow(monotonicClock.elapsedMs())

    /** Opens the connection unless one is open or being opened. */
    fun connect() {
        if (runJob?.isActive == true) return
        closedByApp = false
        fatal = null
        runJob = scope.launch { run() }
    }

    /** Sends [message]; false when there is no open socket (nothing was sent). */
    suspend fun send(message: DuelClientMessage): Boolean {
        val open = socket ?: return false
        if (_connection.value != DuelConnectionState.Connected) return false
        return open.send(message)
    }

    /** "Yenidən qoşul" or back in the foreground: skip the backoff pause (DUEL-13, DUEL-16). */
    fun reconnectNow() {
        when (_connection.value) {
            is DuelConnectionState.Reconnecting -> kick.trySend(Unit)
            DuelConnectionState.Idle, is DuelConnectionState.Lost, is DuelConnectionState.Failed -> connect()
            else -> Unit
        }
    }

    /**
     * DUEL-15: leaves the match on purpose (forfeit). Sent when connected; otherwise the match is
     * remembered as left and left automatically on the next connection (OQ-058). Either way the
     * saved match is cleared and the socket closed.
     */
    suspend fun leaveMatch(matchId: String) {
        val sent = send(DuelClientMessage.MatchLeave(matchId))
        if (!sent) store.setLeftMatchId(matchId)
        ignoredMatchId = matchId
        setActive(null)
        close()
    }

    /** Closes the socket normally (1000) and stops reconnecting. */
    suspend fun close() {
        closedByApp = true
        val open = socket
        socket = null
        runJob?.cancel()
        runJob = null
        open?.close(DuelSocket.NORMAL_CLOSE)
        _connection.value = DuelConnectionState.Idle
    }

    private suspend fun run() {
        loadActive()
        var attempt = 0
        var since = 0L
        while (scope.isActive && !closedByApp) {
            _connection.value = if (attempt == 0) {
                DuelConnectionState.Connecting
            } else {
                DuelConnectionState.Reconnecting(since, attempt)
            }
            when (val result = socketFactory.open()) {
                is DuelOpenResult.Failed -> {
                    if (active == null || result.error == DuelConnectError.UNAUTHORIZED) {
                        _connection.value = DuelConnectionState.Failed(result.error)
                        return
                    }
                    if (attempt == 0) since = monotonicClock.elapsedMs()
                }
                is DuelOpenResult.Opened -> {
                    val closeCode = session(result.socket)
                    if (closedByApp) return
                    fatal?.let { _connection.value = it; return }
                    if (DuelCloseCode.isFatal(closeCode)) {
                        _connection.value = if (closeCode == DuelCloseCode.UPGRADE_REQUIRED) {
                            DuelConnectionState.UpgradeRequired
                        } else {
                            DuelConnectionState.SessionReplaced
                        }
                        return
                    }
                    if (active == null) {
                        _connection.value = DuelConnectionState.Lost(closeCode)
                        return
                    }
                    if (attempt == 0 || _connection.value == DuelConnectionState.Connected) {
                        since = monotonicClock.elapsedMs()
                        attempt = 0
                    }
                }
            }
            attempt++
            _connection.value = DuelConnectionState.Reconnecting(since, attempt)
            // First retry at once, then about every second (DUEL-13).
            if (attempt > 1) withTimeoutOrNull(config.reconnectDelayMs) { kick.receive() }
        }
    }

    /** Runs one open socket until it closes; returns the close code (null = broken). */
    private suspend fun session(open: DuelSocket): Int? = coroutineScope {
        socket = open
        lastServerMessageAt = monotonicClock.elapsedMs()
        val heartbeat = launch {
            while (isActive) {
                open.send(DuelClientMessage.Ping)
                delay(heartbeatMs)
            }
        }
        val watchdog = launch {
            while (isActive) {
                delay(WATCHDOG_TICK_MS)
                if (monotonicClock.elapsedMs() - lastServerMessageAt >= config.silenceTimeoutMs) {
                    Log.i(TAG, "No server message for ${config.silenceTimeoutMs} ms; dropping the socket")
                    open.close(DuelSocket.NORMAL_CLOSE)
                    break
                }
            }
        }
        var code: Int? = null
        try {
            for (inbound in open.incoming) {
                when (inbound) {
                    is DuelInbound.Message -> {
                        lastServerMessageAt = monotonicClock.elapsedMs()
                        handle(inbound.message)
                    }
                    DuelInbound.Malformed -> Log.w(TAG, "Malformed duel frame ignored")
                    is DuelInbound.Closed -> {
                        code = inbound.code
                        break
                    }
                }
            }
        } finally {
            heartbeat.cancel()
            watchdog.cancel()
            if (socket === open) socket = null
            // Also when the owning scope is cancelled (the duel screen is gone): close with 1000.
            withContext(NonCancellable) { open.close(DuelSocket.NORMAL_CLOSE) }
        }
        code
    }

    private suspend fun handle(message: DuelServerMessage) {
        clock.sample(message.serverNowEpochMs, monotonicClock.elapsedMs())
        when (message) {
            is DuelServerMessage.Hello -> onHello(message)
            is DuelServerMessage.Pong -> Unit
            is DuelServerMessage.Unknown -> Log.i(TAG, "Unknown duel message type ignored: ${message.type}")
            is DuelServerMessage.Error -> onError(message)
            is DuelServerMessage.Stream -> onStream(message)
            else -> _messages.send(message)
        }
    }

    private suspend fun onHello(hello: DuelServerMessage.Hello) {
        heartbeatMs = hello.heartbeatIntervalMs.takeIf { it > 0 } ?: config.pingIntervalMs
        _connection.value = DuelConnectionState.Connected
        val left = store.leftMatchId()
        val serverMatch = hello.activeMatchId
        val saved = active
        when {
            serverMatch != null && serverMatch == left -> leaveForgotten(serverMatch)
            serverMatch != null -> {
                // The server's value wins over the saved one (OQ-071); match.state follows by itself.
                if (saved?.matchId != serverMatch) setActive(ActiveDuel(serverMatch, null, null))
                awaitingSnapshot = true
            }
            saved != null -> {
                // Process death or a reconnect after the match ended or the server restarted.
                awaitingSnapshot = true
                send(DuelClientMessage.MatchRejoin(saved.matchId))
            }
        }
        _messages.send(hello)
    }

    private suspend fun onError(error: DuelServerMessage.Error) {
        when (error.code) {
            DuelErrorCode.UPGRADE_REQUIRED -> fatal = DuelConnectionState.UpgradeRequired
            DuelErrorCode.SESSION_REPLACED -> fatal = DuelConnectionState.SessionReplaced
            DuelErrorCode.MATCH_NOT_FOUND -> if (error.matchId == null || error.matchId == active?.matchId) {
                awaitingSnapshot = false
                lastSeq = 0
                setActive(null)
            }
            DuelErrorCode.ALREADY_IN_MATCH -> {
                val matchId = error.matchId ?: return _messages.send(error)
                if (matchId == store.leftMatchId()) {
                    // OQ-058: the user already confirmed leaving it; leave instead of rejoining and
                    // keep searching.
                    leaveForgotten(matchId)
                    if (error.ref == DuelClientMessage.QueueJoin.type) send(DuelClientMessage.QueueJoin)
                    return
                }
                if (active?.matchId != matchId) setActive(ActiveDuel(matchId, null, null))
                awaitingSnapshot = true
                // queue.join: match.state follows by itself. A late queue.leave (OQ-073): ask for it.
                if (error.ref != DuelClientMessage.QueueJoin.type) send(DuelClientMessage.MatchRejoin(matchId))
            }
            else -> Unit
        }
        _messages.send(error)
    }

    private suspend fun leaveForgotten(matchId: String) {
        send(DuelClientMessage.MatchLeave(matchId))
        store.setLeftMatchId(null)
        ignoredMatchId = matchId
        if (active?.matchId == matchId) setActive(null)
        awaitingSnapshot = false
    }

    private suspend fun onStream(message: DuelServerMessage.Stream) {
        val matchId = message.streamMatchId ?: return
        if (matchId == ignoredMatchId) return
        when (message) {
            is DuelServerMessage.MatchFound -> {
                // A repeated match.found of the running match is a duplicate.
                if (active?.matchId == matchId && lastSeq >= message.seq) return
                lastSeq = message.seq
                awaitingSnapshot = false
                setActive(ActiveDuel(matchId, message.you, message.opponent, message.opponentType))
            }
            is DuelServerMessage.MatchState -> {
                lastSeq = message.seq
                awaitingSnapshot = false
                setActive(
                    ActiveDuel(matchId, message.snapshot.you, message.snapshot.opponent, message.snapshot.opponentType),
                )
            }
            is DuelServerMessage.MatchResultMessage -> {
                val current = active?.matchId
                if (current != null && current != matchId) return
                lastSeq = message.seq
                awaitingSnapshot = false
                setActive(null)
            }
            else -> if (!acceptInOrder(message, matchId)) return
        }
        _messages.send(message)
    }

    /** §1.4 for ordinary stream messages; a gap triggers `match.rejoin`. */
    private suspend fun acceptInOrder(message: DuelServerMessage.Stream, matchId: String): Boolean {
        val current = active?.matchId
        val expected = lastSeq + 1
        return when {
            current != matchId || awaitingSnapshot || message.seq < expected -> false
            message.seq > expected -> {
                Log.i(TAG, "seq gap: expected $expected, got ${message.seq}; rejoining")
                awaitingSnapshot = true
                send(DuelClientMessage.MatchRejoin(matchId))
                false
            }
            else -> {
                lastSeq = message.seq
                if (message is DuelServerMessage.MatchCancelled) {
                    lastSeq = 0
                    setActive(null)
                }
                true
            }
        }
    }

    private suspend fun loadActive() {
        if (activeLoaded) return
        activeLoaded = true
        active = store.activeDuel()
        if (active != null) lastPlayers = active
    }

    private suspend fun setActive(duel: ActiveDuel?) {
        activeLoaded = true
        if (duel == active) return
        val merged = if (duel != null && duel.you == null && duel.matchId == lastPlayers?.matchId) lastPlayers else duel
        active = merged
        if (merged?.you != null) lastPlayers = merged
        store.setActiveDuel(merged)
    }

    private companion object {
        const val TAG = "DuelClient"
        const val WATCHDOG_TICK_MS = 1_000L
    }
}

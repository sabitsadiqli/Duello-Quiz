package com.sabit.duelloquiz.backend.duel

import com.sabit.duelloquiz.backend.image.MediaUrls
import com.sabit.duelloquiz.backend.user.AccountDeletionListener
import com.sabit.duelloquiz.backend.user.CalibrationLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Owner of duel connections, the matchmaking queue and running matches
 * (docs/api/duel-protocol.md). One instance per server; registry state is guarded by
 * [mutex], match state lives in each [DuelMatch] coroutine.
 */
class DuelService(
    private val scope: CoroutineScope,
    private val config: DuelConfig,
    private val clock: DuelClock,
    private val store: DuelStore,
    private val queue: MatchmakingQueue,
    private val mediaUrls: MediaUrls,
) : MatchListener, AccountDeletionListener {
    private val log = LoggerFactory.getLogger(DuelService::class.java)
    private val mutex = Mutex()
    private val sessions = HashMap<UUID, DuelConnection>()
    private val matchByUser = HashMap<UUID, DuelMatch>()
    private val ended = LinkedHashMap<UUID, EndedMatch>()

    private class EndedMatch(val endedAtMs: Long, val results: Map<UUID, Outgoing>)

    /** Matches still `active` in storage were lost by a restart: abandoned (OQ-033). */
    suspend fun abandonLostMatches() {
        val count = store.abandonActiveMatches(clock.nowMs())
        if (count > 0) log.warn("duel_matches_abandoned_on_startup count={}", count)
    }

    /** A new socket of [userId]; the newest connection wins (DUEL-04). */
    suspend fun connect(userId: UUID, connection: DuelConnection) = mutex.withLock {
        val old = sessions.put(userId, connection)
        if (old != null && old !== connection) {
            old.send(out(ServerMessage.Error(DuelErrorCodes.SESSION_REPLACED, "A newer connection of this user opened")))
            old.close(DuelCloseCodes.SESSION_REPLACED, DuelErrorCodes.SESSION_REPLACED)
            queue.leave(userId)
            log.info("duel_session_replaced userId={}", userId)
        }
        val match = matchByUser[userId]
        connection.send(out(ServerMessage.Hello(config.protocolVersion, config.heartbeatIntervalMs, match?.id?.toString())))
        match?.submit(MatchEvent.Attach(userId, connection))
        Unit
    }

    /** The socket closed or timed out. A replaced connection changes nothing. */
    suspend fun disconnect(userId: UUID, connection: DuelConnection) = mutex.withLock {
        if (sessions[userId] !== connection) return@withLock
        sessions.remove(userId)
        queue.leave(userId)
        matchByUser[userId]?.submit(MatchEvent.Detach(userId, connection))
        Unit
    }

    /** Handles one parsed client message received at [receivedAtMs] (server clock). */
    suspend fun handle(userId: UUID, connection: DuelConnection, message: ClientMessage, receivedAtMs: Long) {
        if (mutex.withLock { sessions[userId] !== connection }) return
        when (message) {
            ClientMessage.Ping -> connection.send(out(ServerMessage.Pong))
            ClientMessage.QueueJoin -> joinQueue(userId, connection)
            ClientMessage.QueueLeave -> leaveQueue(userId, connection)
            is ClientMessage.MatchRejoin -> rejoin(userId, connection, message.matchId)
            is ClientMessage.MatchLeave -> withMatch(userId, connection, message.matchId, null, "match.leave") {
                it.submit(MatchEvent.Leave(userId))
            }
            is ClientMessage.RoundBet -> withMatch(userId, connection, message.matchId, message.roundNo, "round.bet") {
                it.submit(MatchEvent.Bet(userId, message.roundNo, message.bet, receivedAtMs, connection))
            }
            is ClientMessage.RoundAnswer -> withMatch(userId, connection, message.matchId, message.roundNo, "round.answer") {
                it.submit(MatchEvent.Answer(userId, message.roundNo, message.optionId, receivedAtMs, connection))
            }
        }
    }

    private suspend fun withMatch(
        userId: UUID,
        connection: DuelConnection,
        rawMatchId: String,
        roundNo: Int?,
        ref: String,
        block: (DuelMatch) -> Boolean,
    ) {
        val matchId = parseUuid(rawMatchId)
        val match = mutex.withLock { matchByUser[userId] }?.takeIf { it.id == matchId }
        if (match == null || !block(match)) {
            connection.send(out(ServerMessage.Error(DuelErrorCodes.MATCH_NOT_FOUND, "No such running match", rawMatchId, roundNo, ref)))
        }
    }

    // -----------------------------------------------------------------------
    // Queue
    // -----------------------------------------------------------------------

    private suspend fun joinQueue(userId: UUID, connection: DuelConnection) {
        if (alreadyQueuedOrPlaying(userId, connection)) return
        val available = try {
            store.activeQuestionCount() >= config.questionsPerMatch
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("duel_question_count_failed", e)
            connection.send(out(ServerMessage.Error(DuelErrorCodes.INTERNAL_ERROR, "Unexpected server error", ref = "queue.join")))
            return
        }
        if (!available) {
            connection.send(out(ServerMessage.Error(DuelErrorCodes.DUEL_UNAVAILABLE, "Not enough active questions", ref = "queue.join")))
            return
        }
        mutex.withLock {
            if (sessions[userId] !== connection) return
            if (matchByUser.containsKey(userId) || queue.entry(userId) != null) {
                // Raced with another join of the same connection: answer like the first one.
                queue.entry(userId)?.let { connection.send(out(ServerMessage.QueueJoined(it.deadlineMs))) }
                return
            }
            val entry = queue.join(userId, clock.nowMs(), config.queueTimeoutMs)
            connection.send(out(ServerMessage.QueueJoined(entry.deadlineMs)))
            scheduleQueueTimeout(entry)
            log.info("duel_queue_join userId={}", userId)
            tryPair()
        }
    }

    private suspend fun alreadyQueuedOrPlaying(userId: UUID, connection: DuelConnection): Boolean = mutex.withLock {
        val match = matchByUser[userId]
        if (match != null) {
            connection.send(out(ServerMessage.Error(DuelErrorCodes.ALREADY_IN_MATCH, "User is in a running match", match.id.toString(), ref = "queue.join")))
            match.submit(MatchEvent.Attach(userId, connection))
            return@withLock true
        }
        val entry = queue.entry(userId) ?: return@withLock false
        connection.send(out(ServerMessage.QueueJoined(entry.deadlineMs)))
        true
    }

    private suspend fun leaveQueue(userId: UUID, connection: DuelConnection) = mutex.withLock {
        if (queue.leave(userId) != null) {
            log.info("duel_queue_leave userId={}", userId)
            connection.send(out(ServerMessage.QueueLeft))
            return@withLock
        }
        val match = matchByUser[userId]
        if (match == null || !match.submit(MatchEvent.QueueLeave(userId, connection))) {
            connection.send(out(ServerMessage.QueueLeft))
        }
    }

    private fun scheduleQueueTimeout(entry: QueueEntry) {
        scope.launch {
            delay((entry.deadlineMs - clock.nowMs()).coerceAtLeast(0))
            mutex.withLock {
                if (queue.entry(entry.userId)?.ticket != entry.ticket) return@withLock
                queue.leave(entry.userId)
                sessions[entry.userId]?.send(out(ServerMessage.QueueTimeout))
                log.info("duel_queue_timeout userId={}", entry.userId)
            }
        }
    }

    /** Pairs the two oldest entries; must run under [mutex]. */
    private suspend fun tryPair() {
        while (true) {
            val (first, second) = queue.pollPair() ?: return
            val a = first.userId
            val b = second.userId
            try {
                val profiles = store.profiles(listOf(a, b))
                val questions = store.pickQuestions(config.questionsPerMatch)
                if (questions.size < config.questionsPerMatch || profiles.size < 2) {
                    listOf(a, b).forEach {
                        sessions[it]?.send(out(ServerMessage.Error(DuelErrorCodes.DUEL_UNAVAILABLE, "Not enough active questions", ref = "queue.join")))
                    }
                    continue
                }
                val matchId = UUID.randomUUID()
                store.createMatch(matchId, a, b, clock.nowMs())
                val match = DuelMatch(
                    id = matchId,
                    playerA = profiles.getValue(a),
                    playerB = profiles.getValue(b),
                    connectionA = sessions[a],
                    connectionB = sessions[b],
                    questions = questions.take(config.questionsPerMatch),
                    config = config,
                    clock = clock,
                    store = store,
                    mediaUrls = mediaUrls,
                    listener = this,
                )
                matchByUser[a] = match
                matchByUser[b] = match
                match.start(scope)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("duel_match_create_failed playerA={} playerB={}", a, b, e)
                listOf(a, b).forEach {
                    sessions[it]?.send(out(ServerMessage.Error(DuelErrorCodes.INTERNAL_ERROR, "Unexpected server error", ref = "queue.join")))
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Rejoin
    // -----------------------------------------------------------------------

    private suspend fun rejoin(userId: UUID, connection: DuelConnection, rawMatchId: String) {
        val matchId = parseUuid(rawMatchId)
        val notFound = out(ServerMessage.Error(DuelErrorCodes.MATCH_NOT_FOUND, "No such match", rawMatchId, ref = "match.rejoin"))
        if (matchId == null) return connection.send(notFound)
        val handled = mutex.withLock {
            val running = matchByUser[userId]?.takeIf { it.id == matchId }
            if (running != null && running.submit(MatchEvent.Attach(userId, connection))) return@withLock true
            val result = ended[matchId]?.results?.get(userId) ?: running?.results()?.get(userId)
            result?.let(connection::send)
            result != null
        }
        if (handled) return
        val stored = try {
            store.findMatch(matchId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("duel_match_load_failed matchId={}", matchId, e)
            null
        }
        val side = when (userId) {
            stored?.playerA -> Side.A
            stored?.playerB -> Side.B
            else -> null
        }
        // Only finished matches can be shown again; abandoned/cancelled/lost ones are "not found" (DUEL-13).
        if (stored == null || side == null || stored.status != MatchStatus.FINISHED) return connection.send(notFound)
        val profiles = try {
            store.profiles(listOfNotNull(stored.playerA, stored.playerB))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("duel_match_load_failed matchId={}", matchId, e)
            return connection.send(out(ServerMessage.Error(DuelErrorCodes.INTERNAL_ERROR, "Unexpected server error", rawMatchId, ref = "match.rejoin")))
        }
        connection.send(Outgoing(0, clock.nowMs(), storedResult(stored, side, profiles)))
    }

    private fun storedResult(m: StoredMatch, side: Side, profiles: Map<UUID, PlayerProfile>): ServerMessage.MatchResult {
        val own = if (side == Side.A) m.scoreA else m.scoreB
        val other = if (side == Side.A) m.scoreB else m.scoreA
        val r = m.ratings
        val before = r?.let { if (side == Side.A) it.aBefore else it.bBefore } ?: 0
        val after = r?.let { if (side == Side.A) it.aAfter else it.bAfter } ?: before
        val opponentBefore = r?.let { if (side == Side.A) it.bBefore else it.aBefore } ?: 0
        val opponentAfter = r?.let { if (side == Side.A) it.bAfter else it.aAfter } ?: opponentBefore
        fun player(userId: UUID?, before: Int, after: Int): ResultPlayer {
            val profile = userId?.let { profiles[it] }
            return ResultPlayer(profile?.displayName, profile?.avatarUrl, before, after, deleted = profile == null)
        }
        val counted = m.rounds
            .filter { !it.suddenDeath && it.roundNo <= config.regularRounds }
            .mapNotNull { it.answers[side] }
            .filter { !it.betDefaulted && !it.missed }
        val empty = RoundSummarySide(1, false, false, 0, false)
        fun AnswerRow.summary() = RoundSummarySide(bet, betDefaulted, isCorrect, points, missed)
        return ServerMessage.MatchResult(
            matchId = m.id.toString(),
            outcome = when (m.winnerSide) {
                null -> "draw"
                side -> "win"
                else -> "loss"
            },
            endReason = m.endReason ?: EndReasons.NORMAL,
            forfeitedBy = m.forfeitedSide?.let { if (it == side) Who.SELF else Who.OPPONENT },
            forfeitCause = m.forfeitCause,
            scores = Scores(own, other),
            ratingBefore = before,
            ratingAfter = after,
            ratingChange = after - before,
            opponentRatingAfter = opponentAfter,
            suddenDeathPlayed = m.rounds.count { it.suddenDeath },
            calibration = (1..3).map { level ->
                val atLevel = counted.filter { it.bet == level }
                CalibrationLevel(level, atLevel.size, atLevel.count { it.isCorrect })
            },
            rounds = m.rounds.map {
                RoundSummary(it.roundNo, it.suddenDeath, it.answers[side]?.summary() ?: empty, it.answers[side.other]?.summary() ?: empty)
            },
            you = player(if (side == Side.A) m.playerA else m.playerB, before, after),
            opponent = player(if (side == Side.A) m.playerB else m.playerA, opponentBefore, opponentAfter),
            progress = m.progress[side],
        )
    }

    // -----------------------------------------------------------------------
    // Match callbacks
    // -----------------------------------------------------------------------

    override suspend fun onMatchEnded(match: DuelMatch) = mutex.withLock {
        match.userIds.forEach { if (matchByUser[it] === match) matchByUser.remove(it) }
        val now = clock.nowMs()
        ended[match.id] = EndedMatch(now, match.results())
        ended.entries.removeIf { now - it.value.endedAtMs > config.resultRetentionMs }
        Unit
    }

    override suspend fun onMatchCancelled(match: DuelMatch, leaver: UUID, other: UUID) = mutex.withLock {
        match.userIds.forEach { if (matchByUser[it] === match) matchByUser.remove(it) }
        val connection = sessions[other] ?: return@withLock
        // OQ-037: the opponent goes back to the front of the queue with a new deadline.
        val entry = queue.joinFront(other, clock.nowMs(), config.queueTimeoutMs)
        connection.send(out(ServerMessage.QueueJoined(entry.deadlineMs)))
        scheduleQueueTimeout(entry)
        tryPair()
    }

    /** DELETE /v1/me: leave the queue, forfeit a running match, close the socket. */
    override suspend fun beforeAccountDeletion(userId: UUID) {
        val match = mutex.withLock {
            queue.leave(userId)
            matchByUser[userId]
        }
        if (match != null && match.submit(MatchEvent.Leave(userId))) {
            withTimeoutOrNull(ACCOUNT_DELETION_WAIT_MS) { match.awaitEnd() }
        }
        mutex.withLock { sessions.remove(userId) }?.close(1000, "account_deleted")
    }

    suspend fun activeMatchOf(userId: UUID): UUID? = mutex.withLock { matchByUser[userId]?.id }

    private fun out(message: ServerMessage) = Outgoing(0, clock.nowMs(), message)

    private fun parseUuid(raw: String): UUID? = runCatching { UUID.fromString(raw) }.getOrNull()

    private companion object {
        const val ACCOUNT_DELETION_WAIT_MS = 5_000L
    }
}

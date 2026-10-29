package com.sabit.duelloquiz.backend.duel

import com.sabit.duelloquiz.backend.common.resolve
import com.sabit.duelloquiz.backend.image.MediaUrls
import com.sabit.duelloquiz.backend.quiz.ImageDto
import com.sabit.duelloquiz.backend.quiz.OptionDto
import com.sabit.duelloquiz.backend.user.CalibrationLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import org.slf4j.LoggerFactory
import java.util.UUID

/** Server time source of the duel; virtual in tests (`runTest`). */
fun interface DuelClock {
    fun nowMs(): Long

    companion object {
        val System = DuelClock { java.lang.System.currentTimeMillis() }
    }
}

/** One player's socket as seen by the engine. [send] never blocks. */
interface DuelConnection {
    val language: String
    fun send(message: Outgoing)
    fun close(code: Short, reason: String)
}

/** Callbacks from a match to its owner ([DuelService]). */
interface MatchListener {
    suspend fun onMatchEnded(match: DuelMatch)
    suspend fun onMatchCancelled(match: DuelMatch, leaver: UUID, other: UUID)
}

/** Input of a match actor. Times are server receive times. */
sealed interface MatchEvent {
    val userId: UUID

    data class Bet(override val userId: UUID, val roundNo: Int, val bet: Int?, val receivedAtMs: Long, val connection: DuelConnection) : MatchEvent
    data class Answer(override val userId: UUID, val roundNo: Int, val optionId: String, val receivedAtMs: Long, val connection: DuelConnection) : MatchEvent
    data class Leave(override val userId: UUID) : MatchEvent
    data class QueueLeave(override val userId: UUID, val connection: DuelConnection) : MatchEvent
    data class Attach(override val userId: UUID, val connection: DuelConnection) : MatchEvent
    data class Detach(override val userId: UUID, val connection: DuelConnection) : MatchEvent
}

/**
 * Server-authoritative state machine of one match, running in its own coroutine:
 * WAITING (intro) → QUESTION → CONFIDENCE → ANSWER → RESULT → next round | sudden
 * death | FINISHED. All state is owned by the coroutine; other code talks to it only
 * through [submit]. Rules: docs/api/duel-protocol.md §7.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DuelMatch(
    val id: UUID,
    playerA: PlayerProfile,
    playerB: PlayerProfile,
    connectionA: DuelConnection?,
    connectionB: DuelConnection?,
    private val questions: List<DuelQuestion>,
    private val config: DuelConfig,
    private val clock: DuelClock,
    private val store: DuelStore,
    private val mediaUrls: MediaUrls,
    private val listener: MatchListener,
    /** Set for a bot match (§7a): side A is the player, side B ([playerB]) is the bot. */
    private val bot: BotPlayer? = null,
    private val botIdentity: BotIdentity = BotIdentity.DEFAULT,
) {
    enum class Phase { WAITING, QUESTION, CONFIDENCE, ANSWER, RESULT, FINISHED }

    private val log = LoggerFactory.getLogger(DuelMatch::class.java)
    private val inbox = Channel<MatchEvent>(Channel.UNLIMITED)
    private val seats = mapOf(
        Side.A to Seat(Side.A, playerA, connectionA, isBot = false),
        Side.B to Seat(Side.B, playerB, if (bot == null) connectionB else null, isBot = bot != null),
    )

    /** The bot's next move, run by [waitUntil] at its server time. */
    private class BotAction(val atMs: Long, val run: () -> Unit)
    private var botAction: BotAction? = null
    private val botRounds = mutableListOf<BotRoundPlan>()
    private val completed = mutableListOf<CompletedRound>()
    private var current: RoundState? = null
    private var introDeadline = 0L
    private var over = false
    private val ended = CompletableDeferred<Unit>()

    init {
        require(questions.size >= config.questionsPerMatch) { "A match needs ${config.questionsPerMatch} questions" }
        require(questions.map { it.id }.toSet().size == questions.size) { "Questions must be distinct" }
    }

    val userIds: List<UUID> get() = seats.values.filterNot { it.isBot }.map { it.profile.userId }

    val isBotMatch: Boolean get() = bot != null

    private val opponentType: String get() = if (bot != null) OpponentTypes.BOT else OpponentTypes.HUMAN

    /** Delivers an event to the match; false once the match has ended. */
    fun submit(event: MatchEvent): Boolean = inbox.trySend(event).isSuccess

    fun start(scope: CoroutineScope): Job = scope.launch { run() }

    suspend fun awaitEnd() = ended.await()

    /** The `match.result` per user once the match has ended (for rejoin). */
    fun results(): Map<UUID, Outgoing> =
        seats.values.mapNotNull { seat -> seat.result?.let { seat.profile.userId to it } }.toMap()

    // -----------------------------------------------------------------------
    // Main loop
    // -----------------------------------------------------------------------

    private suspend fun run() {
        try {
            introDeadline = now() + config.introMs
            // A player without a socket at the start is already "disconnected" (the bot never is).
            seats.values.filter { it.connection == null && !it.isBot }.forEach { it.reconnectDeadline = now() + config.reconnectWindowMs }
            seats.values.forEach { seat ->
                val opponent = seats.getValue(seat.side.other)
                emit(
                    seat,
                    ServerMessage.MatchFound(
                        matchId = id.toString(),
                        you = seat.card(seat.language),
                        opponent = opponent.card(seat.language),
                        rounds = config.regularRounds,
                        bet3Limit = config.maxBet3PerMatch,
                        deadlineEpochMs = introDeadline,
                        opponentType = opponentType,
                    ),
                )
            }
            if (bot != null) {
                log.info(
                    "duel_match_started matchId={} playerA={} opponentType=bot playerRating={} botForm={}",
                    id, seats.getValue(Side.A).profile.userId, bot.playerRating, bot.form,
                )
            } else {
                log.info("duel_match_started matchId={} playerA={} playerB={}", id, seats.getValue(Side.A).profile.userId, seats.getValue(Side.B).profile.userId)
            }
            waitUntil(introDeadline) { over }
            var roundNo = 0
            while (!over) {
                roundNo++
                playRound(roundNo, suddenDeathNo = (roundNo - config.regularRounds).takeIf { it > 0 })
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("duel_match_failed matchId={}", id, e)
            if (!over) finish(MatchStatus.ABANDONED, EndReasons.ABANDONED, winner = null, forfeited = null, cause = null)
        } finally {
            inbox.close()
            // Late events: a rejoin still gets the result, everything else is dropped.
            while (true) {
                val event = inbox.tryReceive().getOrNull() ?: break
                if (event is MatchEvent.Attach) seats.values.firstOrNull { it.profile.userId == event.userId }?.result?.let(event.connection::send)
            }
            ended.complete(Unit)
        }
    }

    private suspend fun playRound(roundNo: Int, suddenDeathNo: Int?) {
        val question = questions[roundNo - 1]
        val round = RoundState(roundNo, suddenDeathNo, question, allIn = suddenDeathNo == null && roundNo == config.regularRounds)
        current = round
        // The bot decides at the start of the round, before the player can do anything (DUEL-25).
        val botSeat = seats.values.firstOrNull { it.isBot }
        val botPlan = botSeat?.let { bot!!.plan(roundNo, question, round.allIn, round.suddenDeath, it.bet3Left()) }
        if (botPlan != null) {
            botRounds += botPlan
            log.info(
                "duel_bot_round matchId={} roundNo={} difficulty={} p={} knows={} bet={}",
                id, roundNo, question.difficulty, "%.3f".format(java.util.Locale.ROOT, botPlan.p), botPlan.knows, botPlan.bet,
            )
        }

        // QUESTION: text only.
        round.phase = Phase.QUESTION
        round.deadline = now() + config.questionMs
        seats.values.forEach { seat ->
            emit(
                seat,
                ServerMessage.RoundQuestion(
                    matchId = id.toString(),
                    roundNo = roundNo,
                    allIn = round.allIn,
                    suddenDeath = round.suddenDeath,
                    suddenDeathNo = suddenDeathNo,
                    text = question.text.resolve(seat.language),
                    image = imageDto(question, seat.language),
                    categoryName = question.categoryName.resolve(seat.language),
                    deadlineEpochMs = round.deadline,
                ),
            )
        }
        waitUntil(round.deadline) { over }
        if (over) return

        // CONFIDENCE: regular rounds only; ends early when both locked (OQ-035).
        if (!round.suddenDeath) {
            round.phase = Phase.CONFIDENCE
            round.deadline = now() + config.confidenceMs
            seats.values.forEach { seat ->
                emit(
                    seat,
                    ServerMessage.RoundConfidence(id.toString(), roundNo, round.allIn, seat.bet3Left(), round.deadline),
                )
            }
            if (botSeat != null && botPlan?.bet != null) {
                val bet = botPlan.bet
                botAction = BotAction(round.deadline - config.confidenceMs + botPlan.pickDelayMs!!.coerceAtMost(config.confidenceMs)) {
                    if (round.phase == Phase.CONFIDENCE && round.players.getValue(botSeat.side).bet == null) {
                        lockBet(botSeat, round, bet, now())
                    }
                }
            }
            waitUntil(lateFrom(round.deadline)) { over || round.players.values.all { it.bet != null } }
            botAction = null
            if (over) return
            round.players.values.filter { it.bet == null }.forEach {
                it.bet = 1
                it.betDefaulted = true
            }
        } else {
            round.players.values.forEach { it.bet = 1 }
        }

        // ANSWER: options to both players at the same server time.
        round.phase = Phase.ANSWER
        val sentAt = now()
        round.optionsSentAt = sentAt
        round.deadline = sentAt + config.answerMs
        val options = seats.values.associate { seat -> seat.side to optionDtos(question, seat.language) }
        seats.values.forEach { seat ->
            val own = round.players.getValue(seat.side)
            emit(
                seat,
                ServerMessage.RoundOptions(id.toString(), roundNo, options.getValue(seat.side), own.bet!!, own.betDefaulted, round.deadline),
            )
        }
        if (botSeat != null && botPlan != null) {
            botAction = BotAction(sentAt + botPlan.answerDelayMs.coerceAtMost(config.answerMs)) {
                if (round.phase == Phase.ANSWER && round.players.getValue(botSeat.side).optionId == null) {
                    lockAnswer(botSeat, round, botPlan.optionId, now())
                }
            }
        }
        waitUntil(lateFrom(round.deadline)) { over || round.players.values.all { it.optionId != null } }
        botAction = null
        if (over) return

        resolve(round)
    }

    // -----------------------------------------------------------------------
    // Result
    // -----------------------------------------------------------------------

    private suspend fun resolve(round: RoundState) {
        round.phase = Phase.RESULT
        val correctId = round.question.correctOption.id
        val outcomes = Side.entries.associateWith { side ->
            val seat = seats.getValue(side)
            val p = round.players.getValue(side)
            val bet = p.bet ?: 1
            val answered = p.optionId != null
            val correct = p.optionId == correctId
            val answeredMs = p.answerAt?.let { it - round.optionsSentAt }
            val bonus = if (correct && !round.suddenDeath) {
                DuelRules.speedBonus(p.answerAt!!, round.deadline, config.answerMs, config.maxSpeedBonus)
            } else {
                0
            }
            val points = if (round.suddenDeath) 0 else DuelRules.roundPoints(bet, correct, round.allIn, bonus)
            val activeBet = p.bet != null && !p.betDefaulted && !round.suddenDeath
            val missed = if (round.suddenDeath) !answered else !activeBet && !answered
            seat.score += points
            seat.missedInARow = if (missed) seat.missedInARow + 1 else 0
            Outcome(bet, p.betDefaulted, p.optionId, correct, answeredMs, bonus, points, missed, seat.missedInARow)
        }

        // What happens next.
        val forfeiters = Side.entries.filter { seats.getValue(it).missedInARow >= config.missedRoundsToForfeit }
        var winner: Side? = null
        val next: String
        var endReason: String? = null
        when {
            forfeiters.size == 2 -> { next = NextStep.END; endReason = EndReasons.ABANDONED }
            forfeiters.size == 1 -> { next = NextStep.END; endReason = EndReasons.FORFEIT; winner = forfeiters.single().other }
            !round.suddenDeath && round.roundNo < config.regularRounds -> next = NextStep.ROUND
            !round.suddenDeath -> {
                val a = seats.getValue(Side.A).score
                val b = seats.getValue(Side.B).score
                if (a == b) {
                    next = NextStep.SUDDEN_DEATH
                } else {
                    next = NextStep.END
                    endReason = EndReasons.NORMAL
                    winner = if (a > b) Side.A else Side.B
                }
            }
            else -> {
                winner = suddenDeathWinner(outcomes)
                when {
                    winner != null -> { next = NextStep.END; endReason = EndReasons.SUDDEN_DEATH }
                    round.suddenDeathNo!! < config.maxSuddenDeath -> next = NextStep.SUDDEN_DEATH
                    else -> { next = NextStep.END; endReason = EndReasons.DRAW }
                }
            }
        }

        val resultDeadline = now() + config.resultMs
        seats.values.forEach { seat ->
            val opponent = seats.getValue(seat.side.other)
            val message = ServerMessage.RoundResult(
                matchId = id.toString(),
                roundNo = round.roundNo,
                allIn = round.allIn,
                suddenDeath = round.suddenDeath,
                suddenDeathNo = round.suddenDeathNo,
                correctOptionId = correctId.toString(),
                you = outcomes.getValue(seat.side).toDto(),
                opponent = outcomes.getValue(opponent.side).toDto(),
                scores = Scores(seat.score, opponent.score),
                bet3Left = seat.bet3Left(),
                next = next,
                deadlineEpochMs = resultDeadline,
                suddenDeathOutcome = if (round.suddenDeath) suddenDeathOutcome(outcomes, seat.side) else null,
            )
            round.results[seat.side] = message
            emit(seat, message)
        }
        round.deadline = resultDeadline
        completed += CompletedRound(round.roundNo, round.suddenDeath, outcomes)
        persistRound(round, outcomes)

        when (endReason) {
            null -> waitUntil(resultDeadline) { over }
            EndReasons.ABANDONED -> finish(MatchStatus.ABANDONED, EndReasons.ABANDONED, null, null, null)
            EndReasons.FORFEIT -> finish(MatchStatus.FINISHED, EndReasons.FORFEIT, winner, winner!!.other, FORFEIT_MISSED)
            else -> finish(MatchStatus.FINISHED, endReason, winner, null, null)
        }
    }

    private fun suddenDeathOutcome(outcomes: Map<Side, Outcome>, viewer: Side): String {
        val winner = suddenDeathWinner(outcomes)
        return when {
            winner == viewer -> "you"
            winner != null -> Who.OPPONENT
            outcomes.values.all { it.correct } -> SuddenDeathOutcomes.SAME_TIME
            else -> SuddenDeathOutcomes.NONE
        }
    }

    /** Exactly one correct wins; both correct → earlier receive time wins, equal → none (DUEL-11). */
    private fun suddenDeathWinner(outcomes: Map<Side, Outcome>): Side? {
        val a = outcomes.getValue(Side.A)
        val b = outcomes.getValue(Side.B)
        return when {
            a.correct && !b.correct -> Side.A
            b.correct && !a.correct -> Side.B
            a.correct && b.correct -> when {
                a.answeredMs!! < b.answeredMs!! -> Side.A
                b.answeredMs < a.answeredMs -> Side.B
                else -> null
            }
            else -> null
        }
    }

    private suspend fun persistRound(round: RoundState, outcomes: Map<Side, Outcome>) {
        val record = RoundRecord(
            matchId = id,
            roundNo = round.roundNo,
            questionId = round.question.id,
            allIn = round.allIn,
            suddenDeath = round.suddenDeath,
            optionsSentAtMs = round.optionsSentAt,
            answerDeadlineMs = round.optionsSentAt + config.answerMs,
            answers = Side.entries.map { side ->
                val o = outcomes.getValue(side)
                AnswerRow(
                    userId = seats.getValue(side).profile.userId,
                    side = side,
                    bet = o.bet,
                    betDefaulted = o.betDefaulted,
                    optionId = o.optionId,
                    isCorrect = o.correct,
                    answeredMs = o.answeredMs,
                    speedBonus = o.speedBonus,
                    points = o.points,
                    missed = o.missed,
                )
            },
        )
        try {
            store.saveRound(record)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("duel_round_persist_failed matchId={} roundNo={}", id, round.roundNo, e)
        }
    }

    // -----------------------------------------------------------------------
    // End of match
    // -----------------------------------------------------------------------

    private suspend fun finish(status: MatchStatus, endReason: String, winner: Side?, forfeited: Side?, cause: String?) {
        if (over) return
        over = true
        current?.phase = Phase.FINISHED
        val a = seats.getValue(Side.A)
        val b = seats.getValue(Side.B)
        val end = MatchEnd(id, status, endReason, winner, forfeited, a.score, b.score, now(), cause)
        val endResult = try {
            store.endMatch(end, a.profile.rating, b.profile.rating)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("duel_match_persist_failed matchId={}", id, e)
            MatchEndResult(RatingUpdate(a.profile.rating, a.profile.rating, b.profile.rating, b.profile.rating))
        }
        val ratings = endResult.ratings
        val suddenDeathPlayed = completed.count { it.suddenDeath }
        seats.values.filterNot { it.isBot }.forEach { seat ->
            val opponent = seats.getValue(seat.side.other)
            val (before, after) = if (seat.side == Side.A) ratings.aBefore to ratings.aAfter else ratings.bBefore to ratings.bAfter
            // The bot has no rating (OQ-107).
            val opponentAfter = if (opponent.isBot) null else if (seat.side == Side.A) ratings.bAfter else ratings.aAfter
            val opponentBefore = if (opponent.isBot) null else if (seat.side == Side.A) ratings.bBefore else ratings.aBefore
            seat.result = emit(
                seat,
                ServerMessage.MatchResult(
                    matchId = id.toString(),
                    outcome = when {
                        status == MatchStatus.ABANDONED -> "abandoned"
                        winner == null -> "draw"
                        winner == seat.side -> "win"
                        else -> "loss"
                    },
                    endReason = endReason,
                    forfeitedBy = forfeited?.let { if (it == seat.side) Who.SELF else Who.OPPONENT },
                    forfeitCause = cause,
                    scores = Scores(seat.score, opponent.score),
                    ratingBefore = before,
                    ratingAfter = after,
                    ratingChange = after - before,
                    opponentRatingAfter = opponentAfter,
                    suddenDeathPlayed = suddenDeathPlayed,
                    calibration = calibration(seat.side),
                    rounds = summaries(seat.side),
                    you = ResultPlayer(seat.profile.displayName, seat.profile.avatarUrl, before, after, deleted = false),
                    opponent = ResultPlayer(
                        opponent.displayName(seat.language), opponent.avatarUrl,
                        ratingBefore = opponentBefore,
                        ratingAfter = opponentAfter,
                        deleted = false,
                    ),
                    progress = endResult.progress[seat.side],
                    opponentType = opponentType,
                ),
            )
        }
        log.info(
            "duel_match_ended matchId={} status={} endReason={} winner={} forfeited={} scoreA={} scoreB={} opponentType={}",
            id, status.db, endReason, winner?.db, forfeited?.db, a.score, b.score, opponentType,
        )
        if (bot != null) {
            // Tuning data (DUEL-25): target ≈ 50 % player wins.
            val playerOutcome = when {
                status == MatchStatus.ABANDONED -> "abandoned"
                winner == null -> "draw"
                winner == Side.A -> "win"
                else -> "loss"
            }
            log.info(
                "duel_bot_match_ended matchId={} userId={} playerRating={} botForm={} rounds={} botKnew={} meanP={} playerOutcome={} endReason={} scorePlayer={} scoreBot={}",
                id, a.profile.userId, bot.playerRating, bot.form, botRounds.size, botRounds.count { it.knows },
                "%.3f".format(java.util.Locale.ROOT, if (botRounds.isEmpty()) 0.0 else botRounds.map { it.p }.average()),
                playerOutcome, endReason, a.score, b.score,
            )
        }
        listener.onMatchEnded(this)
    }

    private suspend fun cancel(leaver: Side) {
        if (over) return
        over = true
        val a = seats.getValue(Side.A)
        val b = seats.getValue(Side.B)
        try {
            store.endMatch(MatchEnd(id, MatchStatus.CANCELLED, EndReasons.CANCELLED, null, null, 0, 0, now()), a.profile.rating, b.profile.rating)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("duel_match_persist_failed matchId={}", id, e)
        }
        seats.values.filterNot { it.isBot }.forEach { seat ->
            emit(seat, ServerMessage.MatchCancelled(id.toString(), if (seat.side == leaver) Who.SELF else Who.OPPONENT))
        }
        log.info("duel_match_cancelled matchId={} by={} opponentType={}", id, seats.getValue(leaver).profile.userId, opponentType)
        listener.onMatchCancelled(this, seats.getValue(leaver).profile.userId, seats.getValue(leaver.other).profile.userId)
    }

    private fun calibration(side: Side): List<CalibrationLevel> {
        val counted = completed
            .filter { !it.suddenDeath && it.roundNo <= config.regularRounds }
            .map { it.outcomes.getValue(side) }
            .filter { !it.betDefaulted && !it.missed }
        return (1..3).map { level ->
            val atLevel = counted.filter { it.bet == level }
            CalibrationLevel(level, atLevel.size, atLevel.count { it.correct })
        }
    }

    private fun summaries(side: Side): List<RoundSummary> = completed.map { r ->
        RoundSummary(r.roundNo, r.suddenDeath, r.outcomes.getValue(side).toSummary(), r.outcomes.getValue(side.other).toSummary())
    }

    // -----------------------------------------------------------------------
    // Events
    // -----------------------------------------------------------------------

    /** Waits until [done] or the server time reaches [deadlineMs], handling events meanwhile. */
    private suspend fun waitUntil(deadlineMs: Long, done: () -> Boolean) {
        while (!done()) {
            val action = botAction?.takeIf { it.atMs <= deadlineMs }
            if (action != null && now() >= action.atMs) {
                botAction = null
                // Player events received before the bot's moment are handled first.
                while (true) {
                    val event = inbox.tryReceive().getOrNull() ?: break
                    handle(event)
                }
                if (over) return
                action.run()
                continue
            }
            val remaining = (action?.atMs ?: deadlineMs) - now()
            if (remaining <= 0) {
                // Events already queued were received before this moment; their receive time decides.
                while (true) {
                    val event = inbox.tryReceive().getOrNull() ?: break
                    handle(event)
                }
                return
            }
            select {
                inbox.onReceive { handle(it) }
                onTimeout(remaining) { }
            }
        }
    }

    private suspend fun handle(event: MatchEvent) {
        val seat = seats.values.firstOrNull { it.profile.userId == event.userId } ?: return
        when (event) {
            is MatchEvent.Bet -> onBet(seat, event)
            is MatchEvent.Answer -> onAnswer(seat, event)
            is MatchEvent.Leave -> if (!over) {
                log.info("duel_match_leave matchId={} userId={}", id, seat.profile.userId)
                finish(MatchStatus.FINISHED, EndReasons.FORFEIT, seat.side.other, seat.side, FORFEIT_LEFT)
            }
            is MatchEvent.QueueLeave -> when {
                over -> event.connection.send(out(ServerMessage.QueueLeft))
                current == null -> {
                    cancel(seat.side)
                    event.connection.send(out(ServerMessage.QueueLeft))
                }
                else -> event.connection.send(error(DuelErrorCodes.ALREADY_IN_MATCH, "Match already started; use match.leave", null, "queue.leave"))
            }
            is MatchEvent.Attach -> {
                val wasAway = seat.connection == null
                seat.connection = event.connection
                seat.reconnectDeadline = null
                seat.language = event.connection.language
                if (over) {
                    seat.result?.let(event.connection::send)
                } else {
                    val other = seats.getValue(seat.side.other)
                    if (wasAway && !other.isBot) {
                        emit(other, ServerMessage.OpponentConnection(id.toString(), "connected", null))
                    }
                    emit(seat, snapshot(seat))
                }
            }
            is MatchEvent.Detach -> if (seat.connection === event.connection) {
                seat.connection = null
                val deadline = now() + config.reconnectWindowMs
                seat.reconnectDeadline = deadline
                val other = seats.getValue(seat.side.other)
                if (!over && !other.isBot) {
                    emit(other, ServerMessage.OpponentConnection(id.toString(), "disconnected", deadline))
                }
            }
        }
    }

    private fun onBet(seat: Seat, e: MatchEvent.Bet) {
        val ref = "round.bet"
        fun reject(code: String, message: String) = e.connection.send(error(code, message, e.roundNo, ref))
        if (over) return reject(DuelErrorCodes.MATCH_NOT_FOUND, "Match has ended")
        val round = current
        if (round == null) {
            return if (e.roundNo == 1) reject(DuelErrorCodes.PHASE_NOT_OPEN, "Round has not started") else reject(DuelErrorCodes.STALE_ROUND, "Not the current round")
        }
        if (e.roundNo != round.roundNo) return reject(DuelErrorCodes.STALE_ROUND, "Not the current round")
        if (round.suddenDeath) return reject(DuelErrorCodes.PHASE_NOT_OPEN, "No confidence phase in sudden death")
        val p = round.players.getValue(seat.side)
        if (p.bet != null && !p.betDefaulted) return reject(DuelErrorCodes.BET_ALREADY_LOCKED, "Confidence already locked")
        if (p.betDefaulted) return reject(DuelErrorCodes.TOO_LATE, "Received after the deadline")
        if (round.phase == Phase.QUESTION) return reject(DuelErrorCodes.PHASE_NOT_OPEN, "Confidence phase not open")
        if (e.receivedAtMs > round.deadline + config.graceMs) return reject(DuelErrorCodes.TOO_LATE, "Received after the deadline")
        val bet = e.bet
        if (bet == null || bet !in 1..3) return reject(DuelErrorCodes.INVALID_BET, "Confidence must be 1, 2 or 3")
        if (bet == 3 && seat.bet3Used >= config.maxBet3PerMatch) return reject(DuelErrorCodes.BET_LIMIT_REACHED, "Confidence 3 already used twice")
        lockBet(seat, round, bet, e.receivedAtMs)
    }

    /** Stores a valid pick and tells both players (the opponent without the value). */
    private fun lockBet(seat: Seat, round: RoundState, bet: Int, atMs: Long) {
        check(bet in 1..3 && (bet != 3 || seat.bet3Used < config.maxBet3PerMatch)) { "invalid bet $bet" }
        val p = round.players.getValue(seat.side)
        p.bet = bet
        p.betAt = atMs
        if (bet == 3) seat.bet3Used++
        emit(seat, ServerMessage.RoundBetLocked(id.toString(), round.roundNo, Who.SELF, bet))
        emit(seats.getValue(seat.side.other), ServerMessage.RoundBetLocked(id.toString(), round.roundNo, Who.OPPONENT, null))
    }

    private fun onAnswer(seat: Seat, e: MatchEvent.Answer) {
        val ref = "round.answer"
        fun reject(code: String, message: String) = e.connection.send(error(code, message, e.roundNo, ref))
        if (over) return reject(DuelErrorCodes.MATCH_NOT_FOUND, "Match has ended")
        val round = current
        if (round == null) {
            return if (e.roundNo == 1) reject(DuelErrorCodes.PHASE_NOT_OPEN, "Round has not started") else reject(DuelErrorCodes.STALE_ROUND, "Not the current round")
        }
        if (e.roundNo != round.roundNo) return reject(DuelErrorCodes.STALE_ROUND, "Not the current round")
        if (round.phase == Phase.QUESTION || round.phase == Phase.CONFIDENCE) {
            return reject(DuelErrorCodes.PHASE_NOT_OPEN, "Options not sent yet")
        }
        val p = round.players.getValue(seat.side)
        val optionId = runCatching { UUID.fromString(e.optionId) }.getOrNull()
        if (p.optionId != null) {
            // An identical retry is ignored without an answer (idempotent).
            return if (optionId == p.optionId) Unit else reject(DuelErrorCodes.ALREADY_ANSWERED, "A different answer is stored")
        }
        if (round.phase != Phase.ANSWER || e.receivedAtMs > round.deadline + config.graceMs) {
            return reject(DuelErrorCodes.TOO_LATE, "Received after the deadline")
        }
        if (optionId == null || round.question.options.none { it.id == optionId }) {
            return reject(DuelErrorCodes.INVALID_OPTION, "Option does not belong to this question")
        }
        lockAnswer(seat, round, optionId, e.receivedAtMs)
    }

    /** Stores a valid answer received at [atMs] and tells both players (the opponent without the option). */
    private fun lockAnswer(seat: Seat, round: RoundState, optionId: UUID, atMs: Long) {
        val p = round.players.getValue(seat.side)
        p.optionId = optionId
        p.answerAt = atMs
        emit(seat, ServerMessage.RoundAnswerLocked(id.toString(), round.roundNo, Who.SELF, optionId.toString()))
        emit(seats.getValue(seat.side.other), ServerMessage.RoundAnswerLocked(id.toString(), round.roundNo, Who.OPPONENT, null))
    }

    // -----------------------------------------------------------------------
    // Snapshot
    // -----------------------------------------------------------------------

    private fun snapshot(seat: Seat): ServerMessage.MatchState {
        val opponent = seats.getValue(seat.side.other)
        val round = current
        val own = round?.players?.getValue(seat.side)
        val theirs = round?.players?.getValue(opponent.side)
        val phase = round?.phase ?: Phase.WAITING
        return ServerMessage.MatchState(
            matchId = id.toString(),
            you = seat.card(seat.language),
            opponent = OpponentCard(
                displayName = opponent.displayName(seat.language),
                avatarUrl = opponent.avatarUrl,
                rating = opponent.rating,
                connected = opponent.isBot || opponent.connection != null,
                reconnectDeadlineEpochMs = opponent.reconnectDeadline.takeIf { !opponent.isBot && opponent.connection == null },
            ),
            phase = when (phase) {
                Phase.WAITING -> Phases.INTRO
                Phase.QUESTION -> Phases.QUESTION
                Phase.CONFIDENCE -> Phases.CONFIDENCE
                Phase.ANSWER -> Phases.ANSWER
                Phase.RESULT -> Phases.RESULT
                Phase.FINISHED -> Phases.FINISHED
            },
            deadlineEpochMs = round?.deadline ?: introDeadline,
            roundNo = round?.roundNo ?: 0,
            allIn = round?.allIn ?: false,
            suddenDeath = round?.suddenDeath ?: false,
            suddenDeathNo = round?.suddenDeathNo,
            question = round?.let {
                QuestionView(it.question.text.resolve(seat.language), imageDto(it.question, seat.language), it.question.categoryName.resolve(seat.language))
            },
            options = round?.takeIf { it.phase == Phase.ANSWER || it.phase == Phase.RESULT }?.let { optionDtos(it.question, seat.language) },
            ownBet = own?.bet?.let { OwnBet(it, own.betDefaulted) },
            ownOptionId = own?.optionId?.toString(),
            opponentBetLocked = theirs?.let { it.bet != null && !it.betDefaulted } ?: false,
            opponentAnswered = theirs?.optionId != null,
            bet3Left = seat.bet3Left(),
            scores = Scores(seat.score, opponent.score),
            missedInARow = MissedCounts(seat.missedInARow, opponent.missedInARow),
            lastResult = round?.takeIf { it.phase == Phase.RESULT }?.results?.get(seat.side),
            rounds = summaries(seat.side),
            opponentType = opponentType,
        )
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun now() = clock.nowMs()

    /**
     * First instant at which a pick or answer is too late: received at or before
     * deadline + grace counts (OQ-036), so the phase closes 1 ms after that.
     */
    private fun lateFrom(deadline: Long) = deadline + config.graceMs + 1

    private fun emit(seat: Seat, message: ServerMessage): Outgoing {
        seat.seq++
        val outgoing = Outgoing(seat.seq, now(), message)
        seat.connection?.send(outgoing)
        return outgoing
    }

    private fun out(message: ServerMessage) = Outgoing(0, now(), message)

    private fun error(code: String, message: String, roundNo: Int?, ref: String) =
        out(ServerMessage.Error(code, message, matchId = id.toString(), roundNo = roundNo, ref = ref))

    private fun Seat.bet3Left() = (config.maxBet3PerMatch - bet3Used).coerceAtLeast(0)

    private fun Seat.card(viewerLanguage: String) = PlayerCard(displayName(viewerLanguage), avatarUrl, rating)

    /** The bot's name is backend i18n in the viewer's language (OQ-107). */
    private fun Seat.displayName(viewerLanguage: String) = if (isBot) botIdentity.displayName(viewerLanguage) else profile.displayName

    private val Seat.avatarUrl: String? get() = if (isBot) null else profile.avatarUrl

    private val Seat.rating: Int? get() = if (isBot) null else profile.rating

    private fun optionDtos(question: DuelQuestion, language: String) =
        question.options.map { OptionDto(it.id.toString(), it.text.resolve(language)) }

    private fun imageDto(question: DuelQuestion, language: String): ImageDto? = question.image?.let {
        ImageDto(
            url = mediaUrls.url(it.storageKey),
            thumbUrl = mediaUrls.url(it.thumbKey),
            width = it.width,
            height = it.height,
            dominantColor = it.dominantColor,
            alt = it.alt?.resolve(language),
        )
    }

    private class Seat(val side: Side, val profile: PlayerProfile, var connection: DuelConnection?, val isBot: Boolean) {
        var language: String = connection?.language ?: "az"
        var seq = 0L
        var score = 0
        var bet3Used = 0
        var missedInARow = 0
        var result: Outgoing? = null

        /** Set while disconnected: disconnect time + reconnect window (informational, OQ-032). */
        var reconnectDeadline: Long? = null
    }

    private class PlayerRound {
        var bet: Int? = null
        var betDefaulted = false
        var betAt: Long? = null
        var optionId: UUID? = null
        var answerAt: Long? = null
    }

    private class RoundState(val roundNo: Int, val suddenDeathNo: Int?, val question: DuelQuestion, val allIn: Boolean) {
        val suddenDeath: Boolean get() = suddenDeathNo != null
        var phase = Phase.QUESTION
        var deadline = 0L
        var optionsSentAt = 0L
        val players = mapOf(Side.A to PlayerRound(), Side.B to PlayerRound())
        val results = mutableMapOf<Side, ServerMessage.RoundResult>()
    }

    private data class Outcome(
        val bet: Int,
        val betDefaulted: Boolean,
        val optionId: UUID?,
        val correct: Boolean,
        val answeredMs: Long?,
        val speedBonus: Int,
        val points: Int,
        val missed: Boolean,
        val missedInARow: Int,
    ) {
        fun toDto() = PlayerRoundResult(bet, betDefaulted, optionId?.toString(), correct, answeredMs, speedBonus, points, missed, missedInARow)
        fun toSummary() = RoundSummarySide(bet, betDefaulted, correct, points, missed)
    }

    private class CompletedRound(val roundNo: Int, val suddenDeath: Boolean, val outcomes: Map<Side, Outcome>)

    companion object {
        const val FORFEIT_MISSED = "missed_rounds"
        const val FORFEIT_LEFT = "left"
    }
}

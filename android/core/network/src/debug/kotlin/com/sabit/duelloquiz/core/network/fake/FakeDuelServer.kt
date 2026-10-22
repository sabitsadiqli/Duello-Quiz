package com.sabit.duelloquiz.core.network.fake

import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.model.QuizOption
import com.sabit.duelloquiz.core.model.XpBreakdownItem
import com.sabit.duelloquiz.core.model.duel.CalibrationLevel
import com.sabit.duelloquiz.core.model.duel.CompactRound
import com.sabit.duelloquiz.core.model.duel.CompactRoundPlayer
import com.sabit.duelloquiz.core.model.duel.DuelClientMessage
import com.sabit.duelloquiz.core.model.duel.DuelCloseCode
import com.sabit.duelloquiz.core.model.duel.DuelErrorCode
import com.sabit.duelloquiz.core.model.duel.DuelPhase
import com.sabit.duelloquiz.core.model.duel.DuelPlayer
import com.sabit.duelloquiz.core.model.duel.DuelQuestion
import com.sabit.duelloquiz.core.model.duel.DuelScores
import com.sabit.duelloquiz.core.model.duel.DuelServerMessage
import com.sabit.duelloquiz.core.model.duel.DuelSide
import com.sabit.duelloquiz.core.model.duel.ForfeitCause
import com.sabit.duelloquiz.core.model.duel.MatchEndReason
import com.sabit.duelloquiz.core.model.duel.MatchOutcome
import com.sabit.duelloquiz.core.model.duel.MatchResult
import com.sabit.duelloquiz.core.model.duel.MatchSnapshot
import com.sabit.duelloquiz.core.model.duel.MatchSummary
import com.sabit.duelloquiz.core.model.duel.OwnBet
import com.sabit.duelloquiz.core.model.duel.RoundNext
import com.sabit.duelloquiz.core.model.duel.RoundPlayerResult
import com.sabit.duelloquiz.core.model.duel.RoundResult
import com.sabit.duelloquiz.core.model.duel.SuddenDeathOutcome
import com.sabit.duelloquiz.core.network.duel.DuelConnectError
import com.sabit.duelloquiz.core.network.duel.DuelInbound
import com.sabit.duelloquiz.core.network.duel.DuelOpenResult
import com.sabit.duelloquiz.core.network.duel.DuelSocket
import com.sabit.duelloquiz.core.network.duel.DuelSocketFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random

/**
 * DEBUG ONLY: a duel server in the app process with a scripted bot opponent, so the whole duel
 * (search, intro, 7 rounds, round 7 All-in, sudden death, reveal, result, leave, rejoin) can be
 * played without a backend. It follows docs/api/duel-protocol.md for timing, seq, scoring and Elo;
 * the bot picks Əminlik and answers at random times and is right about 70 % of the time. Every
 * fake question names its right answer so testers can win or lose on purpose. Texts are fake
 * server content, not UI copy. Never part of a release build.
 */
internal class FakeDuelSocketFactory(
    private val authRepository: AuthRepository,
    private val clock: WallClock,
) : DuelSocketFactory {
    override suspend fun open(): DuelOpenResult {
        delay(HANDSHAKE_MS)
        val user = authRepository.currentUser.first() ?: return DuelOpenResult.Failed(DuelConnectError.UNAUTHORIZED)
        return DuelOpenResult.Opened(FakeBotServer.connect(user.uid, user.displayName, clock))
    }

    private companion object {
        const val HANDSHAKE_MS = 300L
    }
}

private class FakeConnection(val uid: String, private val server: FakeBotServer) : DuelSocket {
    private val channel = Channel<DuelInbound>(Channel.UNLIMITED)
    override val incoming: ReceiveChannel<DuelInbound> = channel
    var open = true
        private set

    fun deliver(message: DuelServerMessage) {
        if (open) channel.trySend(DuelInbound.Message(message))
    }

    fun closeFromServer(code: Int) {
        if (!open) return
        open = false
        channel.trySend(DuelInbound.Closed(code))
        channel.close()
    }

    override suspend fun send(message: DuelClientMessage): Boolean {
        if (!open) return false
        server.onClient(this, message)
        return true
    }

    override suspend fun close(code: Int) {
        if (!open) return
        open = false
        channel.trySend(DuelInbound.Closed(code))
        channel.close()
        server.onClosed(this)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private object FakeBotServer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private lateinit var clock: WallClock
    private var connection: FakeConnection? = null
    private var queueJob: Job? = null
    private var match: FakeMatch? = null
    private var playerName: String = DEFAULT_NAME

    private fun now() = clock.nowMs()

    fun connect(uid: String, name: String?, clock: WallClock): DuelSocket {
        this.clock = clock
        playerName = name ?: DEFAULT_NAME
        val socket = FakeConnection(uid, this)
        scope.launch {
            // Newest connection wins (DUEL-04).
            connection?.let {
                it.deliver(error(DuelErrorCode.SESSION_REPLACED))
                it.closeFromServer(DuelCloseCode.SESSION_REPLACED)
            }
            connection = socket
            val running = match?.takeIf { it.uid == uid && it.running }
            socket.deliver(DuelServerMessage.Hello(0, now(), 1, HEARTBEAT_MS, running?.id))
            running?.let { socket.deliver(it.snapshot()) }
        }
        return socket
    }

    fun onClosed(socket: FakeConnection) {
        scope.launch {
            if (connection !== socket) return@launch
            connection = null
            queueJob?.cancel()
        }
    }

    fun onClient(socket: FakeConnection, message: DuelClientMessage) {
        scope.launch {
            if (connection !== socket) return@launch
            when (message) {
                DuelClientMessage.Ping -> socket.deliver(DuelServerMessage.Pong(0, now()))
                DuelClientMessage.QueueJoin -> join(socket)
                DuelClientMessage.QueueLeave -> leaveQueue(socket)
                is DuelClientMessage.MatchRejoin -> {
                    val m = match?.takeIf { it.id == message.matchId && it.uid == socket.uid }
                    when {
                        m == null -> socket.deliver(error(DuelErrorCode.MATCH_NOT_FOUND, message.matchId, ref = message.type))
                        m.running -> socket.deliver(m.snapshot())
                        else -> m.finalResult?.let(socket::deliver)
                    }
                }
                is DuelClientMessage.MatchLeave -> match?.takeIf { it.id == message.matchId && it.running }?.leave()
                    ?: socket.deliver(error(DuelErrorCode.MATCH_NOT_FOUND, message.matchId, ref = message.type))
                is DuelClientMessage.RoundBet -> match?.takeIf { it.id == message.matchId }?.bet(message)
                is DuelClientMessage.RoundAnswer -> match?.takeIf { it.id == message.matchId }?.answer(message)
            }
        }
    }

    private fun join(socket: FakeConnection) {
        val running = match?.takeIf { it.running && it.uid == socket.uid }
        if (running != null) {
            socket.deliver(error(DuelErrorCode.ALREADY_IN_MATCH, running.id, ref = DuelClientMessage.QueueJoin.type))
            socket.deliver(running.snapshot())
            return
        }
        if (queueJob?.isActive == true) return
        val deadline = now() + QUEUE_MS
        socket.deliver(DuelServerMessage.QueueJoined(0, now(), deadline))
        queueJob = scope.launch {
            delay(Random.nextLong(PAIR_MIN_MS, PAIR_MAX_MS))
            val created = FakeMatch(socket.uid, playerName)
            match = created
            created.start()
        }
    }

    private fun leaveQueue(socket: FakeConnection) {
        queueJob?.cancel()
        val m = match
        when {
            m != null && m.running && m.inIntro -> {
                m.cancel()
                socket.deliver(DuelServerMessage.QueueLeft(0, now()))
            }
            m != null && m.running -> socket.deliver(error(DuelErrorCode.ALREADY_IN_MATCH, m.id, ref = DuelClientMessage.QueueLeave.type))
            else -> socket.deliver(DuelServerMessage.QueueLeft(0, now()))
        }
    }

    private fun error(code: DuelErrorCode, matchId: String? = null, roundNo: Int? = null, ref: String? = null) =
        DuelServerMessage.Error(0, now(), code, matchId, roundNo, ref, null)

    /** One match against the bot; every stream message is numbered even when not delivered. */
    private class FakeMatch(val uid: String, playerName: String) {
        val id: String = UUID.randomUUID().toString()
        private val you = DuelPlayer(playerName, null, FakeDuelLedger.rating(uid))
        private val bot = DuelPlayer(BOT_NAMES.random(), null, FakeDuelLedger.rating(uid) + Random.nextInt(-BOT_SPREAD, BOT_SPREAD))
        private var seq = 0L
        private var job: Job? = null
        var running = false
            private set
        var inIntro = true
            private set
        var finalResult: DuelServerMessage.MatchResultMessage? = null
            private set

        private var phase = DuelPhase.INTRO
        private var deadline = 0L
        private var roundNo = 0
        private var question: DuelQuestion? = null
        private var options: List<QuizOption> = emptyList()
        private var correctId = ""
        private var optionsSentAt = 0L
        private var bet3Left = BET3_LIMIT
        private var botBet3Left = BET3_LIMIT
        private var ownBet: Int? = null
        private var ownAnswer: String? = null
        private var ownAnsweredAt = 0L
        private var botBet: Int? = null
        private var botAnswer: String? = null
        private var botAnsweredAt = 0L
        private var scores = DuelScores(0, 0)
        private var missedYou = 0
        private var missedBot = 0
        private var lastResult: RoundResult? = null
        private val rounds = mutableListOf<CompactRound>()
        private val picks = IntArray(LEVELS)
        private val correctPicks = IntArray(LEVELS)

        private fun now() = FakeBotServer.now()

        private fun stream(build: (Long) -> DuelServerMessage) {
            seq++
            FakeBotServer.connection?.takeIf { it.uid == uid }?.deliver(build(seq))
        }

        fun start() {
            running = true
            job = FakeBotServer.scope.launch { play() }
        }

        fun cancel() {
            job?.cancel()
            running = false
            stream { DuelServerMessage.MatchCancelled(it, now(), id, DuelSide.SELF) }
        }

        fun leave() {
            job?.cancel()
            finish(MatchOutcome.LOSS, MatchEndReason.FORFEIT, DuelSide.SELF, ForfeitCause.LEFT)
        }

        private suspend fun play() {
            phase = DuelPhase.INTRO
            deadline = now() + INTRO_MS
            stream { DuelServerMessage.MatchFound(it, now(), id, you, bot, REGULAR_ROUNDS, BET3_LIMIT, deadline) }
            delay(INTRO_MS)
            inIntro = false
            for (n in 1..REGULAR_ROUNDS) {
                if (!round(n, suddenDeathNo = null)) return
            }
            if (scores.you == scores.opponent) {
                for (sd in 1..SUDDEN_DEATH_ROUNDS) {
                    if (!round(REGULAR_ROUNDS + sd, suddenDeathNo = sd)) return
                }
            }
        }

        /** Plays one round; false when the match ended with it. */
        @Suppress("LongMethod", "CyclomaticComplexMethod")
        private suspend fun round(n: Int, suddenDeathNo: Int?): Boolean {
            roundNo = n
            val allIn = n == REGULAR_ROUNDS
            val correctIndex = Random.nextInt(OPTIONS)
            options = (1..OPTIONS).map { QuizOption(UUID.randomUUID().toString(), "Variant $it") }
            correctId = options[correctIndex].id
            question = DuelQuestion("Nümunə duel sualı $n. Düzgün cavab «Variant ${correctIndex + 1}»-dir.", null, CATEGORIES.random())
            ownBet = null
            ownAnswer = null
            botBet = null
            botAnswer = null
            lastResult = null

            phase = DuelPhase.QUESTION
            deadline = now() + QUESTION_MS
            stream {
                DuelServerMessage.RoundQuestion(it, now(), id, n, allIn, suddenDeathNo != null, suddenDeathNo, question!!, deadline)
            }
            delay(QUESTION_MS)

            var ownDefaulted = false
            if (suddenDeathNo == null) {
                phase = DuelPhase.CONFIDENCE
                deadline = now() + CONFIDENCE_MS
                stream { DuelServerMessage.RoundConfidence(it, now(), id, n, allIn, bet3Left, deadline) }
                val botAt = now() + Random.nextLong(BOT_PICK_MIN_MS, BOT_PICK_MAX_MS)
                waitUntil(deadline + GRACE_MS + 1) {
                    if (botBet == null && now() >= botAt) {
                        botBet = if (botBet3Left > 0 && Random.nextInt(BOT_THREE_ONE_IN) == 0) 3 else Random.nextInt(1, 3)
                        if (botBet == 3) botBet3Left--
                        stream { DuelServerMessage.BetLocked(it, now(), id, n, DuelSide.OPPONENT, null) }
                    }
                    ownBet != null && botBet != null
                }
                ownDefaulted = ownBet == null
            }

            phase = DuelPhase.ANSWER
            optionsSentAt = now()
            deadline = optionsSentAt + ANSWER_MS
            val bet = ownBet ?: 1
            stream { DuelServerMessage.RoundOptions(it, now(), id, n, options, bet, ownDefaulted, deadline) }
            val botAnswerAt = now() + Random.nextLong(BOT_ANSWER_MIN_MS, BOT_ANSWER_MAX_MS)
            waitUntil(deadline + GRACE_MS + 1) {
                if (botAnswer == null && now() >= botAnswerAt) {
                    botAnswer = if (Random.nextInt(BOT_ACCURACY_OUT_OF) < BOT_ACCURACY) correctId else options.filter { it.id != correctId }.random().id
                    botAnsweredAt = now()
                    stream { DuelServerMessage.AnswerLocked(it, now(), id, n, DuelSide.OPPONENT, null) }
                }
                ownAnswer != null && botAnswer != null
            }

            val you = resolve(ownBet, ownDefaulted, ownAnswer, ownAnsweredAt, allIn, suddenDeathNo != null, missedYou)
            val opp = resolve(botBet, suddenDeathNo == null && botBet == null, botAnswer, botAnsweredAt, allIn, suddenDeathNo != null, missedBot)
            missedYou = you.missedInARow
            missedBot = opp.missedInARow
            if (suddenDeathNo == null) {
                scores = DuelScores(scores.you + you.points, scores.opponent + opp.points)
                if (!you.betDefaulted && !you.missed) {
                    picks[you.bet - 1]++
                    if (you.correct) correctPicks[you.bet - 1]++
                }
            }
            val outcome = suddenDeathNo?.let { suddenDeath(you, opp) }
            val forfeitYou = missedYou >= MISSED_FORFEIT
            val forfeitBot = missedBot >= MISSED_FORFEIT
            val next = when {
                forfeitYou || forfeitBot -> RoundNext.END
                outcome == SuddenDeathOutcome.YOU || outcome == SuddenDeathOutcome.OPPONENT -> RoundNext.END
                suddenDeathNo == SUDDEN_DEATH_ROUNDS -> RoundNext.END
                n == REGULAR_ROUNDS && scores.you != scores.opponent -> RoundNext.END
                n >= REGULAR_ROUNDS -> RoundNext.SUDDEN_DEATH
                else -> RoundNext.ROUND
            }
            phase = DuelPhase.RESULT
            deadline = now() + RESULT_MS
            val result = RoundResult(
                id, n, allIn, suddenDeathNo != null, suddenDeathNo, correctId, you, opp, scores, bet3Left, next, deadline, outcome,
            )
            lastResult = result
            rounds += CompactRound(n, suddenDeathNo != null, you.compact(), opp.compact())
            stream { DuelServerMessage.RoundResultMessage(it, now(), result) }
            if (next != RoundNext.END) {
                delay(RESULT_MS)
                return true
            }
            when {
                forfeitYou && forfeitBot -> finish(MatchOutcome.ABANDONED, MatchEndReason.ABANDONED, null, null)
                forfeitYou -> finish(MatchOutcome.LOSS, MatchEndReason.FORFEIT, DuelSide.SELF, ForfeitCause.MISSED_ROUNDS)
                forfeitBot -> finish(MatchOutcome.WIN, MatchEndReason.FORFEIT, DuelSide.OPPONENT, ForfeitCause.MISSED_ROUNDS)
                outcome == SuddenDeathOutcome.YOU -> finish(MatchOutcome.WIN, MatchEndReason.SUDDEN_DEATH, null, null)
                outcome == SuddenDeathOutcome.OPPONENT -> finish(MatchOutcome.LOSS, MatchEndReason.SUDDEN_DEATH, null, null)
                suddenDeathNo != null -> finish(MatchOutcome.DRAW, MatchEndReason.DRAW, null, null)
                scores.you > scores.opponent -> finish(MatchOutcome.WIN, MatchEndReason.NORMAL, null, null)
                else -> finish(MatchOutcome.LOSS, MatchEndReason.NORMAL, null, null)
            }
            return false
        }

        @Suppress("LongParameterList")
        private fun resolve(
            bet: Int?,
            defaulted: Boolean,
            answer: String?,
            answeredAt: Long,
            allIn: Boolean,
            suddenDeath: Boolean,
            missedBefore: Int,
        ): RoundPlayerResult {
            val level = bet ?: 1
            val correct = answer == correctId
            val answeredMs = answer?.let { answeredAt - optionsSentAt }
            val remaining = answer?.let { deadline - minOf(answeredAt, deadline) } ?: 0
            val bonus = if (correct) (SPEED_BONUS_MAX * remaining / ANSWER_MS).toInt() else 0
            val multiplier = if (allIn) 2 else 1
            val points = if (suddenDeath) 0 else (if (correct) GAIN[level - 1] else -LOSS[level - 1]) * multiplier + bonus
            val missed = answer == null && (suddenDeath || defaulted)
            return RoundPlayerResult(
                bet = level,
                betDefaulted = !suddenDeath && defaulted,
                optionId = answer,
                correct = correct,
                answeredMs = answeredMs,
                speedBonus = if (suddenDeath) 0 else bonus,
                points = points,
                missed = missed,
                missedInARow = if (missed) missedBefore + 1 else 0,
            )
        }

        private fun suddenDeath(you: RoundPlayerResult, opp: RoundPlayerResult): SuddenDeathOutcome = when {
            you.correct && !opp.correct -> SuddenDeathOutcome.YOU
            opp.correct && !you.correct -> SuddenDeathOutcome.OPPONENT
            !you.correct -> SuddenDeathOutcome.NONE
            you.answeredMs!! < opp.answeredMs!! -> SuddenDeathOutcome.YOU
            you.answeredMs!! > opp.answeredMs!! -> SuddenDeathOutcome.OPPONENT
            else -> SuddenDeathOutcome.SAME_TIME
        }

        private fun RoundPlayerResult.compact() = CompactRoundPlayer(bet, betDefaulted, correct, points, missed)

        fun bet(message: DuelClientMessage.RoundBet) {
            val error = when {
                message.roundNo != roundNo -> DuelErrorCode.STALE_ROUND
                phase != DuelPhase.CONFIDENCE -> DuelErrorCode.PHASE_NOT_OPEN
                now() > deadline + GRACE_MS -> DuelErrorCode.TOO_LATE
                ownBet != null -> DuelErrorCode.BET_ALREADY_LOCKED
                message.bet !in 1..LEVELS -> DuelErrorCode.INVALID_BET
                message.bet == LEVELS && bet3Left == 0 -> DuelErrorCode.BET_LIMIT_REACHED
                else -> null
            }
            if (error != null) {
                FakeBotServer.connection?.deliver(FakeBotServer.error(error, id, message.roundNo, message.type))
                return
            }
            ownBet = message.bet
            if (message.bet == LEVELS) bet3Left--
            stream { DuelServerMessage.BetLocked(it, now(), id, roundNo, DuelSide.SELF, message.bet) }
        }

        fun answer(message: DuelClientMessage.RoundAnswer) {
            if (ownAnswer == message.optionId) return
            val error = when {
                message.roundNo != roundNo -> DuelErrorCode.STALE_ROUND
                phase != DuelPhase.ANSWER -> DuelErrorCode.PHASE_NOT_OPEN
                now() > deadline + GRACE_MS -> DuelErrorCode.TOO_LATE
                ownAnswer != null -> DuelErrorCode.ALREADY_ANSWERED
                options.none { it.id == message.optionId } -> DuelErrorCode.INVALID_OPTION
                else -> null
            }
            if (error != null) {
                FakeBotServer.connection?.deliver(FakeBotServer.error(error, id, message.roundNo, message.type))
                return
            }
            ownAnswer = message.optionId
            ownAnsweredAt = now()
            stream { DuelServerMessage.AnswerLocked(it, now(), id, roundNo, DuelSide.SELF, message.optionId) }
        }

        fun snapshot(): DuelServerMessage.MatchState = DuelServerMessage.MatchState(
            seq,
            now(),
            MatchSnapshot(
                matchId = id,
                you = you,
                opponent = bot,
                opponentConnected = true,
                phase = phase,
                deadlineEpochMs = deadline,
                roundNo = roundNo,
                allIn = roundNo == REGULAR_ROUNDS,
                suddenDeath = roundNo > REGULAR_ROUNDS,
                suddenDeathNo = (roundNo - REGULAR_ROUNDS).takeIf { it > 0 },
                question = question.takeIf { phase != DuelPhase.INTRO },
                options = options.takeIf { phase == DuelPhase.ANSWER || phase == DuelPhase.RESULT },
                ownBet = ownBet?.let { OwnBet(it, false) },
                ownOptionId = ownAnswer,
                opponentBetLocked = botBet != null,
                opponentAnswered = botAnswer != null,
                bet3Left = bet3Left,
                scores = scores,
                missedInARowYou = missedYou,
                missedInARowOpponent = missedBot,
                lastResult = lastResult.takeIf { phase == DuelPhase.RESULT },
                rounds = rounds.toList(),
            ),
        )

        private fun finish(outcome: MatchOutcome, reason: MatchEndReason, forfeitedBy: DuelSide?, cause: ForfeitCause?) {
            running = false
            val score = when (outcome) {
                MatchOutcome.WIN -> 1.0
                MatchOutcome.DRAW -> HALF
                else -> 0.0
            }
            val expected = 1 / (1 + TEN.pow((bot.rating - you.rating) / ELO_SCALE))
            // OQ-038: rounded half away from zero.
            val raw = ELO_K * (score - expected)
            val change = if (outcome == MatchOutcome.ABANDONED) 0 else (abs(raw) + HALF).toInt() * if (raw < 0) -1 else 1
            val calibration = (1..LEVELS).map { CalibrationLevel(it, picks[it - 1], correctPicks[it - 1]) }
            // OQ-081: 20 for a finished match + 30 win / 15 draw; 0 for own forfeit and abandoned.
            val earns = outcome != MatchOutcome.ABANDONED && forfeitedBy != DuelSide.SELF
            val bonus = buildList {
                if (earns) add(XpBreakdownItem("duel_played", null, DUEL_PLAYED_XP))
                if (earns && outcome == MatchOutcome.WIN) add(XpBreakdownItem("duel_win", null, DUEL_WIN_XP))
                if (earns && outcome == MatchOutcome.DRAW) add(XpBreakdownItem("duel_draw", null, DUEL_DRAW_XP))
            }
            val progress = FakeProgressLedger.award(uid, "duel:$id", now(), emptyMap(), bonus, activates = earns)
            val message = DuelServerMessage.MatchResultMessage(
                seq + 1,
                now(),
                MatchResult(
                    matchId = id,
                    outcome = outcome,
                    endReason = reason,
                    forfeitedBy = forfeitedBy,
                    forfeitCause = cause,
                    scores = scores,
                    ratingBefore = you.rating,
                    ratingAfter = you.rating + change,
                    ratingChange = change,
                    opponentRatingAfter = bot.rating - change,
                    suddenDeathPlayed = (roundNo - REGULAR_ROUNDS).coerceAtLeast(0),
                    calibration = calibration,
                    rounds = rounds.toList(),
                    progress = progress,
                ),
            )
            finalResult = message
            stream { message.copy(seq = it) }
            FakeDuelLedger.record(
                uid,
                MatchSummary(
                    id, bot.displayName, null, false, outcome, reason, scores.you, scores.opponent, change,
                    now(),
                ),
                you.rating + change,
                calibration,
            )
        }

        private suspend fun waitUntil(until: Long, done: () -> Boolean) {
            while (!done() && now() < until) delay(POLL_MS)
        }
    }

    private const val DEFAULT_NAME = "Oyunçu"
    private val BOT_NAMES = listOf("Bot Aysel", "Bot Murad", "Bot Leyla", "Bot Tural")
    private val CATEGORIES = listOf("Coğrafiya", "Tarix", "Biologiya", "Fizika", "İdman")
    private const val HEARTBEAT_MS = 5_000L
    private const val QUEUE_MS = 30_000L
    private const val PAIR_MIN_MS = 1_500L
    private const val PAIR_MAX_MS = 4_000L
    private const val INTRO_MS = 3_000L
    private const val QUESTION_MS = 3_000L
    private const val CONFIDENCE_MS = 5_000L
    private const val ANSWER_MS = 10_000L
    private const val RESULT_MS = 3_000L
    private const val GRACE_MS = 250L
    private const val POLL_MS = 50L
    private const val REGULAR_ROUNDS = 7
    private const val SUDDEN_DEATH_ROUNDS = 3
    private const val BET3_LIMIT = 2
    private const val LEVELS = 3
    private const val OPTIONS = 4
    private const val DUEL_PLAYED_XP = 20
    private const val DUEL_WIN_XP = 30
    private const val DUEL_DRAW_XP = 15
    private const val MISSED_FORFEIT = 2
    private const val SPEED_BONUS_MAX = 50L
    private val GAIN = intArrayOf(100, 200, 300)
    private val LOSS = intArrayOf(0, 100, 200)
    private const val BOT_SPREAD = 40
    private const val BOT_PICK_MIN_MS = 1_000L
    private const val BOT_PICK_MAX_MS = 4_000L
    private const val BOT_ANSWER_MIN_MS = 2_000L
    private const val BOT_ANSWER_MAX_MS = 8_000L
    private const val BOT_THREE_ONE_IN = 4
    private const val BOT_ACCURACY = 7
    private const val BOT_ACCURACY_OUT_OF = 10
    private const val ELO_K = 32.0
    private const val ELO_SCALE = 400.0
    private const val TEN = 10.0
    private const val HALF = 0.5
}

package com.sabit.duelloquiz.core.network.duel

import com.sabit.duelloquiz.core.model.duel.CalibrationLevel
import com.sabit.duelloquiz.core.model.duel.CompactRound
import com.sabit.duelloquiz.core.model.duel.CompactRoundPlayer
import com.sabit.duelloquiz.core.model.duel.DuelClientMessage
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
import com.sabit.duelloquiz.core.model.duel.OwnBet
import com.sabit.duelloquiz.core.model.duel.RoundNext
import com.sabit.duelloquiz.core.model.duel.RoundPlayerResult
import com.sabit.duelloquiz.core.model.duel.RoundResult
import com.sabit.duelloquiz.core.model.duel.SuddenDeathOutcome
import com.sabit.duelloquiz.core.network.model.asExternalModel

/** Wire enum value → domain enum; an unknown value makes the whole message malformed. */
private fun <T> enumOf(value: String, vararg pairs: Pair<String, T>): T =
    pairs.firstOrNull { it.first == value }?.second ?: throw IllegalArgumentException("Unexpected value $value")

private fun side(value: String): DuelSide = enumOf(value, "self" to DuelSide.SELF, "opponent" to DuelSide.OPPONENT)

internal fun WirePlayer.asExternalModel() = DuelPlayer(displayName, avatarUrl, rating)

internal fun WireScores.asExternalModel() = DuelScores(you, opponent)

internal fun WireQuestion.asExternalModel() = DuelQuestion(text, image?.asExternalModel(), categoryName)

internal fun WireRoundPlayer.asExternalModel() = RoundPlayerResult(
    bet = bet,
    betDefaulted = betDefaulted,
    optionId = optionId,
    correct = correct,
    answeredMs = answeredMs,
    speedBonus = speedBonus,
    points = points,
    missed = missed,
    missedInARow = missedInARow,
)

internal fun WireCompactPlayer.asExternalModel() = CompactRoundPlayer(bet, betDefaulted, correct, points, missed)

internal fun WireCompactRound.asExternalModel() =
    CompactRound(roundNo, suddenDeath, you.asExternalModel(), opponent.asExternalModel())

internal fun WireRoundResultBody.asExternalModel() = RoundResult(
    matchId = matchId,
    roundNo = roundNo,
    allIn = allIn,
    suddenDeath = suddenDeath,
    suddenDeathNo = suddenDeathNo,
    correctOptionId = correctOptionId,
    you = you.asExternalModel(),
    opponent = opponent.asExternalModel(),
    scores = scores.asExternalModel(),
    bet3Left = bet3Left,
    next = enumOf(next, "round" to RoundNext.ROUND, "sudden_death" to RoundNext.SUDDEN_DEATH, "end" to RoundNext.END),
    deadlineEpochMs = deadlineEpochMs,
    suddenDeathOutcome = suddenDeathOutcome?.let {
        enumOf(
            it,
            "you" to SuddenDeathOutcome.YOU,
            "opponent" to SuddenDeathOutcome.OPPONENT,
            "same_time" to SuddenDeathOutcome.SAME_TIME,
            "none" to SuddenDeathOutcome.NONE,
        )
    },
)

private fun WireServerMessage.RoundResult.body() = WireRoundResultBody(
    matchId, roundNo, allIn, suddenDeath, suddenDeathNo, correctOptionId, you, opponent, scores, bet3Left, next,
    deadlineEpochMs, suddenDeathOutcome,
)

private fun WireServerMessage.MatchState.asSnapshot() = MatchSnapshot(
    matchId = matchId,
    you = you.asExternalModel(),
    opponent = opponent.asExternalModel(),
    opponentConnected = opponent.connected ?: true,
    phase = enumOf(
        phase,
        "intro" to DuelPhase.INTRO,
        "question" to DuelPhase.QUESTION,
        "confidence" to DuelPhase.CONFIDENCE,
        "answer" to DuelPhase.ANSWER,
        "result" to DuelPhase.RESULT,
    ),
    deadlineEpochMs = deadlineEpochMs,
    roundNo = roundNo,
    allIn = allIn,
    suddenDeath = suddenDeath,
    suddenDeathNo = suddenDeathNo,
    question = question?.asExternalModel(),
    options = options?.map { it.asExternalModel() },
    ownBet = ownBet?.let { OwnBet(it.bet, it.betDefaulted) },
    ownOptionId = ownOptionId,
    opponentBetLocked = opponentBetLocked,
    opponentAnswered = opponentAnswered,
    bet3Left = bet3Left,
    scores = scores.asExternalModel(),
    missedInARowYou = missedInARow.you,
    missedInARowOpponent = missedInARow.opponent,
    lastResult = lastResult?.asExternalModel(),
    rounds = rounds.map { it.asExternalModel() },
)

private fun WireServerMessage.MatchResult.asResult() = MatchResult(
    matchId = matchId,
    outcome = enumOf(
        outcome,
        "win" to MatchOutcome.WIN,
        "loss" to MatchOutcome.LOSS,
        "draw" to MatchOutcome.DRAW,
        "abandoned" to MatchOutcome.ABANDONED,
    ),
    endReason = endReason(endReason),
    forfeitedBy = forfeitedBy?.let(::side),
    forfeitCause = forfeitCause?.let {
        enumOf(it, "missed_rounds" to ForfeitCause.MISSED_ROUNDS, "left" to ForfeitCause.LEFT)
    },
    scores = scores.asExternalModel(),
    ratingBefore = ratingBefore,
    ratingAfter = ratingAfter,
    ratingChange = ratingChange,
    opponentRatingAfter = opponentRatingAfter,
    suddenDeathPlayed = suddenDeathPlayed,
    calibration = calibration.map { CalibrationLevel(it.level, it.picks, it.correct) }.sortedBy { it.level },
    rounds = rounds.map { it.asExternalModel() },
)

internal fun endReason(value: String): MatchEndReason = enumOf(
    value,
    "normal" to MatchEndReason.NORMAL,
    "sudden_death" to MatchEndReason.SUDDEN_DEATH,
    "draw" to MatchEndReason.DRAW,
    "forfeit" to MatchEndReason.FORFEIT,
    "abandoned" to MatchEndReason.ABANDONED,
)

@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun WireServerMessage.asExternalModel(): DuelServerMessage = when (this) {
    is WireServerMessage.Hello ->
        DuelServerMessage.Hello(seq, serverNowEpochMs, protocolVersion, heartbeatIntervalMs, activeMatchId)
    is WireServerMessage.Pong -> DuelServerMessage.Pong(seq, serverNowEpochMs)
    is WireServerMessage.QueueJoined -> DuelServerMessage.QueueJoined(seq, serverNowEpochMs, deadlineEpochMs)
    is WireServerMessage.QueueTimeout -> DuelServerMessage.QueueTimeout(seq, serverNowEpochMs)
    is WireServerMessage.QueueLeft -> DuelServerMessage.QueueLeft(seq, serverNowEpochMs)
    is WireServerMessage.MatchFound -> DuelServerMessage.MatchFound(
        seq, serverNowEpochMs, matchId, you.asExternalModel(), opponent.asExternalModel(), rounds, bet3Limit,
        deadlineEpochMs,
    )
    is WireServerMessage.MatchCancelled -> DuelServerMessage.MatchCancelled(seq, serverNowEpochMs, matchId, side(by))
    is WireServerMessage.RoundQuestion -> DuelServerMessage.RoundQuestion(
        seq = seq,
        serverNowEpochMs = serverNowEpochMs,
        matchId = matchId,
        roundNo = roundNo,
        allIn = allIn,
        suddenDeath = suddenDeath,
        suddenDeathNo = suddenDeathNo,
        question = DuelQuestion(text, image?.asExternalModel(), categoryName),
        deadlineEpochMs = deadlineEpochMs,
    )
    is WireServerMessage.RoundConfidence ->
        DuelServerMessage.RoundConfidence(seq, serverNowEpochMs, matchId, roundNo, allIn, bet3Left, deadlineEpochMs)
    is WireServerMessage.BetLocked ->
        DuelServerMessage.BetLocked(seq, serverNowEpochMs, matchId, roundNo, side(who), bet)
    is WireServerMessage.RoundOptions -> DuelServerMessage.RoundOptions(
        seq, serverNowEpochMs, matchId, roundNo, options.map { it.asExternalModel() }, bet, betDefaulted,
        deadlineEpochMs,
    )
    is WireServerMessage.AnswerLocked ->
        DuelServerMessage.AnswerLocked(seq, serverNowEpochMs, matchId, roundNo, side(who), optionId)
    is WireServerMessage.RoundResult ->
        DuelServerMessage.RoundResultMessage(seq, serverNowEpochMs, body().asExternalModel())
    is WireServerMessage.OpponentConnection -> DuelServerMessage.OpponentConnection(
        seq = seq,
        serverNowEpochMs = serverNowEpochMs,
        matchId = matchId,
        connected = enumOf(state, "connected" to true, "disconnected" to false),
        reconnectDeadlineEpochMs = reconnectDeadlineEpochMs,
    )
    is WireServerMessage.MatchState -> DuelServerMessage.MatchState(seq, serverNowEpochMs, asSnapshot())
    is WireServerMessage.MatchResult -> DuelServerMessage.MatchResultMessage(seq, serverNowEpochMs, asResult())
    is WireServerMessage.Error -> DuelServerMessage.Error(
        seq = seq,
        serverNowEpochMs = serverNowEpochMs,
        code = DuelErrorCode.fromWire(code),
        matchId = matchId,
        roundNo = roundNo,
        ref = ref,
        minProtocolVersion = minProtocolVersion,
    )
}

internal fun DuelClientMessage.asWire(): WireClientMessage = when (this) {
    DuelClientMessage.Ping -> WireClientMessage.Ping
    DuelClientMessage.QueueJoin -> WireClientMessage.QueueJoin
    DuelClientMessage.QueueLeave -> WireClientMessage.QueueLeave
    is DuelClientMessage.MatchRejoin -> WireClientMessage.MatchRejoin(matchId)
    is DuelClientMessage.MatchLeave -> WireClientMessage.MatchLeave(matchId)
    is DuelClientMessage.RoundBet -> WireClientMessage.RoundBet(matchId, roundNo, bet)
    is DuelClientMessage.RoundAnswer -> WireClientMessage.RoundAnswer(matchId, roundNo, optionId)
}

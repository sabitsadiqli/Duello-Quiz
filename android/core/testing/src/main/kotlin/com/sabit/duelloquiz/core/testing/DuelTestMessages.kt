package com.sabit.duelloquiz.core.testing

import com.sabit.duelloquiz.core.model.QuizOption
import com.sabit.duelloquiz.core.model.duel.CalibrationLevel
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

/**
 * Builders for duel server messages in tests. [now] is the server clock of the message; every
 * deadline is given explicitly. Option ids of round N are "rN-a" … "rN-d".
 */
object DuelTestMessages {
    const val MATCH_ID = "match-1"
    val YOU = DuelPlayer("Leyla", null, 1000)
    val OPPONENT = DuelPlayer("Murad", "https://example.com/m.png", 1016)

    fun options(roundNo: Int) = listOf("a", "b", "c", "d").map { QuizOption("r$roundNo-$it", "Variant $it") }

    fun hello(now: Long, activeMatchId: String? = null, heartbeatMs: Long = 5_000) =
        DuelServerMessage.Hello(0, now, 1, heartbeatMs, activeMatchId)

    fun pong(now: Long) = DuelServerMessage.Pong(0, now)

    fun queueJoined(now: Long, deadline: Long = now + 30_000) = DuelServerMessage.QueueJoined(0, now, deadline)

    fun queueTimeout(now: Long) = DuelServerMessage.QueueTimeout(0, now)

    fun queueLeft(now: Long) = DuelServerMessage.QueueLeft(0, now)

    fun matchFound(now: Long, matchId: String = MATCH_ID, deadline: Long = now + 3_000) =
        DuelServerMessage.MatchFound(1, now, matchId, YOU, OPPONENT, 7, 2, deadline)

    fun matchCancelled(seq: Long, now: Long, by: DuelSide, matchId: String = MATCH_ID) =
        DuelServerMessage.MatchCancelled(seq, now, matchId, by)

    fun question(
        seq: Long,
        now: Long,
        roundNo: Int,
        deadline: Long = now + 3_000,
        allIn: Boolean = roundNo == 7,
        suddenDeathNo: Int? = null,
        matchId: String = MATCH_ID,
    ) = DuelServerMessage.RoundQuestion(
        seq = seq,
        serverNowEpochMs = now,
        matchId = matchId,
        roundNo = roundNo,
        allIn = allIn,
        suddenDeath = suddenDeathNo != null,
        suddenDeathNo = suddenDeathNo,
        question = DuelQuestion("Sual $roundNo", null, "Tarix"),
        deadlineEpochMs = deadline,
    )

    fun confidence(seq: Long, now: Long, roundNo: Int, bet3Left: Int = 2, deadline: Long = now + 5_000) =
        DuelServerMessage.RoundConfidence(seq, now, MATCH_ID, roundNo, roundNo == 7, bet3Left, deadline)

    fun betLocked(seq: Long, now: Long, roundNo: Int, who: DuelSide, bet: Int? = null) =
        DuelServerMessage.BetLocked(seq, now, MATCH_ID, roundNo, who, if (who == DuelSide.SELF) bet else null)

    fun options(
        seq: Long,
        now: Long,
        roundNo: Int,
        bet: Int = 1,
        betDefaulted: Boolean = false,
        deadline: Long = now + 10_000,
    ) = DuelServerMessage.RoundOptions(seq, now, MATCH_ID, roundNo, options(roundNo), bet, betDefaulted, deadline)

    fun answerLocked(seq: Long, now: Long, roundNo: Int, who: DuelSide, optionId: String? = null) =
        DuelServerMessage.AnswerLocked(seq, now, MATCH_ID, roundNo, who, if (who == DuelSide.SELF) optionId else null)

    fun player(
        bet: Int = 1,
        optionId: String? = null,
        correct: Boolean = false,
        points: Int = 0,
        speedBonus: Int = 0,
        missed: Boolean = false,
        missedInARow: Int = 0,
        betDefaulted: Boolean = false,
        answeredMs: Long? = optionId?.let { 4_000L },
    ) = RoundPlayerResult(bet, betDefaulted, optionId, correct, answeredMs, speedBonus, points, missed, missedInARow)

    @Suppress("LongParameterList")
    fun roundResult(
        seq: Long,
        now: Long,
        roundNo: Int,
        you: RoundPlayerResult = player(),
        opponent: RoundPlayerResult = player(),
        scores: DuelScores = DuelScores(0, 0),
        next: RoundNext = RoundNext.ROUND,
        deadline: Long = now + 3_000,
        suddenDeathNo: Int? = null,
        outcome: SuddenDeathOutcome? = null,
        bet3Left: Int = 2,
    ) = DuelServerMessage.RoundResultMessage(
        seq,
        now,
        RoundResult(
            matchId = MATCH_ID,
            roundNo = roundNo,
            allIn = roundNo == 7,
            suddenDeath = suddenDeathNo != null,
            suddenDeathNo = suddenDeathNo,
            correctOptionId = "r$roundNo-a",
            you = you,
            opponent = opponent,
            scores = scores,
            bet3Left = bet3Left,
            next = next,
            deadlineEpochMs = deadline,
            suddenDeathOutcome = outcome,
        ),
    )

    fun opponentConnection(seq: Long, now: Long, connected: Boolean) = DuelServerMessage.OpponentConnection(
        seq, now, MATCH_ID, connected, if (connected) null else now + 10_000,
    )

    @Suppress("LongParameterList")
    fun state(
        seq: Long,
        now: Long,
        phase: DuelPhase,
        roundNo: Int,
        deadline: Long,
        ownBet: OwnBet? = null,
        ownOptionId: String? = null,
        opponentConnected: Boolean = true,
        scores: DuelScores = DuelScores(0, 0),
        lastResult: RoundResult? = null,
        bet3Left: Int = 2,
        suddenDeathNo: Int? = null,
        matchId: String = MATCH_ID,
    ) = DuelServerMessage.MatchState(
        seq,
        now,
        MatchSnapshot(
            matchId = matchId,
            you = YOU,
            opponent = OPPONENT,
            opponentConnected = opponentConnected,
            phase = phase,
            deadlineEpochMs = deadline,
            roundNo = roundNo,
            allIn = roundNo == 7,
            suddenDeath = suddenDeathNo != null,
            suddenDeathNo = suddenDeathNo,
            question = if (phase == DuelPhase.INTRO) null else DuelQuestion("Sual $roundNo", null, "Tarix"),
            options = if (phase == DuelPhase.ANSWER || phase == DuelPhase.RESULT) options(roundNo) else null,
            ownBet = ownBet,
            ownOptionId = ownOptionId,
            opponentBetLocked = false,
            opponentAnswered = false,
            bet3Left = bet3Left,
            scores = scores,
            missedInARowYou = 0,
            missedInARowOpponent = 0,
            lastResult = lastResult,
            rounds = emptyList(),
        ),
    )

    @Suppress("LongParameterList")
    fun matchResult(
        seq: Long,
        now: Long,
        outcome: MatchOutcome = MatchOutcome.WIN,
        endReason: MatchEndReason = MatchEndReason.NORMAL,
        forfeitedBy: DuelSide? = null,
        forfeitCause: ForfeitCause? = null,
        scores: DuelScores = DuelScores(640, 410),
        ratingChange: Int = 16,
        matchId: String = MATCH_ID,
    ) = DuelServerMessage.MatchResultMessage(
        seq,
        now,
        MatchResult(
            matchId = matchId,
            outcome = outcome,
            endReason = endReason,
            forfeitedBy = forfeitedBy,
            forfeitCause = forfeitCause,
            scores = scores,
            ratingBefore = 1000,
            ratingAfter = 1000 + ratingChange,
            ratingChange = ratingChange,
            opponentRatingAfter = 1016 - ratingChange,
            suddenDeathPlayed = 0,
            calibration = listOf(CalibrationLevel(1, 2, 1), CalibrationLevel(2, 3, 2), CalibrationLevel(3, 2, 2)),
            rounds = emptyList(),
        ),
    )

    fun error(now: Long, code: DuelErrorCode, ref: String? = null, matchId: String? = null, roundNo: Int? = null) =
        DuelServerMessage.Error(0, now, code, matchId, roundNo, ref, null)
}

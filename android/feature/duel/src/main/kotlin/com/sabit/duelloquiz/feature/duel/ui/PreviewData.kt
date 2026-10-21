package com.sabit.duelloquiz.feature.duel.ui

import com.sabit.duelloquiz.core.designsystem.component.OptionCardState
import com.sabit.duelloquiz.core.model.duel.CalibrationHint
import com.sabit.duelloquiz.core.model.duel.CalibrationLevel
import com.sabit.duelloquiz.core.model.duel.DuelPhase
import com.sabit.duelloquiz.core.model.duel.DuelPlayer
import com.sabit.duelloquiz.core.model.duel.DuelScores
import com.sabit.duelloquiz.core.model.duel.DuelSide
import com.sabit.duelloquiz.core.model.duel.MatchOutcome
import com.sabit.duelloquiz.feature.duel.ChipState
import com.sabit.duelloquiz.feature.duel.ChipUi
import com.sabit.duelloquiz.feature.duel.ConfidenceStatus
import com.sabit.duelloquiz.feature.duel.ConfidenceUi
import com.sabit.duelloquiz.feature.duel.DuelOptionUi
import com.sabit.duelloquiz.feature.duel.MatchUi
import com.sabit.duelloquiz.feature.duel.PanelStatus
import com.sabit.duelloquiz.feature.duel.PanelUi
import com.sabit.duelloquiz.feature.duel.QuestionUi
import com.sabit.duelloquiz.feature.duel.ResultUi
import com.sabit.duelloquiz.feature.duel.RevealFaceUi
import com.sabit.duelloquiz.feature.duel.RevealKind
import com.sabit.duelloquiz.feature.duel.RevealUi
import com.sabit.duelloquiz.feature.duel.RoundLabelUi
import com.sabit.duelloquiz.feature.duel.TimerStyle
import com.sabit.duelloquiz.feature.duel.TimerUi

/** Sample data for @Preview only (fake players and a sample question, not UI copy). */
@Suppress("MagicNumber")
internal object PreviewData {
    val you = DuelPlayer("Leyla", null, 1000)
    val opponent = DuelPlayer("Murad", null, 1016)

    private val question = QuestionUi("Azərbaycanın ərazisindən axan ən uzun çay hansıdır?", null, "Coğrafiya")
    private val label = RoundLabelUi(3, 7, allIn = false, suddenDeathNo = null, suddenDeathTotal = 3)

    val confidenceMatch = MatchUi(
        roundLabel = label,
        phase = DuelPhase.CONFIDENCE,
        timer = TimerUi(4, 0.8f, TimerStyle.NORMAL, null, 5_000),
        self = PanelUi(isSelf = true, name = null, avatarUrl = null, score = 231, previousScore = 231, status = PanelStatus.SENDING),
        opponent = PanelUi(
            isSelf = false, name = opponent.displayName, avatarUrl = null, score = -100, previousScore = -100,
            status = PanelStatus.LOCKED_OPPONENT, showLock = true,
        ),
        question = question,
        confidence = ConfidenceUi(
            allIn = false,
            bet3Left = 2,
            chips = listOf(
                ChipUi(1, 100, 0, ChipState.FADED),
                ChipUi(2, 200, 100, ChipState.SELECTED),
                ChipUi(3, 300, 200, ChipState.FADED),
            ),
            status = ConfidenceStatus.SENDING,
            interactive = false,
        ),
    )

    val revealMatch = MatchUi(
        roundLabel = label,
        phase = DuelPhase.RESULT,
        timer = TimerUi(2, 0.6f, TimerStyle.NEUTRAL, null, 3_000),
        self = PanelUi(
            isSelf = true, name = null, avatarUrl = null, score = 462, previousScore = 231,
            reveal = RevealFaceUi(RevealKind.CORRECT, 2, "B", 200, 31, showPoints = true),
        ),
        opponent = PanelUi(
            isSelf = false, name = opponent.displayName, avatarUrl = null, score = -200, previousScore = -100,
            reveal = RevealFaceUi(RevealKind.WRONG, 2, "A", -100, 0, showPoints = true),
        ),
        question = question,
        options = listOf(
            DuelOptionUi("a", "A", "Araz", OptionCardState.Wrong, enabled = false, pickedByOpponent = true),
            DuelOptionUi("b", "B", "Kür", OptionCardState.Correct, enabled = false, pickedBySelf = true),
            DuelOptionUi("c", "C", "Samur", OptionCardState.Dimmed, enabled = false),
            DuelOptionUi("d", "D", "Qanıx", OptionCardState.Dimmed, enabled = false),
        ),
        reveal = RevealUi(3, animate = false, outcomeCorrect = true),
    )

    val result = ResultUi(
        matchId = "m",
        outcome = MatchOutcome.WIN,
        reason = null,
        you = you,
        opponent = opponent,
        scores = DuelScores(640, 410),
        ratingAfter = 1016,
        ratingChange = 16,
        calibration = listOf(CalibrationLevel(1, 2, 1), CalibrationLevel(2, 3, 2), CalibrationLevel(3, 0, 0)),
        hint = CalibrationHint.GOOD,
        winner = DuelSide.SELF,
    )
}

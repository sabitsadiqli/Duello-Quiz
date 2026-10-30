package com.sabit.duelloquiz.feature.duel

import com.sabit.duelloquiz.core.designsystem.component.OptionCardState
import com.sabit.duelloquiz.core.model.Profile
import com.sabit.duelloquiz.core.model.duel.CalibrationHint
import com.sabit.duelloquiz.core.model.duel.CalibrationRules
import com.sabit.duelloquiz.core.model.duel.DuelPhase
import com.sabit.duelloquiz.core.model.duel.DuelSide
import com.sabit.duelloquiz.core.model.duel.ForfeitCause
import com.sabit.duelloquiz.core.model.duel.MatchEndReason
import com.sabit.duelloquiz.core.model.duel.MatchOutcome
import com.sabit.duelloquiz.core.model.duel.MatchResult
import com.sabit.duelloquiz.core.model.duel.RoundNext
import com.sabit.duelloquiz.core.model.duel.RoundPlayerResult
import com.sabit.duelloquiz.core.model.duel.RoundResult
import com.sabit.duelloquiz.core.model.duel.SuddenDeathOutcome

/**
 * Pure mapping of [DuelModel] + the current time to [DuelUiState]. Time is the server clock
 * estimate ([serverNow]) and the monotonic clock ([elapsed]); countdowns never go below 0.
 */
internal class DuelUiRenderer(
    private val model: DuelModel,
    private val selfProfile: Profile?,
    private val serverNow: Long,
    private val elapsed: Long,
    private val toElapsed: (Long) -> Long,
) {

    fun render(): DuelUiState = DuelUiState(
        screen = when (model.mode) {
            Mode.SEARCH -> DuelScreen.Search(search())
            Mode.SEARCH_ENDED -> DuelScreen.SearchEnded(model.searchEnd ?: SearchEndReason.TIMEOUT)
            Mode.INTRO -> intro()?.let { DuelScreen.Intro(it) } ?: DuelScreen.Loading(LoadingCaption.REJOINING)
            Mode.MATCH -> match()?.let { DuelScreen.Match(it) } ?: DuelScreen.Loading(LoadingCaption.REJOINING)
            Mode.RESULT -> result()?.let { DuelScreen.Result(it) } ?: DuelScreen.Loading(LoadingCaption.REJOINING)
            Mode.LOADING -> DuelScreen.Loading(model.loading)
            Mode.BLOCKED -> DuelScreen.Blocked(model.blocked ?: BlockedReason.CONNECT_FAILED, model.retrying)
        },
        leaveDialogVisible = model.leaveDialog && model.mode in setOf(Mode.INTRO, Mode.MATCH, Mode.LOADING),
        leaveDialogBot = model.opponentIsBot,
    )

    private fun remainingMs(deadline: Long): Long = (deadline - serverNow).coerceAtLeast(0)

    private fun seconds(ms: Long): Int = ((ms + MS_PER_SECOND - 1) / MS_PER_SECOND).toInt()

    private fun search(): SearchUi {
        val toBot = model.countdownToBot && model.queueDeadline != null
        // duel-search.md: to the bot moment while a bot follows, else (or after the 3 s grace) to
        // the queue deadline. At the bot moment the countdown reads 0 and is hidden.
        val deadline = if (toBot) model.botAt else model.queueDeadline
        val remaining = deadline?.let(::remainingMs) ?: 0
        val searching = model.searchPhase == SearchPhase.SEARCHING && deadline != null
        return SearchUi(
            selfName = selfProfile?.displayName,
            selfAvatarUrl = selfProfile?.avatarUrl,
            selfRating = selfProfile?.rating,
            phase = model.searchPhase,
            showConnecting = model.searchPhase == SearchPhase.CONNECTING &&
                elapsed - model.searchStartedElapsed >= CONNECTING_LABEL_AFTER_MS,
            remainingSeconds = if (searching) seconds(remaining) else 0,
            deadlineElapsedMs = deadline?.let(toElapsed) ?: 0,
            tipIndex = (((elapsed - model.searchStartedElapsed) / TIP_ROTATION_MS) % TIP_COUNT).toInt(),
            cancelling = model.cancelling,
            botHintSeconds = model.botHintSeconds.takeIf { toBot && model.searchPhase == SearchPhase.SEARCHING },
            countdownToBot = toBot,
        )
    }

    private fun intro(): IntroUi? {
        val you = model.you ?: return null
        val opponent = model.opponent ?: return null
        return IntroUi(
            you = you,
            opponent = opponent,
            opponentIsBot = model.opponentIsBot,
            countdown = seconds(remainingMs(model.introDeadline)).coerceIn(1, INTRO_SECONDS),
            overlay = model.overlay,
        )
    }

    // Match

    private fun match(): MatchUi? {
        val round = model.round ?: return null
        val result = round.result
        val suddenDeath = round.suddenDeathNo != null
        return MatchUi(
            roundLabel = RoundLabelUi(round.roundNo, REGULAR_ROUNDS, round.allIn, round.suddenDeathNo, SUDDEN_DEATH_ROUNDS),
            phase = round.phase,
            timer = timer(round),
            self = selfPanel(round),
            opponent = opponentPanel(round),
            roundBanner = when {
                round.phase != DuelPhase.QUESTION -> null
                suddenDeath -> RoundBanner.SUDDEN_DEATH
                round.allIn -> RoundBanner.ALL_IN
                else -> null
            },
            opponentBanner = when (val presence = if (model.opponentIsBot) Presence.Connected else model.presence) {
                is Presence.Disconnected -> OpponentBannerUi.Disconnected(seconds(remainingMs(presence.deadline)))
                Presence.Away -> OpponentBannerUi.StillAway
                is Presence.Returned -> OpponentBannerUi.Returned
                Presence.Connected -> null
            },
            question = round.question?.let { QuestionUi(it.text, it.image, it.categoryName) },
            confidence = if (round.phase == DuelPhase.CONFIDENCE) confidence(round) else null,
            options = options(round),
            answerStatus = answerStatus(round),
            suddenDeathLine = result?.let(::suddenDeathLine),
            reveal = result?.let { RevealUi(it.roundNo, round.revealAnimate, it.you.correct) },
            overlay = model.overlay,
            opponentIsBot = model.opponentIsBot,
        )
    }

    private fun timer(round: RoundModel): TimerUi {
        val nominal = when (round.phase) {
            DuelPhase.INTRO -> NOMINAL_INTRO_MS
            DuelPhase.QUESTION -> NOMINAL_QUESTION_MS
            DuelPhase.CONFIDENCE -> NOMINAL_CONFIDENCE_MS
            DuelPhase.ANSWER -> NOMINAL_ANSWER_MS
            DuelPhase.RESULT -> NOMINAL_RESULT_MS
        }
        val frozen = round.phase == DuelPhase.ANSWER && round.answerSent != null
        val remaining = if (frozen) round.answerFrozenMs ?: 0 else remainingMs(round.deadline)
        val warningAt = when (round.phase) {
            DuelPhase.CONFIDENCE -> WARNING_CONFIDENCE_MS
            DuelPhase.ANSWER -> WARNING_ANSWER_MS
            else -> 0
        }
        val style = when {
            frozen -> TimerStyle.FROZEN
            round.phase == DuelPhase.RESULT -> TimerStyle.NEUTRAL
            remaining == 0L && warningAt > 0 -> TimerStyle.WARNING
            warningAt > 0 && remaining <= warningAt -> TimerStyle.WARNING
            else -> TimerStyle.NORMAL
        }
        return TimerUi(
            remainingSeconds = seconds(remaining),
            fraction = (remaining.toFloat() / nominal).coerceIn(0f, 1f),
            style = style,
            deadlineElapsedMs = if (frozen || remaining == 0L) null else toElapsed(round.deadline),
            nominalMs = nominal,
        )
    }

    private fun ownLevel(round: RoundModel): Int? = when {
        round.suddenDeathNo != null -> 1
        round.ownBet != null -> round.ownBet.bet
        round.betLocked != null -> round.betLocked
        round.phase == DuelPhase.CONFIDENCE && (round.betTooLate || (round.betSent == null && remainingMs(round.deadline) == 0L)) -> 1
        else -> null
    }

    private fun selfPanel(round: RoundModel): PanelUi {
        val result = round.result
        val reconnecting = model.overlay == SelfOverlay.RECONNECTING || model.overlay == SelfOverlay.OFFLINE ||
            model.overlay == SelfOverlay.OFFLINE_RETRYING
        val status = when {
            result != null -> null
            reconnecting -> PanelStatus.DISCONNECTED
            round.phase == DuelPhase.CONFIDENCE -> when {
                round.betLocked != null -> PanelStatus.LOCKED_SELF
                round.betSent != null && !round.betTooLate -> PanelStatus.SENDING
                ownLevel(round) != null -> PanelStatus.LOCKED_SELF
                else -> null
            }
            round.phase == DuelPhase.ANSWER -> when {
                round.answerTooLate -> PanelStatus.TIMEOUT
                round.answerSent != null -> PanelStatus.ANSWERED_SELF
                remainingMs(round.deadline) == 0L -> PanelStatus.TIMEOUT
                else -> null
            }
            else -> null
        }
        return PanelUi(
            isSelf = true,
            name = null,
            avatarUrl = model.you?.avatarUrl,
            score = model.scores.you,
            previousScore = round.scoresBefore.you,
            status = status,
            lockedBet = if (result == null) ownLevel(round) else null,
            reveal = result?.let { face(it.you, it, round) },
            winnerBadge = result?.suddenDeathOutcome == SuddenDeathOutcome.YOU,
        )
    }

    private fun opponentPanel(round: RoundModel): PanelUi {
        val result = round.result
        // A bot never disconnects (duel-interruptions.md H): no presence pills even if a stale state says so.
        val presence = if (model.opponentIsBot) Presence.Connected else model.presence
        val status = when {
            result != null -> null
            presence is Presence.Disconnected || presence == Presence.Away -> PanelStatus.DISCONNECTED
            presence is Presence.Returned -> PanelStatus.RETURNED
            round.phase == DuelPhase.CONFIDENCE ->
                if (round.opponentBetLocked) PanelStatus.LOCKED_OPPONENT else PanelStatus.CHOOSING
            round.phase == DuelPhase.ANSWER ->
                if (round.opponentAnswered) PanelStatus.ANSWERED_OPPONENT else PanelStatus.ANSWERING
            else -> null
        }
        return PanelUi(
            isSelf = false,
            name = model.opponent?.displayName,
            avatarUrl = model.opponent?.avatarUrl,
            score = model.scores.opponent,
            previousScore = round.scoresBefore.opponent,
            status = status,
            showLock = result == null &&
                ((round.phase == DuelPhase.CONFIDENCE && round.opponentBetLocked) ||
                    (round.phase == DuelPhase.ANSWER && round.opponentAnswered)),
            reveal = result?.let { face(it.opponent, it, round) },
            winnerBadge = result?.suddenDeathOutcome == SuddenDeathOutcome.OPPONENT,
            isBot = model.opponentIsBot,
        )
    }

    private fun face(player: RoundPlayerResult, result: RoundResult, round: RoundModel): RevealFaceUi {
        val index = round.options.indexOfFirst { it.id == player.optionId }
        return RevealFaceUi(
            kind = when {
                player.missed -> RevealKind.MISSED
                player.optionId == null -> RevealKind.TIMEOUT
                player.correct -> RevealKind.CORRECT
                else -> RevealKind.WRONG
            },
            bet = player.bet,
            answerLetter = if (index >= 0) LETTERS[index].toString() else null,
            points = player.points - player.speedBonus,
            speedBonus = player.speedBonus,
            showPoints = !result.suddenDeath,
        )
    }

    private fun confidence(round: RoundModel): ConfidenceUi {
        val expired = remainingMs(round.deadline) == 0L
        val chosen = round.betLocked ?: round.betSent?.takeIf { !round.betTooLate }
        val defaulted = round.betTooLate || (expired && chosen == null)
        val multiplier = if (round.allIn) ALL_IN_MULTIPLIER else 1
        val chips = (1..MAX_LEVEL).map { level ->
            val state = when {
                defaulted -> if (level == 1) ChipState.DEFAULTED else ChipState.FADED
                chosen != null -> when {
                    level != chosen -> ChipState.FADED
                    round.betLocked != null -> ChipState.LOCKED
                    else -> ChipState.SELECTED
                }
                level == MAX_LEVEL && round.bet3Left == 0 -> ChipState.DISABLED_LIMIT
                expired || model.overlay != null -> ChipState.DISABLED
                else -> ChipState.ENABLED
            }
            ChipUi(level, gain = GAIN[level - 1] * multiplier, loss = LOSS[level - 1] * multiplier, state = state)
        }
        val status = when {
            round.betTooLate -> ConfidenceStatus.TOO_LATE
            defaulted -> ConfidenceStatus.DEFAULTED
            round.betLocked != null ->
                if (!round.opponentBetLocked && elapsed - round.betLockedAtElapsed >= LOCKED_TEXT_MS) {
                    ConfidenceStatus.WAITING_OPPONENT
                } else {
                    ConfidenceStatus.LOCKED
                }
            round.betSent != null -> ConfidenceStatus.SENDING
            round.betLimitError -> ConfidenceStatus.LIMIT_REACHED
            else -> null
        }
        return ConfidenceUi(
            allIn = round.allIn,
            bet3Left = round.bet3Left,
            chips = chips,
            status = status,
            interactive = chips.any { it.state == ChipState.ENABLED },
        )
    }

    private fun options(round: RoundModel): List<DuelOptionUi> {
        if (round.phase != DuelPhase.ANSWER && round.phase != DuelPhase.RESULT) return emptyList()
        val result = round.result
        val open = round.phase == DuelPhase.ANSWER && round.answerSent == null &&
            remainingMs(round.deadline) > 0 && model.overlay == null
        return round.options.mapIndexed { index, option ->
            if (result != null) {
                val own = result.you.optionId == option.id
                val opponent = result.opponent.optionId == option.id
                val state = when {
                    option.id == result.correctOptionId -> OptionCardState.Correct
                    own -> OptionCardState.Wrong
                    opponent -> OptionCardState.Wrong
                    else -> OptionCardState.Dimmed
                }
                DuelOptionUi(option.id, LETTERS[index].toString(), option.text, state, false, own, opponent)
            } else {
                val state = when {
                    round.answerSent == option.id && !round.answerTooLate -> OptionCardState.Selected
                    open -> OptionCardState.Default
                    else -> OptionCardState.Dimmed
                }
                DuelOptionUi(option.id, LETTERS[index].toString(), option.text, state, open)
            }
        }
    }

    private fun answerStatus(round: RoundModel): AnswerStatus? = when {
        round.phase != DuelPhase.ANSWER -> null
        round.answerTooLate -> AnswerStatus.TOO_LATE
        round.answerSent != null -> AnswerStatus.SENT
        remainingMs(round.deadline) == 0L -> AnswerStatus.TIMEOUT
        else -> null
    }

    private fun suddenDeathLine(result: RoundResult): SuddenDeathLine? = when (result.suddenDeathOutcome) {
        null -> null
        SuddenDeathOutcome.YOU -> SuddenDeathLine.YOU_WON
        SuddenDeathOutcome.OPPONENT -> SuddenDeathLine.OPPONENT_WON
        SuddenDeathOutcome.SAME_TIME -> SuddenDeathLine.SAME_TIME
        SuddenDeathOutcome.NONE -> if (result.next == RoundNext.END) SuddenDeathLine.NONE_LAST else SuddenDeathLine.NONE
    }

    // Result

    private fun result(): ResultUi? {
        val result = model.result ?: return null
        val abandoned = result.outcome == MatchOutcome.ABANDONED
        return ResultUi(
            matchId = result.matchId,
            outcome = result.outcome,
            reason = reason(result),
            you = model.you,
            opponent = model.opponent,
            scores = result.scores,
            ratingAfter = result.ratingAfter,
            ratingChange = result.ratingChange,
            calibration = if (abandoned) null else result.calibration,
            hint = if (abandoned) CalibrationHint.NEUTRAL else CalibrationRules.cardHint(result.calibration),
            winner = when (result.outcome) {
                MatchOutcome.WIN -> DuelSide.SELF
                MatchOutcome.LOSS -> DuelSide.OPPONENT
                else -> null
            },
            playAgainLoading = model.playAgainLoading,
            progress = model.progressCard.takeUnless { abandoned },
            opponentIsBot = model.opponentIsBot,
        )
    }

    private fun reason(result: MatchResult): ResultReason? = resultReason(result)

    companion object {
        const val REGULAR_ROUNDS = 7
        const val SUDDEN_DEATH_ROUNDS = 3
        const val MAX_LEVEL = 3
        const val ALL_IN_MULTIPLIER = 2
        const val INTRO_SECONDS = 3
        const val TIP_COUNT = 3

        /** docs/mvp-spec.md §2 stakes shown on the chips: correct +100/+200/+300, wrong 0/−100/−200. */
        val GAIN = intArrayOf(100, 200, 300)
        val LOSS = intArrayOf(0, 100, 200)

        const val LETTERS = "ABCD"
        private const val MS_PER_SECOND = 1000L
        private const val CONNECTING_LABEL_AFTER_MS = 1_000L
        private const val TIP_ROTATION_MS = 10_000L
        private const val LOCKED_TEXT_MS = 1_500L
        private const val NOMINAL_INTRO_MS = 3_000
        private const val NOMINAL_QUESTION_MS = 3_000
        private const val NOMINAL_CONFIDENCE_MS = 5_000
        private const val NOMINAL_ANSWER_MS = 10_000
        private const val NOMINAL_RESULT_MS = 3_000
        private const val WARNING_CONFIDENCE_MS = 2_000L
        private const val WARNING_ANSWER_MS = 3_000L
    }
}

/**
 * The reason line of the result hero (duel-result.md, az.md `duel_result_reason_*`). A forfeit
 * names its cause (`forfeitCause`); `null` (rebuilt old forfeit) falls back to a generic line.
 */
internal fun resultReason(result: MatchResult): ResultReason? = when (result.outcome) {
    MatchOutcome.WIN -> when (result.endReason) {
        MatchEndReason.SUDDEN_DEATH -> ResultReason.SUDDEN_DEATH_WIN
        MatchEndReason.FORFEIT -> when (result.forfeitCause) {
            ForfeitCause.LEFT -> ResultReason.OPPONENT_LEFT
            ForfeitCause.MISSED_ROUNDS -> ResultReason.OPPONENT_MISSED_ROUNDS
            null -> ResultReason.OPPONENT_FORFEIT
        }
        else -> null
    }
    MatchOutcome.LOSS -> when (result.endReason) {
        MatchEndReason.SUDDEN_DEATH -> ResultReason.SUDDEN_DEATH_LOSS
        MatchEndReason.FORFEIT -> when (result.forfeitCause) {
            ForfeitCause.MISSED_ROUNDS -> ResultReason.SELF_FORFEIT
            ForfeitCause.LEFT -> ResultReason.SELF_LEFT
            null -> ResultReason.SELF_FORFEIT_UNKNOWN
        }
        else -> ResultReason.LOSS_MESSAGE
    }
    MatchOutcome.DRAW -> ResultReason.DRAW
    MatchOutcome.ABANDONED -> ResultReason.ABANDONED
}

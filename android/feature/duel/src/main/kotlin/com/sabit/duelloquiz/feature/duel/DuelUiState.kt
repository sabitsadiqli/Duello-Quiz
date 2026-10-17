package com.sabit.duelloquiz.feature.duel

import androidx.compose.runtime.Immutable
import com.sabit.duelloquiz.core.designsystem.component.OptionCardState
import com.sabit.duelloquiz.core.model.QuestionImage
import com.sabit.duelloquiz.core.model.duel.CalibrationHint
import com.sabit.duelloquiz.core.model.duel.CalibrationLevel
import com.sabit.duelloquiz.core.model.duel.DuelPhase
import com.sabit.duelloquiz.core.model.duel.DuelPlayer
import com.sabit.duelloquiz.core.model.duel.DuelScores
import com.sabit.duelloquiz.core.model.duel.DuelSide
import com.sabit.duelloquiz.core.model.duel.MatchOutcome

/** Whole duel flow (one destination): search → intro → match → result, plus the full-screen states. */
@Immutable
data class DuelUiState(
    val screen: DuelScreen = DuelScreen.Search(SearchUi()),
    /** DUEL-15 dialog over the intro or the match; timers keep running behind it. */
    val leaveDialogVisible: Boolean = false,
)

@Immutable
sealed interface DuelScreen {
    data class Search(val search: SearchUi) : DuelScreen

    /** Body replaced by a state view (duel-search.md). */
    data class SearchEnded(val reason: SearchEndReason, val retrying: Boolean = false) : DuelScreen

    data class Intro(val intro: IntroUi) : DuelScreen

    data class Match(val match: MatchUi) : DuelScreen

    data class Result(val result: ResultUi) : DuelScreen

    /** Full-screen loading: after process death ("Oyuna qayıdırsan…") or `already_in_match`. */
    data class Loading(val caption: LoadingCaption) : DuelScreen

    /** duel-availability.md and duel-interruptions.md F/G. */
    data class Blocked(val reason: BlockedReason, val retrying: Boolean = false) : DuelScreen
}

enum class SearchEndReason { TIMEOUT, STOPPED, DISCONNECTED }

enum class LoadingCaption { REJOINING, ALREADY_IN_MATCH }

enum class BlockedReason { UPGRADE_REQUIRED, UNAVAILABLE, CONNECT_FAILED, SESSION_REPLACED, MATCH_LOST }

enum class SearchPhase {
    /** Handshake or `queue.join` in flight; after 1 s the title reads "Qoşulur…". */
    CONNECTING,

    /** `queue.joined`: countdown to the server deadline. */
    SEARCHING,

    /** Local countdown at 0, no server answer yet (up to 5 s more, DUEL-03). */
    OVERTIME,
}

@Immutable
data class SearchUi(
    val selfName: String? = null,
    val selfAvatarUrl: String? = null,
    val selfRating: Int? = null,
    val phase: SearchPhase = SearchPhase.CONNECTING,
    /** "Qoşulur…" instead of "Rəqib axtarılır…" (join slower than 1 s). */
    val showConnecting: Boolean = false,
    val remainingSeconds: Int = 0,
    /** Queue deadline on the monotonic clock, for the draining bar. */
    val deadlineElapsedMs: Long = 0,
    val tipIndex: Int = 0,
    val cancelling: Boolean = false,
)

@Immutable
data class IntroUi(
    val you: DuelPlayer,
    val opponent: DuelPlayer,
    /** 3 → 2 → 1; stays 1 until round 1 arrives. */
    val countdown: Int,
    val overlay: SelfOverlay? = null,
)

/** Own connection problems over the intro/match body (duel-interruptions.md A–C). */
enum class SelfOverlay {
    /** First 10 s: automatic reconnect. */
    RECONNECTING,

    /** After 10 s: "Bağlantı yoxdur" with "Yenidən qoşul" and "Oyundan çıx". */
    OFFLINE,

    /** Same card while "Yenidən qoşul" runs. */
    OFFLINE_RETRYING,

    /** Connected again, waiting for `match.state`. */
    REJOINING,
}

@Immutable
data class RoundLabelUi(
    val roundNo: Int,
    val totalRounds: Int,
    val allIn: Boolean,
    /** Non-null in sudden death: 1–3. */
    val suddenDeathNo: Int?,
    val suddenDeathTotal: Int,
)

enum class TimerStyle { NORMAL, WARNING, FROZEN, NEUTRAL }

@Immutable
data class TimerUi(
    val remainingSeconds: Int,
    /** Fraction of the nominal phase length left (0–1), for the ring. */
    val fraction: Float,
    val style: TimerStyle,
    /** Deadline on the monotonic clock while running; null when frozen or expired. */
    val deadlineElapsedMs: Long?,
    val nominalMs: Int,
)

/** Small status pill under a player (components.md §17). */
enum class PanelStatus {
    CHOOSING,
    ANSWERING,
    SENDING,
    LOCKED_SELF,
    LOCKED_OPPONENT,
    ANSWERED_SELF,
    ANSWERED_OPPONENT,
    TIMEOUT,
    DISCONNECTED,
    RETURNED,
}

enum class RevealKind { CORRECT, WRONG, TIMEOUT, MISSED }

@Immutable
data class RevealFaceUi(
    val kind: RevealKind,
    val bet: Int,
    /** A–D of the chosen option; null for timeout / missed. */
    val answerLetter: String?,
    /** Əminlik points of the round (bonus excluded). */
    val points: Int,
    val speedBonus: Int,
    /** False in sudden death (scores do not change). */
    val showPoints: Boolean,
)

@Immutable
data class PanelUi(
    val isSelf: Boolean,
    /** Opponent display name; null for self ("Sən"). */
    val name: String?,
    val avatarUrl: String?,
    val score: Int,
    /** Score before the current reveal (count-up start). */
    val previousScore: Int,
    val status: PanelStatus? = null,
    /** Own locked level shown as the mini badge before the reveal. */
    val lockedBet: Int? = null,
    /** Opponent lock icon before the reveal. */
    val showLock: Boolean = false,
    val reveal: RevealFaceUi? = null,
    val winnerBadge: Boolean = false,
)

enum class RoundBanner { ALL_IN, SUDDEN_DEATH }

@Immutable
sealed interface OpponentBannerUi {
    data class Disconnected(val secondsLeft: Int) : OpponentBannerUi

    data object StillAway : OpponentBannerUi

    data object Returned : OpponentBannerUi
}

enum class ChipState { ENABLED, SELECTED, LOCKED, DEFAULTED, FADED, DISABLED_LIMIT, DISABLED }

@Immutable
data class ChipUi(
    val level: Int,
    /** Points on a correct answer (doubled in round 7). */
    val gain: Int,
    /** Points lost on a wrong answer, as a positive number (0 for level 1). */
    val loss: Int,
    val state: ChipState,
)

enum class ConfidenceStatus { SENDING, LOCKED, WAITING_OPPONENT, DEFAULTED, TOO_LATE, LIMIT_REACHED }

@Immutable
data class ConfidenceUi(
    val allIn: Boolean,
    val bet3Left: Int,
    val chips: List<ChipUi>,
    val status: ConfidenceStatus?,
    val interactive: Boolean,
)

@Immutable
data class DuelOptionUi(
    val id: String,
    val letter: String,
    val text: String,
    val state: OptionCardState,
    val enabled: Boolean,
    val pickedBySelf: Boolean = false,
    val pickedByOpponent: Boolean = false,
)

enum class AnswerStatus { SENT, TIMEOUT, TOO_LATE }

enum class SuddenDeathLine { YOU_WON, OPPONENT_WON, SAME_TIME, NONE, NONE_LAST }

@Immutable
data class QuestionUi(
    val text: String,
    val image: QuestionImage?,
    val categoryName: String,
)

@Immutable
data class RevealUi(
    /** Changes per round: drives the one-time flip. */
    val roundNo: Int,
    /** False when less than 1.5 s was left (late message, rejoin): final state with a cross-fade. */
    val animate: Boolean,
    val outcomeCorrect: Boolean,
)

@Immutable
data class MatchUi(
    val roundLabel: RoundLabelUi,
    val phase: DuelPhase,
    val timer: TimerUi,
    val self: PanelUi,
    val opponent: PanelUi,
    val roundBanner: RoundBanner? = null,
    val opponentBanner: OpponentBannerUi? = null,
    val question: QuestionUi? = null,
    val confidence: ConfidenceUi? = null,
    val options: List<DuelOptionUi> = emptyList(),
    val answerStatus: AnswerStatus? = null,
    val suddenDeathLine: SuddenDeathLine? = null,
    val reveal: RevealUi? = null,
    val overlay: SelfOverlay? = null,
)

enum class ResultReason {
    SUDDEN_DEATH_WIN,
    SUDDEN_DEATH_LOSS,
    DRAW,
    OPPONENT_FORFEIT,
    SELF_FORFEIT,
    ABANDONED,
    LOSS_MESSAGE,
}

@Immutable
data class ResultUi(
    val matchId: String,
    val outcome: MatchOutcome,
    val reason: ResultReason?,
    val you: DuelPlayer?,
    val opponent: DuelPlayer?,
    val scores: DuelScores,
    val ratingAfter: Int,
    val ratingChange: Int,
    /** Null for abandoned matches (no Əminlik kartı, DUEL-21). */
    val calibration: List<CalibrationLevel>?,
    val hint: CalibrationHint,
    val winner: DuelSide?,
    val playAgainLoading: Boolean = false,
)

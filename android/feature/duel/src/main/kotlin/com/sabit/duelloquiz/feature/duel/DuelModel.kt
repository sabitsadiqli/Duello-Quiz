package com.sabit.duelloquiz.feature.duel

import com.sabit.duelloquiz.core.designsystem.component.ProgressCardUi
import com.sabit.duelloquiz.core.model.QuizOption
import com.sabit.duelloquiz.core.model.duel.DuelPhase
import com.sabit.duelloquiz.core.model.duel.DuelPlayer
import com.sabit.duelloquiz.core.model.duel.DuelQuestion
import com.sabit.duelloquiz.core.model.duel.DuelScores
import com.sabit.duelloquiz.core.model.duel.MatchResult
import com.sabit.duelloquiz.core.model.duel.MatchResultPlayer
import com.sabit.duelloquiz.core.model.duel.OpponentType
import com.sabit.duelloquiz.core.model.duel.OwnBet
import com.sabit.duelloquiz.core.model.duel.RoundResult

// What the duel ViewModel knows, without time-derived values (those are computed by
// DuelUiRenderer from the server deadlines and the clock offset on every tick).

internal enum class Mode { SEARCH, SEARCH_ENDED, INTRO, MATCH, RESULT, LOADING, BLOCKED }

/** The opponent's connection (DUEL-13). */
internal sealed interface Presence {
    data object Connected : Presence

    /** [deadline] = `reconnectDeadlineEpochMs` (server clock). */
    data class Disconnected(val deadline: Long) : Presence

    /** Not back after the 10 s window (or unknown deadline after a rejoin). */
    data object Away : Presence

    /** "Rəqib qayıtdı." until [untilElapsed] (monotonic). */
    data class Returned(val untilElapsed: Long) : Presence
}

/**
 * Presence after a `match.state` (DUEL-13): an opponent who is away with a reconnect window still
 * running keeps the countdown from `opponent.reconnectDeadlineEpochMs`; an expired or unknown
 * window is [Presence.Away]. A running "Rəqib qayıtdı." stays.
 */
internal fun snapshotPresence(
    connected: Boolean,
    reconnectDeadline: Long?,
    serverNow: Long,
    current: Presence?,
): Presence = when {
    !connected && reconnectDeadline != null && reconnectDeadline > serverNow -> Presence.Disconnected(reconnectDeadline)
    !connected -> Presence.Away
    current is Presence.Returned -> current
    else -> Presence.Connected
}

/** `match.result.you/opponent` as a result-screen player; null for a deleted account. */
internal fun MatchResultPlayer.toDuelPlayer(): DuelPlayer? =
    displayName?.takeUnless { deleted }?.let { DuelPlayer(it, avatarUrl, ratingAfter) }

internal data class RoundModel(
    val roundNo: Int,
    val allIn: Boolean,
    val suddenDeathNo: Int?,
    val question: DuelQuestion?,
    val phase: DuelPhase,
    /** End of the current phase (server clock). */
    val deadline: Long,
    val bet3Left: Int,
    /** Level the player tapped (sent once). */
    val betSent: Int? = null,
    /** Level the server confirmed (`round.betLocked {self}`). */
    val betLocked: Int? = null,
    val betLockedAtElapsed: Long = 0,
    val betTooLate: Boolean = false,
    val betLimitError: Boolean = false,
    val opponentBetLocked: Boolean = false,
    val options: List<QuizOption> = emptyList(),
    /** The own final pick from `round.options` (or the snapshot). */
    val ownBet: OwnBet? = null,
    val answerSent: String? = null,
    /** Countdown value when the own answer was tapped (the ring freezes there). */
    val answerFrozenMs: Long? = null,
    val answerLocked: Boolean = false,
    val answerTooLate: Boolean = false,
    val opponentAnswered: Boolean = false,
    val result: RoundResult? = null,
    val revealAnimate: Boolean = false,
    /** Totals before this round's reveal (count-up start). */
    val scoresBefore: DuelScores,
)

internal data class DuelModel(
    val mode: Mode = Mode.SEARCH,
    val searchPhase: SearchPhase = SearchPhase.CONNECTING,
    val searchStartedElapsed: Long = 0,
    /** True once the user asked for a search in this flow (not a pure resume). */
    val searchRequested: Boolean = false,
    val queueDeadline: Long? = null,
    /** `queue.joined.botAtEpochMs` (server clock); null = no bot for this entry (OQ-111). */
    val botAt: Long? = null,
    /** Seconds shown in the bot hint, fixed when `queue.joined` arrived. */
    val botHintSeconds: Int? = null,
    /** No `match.found` by bot moment + 3 s: back to the queue deadline (duel-search.md). */
    val botFallback: Boolean = false,
    val overtimeSince: Long? = null,
    val cancelling: Boolean = false,
    val searchEnd: SearchEndReason? = null,
    val blocked: BlockedReason? = null,
    val retrying: Boolean = false,
    val loading: LoadingCaption = LoadingCaption.REJOINING,
    val matchId: String? = null,
    val you: DuelPlayer? = null,
    val opponent: DuelPlayer? = null,
    /** v1.2 `opponentType` of the current match: the only bot signal (never the name). */
    val opponentType: OpponentType = OpponentType.HUMAN,
    val introDeadline: Long = 0,
    val round: RoundModel? = null,
    val scores: DuelScores = DuelScores(0, 0),
    val presence: Presence = Presence.Connected,
    val overlay: SelfOverlay? = null,
    val result: MatchResult? = null,
    /** `match.result` that waits for the last reveal to end (duel-reveal.md "Last round"). */
    val pendingResult: MatchResult? = null,
    val playAgainLoading: Boolean = false,
    /** XP, level and streak card of the shown result (PROG-02); null = not rendered. */
    val progressCard: ProgressCardUi? = null,
    val leaveDialog: Boolean = false,
) {
    val opponentIsBot: Boolean get() = opponentType == OpponentType.BOT

    /** Searching with a bot ahead: countdown, bar and hint run to [botAt]. */
    val countdownToBot: Boolean get() = botAt != null && !botFallback
}

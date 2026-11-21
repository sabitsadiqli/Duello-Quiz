package com.sabit.duelloquiz.feature.exams.audio

/**
 * The listening rules of an attempt (OQ-119, OQ-D43, components.md §42) as a pure state machine:
 * [maxPlays] plays; a play runs from 0 to the end; pause / resume inside a play; no seek, no
 * restart; a play **counts** once [countsAfterMs] of it were played (paused time and wall-clock
 * jumps do not count — only the advance of the playback position does). An app kill during a
 * counted play resumes that play paused at the saved position; a play killed before it counted is
 * not counted and starts over. An audio failure never consumes a play. Unit-tested.
 */
data class ListeningPlayState(
    val maxPlays: Int,
    val countsAfterMs: Long,
    /** Counted plays (what the server stores as `playsUsed`). */
    val playsUsed: Int = 0,
    /** A play is in progress (playing or paused inside it). */
    val playActive: Boolean = false,
    /** The current play has been counted (≥ [countsAfterMs] played). */
    val currentCounted: Boolean = false,
    /** Played time of the current play (position advance while playing). */
    val currentPlayedMs: Long = 0,
    val positionMs: Long = 0,
    /** Restored after an app kill mid-play (shows `exam_audio_restored`). */
    val restored: Boolean = false,
    val audioFailed: Boolean = false,
) {
    /** True when no new play may start (all plays used and none in progress). */
    val exhausted: Boolean get() = !playActive && playsUsed >= maxPlays

    /** "Dinləmə n / max": the number of the running (or paused) play. */
    val currentPlayNumber: Int get() = if (currentCounted) playsUsed else (playsUsed + 1).coerceAtMost(maxPlays)

    val playsLeft: Int get() = (maxPlays - playsUsed - if (playActive && !currentCounted) 1 else 0).coerceAtLeast(0)

    /** "10 saniyədən sonra sayılacaq" while a play runs and has not counted yet. */
    val showCountsAfterCaption: Boolean get() = playActive && !currentCounted

    /** Starts a new play from 0 or resumes the current one. Null when not allowed. */
    fun play(): ListeningPlayState? = when {
        playActive -> copy(restored = false)
        playsUsed >= maxPlays -> null
        else -> copy(playActive = true, currentCounted = false, currentPlayedMs = 0, positionMs = 0, restored = false)
    }

    /**
     * The player moved to [newPositionMs] while playing. Returns the new state and whether this
     * update counted the play (sync it at once).
     */
    fun advance(newPositionMs: Long): Pair<ListeningPlayState, Boolean> {
        if (!playActive) return this to false
        val delta = newPositionMs - positionMs
        // Only a forward step of a plausible size is played time (no seek exists in this mode).
        val played = if (delta in 1..MAX_STEP_MS) delta else 0
        val playedTotal = currentPlayedMs + played
        val countsNow = !currentCounted && playedTotal >= countsAfterMs
        val next = copy(
            positionMs = newPositionMs.coerceAtLeast(0),
            currentPlayedMs = playedTotal,
            currentCounted = currentCounted || countsNow,
            playsUsed = if (countsNow) playsUsed + 1 else playsUsed,
        )
        return next to countsNow
    }

    /** The file played to its end: the play is over (counted even if the file is shorter than the threshold). */
    fun ended(): ListeningPlayState {
        if (!playActive) return this
        return copy(
            playActive = false,
            currentCounted = false,
            currentPlayedMs = 0,
            positionMs = 0,
            playsUsed = if (currentCounted) playsUsed else (playsUsed + 1).coerceAtMost(maxPlays),
        )
    }

    /** Playback failed (EXAM-10): plays are not consumed; an uncounted play is undone. */
    fun failed(): ListeningPlayState = copy(
        audioFailed = true,
        playActive = playActive && currentCounted,
    )

    /** A retry after a failure (re-download succeeded). */
    fun recovered(): ListeningPlayState = copy(audioFailed = false)

    /** What is persisted every second (Room, then `PUT …/listening`). */
    val persistedPlayInProgress: Boolean get() = playActive && currentCounted

    companion object {
        /** Larger jumps (resume after a stall, restore) are not counted as played time. */
        const val MAX_STEP_MS = 2_000L

        /**
         * Restores the state saved before an app kill or on another device (OQ-D43): a counted play
         * in progress resumes paused at [positionMs]; anything else starts from the next play.
         */
        fun restore(
            maxPlays: Int,
            countsAfterMs: Long,
            playsUsed: Int,
            playInProgress: Boolean,
            positionMs: Long,
            audioFailed: Boolean,
        ): ListeningPlayState {
            val resume = playInProgress && playsUsed in 1..maxPlays && positionMs > 0
            return ListeningPlayState(
                maxPlays = maxPlays,
                countsAfterMs = countsAfterMs,
                playsUsed = playsUsed.coerceIn(0, maxPlays),
                playActive = resume,
                currentCounted = resume,
                currentPlayedMs = if (resume) countsAfterMs else 0,
                positionMs = if (resume) positionMs else 0,
                restored = resume,
                audioFailed = audioFailed,
            )
        }
    }
}

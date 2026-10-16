package com.sabit.duelloquiz.core.duel

/**
 * Server clock estimate for countdowns (DUEL-12, duel-protocol.md §1.3).
 *
 * Every server message gives a sample `offset = serverNowEpochMs − deviceNowAtReceipt`, with the
 * device time taken from the monotonic clock, so changing the phone time never matters. A sample is
 * always too small by the one-way latency, so the estimate is the largest sample of the last
 * [WINDOW] messages (latency spikes are ignored). When the estimate has to go down (old maximum left
 * the window, server clock adjusted), it moves by at most [MAX_STEP_DOWN_MS] per message, so a
 * visible countdown never jumps up by more than 1 s.
 */
class DuelClockSync {

    private val samples = ArrayDeque<Long>()
    private var offset: Long? = null

    /** Offset in ms (server epoch − monotonic ms), or null before the first message. */
    val offsetMs: Long? get() = offset

    fun sample(serverNowEpochMs: Long, receivedAtElapsedMs: Long) {
        samples.addLast(serverNowEpochMs - receivedAtElapsedMs)
        while (samples.size > WINDOW) samples.removeFirst()
        val best = samples.max()
        val current = offset
        offset = when {
            current == null || best >= current -> best
            else -> maxOf(best, current - MAX_STEP_DOWN_MS)
        }
    }

    /** Server time now; before the first sample the monotonic time itself (no deadline exists yet). */
    fun serverNow(elapsedMs: Long): Long = elapsedMs + (offset ?: 0)

    /** A server deadline on the monotonic clock (for frame-driven countdown drawing). */
    fun toElapsed(serverEpochMs: Long): Long = serverEpochMs - (offset ?: 0)

    fun reset() {
        samples.clear()
        offset = null
    }

    companion object {
        const val WINDOW = 10
        const val MAX_STEP_DOWN_MS = 1_000L
    }
}

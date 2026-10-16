package com.sabit.duelloquiz.backend.quiz

import java.time.Duration
import java.time.Instant
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

sealed interface RateLimitDecision {
    data object Allowed : RateLimitDecision
    data class Denied(val retryAfter: Duration) : RateLimitDecision
}

/**
 * Counts events per key. The MVP uses [InMemorySlidingWindowRateLimiter] (one server
 * instance); a shared (e.g. Redis) implementation can replace it later.
 */
interface RateLimiter {
    /** Records an event for [key] if allowed; denied attempts are not recorded. */
    fun tryAcquire(key: String, now: Instant): RateLimitDecision
}

/** At most [limit] events per [window] per key, sliding window, in memory. */
class InMemorySlidingWindowRateLimiter(
    private val limit: Int,
    private val window: Duration,
) : RateLimiter {
    private val events = ConcurrentHashMap<String, ArrayDeque<Instant>>()
    private val calls = AtomicLong()

    init {
        require(limit > 0) { "limit must be positive" }
    }

    override fun tryAcquire(key: String, now: Instant): RateLimitDecision {
        if (calls.incrementAndGet() % CLEANUP_EVERY == 0L) cleanup(now)
        val queue = events.computeIfAbsent(key) { ArrayDeque() }
        synchronized(queue) {
            val windowStart = now.minus(window)
            while (queue.isNotEmpty() && !queue.peekFirst().isAfter(windowStart)) queue.pollFirst()
            if (queue.size >= limit) {
                val retryAfter = Duration.between(now, queue.peekFirst().plus(window))
                return RateLimitDecision.Denied(if (retryAfter.isNegative) Duration.ZERO else retryAfter)
            }
            queue.addLast(now)
            return RateLimitDecision.Allowed
        }
    }

    /** Drops keys whose newest event is outside the window, so memory stays bounded. */
    private fun cleanup(now: Instant) {
        val windowStart = now.minus(window)
        events.entries.removeIf { (_, queue) ->
            synchronized(queue) { queue.isEmpty() || !queue.peekLast().isAfter(windowStart) }
        }
    }

    private companion object {
        const val CLEANUP_EVERY = 1_000L
    }
}

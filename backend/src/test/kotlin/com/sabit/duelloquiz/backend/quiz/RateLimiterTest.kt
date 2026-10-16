package com.sabit.duelloquiz.backend.quiz

import com.sabit.duelloquiz.backend.common.QuizConfig
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RateLimiterTest {
    private val start = Instant.parse("2026-10-16T10:00:00Z")

    @Test
    fun `allows the limit per window, then denies with retry-after`() {
        val limiter = InMemorySlidingWindowRateLimiter(limit = 30, window = Duration.ofHours(1))
        repeat(30) { i -> assertEquals(RateLimitDecision.Allowed, limiter.tryAcquire("u1", start.plusSeconds(i * 60L))) }
        val denied = limiter.tryAcquire("u1", start.plusSeconds(30 * 60L))
        assertIs<RateLimitDecision.Denied>(denied)
        assertEquals(Duration.ofMinutes(30), denied.retryAfter)
        // Other users are independent.
        assertEquals(RateLimitDecision.Allowed, limiter.tryAcquire("u2", start))
    }

    @Test
    fun `window slides and denied attempts are not counted`() {
        val limiter = InMemorySlidingWindowRateLimiter(limit = 2, window = Duration.ofHours(1))
        assertEquals(RateLimitDecision.Allowed, limiter.tryAcquire("u", start))
        assertEquals(RateLimitDecision.Allowed, limiter.tryAcquire("u", start.plusSeconds(10)))
        repeat(5) { assertIs<RateLimitDecision.Denied>(limiter.tryAcquire("u", start.plusSeconds(20))) }
        // Exactly one hour after the first event it has left the window.
        assertEquals(RateLimitDecision.Allowed, limiter.tryAcquire("u", start.plus(Duration.ofHours(1))))
        assertIs<RateLimitDecision.Denied>(limiter.tryAcquire("u", start.plus(Duration.ofHours(1)).plusSeconds(1)))
    }

    @Test
    fun `quiz deadline is 245 seconds with the default numbers`() {
        assertEquals(Duration.ofSeconds(245), QuizConfig().sessionDuration)
    }
}

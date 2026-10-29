package com.sabit.duelloquiz.backend.duel

import java.util.UUID

/** [botAtMs]: when a still lone entry is paired with the bot (§7a); null = never (not capable or bot disabled). */
data class QueueEntry(val userId: UUID, val joinedAtMs: Long, val deadlineMs: Long, val ticket: Long, val botAtMs: Long? = null)

/**
 * The global matchmaking queue (docs/mvp-spec.md §2): first come, first served, one
 * entry per user. The MVP runs on a single instance ([InMemoryMatchmakingQueue]); a
 * Redis implementation can replace it later without changing the callers.
 * Callers serialize access (DuelService holds a lock).
 */
interface MatchmakingQueue {
    /**
     * Adds [userId] at the back; an existing entry is returned unchanged (idempotent).
     * [botWaitMs] non-null makes the entry bot-eligible at `now + botWaitMs`.
     */
    suspend fun join(userId: UUID, nowMs: Long, timeoutMs: Long, botWaitMs: Long? = null): QueueEntry

    /** Adds [userId] at the front with a new deadline (opponent of a cancelled match, OQ-037). */
    suspend fun joinFront(userId: UUID, nowMs: Long, timeoutMs: Long, botWaitMs: Long? = null): QueueEntry

    /** Removes the entry of [userId]; returns it, or null if the user was not queued. */
    suspend fun leave(userId: UUID): QueueEntry?

    suspend fun entry(userId: UUID): QueueEntry?

    /** Removes and returns the two oldest entries of different users, or null if fewer than two. */
    suspend fun pollPair(): Pair<QueueEntry, QueueEntry>?

    suspend fun size(): Int
}

class InMemoryMatchmakingQueue : MatchmakingQueue {
    private val entries = ArrayDeque<QueueEntry>()
    private var nextTicket = 1L

    override suspend fun join(userId: UUID, nowMs: Long, timeoutMs: Long, botWaitMs: Long?): QueueEntry {
        entries.firstOrNull { it.userId == userId }?.let { return it }
        return QueueEntry(userId, nowMs, nowMs + timeoutMs, nextTicket++, botWaitMs?.let { nowMs + it }).also { entries.addLast(it) }
    }

    override suspend fun joinFront(userId: UUID, nowMs: Long, timeoutMs: Long, botWaitMs: Long?): QueueEntry {
        entries.removeAll { it.userId == userId }
        return QueueEntry(userId, nowMs, nowMs + timeoutMs, nextTicket++, botWaitMs?.let { nowMs + it }).also { entries.addFirst(it) }
    }

    override suspend fun leave(userId: UUID): QueueEntry? {
        val entry = entries.firstOrNull { it.userId == userId } ?: return null
        entries.remove(entry)
        return entry
    }

    override suspend fun entry(userId: UUID): QueueEntry? = entries.firstOrNull { it.userId == userId }

    override suspend fun pollPair(): Pair<QueueEntry, QueueEntry>? {
        if (entries.size < 2) return null
        val first = entries.first()
        // Entries are unique per user, but never pair a user with themselves anyway (DUEL-04).
        val second = entries.drop(1).firstOrNull { it.userId != first.userId } ?: return null
        entries.remove(first)
        entries.remove(second)
        return first to second
    }

    override suspend fun size(): Int = entries.size
}

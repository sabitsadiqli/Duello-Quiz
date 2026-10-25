package com.sabit.duelloquiz.backend.testsupport

import com.sabit.duelloquiz.backend.auth.AuthAccountDeleter
import com.sabit.duelloquiz.backend.auth.AuthProviderException
import com.sabit.duelloquiz.backend.auth.AuthUser
import com.sabit.duelloquiz.backend.auth.InvalidTokenException
import com.sabit.duelloquiz.backend.auth.TokenVerifier
import com.sabit.duelloquiz.backend.notification.PushMessage
import com.sabit.duelloquiz.backend.notification.PushResult
import com.sabit.duelloquiz.backend.notification.PushSender
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

/** Virtual clock for deadline tests. */
class MutableClock(private var now: Instant = Instant.parse("2026-10-16T10:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }

    fun advanceMs(ms: Long) = advance(Duration.ofMillis(ms))
}

/** Accepts only registered tokens; everything else is invalid. */
class FakeTokenVerifier : TokenVerifier {
    private val tokens = ConcurrentHashMap<String, AuthUser>()

    /** Registers a user and returns its bearer token. */
    fun tokenFor(uid: String, name: String? = "Test Oyunçu", picture: String? = null, admin: Boolean = false): String {
        val token = "token-$uid${if (admin) "-admin" else ""}"
        tokens[token] = AuthUser(uid, name, picture, admin)
        return token
    }

    override suspend fun verify(idToken: String): AuthUser =
        tokens[idToken] ?: throw InvalidTokenException("unknown test token")
}

class FakeAccountDeleter : AuthAccountDeleter {
    val deleted = mutableListOf<String>()
    var failNext = false

    override suspend fun deleteAccount(firebaseUid: String) {
        if (failNext) {
            failNext = false
            throw AuthProviderException("simulated Firebase outage")
        }
        deleted += firebaseUid
    }
}

/**
 * Firebase accounts by uid (value: email or null). Accounts deleted through [deleter]
 * disappear, like in Firebase. Records the size of every batch lookup.
 */
class FakeAuthDirectory(private val deleter: FakeAccountDeleter) : com.sabit.duelloquiz.backend.auth.AuthDirectory {
    val accounts = ConcurrentHashMap<String, String>()
    val batchSizes = mutableListOf<Int>()

    fun add(uid: String, email: String = "$uid@example.test") {
        accounts[uid] = email
    }

    private fun live() = accounts.filterKeys { it !in deleter.deleted }

    override suspend fun findUidByEmail(email: String): String? = live().entries.firstOrNull { it.value == email }?.key

    override suspend fun existingUids(uids: Collection<String>): Set<String> {
        require(uids.size <= com.sabit.duelloquiz.backend.auth.AuthDirectory.MAX_BATCH)
        batchSizes += uids.size
        return uids.filterTo(mutableSetOf()) { it in live() }
    }
}

/** Records pushes; tokens in [invalid] answer like FCM `UNREGISTERED`. */
class FakePushSender : PushSender {
    override val available = true
    val sent = mutableListOf<PushMessage>()
    val invalid = mutableSetOf<String>()

    override suspend fun send(message: PushMessage): PushResult {
        if (message.token in invalid) return PushResult.InvalidToken
        sent += message
        return PushResult.Sent
    }
}

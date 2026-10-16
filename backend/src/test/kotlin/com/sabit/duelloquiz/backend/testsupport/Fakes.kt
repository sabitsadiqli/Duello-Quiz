package com.sabit.duelloquiz.backend.testsupport

import com.sabit.duelloquiz.backend.auth.AuthAccountDeleter
import com.sabit.duelloquiz.backend.auth.AuthProviderException
import com.sabit.duelloquiz.backend.auth.AuthUser
import com.sabit.duelloquiz.backend.auth.InvalidTokenException
import com.sabit.duelloquiz.backend.auth.TokenVerifier
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

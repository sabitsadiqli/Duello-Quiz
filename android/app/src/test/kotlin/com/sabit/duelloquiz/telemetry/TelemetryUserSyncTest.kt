package com.sabit.duelloquiz.telemetry

import com.sabit.duelloquiz.core.auth.SignOutReason
import com.sabit.duelloquiz.core.auth.fake.InMemoryAuthRepository
import com.sabit.duelloquiz.core.common.Telemetry
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.Profile
import com.sabit.duelloquiz.core.testing.TestProfileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryUserSyncTest {

    private class RecordingTelemetry : Telemetry {
        val userIds = mutableListOf<String?>()
        override fun setUserId(userId: String?) {
            userIds += userId
        }
        override fun recordNonFatal(throwable: Throwable) = Unit
    }

    private val profile = Profile(id = "u-42", displayName = "Oyunçu 1234", avatarUrl = null, rating = 1000)

    @Test
    fun setsBackendUserIdWhenSignedInAndClearsItOnSignOut() = runTest {
        val auth = InMemoryAuthRepository(latencyMs = 0)
        val profiles = TestProfileRepository()
        val telemetry = RecordingTelemetry()
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        TelemetryUserSync(auth, profiles, telemetry, scope).start()
        runCurrent()

        auth.signInWithGoogle("token")
        runCurrent()
        // Signed in, but the profile (users.id) is not known yet: nothing is set.
        assertEquals(listOf<String?>(null), telemetry.userIds)

        profiles.nextRefresh = DataResult.Success(profile)
        profiles.refresh()
        runCurrent()
        assertEquals(listOf(null, "u-42"), telemetry.userIds)

        auth.signOut(SignOutReason.UserRequested)
        runCurrent()
        assertEquals(listOf(null, "u-42", null), telemetry.userIds)
        scope.cancel()
    }

    private fun TestScope.cancel() = coroutineContext[kotlinx.coroutines.Job]?.cancel()
}

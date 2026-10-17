package com.sabit.duelloquiz.core.data.repository

import com.sabit.duelloquiz.core.auth.AuthState
import com.sabit.duelloquiz.core.auth.SignOutReason
import com.sabit.duelloquiz.core.auth.fake.InMemoryAuthRepository
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.data.testdoubles.FakeNetworkDataSource
import com.sabit.duelloquiz.core.data.testdoubles.FakeNetworkMonitor
import com.sabit.duelloquiz.core.data.testdoubles.testAppScope
import com.sabit.duelloquiz.core.data.testdoubles.testDuelMatches
import com.sabit.duelloquiz.core.data.testdoubles.testUserCache
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.Category
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileAndCategoryRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val network = FakeNetworkDataSource()
    private val auth = InMemoryAuthRepository(latencyMs = 0)
    private val categoriesSample = listOf(
        Category(1, "cografiya", "Coğrafiya", "geography"),
        Category(2, "tarix", "Tarix", "history"),
    )

    private class Repositories(
        val profile: OfflineFirstProfileRepository,
        val categories: OfflineFirstCategoryRepository,
        val account: DefaultAccountRepository,
        val quiz: DefaultQuizRepository,
    )

    private fun TestScope.repositories(): Repositories {
        val appScope = testAppScope()
        val cache = testUserCache(appScope, tmp.root)
        val profile = OfflineFirstProfileRepository(network, cache, auth, appScope)
        val quiz = DefaultQuizRepository(network, FakeNetworkMonitor(), WallClock { testScheduler.currentTime }, appScope)
        return Repositories(
            profile = profile,
            categories = OfflineFirstCategoryRepository(network, cache, auth),
            account = DefaultAccountRepository(
                network,
                auth,
                cache,
                testDuelMatches(appScope, tmp.root),
                profile,
                quiz,
                appScope,
            ),
            quiz = quiz,
        )
    }

    @Test
    fun categoriesAreCachedForTheSignedInAccountOnly() = runTest {
        val repos = repositories()
        auth.signUpWithEmail("Aysel", "aysel@example.com", "password1")
        network.categories = DataResult.Success(categoriesSample)

        assertNull(repos.categories.categories.first())
        repos.categories.refresh()
        assertEquals(categoriesSample, repos.categories.categories.first())

        // Offline later: the cache stays (HOME-05).
        network.categories = FakeNetworkDataSource.offline
        assertEquals(DataResult.Failure(DataError.Offline), repos.categories.refresh())
        assertEquals(categoriesSample, repos.categories.categories.first())

        // Another account never sees it (AUTH-07).
        auth.signOut(SignOutReason.UserRequested)
        auth.signUpWithEmail("Orxan", "orxan@example.com", "password1")
        assertNull(repos.categories.categories.first())
    }

    @Test
    fun refreshWithoutAUserIsUnauthorized() = runTest {
        val repos = repositories()

        assertEquals(DataResult.Failure(DataError.Unauthorized), repos.profile.refresh())
        assertTrue(network.calls.isEmpty())
    }

    @Test
    fun signUpNameIsPatchedOnceTheNewAccountIsSignedIn() = runTest {
        val repos = repositories()

        repos.profile.applySignUpDisplayName("Aysel Məmmədova")
        advanceUntilIdle()
        assertTrue(network.calls.isEmpty())

        auth.signUpWithEmail("Aysel Məmmədova", "aysel@example.com", "password1")
        advanceUntilIdle()

        assertEquals(listOf("updateDisplayName:Aysel Məmmədova"), network.calls)
        assertEquals("Aysel Məmmədova", repos.profile.profile.first()?.displayName)
    }

    @Test
    fun aFailedSignUpNamePatchIsRetriedBeforeTheNextRefresh() = runTest {
        val repos = repositories()
        network.patchResults += FakeNetworkDataSource.offline
        repos.profile.applySignUpDisplayName("Aysel")
        auth.signUpWithEmail("Aysel", "aysel@example.com", "password1")
        advanceUntilIdle()

        val result = repos.profile.refresh()

        assertEquals("Aysel", (result as DataResult.Success).value.displayName)
        assertEquals(listOf("updateDisplayName:Aysel", "updateDisplayName:Aysel"), network.calls)

        repos.profile.refresh()
        assertEquals("getProfile", network.calls.last())
    }

    @Test
    fun aRejectedSignUpNameFallsBackToTheServerProfile() = runTest {
        val repos = repositories()
        network.patchResults += DataResult.Failure(DataError.Api(ApiErrorCode.DisplayNameNotAllowed))
        repos.profile.applySignUpDisplayName("admin")
        auth.signUpWithEmail("admin", "a@example.com", "password1")
        advanceUntilIdle()

        val result = repos.profile.refresh()

        assertEquals("Oyunçu 1234", (result as DataResult.Success).value.displayName)
        assertEquals(listOf("updateDisplayName:admin", "getProfile"), network.calls)
    }

    @Test
    fun deleteAccountClearsLocalDataAndSignsOutWithTheReason() = runTest {
        val repos = repositories()
        auth.signUpWithEmail("Aysel", "aysel@example.com", "password1")
        network.categories = DataResult.Success(categoriesSample)
        repos.categories.refresh()

        assertEquals(DataResult.Success(Unit), repos.account.deleteAccount())

        assertEquals(AuthState.SignedOut(SignOutReason.AccountDeleted), auth.authState.value)
        auth.signUpWithEmail("Aysel", "aysel@example.com", "password1")
        assertNull(repos.categories.categories.first())
    }

    @Test
    fun failedDeleteKeepsTheUserSignedIn() = runTest {
        val repos = repositories()
        auth.signUpWithEmail("Aysel", "aysel@example.com", "password1")
        network.deleteResult = DataResult.Failure(DataError.Api(ApiErrorCode.AuthProviderError))

        assertEquals(DataResult.Failure(DataError.Api(ApiErrorCode.AuthProviderError)), repos.account.deleteAccount())
        assertTrue(auth.authState.value is AuthState.SignedIn)
    }

    @Test
    fun signOutCleanupDropsQuizSessions() = runTest {
        val repos = repositories()
        auth.signUpWithEmail("Aysel", "aysel@example.com", "password1")
        repos.account.startSignOutCleanup()
        repos.quiz.startSession(1)
        advanceUntilIdle()

        auth.signOut(SignOutReason.UserRequested)
        advanceUntilIdle()

        assertNull(repos.quiz.session("s1"))
    }
}

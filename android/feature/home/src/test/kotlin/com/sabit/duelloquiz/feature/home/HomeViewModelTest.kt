package com.sabit.duelloquiz.feature.home

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.sabit.duelloquiz.core.auth.AuthError
import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.auth.AuthResult
import com.sabit.duelloquiz.core.common.HomeNotice
import com.sabit.duelloquiz.core.common.NavResults
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.AuthProvider
import com.sabit.duelloquiz.core.model.AuthUser
import com.sabit.duelloquiz.core.model.Category
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.Profile
import com.sabit.duelloquiz.core.testing.MainDispatcherRule
import com.sabit.duelloquiz.core.testing.TestCategoryRepository
import com.sabit.duelloquiz.core.testing.TestNetworkMonitor
import com.sabit.duelloquiz.core.testing.TestProfileRepository
import com.sabit.duelloquiz.core.testing.TestQuizRepository
import com.sabit.duelloquiz.core.testing.TestVerificationBannerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val unverified = AuthUser(
        uid = "uid-1",
        email = "aysel@example.com",
        displayName = "Aysel",
        photoUrl = null,
        isEmailVerified = false,
        provider = AuthProvider.PASSWORD,
    )
    private val user = MutableStateFlow<AuthUser?>(unverified)
    private val authRepository: AuthRepository = mockk {
        every { currentUser } returns user
        coEvery { reloadUser() } returns AuthResult.Success(Unit)
    }
    private val bannerRepository = TestVerificationBannerRepository()
    private val profileRepository = TestProfileRepository()
    private val categoryRepository = TestCategoryRepository()
    private val quizRepository = TestQuizRepository()
    private val networkMonitor = TestNetworkMonitor(online = true)
    private val savedStateHandle = SavedStateHandle()

    private val categories = listOf(
        Category(1, "cografiya", "Coğrafiya", "geography"),
        Category(2, "tarix", "Tarix", "history"),
    )
    private val profile = Profile(id = "p1", displayName = "Aysel", avatarUrl = null, rating = 1000)

    private fun TestScope.viewModel(): HomeViewModel {
        val vm = HomeViewModel(
            authRepository,
            bannerRepository,
            profileRepository,
            categoryRepository,
            quizRepository,
            networkMonitor,
            savedStateHandle,
        )
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    // Categories and profile

    @Test
    fun firstLoadShowsSkeletonsThenServerOrder() = runTest {
        categoryRepository.nextRefresh = DataResult.Success(categories)
        profileRepository.nextRefresh = DataResult.Success(profile)
        val gate = CompletableDeferred<Unit>()
        categoryRepository.refreshGate = gate
        val vm = viewModel()
        runCurrent()
        assertEquals(CategoriesUi.Loading, vm.uiState.value.categories)

        gate.complete(Unit)
        runCurrent()

        val content = vm.uiState.value.categories as CategoriesUi.Content
        assertEquals(listOf("Coğrafiya", "Tarix"), content.items.map { it.name })
        assertEquals(ProfileHeaderUi(displayName = "Aysel", rating = 1000), vm.uiState.value.header)
        assertFalse(vm.uiState.value.showOfflineBanner)
    }

    @Test
    fun emptyListShowsTheEmptyState() = runTest {
        categoryRepository.nextRefresh = DataResult.Success(emptyList())
        val vm = viewModel()
        runCurrent()

        assertEquals(CategoriesUi.Empty, vm.uiState.value.categories)
    }

    @Test
    fun serverErrorWithoutCacheShowsTheErrorStateAndRetryReloads() = runTest {
        categoryRepository.nextRefresh = DataResult.Failure(DataError.Server)
        val vm = viewModel()
        runCurrent()
        assertEquals(CategoriesUi.Error, vm.uiState.value.categories)

        categoryRepository.nextRefresh = DataResult.Success(categories)
        vm.onAction(HomeAction.RetryClicked)
        runCurrent()
        assertTrue(vm.uiState.value.categories is CategoriesUi.Content)
    }

    @Test
    fun offlineWithoutCacheIsFullScreenAndTheConnectionReturningRefreshesOnce() = runTest {
        categoryRepository.nextRefresh = DataResult.Failure(DataError.Offline)
        networkMonitor.online.value = false
        val vm = viewModel()
        runCurrent()
        assertEquals(CategoriesUi.Offline, vm.uiState.value.categories)
        assertEquals(1, categoryRepository.refreshCount)

        categoryRepository.nextRefresh = DataResult.Success(categories)
        networkMonitor.online.value = true
        runCurrent()

        assertEquals(2, categoryRepository.refreshCount)
        assertTrue(vm.uiState.value.categories is CategoriesUi.Content)
    }

    @Test
    fun offlineWithCacheShowsTheCacheAndTheBanner() = runTest {
        val cached = TestCategoryRepository(initial = categories)
        cached.nextRefresh = DataResult.Failure(DataError.Offline)
        val vm = HomeViewModel(
            authRepository, bannerRepository, TestProfileRepository(initial = profile), cached,
            quizRepository, networkMonitor, savedStateHandle,
        )
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        assertTrue(vm.uiState.value.categories is CategoriesUi.Content)
        assertTrue(vm.uiState.value.showOfflineBanner)
        assertEquals("Aysel", vm.uiState.value.header.displayName)
    }

    @Test
    fun profileFailureWithoutCacheOffersAnInlineRetry() = runTest {
        profileRepository.nextRefresh = DataResult.Failure(DataError.Server)
        categoryRepository.nextRefresh = DataResult.Success(categories)
        val vm = viewModel()
        runCurrent()
        assertTrue(vm.uiState.value.header.showRetry)

        profileRepository.nextRefresh = DataResult.Success(profile)
        vm.onAction(HomeAction.RetryProfileClicked)
        runCurrent()
        assertEquals(1000, vm.uiState.value.header.rating)
        assertFalse(vm.uiState.value.header.showRetry)
    }

    @Test
    fun aSecondPullWhileRefreshingStartsNothing() = runTest {
        categoryRepository.nextRefresh = DataResult.Success(categories)
        val vm = viewModel()
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        categoryRepository.refreshGate = gate

        vm.onAction(HomeAction.PullToRefresh)
        runCurrent()
        assertTrue(vm.uiState.value.isRefreshing)
        vm.onAction(HomeAction.PullToRefresh)
        vm.onAction(HomeAction.Resumed)
        gate.complete(Unit)
        runCurrent()

        assertEquals(2, categoryRepository.refreshCount)
        assertFalse(vm.uiState.value.isRefreshing)
    }

    // Starting a quiz (HOME-02)

    @Test
    fun aFastStartOpensTheQuizWithTheSessionAndIgnoresADoubleTap() = runTest {
        categoryRepository.nextRefresh = DataResult.Success(categories)
        val vm = viewModel()
        runCurrent()
        val category = CategoryUi(1, "Coğrafiya", "geography")

        val gate = CompletableDeferred<Unit>()
        quizRepository.startGate = gate

        vm.events.test {
            vm.onAction(HomeAction.CategoryClicked(category))
            vm.onAction(HomeAction.CategoryClicked(category.copy(id = 2)))
            runCurrent()
            assertEquals(1, vm.uiState.value.startingCategoryId)
            gate.complete(Unit)
            runCurrent()
            assertEquals(HomeEvent.NavigateToQuiz(1, "Coğrafiya", "session-1"), awaitItem())
        }
        assertEquals(1, quizRepository.startCount)
        assertNull(vm.uiState.value.startingCategoryId)
    }

    @Test
    fun aSlowStartHandsOverToTheLoadingScreenAfterOneSecond() = runTest {
        quizRepository.startGate = CompletableDeferred()
        val vm = viewModel()
        runCurrent()

        vm.events.test {
            vm.onAction(HomeAction.CategoryClicked(CategoryUi(1, "Coğrafiya", "geography")))
            advanceTimeBy(999)
            expectNoEvents()
            advanceTimeBy(2)
            assertEquals(HomeEvent.NavigateToQuiz(1, "Coğrafiya", sessionId = null), awaitItem())
        }
    }

    @Test
    fun startErrorsShowTheirSnackbarAndNotPlayableRefreshesTheList() = runTest {
        val cases = listOf(
            DataError.Api(ApiErrorCode.CategoryNotPlayable) to HomeMessage.CategoryNotPlayable,
            DataError.Api(ApiErrorCode.CategoryNotFound) to HomeMessage.CategoryNotPlayable,
            DataError.RateLimited(retryAfterSeconds = 600) to HomeMessage.RateLimited,
            DataError.Offline to HomeMessage.Offline,
            DataError.Timeout to HomeMessage.Generic,
            DataError.Server to HomeMessage.Generic,
        )
        val vm = viewModel()
        runCurrent()
        cases.forEach { (error, message) ->
            quizRepository.startResult = DataResult.Failure(error)
            val refreshesBefore = categoryRepository.refreshCount
            vm.events.test {
                vm.onAction(HomeAction.CategoryClicked(CategoryUi(1, "Coğrafiya", "geography")))
                runCurrent()
                assertEquals(HomeEvent.ShowMessage(message), awaitItem())
            }
            val refreshed = categoryRepository.refreshCount > refreshesBefore
            assertEquals(message.name, message == HomeMessage.CategoryNotPlayable, refreshed)
        }
    }

    @Test
    fun offlineTapShowsTheNeedsInternetMessageWithoutStarting() = runTest {
        networkMonitor.online.value = false
        val vm = viewModel()
        runCurrent()

        vm.events.test {
            vm.onAction(HomeAction.CategoryClicked(CategoryUi(1, "Coğrafiya", "geography")))
            assertEquals(HomeEvent.ShowMessage(HomeMessage.QuizNeedsInternet), awaitItem())
        }
        assertEquals(0, quizRepository.startCount)
    }

    @Test
    fun aNoticeFromTheQuizFlowIsShownOnceAndRefreshes() = runTest {
        val vm = viewModel()
        runCurrent()
        val refreshesBefore = categoryRepository.refreshCount

        vm.events.test {
            savedStateHandle[NavResults.HOME_NOTICE] = HomeNotice.CategoryNotPlayable.name
            runCurrent()
            assertEquals(HomeEvent.ShowMessage(HomeMessage.CategoryNotPlayable), awaitItem())
            expectNoEvents()
        }
        assertNull(savedStateHandle.get<String>(NavResults.HOME_NOTICE))
        assertEquals(refreshesBefore + 1, categoryRepository.refreshCount)
    }

    // Verification banner (AUTH-08)

    @Test
    fun unverifiedPasswordUserSeesTheBanner() = runTest {
        val vm = viewModel()
        runCurrent()

        assertEquals("aysel@example.com", vm.uiState.value.verificationBanner?.email)
    }

    @Test
    fun googleUsersNeverSeeTheBanner() = runTest {
        user.value = unverified.copy(provider = AuthProvider.GOOGLE)
        val vm = viewModel()
        runCurrent()

        assertNull(vm.uiState.value.verificationBanner)
    }

    @Test
    fun dismissHidesTheBannerForThisAccountOnly() = runTest {
        val vm = viewModel()

        vm.onAction(HomeAction.DismissVerificationBanner)
        runCurrent()
        assertNull(vm.uiState.value.verificationBanner)

        user.value = unverified.copy(uid = "uid-2", email = "other@example.com")
        runCurrent()
        assertEquals("other@example.com", vm.uiState.value.verificationBanner?.email)
    }

    @Test
    fun resendShowsConfirmationAndStartsTheCountdown() = runTest {
        coEvery { authRepository.sendEmailVerification() } returns AuthResult.Success(Unit)
        val vm = viewModel()

        vm.events.test {
            vm.onAction(HomeAction.ResendVerificationClicked)
            runCurrent()
            assertEquals(HomeEvent.ShowMessage(HomeMessage.VerificationSent), awaitItem())
        }
        assertEquals(60, vm.uiState.value.verificationBanner?.resendCountdownSeconds)

        vm.onAction(HomeAction.ResendVerificationClicked)
        runCurrent()
        coVerify(exactly = 1) { authRepository.sendEmailVerification() }

        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.verificationBanner?.resendCountdownSeconds)
    }

    @Test
    fun resendErrorsShowTheirMessage() = runTest {
        coEvery { authRepository.sendEmailVerification() } returns AuthResult.Failure(AuthError.TooManyRequests)
        val vm = viewModel()

        vm.events.test {
            vm.onAction(HomeAction.ResendVerificationClicked)
            runCurrent()
            assertEquals(HomeEvent.ShowMessage(HomeMessage.TooManyRequests), awaitItem())
        }
    }

    @Test
    fun resumeReloadsAnUnverifiedUserAndVerificationIsAnnouncedOnce() = runTest {
        coEvery { authRepository.reloadUser() } coAnswers {
            user.value = unverified.copy(isEmailVerified = true)
            AuthResult.Success(Unit)
        }
        val vm = viewModel()
        runCurrent()

        vm.events.test {
            vm.onAction(HomeAction.Resumed)
            runCurrent()
            assertEquals(HomeEvent.ShowMessage(HomeMessage.VerificationDone), awaitItem())
            expectNoEvents()
        }
        coVerify(exactly = 1) { authRepository.reloadUser() }
        assertNull(vm.uiState.value.verificationBanner)
    }

    @Test
    fun settingsIconNavigates() = runTest {
        val vm = viewModel()

        vm.events.test {
            vm.onAction(HomeAction.SettingsClicked)
            assertEquals(HomeEvent.NavigateToSettings, awaitItem())
        }
    }
}

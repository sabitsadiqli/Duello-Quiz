package com.sabit.duelloquiz.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.auth.AuthError
import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.auth.AuthResult
import com.sabit.duelloquiz.core.common.AppConfig
import com.sabit.duelloquiz.core.common.HomeNotice
import com.sabit.duelloquiz.core.common.NavResults
import com.sabit.duelloquiz.core.data.repository.CategoryRepository
import com.sabit.duelloquiz.core.data.repository.ProfileRepository
import com.sabit.duelloquiz.core.data.repository.QuizRepository
import com.sabit.duelloquiz.core.data.repository.VerificationBannerRepository
import com.sabit.duelloquiz.core.data.util.NetworkMonitor
import com.sabit.duelloquiz.core.duel.ActiveDuelStore
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.AuthUser
import com.sabit.duelloquiz.core.model.Category
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** Email-verification banner (AUTH-08, email-verification-banner.md). */
@Immutable
data class VerificationBannerUi(
    val email: String,
    val isResending: Boolean = false,
    /** Seconds until "Yenidən göndər" is enabled again; 0 = enabled. */
    val resendCountdownSeconds: Int = 0,
)

/** Profile header (HOME-04). [displayName] null = skeleton. */
@Immutable
data class ProfileHeaderUi(
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val rating: Int? = null,
    /** Profile request failed without cache: inline "Yenidən cəhd et" replaces the rating. */
    val showRetry: Boolean = false,
)

@Immutable
data class CategoryUi(val id: Int, val name: String, val iconKey: String)

/** The category area of home (home.md "States"). */
@Immutable
sealed interface CategoriesUi {
    /** First load without cache: 5 skeleton cards. */
    data object Loading : CategoriesUi

    data class Content(val items: List<CategoryUi>) : CategoriesUi

    data object Empty : CategoriesUi

    /** 5xx / timeout without cache. */
    data object Error : CategoriesUi

    /** Offline without cache: full-screen state replacing header and list. */
    data object Offline : CategoriesUi
}

@Immutable
data class HomeUiState(
    val header: ProfileHeaderUi = ProfileHeaderUi(),
    val categories: CategoriesUi = CategoriesUi.Loading,
    /** Offline over cached data (HOME-05). */
    val showOfflineBanner: Boolean = false,
    /** Pull-to-refresh indicator. */
    val isRefreshing: Boolean = false,
    /** Card showing its loading state; every card ignores taps meanwhile (HOME-02). */
    val startingCategoryId: Int? = null,
    /** Null when hidden: verified, Google account, or dismissed until the next cold start. */
    val verificationBanner: VerificationBannerUi? = null,
    /** Fixed session size shown on the cards ("10 sual", home.md assumption). */
    val questionsPerQuiz: Int = QUESTIONS_PER_QUIZ,
    /** OQ-020: the duel entry card exists only when the build flag is on. */
    val duelEnabled: Boolean = false,
)

sealed interface HomeAction {
    data object SettingsClicked : HomeAction

    /** PROF-01: the header opens the profile. */
    data object ProfileClicked : HomeAction

    /** HOME-03: the duel entry card. */
    data object DuelClicked : HomeAction

    /** Screen resumed: refresh in the background and reload an unverified user. */
    data object Resumed : HomeAction
    data object PullToRefresh : HomeAction
    data object RetryClicked : HomeAction
    data object RetryProfileClicked : HomeAction
    data class CategoryClicked(val category: CategoryUi) : HomeAction
    data object ResendVerificationClicked : HomeAction
    data object DismissVerificationBanner : HomeAction
}

enum class HomeMessage {
    VerificationSent,
    VerificationDone,
    TooManyRequests,
    Offline,
    Generic,
    QuizNeedsInternet,
    CategoryNotPlayable,
    RateLimited,
    QuizInvalid,
    DuelNeedsInternet,
    DuelLeft,
}

sealed interface HomeEvent {
    data object NavigateToSettings : HomeEvent
    data object NavigateToProfile : HomeEvent

    /** [resume]: rejoin the saved running match after a process death (DUEL-16). */
    data class NavigateToDuel(val resume: Boolean) : HomeEvent

    /** [sessionId] null: the start is still running and the quiz loading screen takes it over. */
    data class NavigateToQuiz(val categoryId: Int, val categoryName: String, val sessionId: String?) : HomeEvent
    data class ShowMessage(val message: HomeMessage) : HomeEvent
}

/**
 * Home (HOME-01 … HOME-06): profile header, cached category list with loading/empty/error/offline
 * states, starting a quiz with the 1 s hand-over to the loading screen, and the email
 * verification banner. The duel entry exists only behind the build flag (OQ-020).
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val bannerRepository: VerificationBannerRepository,
    private val profileRepository: ProfileRepository,
    private val categoryRepository: CategoryRepository,
    private val quizRepository: QuizRepository,
    networkMonitor: NetworkMonitor,
    private val savedStateHandle: SavedStateHandle,
    private val appConfig: AppConfig,
    private val activeDuels: ActiveDuelStore,
) : ViewModel() {

    private data class ResendState(val isSending: Boolean = false, val countdownSeconds: Int = 0)

    private data class LocalState(
        /** A category refresh has completed at least once (success or failure). */
        val categoriesAttempted: Boolean = false,
        val categoriesLoading: Boolean = false,
        val categoriesError: DataError? = null,
        val profileError: DataError? = null,
        val isPullRefreshing: Boolean = false,
        val startingCategoryId: Int? = null,
        val resend: ResendState = ResendState(),
    )

    private val local = MutableStateFlow(LocalState())
    private var refreshJob: Job? = null
    private var profileJob: Job? = null
    private var countdownJob: Job? = null

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    private val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = true)

    val uiState: StateFlow<HomeUiState> = combine(
        combine(authRepository.currentUser, bannerRepository.dismissedUids, ::Pair),
        profileRepository.profile,
        categoryRepository.categories,
        isOnline,
        local,
    ) { (user, dismissed), profile, categories, online, state ->
        buildState(user, dismissed, profile, categories, online, state)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState(duelEnabled = appConfig.duelEnabled))

    init {
        announceVerification()
        observeConnection()
        observeNotices()
        refresh(userInitiated = false)
        resumeDuel()
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.SettingsClicked -> _events.trySend(HomeEvent.NavigateToSettings)
            HomeAction.ProfileClicked -> _events.trySend(HomeEvent.NavigateToProfile)
            HomeAction.DuelClicked -> if (appConfig.duelEnabled) {
                _events.trySend(
                    if (isOnline.value) HomeEvent.NavigateToDuel(resume = false) else HomeEvent.ShowMessage(HomeMessage.DuelNeedsInternet),
                )
            }
            HomeAction.Resumed -> {
                reloadIfUnverified()
                refresh(userInitiated = false)
            }
            HomeAction.PullToRefresh -> refresh(userInitiated = true)
            HomeAction.RetryClicked -> refresh(userInitiated = false)
            HomeAction.RetryProfileClicked -> refreshProfile()
            is HomeAction.CategoryClicked -> startQuiz(action.category)
            HomeAction.ResendVerificationClicked -> resendVerification()
            HomeAction.DismissVerificationBanner -> viewModelScope.launch {
                authRepository.currentUser.first()?.let { bannerRepository.dismiss(it.uid) }
            }
        }
    }

    private fun buildState(
        user: AuthUser?,
        dismissed: Set<String>,
        profile: Profile?,
        categories: List<Category>?,
        online: Boolean,
        state: LocalState,
    ): HomeUiState {
        val categoriesUi = when {
            categories != null && categories.isEmpty() -> CategoriesUi.Empty
            categories != null -> CategoriesUi.Content(categories.map { CategoryUi(it.id, it.name, it.iconKey) })
            !state.categoriesAttempted || state.categoriesLoading -> CategoriesUi.Loading
            state.categoriesError == DataError.Offline || !online -> CategoriesUi.Offline
            else -> CategoriesUi.Error
        }
        val offlineOverCache = categories != null && (!online || state.categoriesError == DataError.Offline)
        return HomeUiState(
            header = ProfileHeaderUi(
                displayName = profile?.displayName,
                avatarUrl = profile?.avatarUrl ?: user?.photoUrl,
                rating = profile?.rating,
                showRetry = profile == null && state.profileError != null,
            ),
            categories = categoriesUi,
            showOfflineBanner = offlineOverCache,
            isRefreshing = state.isPullRefreshing,
            startingCategoryId = state.startingCategoryId,
            duelEnabled = appConfig.duelEnabled,
            verificationBanner = user
                ?.takeIf { it.shouldShowBanner(dismissed) }
                ?.let {
                    VerificationBannerUi(
                        email = it.email.orEmpty(),
                        isResending = state.resend.isSending,
                        resendCountdownSeconds = state.resend.countdownSeconds,
                    )
                },
        )
    }

    /** Categories and profile in parallel; a running refresh is never started twice (HOME-01). */
    private fun refresh(userInitiated: Boolean) {
        refreshProfile()
        if (refreshJob?.isActive == true) return
        local.update { it.copy(isPullRefreshing = userInitiated, categoriesLoading = true) }
        refreshJob = viewModelScope.launch {
            val result = categoryRepository.refresh()
            local.update {
                it.copy(
                    categoriesAttempted = true,
                    categoriesLoading = false,
                    categoriesError = (result as? DataResult.Failure)?.error,
                    isPullRefreshing = false,
                )
            }
        }
    }

    private fun refreshProfile() {
        if (profileJob?.isActive == true) return
        profileJob = viewModelScope.launch {
            val result = profileRepository.refresh()
            local.update { it.copy(profileError = (result as? DataResult.Failure)?.error) }
        }
    }

    /**
     * DUEL-16: after a process death during a match the app reopens the duel on the saved match
     * ("Oyuna qayıdırsan…"); checked once per home entry.
     */
    private fun resumeDuel() {
        if (!appConfig.duelEnabled || savedStateHandle.get<Boolean>(KEY_DUEL_RESUME_CHECKED) == true) return
        savedStateHandle[KEY_DUEL_RESUME_CHECKED] = true
        viewModelScope.launch {
            if (activeDuels.activeMatchId.first() != null) _events.send(HomeEvent.NavigateToDuel(resume = true))
        }
    }

    /** HOME-05: when the connection returns, refresh once automatically. */
    private fun observeConnection() {
        viewModelScope.launch {
            var wasOnline = true
            isOnline.collect { online ->
                if (online && !wasOnline) refresh(userInitiated = false)
                wasOnline = online
            }
        }
    }

    /** Messages handed back by the quiz flow through this entry's SavedStateHandle. */
    private fun observeNotices() {
        viewModelScope.launch {
            savedStateHandle.getStateFlow<String?>(NavResults.HOME_NOTICE, null).filterNotNull().collect { name ->
                savedStateHandle[NavResults.HOME_NOTICE] = null
                val message = when (HomeNotice.entries.firstOrNull { it.name == name }) {
                    HomeNotice.CategoryNotPlayable -> HomeMessage.CategoryNotPlayable
                    HomeNotice.QuizInvalid -> HomeMessage.QuizInvalid
                    HomeNotice.DuelLeft -> HomeMessage.DuelLeft
                    HomeNotice.Generic, null -> HomeMessage.Generic
                }
                _events.send(HomeEvent.ShowMessage(message))
                if (message == HomeMessage.CategoryNotPlayable) refresh(userInitiated = false)
            }
        }
    }

    /**
     * HOME-02 / QUIZ-01: the card shows its loading state; a response within 1 s opens the quiz
     * directly, a slower one hands the running request over to the quiz loading screen. Fast
     * failures stay on home with a snackbar.
     */
    private fun startQuiz(category: CategoryUi) {
        if (local.value.startingCategoryId != null) return
        if (!isOnline.value) {
            _events.trySend(HomeEvent.ShowMessage(HomeMessage.QuizNeedsInternet))
            return
        }
        local.update { it.copy(startingCategoryId = category.id) }
        viewModelScope.launch {
            val result = withTimeoutOrNull(START_HANDOVER_MS) { quizRepository.startSession(category.id) }
            when (result) {
                null -> _events.send(HomeEvent.NavigateToQuiz(category.id, category.name, sessionId = null))
                is DataResult.Success -> _events.send(HomeEvent.NavigateToQuiz(category.id, category.name, result.value.id))
                is DataResult.Failure -> onStartFailed(result.error)
            }
            local.update { it.copy(startingCategoryId = null) }
        }
    }

    private suspend fun onStartFailed(error: DataError) {
        val message = when {
            error is DataError.Api &&
                (error.code == ApiErrorCode.CategoryNotPlayable || error.code == ApiErrorCode.CategoryNotFound) ->
                HomeMessage.CategoryNotPlayable
            error is DataError.RateLimited -> HomeMessage.RateLimited
            error == DataError.Offline -> HomeMessage.Offline
            else -> HomeMessage.Generic
        }
        _events.send(HomeEvent.ShowMessage(message))
        if (message == HomeMessage.CategoryNotPlayable) refresh(userInitiated = false)
    }

    /** "E-poçtun təsdiqləndi." once, when a visible banner disappears because of verification. */
    private fun announceVerification() {
        viewModelScope.launch {
            var wasBannerVisible: Boolean? = null
            combine(authRepository.currentUser, bannerRepository.dismissedUids) { user, dismissed ->
                user to dismissed
            }.collect { (user, dismissed) ->
                val visible = user?.shouldShowBanner(dismissed) == true
                val verifiedNow = user != null && !user.needsEmailVerification
                if (wasBannerVisible == true && !visible && verifiedNow) {
                    _events.send(HomeEvent.ShowMessage(HomeMessage.VerificationDone))
                }
                wasBannerVisible = visible
            }
        }
    }

    private fun reloadIfUnverified() {
        viewModelScope.launch {
            val user = authRepository.currentUser.first() ?: return@launch
            // Failures (offline) are silent: the banner simply stays.
            if (user.needsEmailVerification) authRepository.reloadUser()
        }
    }

    private fun resendVerification() {
        val state = local.value.resend
        if (state.isSending || state.countdownSeconds > 0) return
        updateResend { it.copy(isSending = true) }
        viewModelScope.launch {
            when (val result = authRepository.sendEmailVerification()) {
                is AuthResult.Success -> {
                    updateResend { it.copy(isSending = false) }
                    _events.send(HomeEvent.ShowMessage(HomeMessage.VerificationSent))
                    startCountdown()
                }
                is AuthResult.Failure -> {
                    updateResend { it.copy(isSending = false) }
                    _events.send(HomeEvent.ShowMessage(result.error.toMessage()))
                }
            }
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (seconds in RESEND_COOLDOWN_SECONDS downTo 1) {
                updateResend { it.copy(countdownSeconds = seconds) }
                delay(ONE_SECOND_MS)
            }
            updateResend { it.copy(countdownSeconds = 0) }
        }
    }

    private fun updateResend(transform: (ResendState) -> ResendState) {
        local.update { it.copy(resend = transform(it.resend)) }
    }

    private fun AuthUser.shouldShowBanner(dismissed: Set<String>): Boolean =
        needsEmailVerification && uid !in dismissed

    private fun AuthError.toMessage(): HomeMessage = when (this) {
        AuthError.TooManyRequests -> HomeMessage.TooManyRequests
        AuthError.Network -> HomeMessage.Offline
        else -> HomeMessage.Generic
    }

    companion object {
        const val RESEND_COOLDOWN_SECONDS = 60

        /** HOME-02 / home.md: after 1 s the quiz loading screen takes over the start request. */
        const val START_HANDOVER_MS = 1_000L
        private const val ONE_SECOND_MS = 1_000L
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val KEY_DUEL_RESUME_CHECKED = "duel_resume_checked"
    }
}

/** Session size shown on category cards (docs/mvp-spec.md §1). */
const val QUESTIONS_PER_QUIZ = 10

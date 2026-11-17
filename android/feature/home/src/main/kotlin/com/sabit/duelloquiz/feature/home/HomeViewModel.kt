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
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.data.repository.CategoryRepository
import com.sabit.duelloquiz.core.data.repository.DailyRepository
import com.sabit.duelloquiz.core.data.repository.ProfileRepository
import com.sabit.duelloquiz.core.data.repository.ProgressRepository
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
import com.sabit.duelloquiz.core.model.ProgressSummary
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

/** Avatar, name and rating from the profile (HOME-04). Null values = not known yet. */
@Immutable
data class ProfileHeaderUi(
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val rating: Int? = null,
    /** The profile load finished (success or failure): the avatar stops being a skeleton. */
    val loaded: Boolean = false,
) {
    /** First word of the display name for "Salam, Aysel." (home.md assumption). */
    val firstName: String? get() = GreetingPool.firstName(displayName)
}

/** Date line and greeting of the Baku day (HOME-04); refreshed on resume, not live at midnight. */
@Immutable
data class TodayUi(
    val weekdayIndex: Int,
    val dayOfMonth: Int,
    val monthIndex: Int,
    /** `home_greeting_N`, 1-based. */
    val greetingLine: Int,
    /** Seed of the squiggle's hand-drawn irregularity (stable for the day). */
    val seed: Long,
)

/** A category row (components.md §5). One colour for all categories in v3. */
@Immutable
data class CategoryUi(
    val id: Int,
    val name: String,
    val iconKey: String,
)

/** The category area of home (home.md "States"). */
@Immutable
sealed interface CategoriesUi {
    /** First load without cache: skeleton tiles. */
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
    val today: TodayUi? = null,
    /** Streak chip in the top row (HOME-08). */
    val streak: StreakChipUi = StreakChipUi.Skeleton,
    val categories: CategoriesUi = CategoriesUi.Loading,
    /** Offline over cached data (HOME-05). */
    val showOfflineBanner: Boolean = false,
    /** Pull-to-refresh indicator. */
    val isRefreshing: Boolean = false,
    /** Row showing its loading state; every row and the duel card ignore taps meanwhile (HOME-02). */
    val startingCategoryId: Int? = null,
    /** Null when hidden: verified, Google account, or dismissed until the next cold start. */
    val verificationBanner: VerificationBannerUi? = null,
    /** Fixed session size shown in the overline ("TƏK OYNA · 10 SUAL"). */
    val questionsPerQuiz: Int = QUESTIONS_PER_QUIZ,
    /** OQ-020: the duel card exists only when the build flag is on. */
    val duelEnabled: Boolean = false,
)

sealed interface HomeAction {
    /** HOME-08: the streak chip opens the Gündəlik tab. */
    data object StreakClicked : HomeAction

    /** HOME-04 / PROF-01: the avatar opens the Profil tab. */
    data object AvatarClicked : HomeAction

    /** HOME-03: the duel card. */
    data object DuelClicked : HomeAction

    /** Screen resumed: refresh when due and reload an unverified user. */
    data object Resumed : HomeAction
    data object PullToRefresh : HomeAction
    data object RetryClicked : HomeAction
    data class CategoryClicked(val category: CategoryUi) : HomeAction
    data object ResendVerificationClicked : HomeAction
    data object DismissVerificationBanner : HomeAction
}

enum class HomeMessage {
    VerificationSent,
    VerificationDone,
    TooManyRequests,
    Offline,

    /** Device online, backend unreachable (DataError.ServerUnreachable). */
    ServerUnreachable,
    Generic,
    QuizNeedsInternet,
    CategoryNotPlayable,
    RateLimited,
    QuizInvalid,
    DuelNeedsInternet,
    DuelLeft,
}

sealed interface HomeEvent {
    /** Switch to the Gündəlik tab (streak chip, HOME-08). */
    data object NavigateToDailyTab : HomeEvent

    /** Switch to the Profil tab (avatar, PROF-01). */
    data object NavigateToProfileTab : HomeEvent

    /** [resume]: rejoin the saved running match after a process death (DUEL-16). */
    data class NavigateToDuel(val resume: Boolean) : HomeEvent

    /** [sessionId] null: the start is still running and the quiz loading screen takes it over. */
    data class NavigateToQuiz(val categoryId: Int, val categoryName: String, val sessionId: String?) : HomeEvent
    data class ShowMessage(val message: HomeMessage) : HomeEvent
}

/**
 * The Oyna tab (home.md v3, HOME-01 … HOME-09): top row (Baku date, streak chip, avatar),
 * greeting, duel card behind the build flag (OQ-020), cached category list with
 * loading/empty/error/offline states, starting a quiz with the 1 s hand-over to the loading
 * screen, and the email verification banner. One refresh loads profile, progress, categories and
 * the daily status (only for the Gündəlik badge, HOME-07): on open, pull, return from a game
 * ([NavResults.TAB_REFRESH]) and on re-entry after more than 5 minutes.
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
    private val progressRepository: ProgressRepository,
    private val dailyRepository: DailyRepository,
    private val clock: WallClock,
) : ViewModel() {

    private data class ResendState(val isSending: Boolean = false, val countdownSeconds: Int = 0)

    private data class LocalState(
        /** A category refresh has completed at least once (success or failure). */
        val categoriesAttempted: Boolean = false,
        val categoriesLoading: Boolean = false,
        val categoriesError: DataError? = null,
        val profileAttempted: Boolean = false,
        val isPullRefreshing: Boolean = false,
        val startingCategoryId: Int? = null,
        val resend: ResendState = ResendState(),
        val progressLoading: Boolean = false,
        val progressAttempted: Boolean = false,
        /** Wall clock of the last resume or refresh; the date line follows it (not live). */
        val nowMs: Long = 0,
    )

    private val local = MutableStateFlow(LocalState(nowMs = clock.nowMs()))
    private var refreshJob: Job? = null
    private var profileJob: Job? = null
    private var countdownJob: Job? = null
    private var progressJob: Job? = null
    private var dailyJob: Job? = null
    private var lastRefreshMs = Long.MIN_VALUE / 2

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    private val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = true)

    val uiState: StateFlow<HomeUiState> = combine(
        combine(authRepository.currentUser, bannerRepository.dismissedUids, ::Pair),
        combine(profileRepository.profile, categoryRepository.categories, ::Pair),
        progressRepository.progress,
        isOnline,
        local,
    ) { (user, dismissed), (profile, categories), progress, online, state ->
        buildState(user, dismissed, profile, categories, progress, online, state)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState(duelEnabled = appConfig.duelEnabled))

    init {
        announceVerification()
        observeConnection()
        observeNotices()
        observeReturns()
        refresh(userInitiated = false)
        resumeDuel()
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.StreakClicked -> _events.trySend(HomeEvent.NavigateToDailyTab)
            HomeAction.AvatarClicked -> _events.trySend(HomeEvent.NavigateToProfileTab)
            HomeAction.DuelClicked -> if (appConfig.duelEnabled && local.value.startingCategoryId == null) {
                _events.trySend(
                    if (isOnline.value) HomeEvent.NavigateToDuel(resume = false) else HomeEvent.ShowMessage(HomeMessage.DuelNeedsInternet),
                )
            }
            HomeAction.Resumed -> {
                reloadIfUnverified()
                val now = clock.nowMs()
                local.update { it.copy(nowMs = now) }
                if (now - lastRefreshMs > REENTRY_REFRESH_MS) refresh(userInitiated = false)
            }
            HomeAction.PullToRefresh -> refresh(userInitiated = true)
            HomeAction.RetryClicked -> refresh(userInitiated = false)
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
        progress: ProgressSummary?,
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
                loaded = profile != null || state.profileAttempted,
            ),
            today = todayUi(state.nowMs, appConfig.duelEnabled),
            streak = streakChipUi(progress, state.progressLoading, state.progressAttempted, state.nowMs),
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

    /** Profile, progress, categories and the daily status in parallel; none is started twice (HOME-01, HOME-07). */
    private fun refresh(userInitiated: Boolean) {
        lastRefreshMs = clock.nowMs()
        local.update { it.copy(nowMs = lastRefreshMs) }
        refreshProfile()
        refreshProgress()
        refreshDailyStatus()
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

    private fun refreshProgress() {
        if (progressJob?.isActive == true) return
        local.update { it.copy(progressLoading = true) }
        progressJob = viewModelScope.launch {
            progressRepository.refresh()
            local.update { it.copy(progressLoading = false, progressAttempted = true) }
        }
    }

    /** HOME-07: only for the Gündəlik badge; a failure just hides the badge (no home error). */
    private fun refreshDailyStatus() {
        if (dailyJob?.isActive == true) return
        dailyJob = viewModelScope.launch { dailyRepository.refreshStatus() }
    }

    /** :app sets [NavResults.TAB_REFRESH] when a quiz or duel flow returns to this tab (HOME-04, HOME-08). */
    private fun observeReturns() {
        viewModelScope.launch {
            savedStateHandle.getStateFlow(NavResults.TAB_REFRESH, false).collect { returned ->
                if (returned) {
                    savedStateHandle[NavResults.TAB_REFRESH] = false
                    refresh(userInitiated = false)
                }
            }
        }
    }

    private fun refreshProfile() {
        if (profileJob?.isActive == true) return
        profileJob = viewModelScope.launch {
            profileRepository.refresh()
            local.update { it.copy(profileAttempted = true) }
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
            error == DataError.ServerUnreachable -> HomeMessage.ServerUnreachable
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

        /** home.md: tab re-entry refreshes only after more than 5 minutes. */
        const val REENTRY_REFRESH_MS = 5 * 60 * 1_000L
        private const val ONE_SECOND_MS = 1_000L
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val KEY_DUEL_RESUME_CHECKED = "duel_resume_checked"
    }
}

/** Session size shown in the category overline (docs/mvp-spec.md §1). */
const val QUESTIONS_PER_QUIZ = 10

/** Date line and greeting line of the Baku day of [nowMs] (HOME-04). Pure. */
internal fun todayUi(nowMs: Long, duelEnabled: Boolean): TodayUi {
    val day = BakuDay.of(nowMs)
    return TodayUi(
        weekdayIndex = day.weekdayIndex,
        dayOfMonth = day.dayOfMonth,
        monthIndex = day.monthIndex,
        greetingLine = GreetingPool.lineFor(day.epochDay, duelEnabled),
        seed = day.epochDay,
    )
}

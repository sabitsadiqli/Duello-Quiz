package com.sabit.duelloquiz.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.auth.AuthError
import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.auth.AuthResult
import com.sabit.duelloquiz.core.data.repository.VerificationBannerRepository
import com.sabit.duelloquiz.core.model.AuthUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Email-verification banner (AUTH-08, email-verification-banner.md). */
@Immutable
data class VerificationBannerUi(
    val email: String,
    val isResending: Boolean = false,
    /** Seconds until "Yenidən göndər" is enabled again; 0 = enabled. */
    val resendCountdownSeconds: Int = 0,
)

@Immutable
data class HomeUiState(
    val displayName: String? = null,
    /** Null when hidden: verified, Google account, or dismissed until the next cold start. */
    val verificationBanner: VerificationBannerUi? = null,
)

sealed interface HomeAction {
    data object SettingsClicked : HomeAction

    /** Screen resumed: reload the user so a verification done in the browser is picked up. */
    data object Resumed : HomeAction
    data object ResendVerificationClicked : HomeAction
    data object DismissVerificationBanner : HomeAction
}

enum class HomeMessage { VerificationSent, VerificationDone, TooManyRequests, Offline, Generic }

sealed interface HomeEvent {
    data object NavigateToSettings : HomeEvent
    data class ShowMessage(val message: HomeMessage) : HomeEvent
}

/**
 * Placeholder home: the signed-in user's name and the verification banner. Categories,
 * profile/rating (`GET /v1/me`) and the duel entry come with the quiz feature.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val bannerRepository: VerificationBannerRepository,
) : ViewModel() {

    private data class ResendState(val isSending: Boolean = false, val countdownSeconds: Int = 0)

    private val resendState = MutableStateFlow(ResendState())
    private var countdownJob: Job? = null

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        authRepository.currentUser,
        bannerRepository.dismissedUids,
        resendState,
    ) { user, dismissed, resend ->
        HomeUiState(
            displayName = user?.displayName,
            verificationBanner = user
                ?.takeIf { it.shouldShowBanner(dismissed) }
                ?.let {
                    VerificationBannerUi(
                        email = it.email.orEmpty(),
                        isResending = resend.isSending,
                        resendCountdownSeconds = resend.countdownSeconds,
                    )
                },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    init {
        announceVerification()
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.SettingsClicked -> _events.trySend(HomeEvent.NavigateToSettings)
            HomeAction.Resumed -> reloadIfUnverified()
            HomeAction.ResendVerificationClicked -> resendVerification()
            HomeAction.DismissVerificationBanner -> viewModelScope.launch {
                authRepository.currentUser.first()?.let { bannerRepository.dismiss(it.uid) }
            }
        }
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
        val state = resendState.value
        if (state.isSending || state.countdownSeconds > 0) return
        resendState.update { it.copy(isSending = true) }
        viewModelScope.launch {
            when (val result = authRepository.sendEmailVerification()) {
                is AuthResult.Success -> {
                    resendState.update { it.copy(isSending = false) }
                    _events.send(HomeEvent.ShowMessage(HomeMessage.VerificationSent))
                    startCountdown()
                }
                is AuthResult.Failure -> {
                    resendState.update { it.copy(isSending = false) }
                    _events.send(HomeEvent.ShowMessage(result.error.toMessage()))
                }
            }
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (seconds in RESEND_COOLDOWN_SECONDS downTo 1) {
                resendState.update { it.copy(countdownSeconds = seconds) }
                delay(ONE_SECOND_MS)
            }
            resendState.update { it.copy(countdownSeconds = 0) }
        }
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
        private const val ONE_SECOND_MS = 1_000L
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}

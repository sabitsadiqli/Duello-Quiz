package com.sabit.duelloquiz.feature.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.auth.AuthRules
import com.sabit.duelloquiz.core.auth.AuthValidator
import com.sabit.duelloquiz.core.auth.DisplayNameError
import com.sabit.duelloquiz.core.common.AppConfig
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.data.repository.DuelHistoryRepository
import com.sabit.duelloquiz.core.data.repository.ProfileRepository
import com.sabit.duelloquiz.core.data.repository.ProgressRepository
import com.sabit.duelloquiz.core.data.util.NetworkMonitor
import com.sabit.duelloquiz.core.designsystem.theme.CategoryAccent
import com.sabit.duelloquiz.core.designsystem.util.RetentionFormat
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.Profile
import com.sabit.duelloquiz.core.model.ProgressSummary
import com.sabit.duelloquiz.core.model.duel.CalibrationHint
import com.sabit.duelloquiz.core.model.duel.CalibrationRules
import com.sabit.duelloquiz.core.model.duel.DuelStats
import com.sabit.duelloquiz.core.model.duel.MatchSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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

/** One section of the profile (profile.md "States"): each loads and fails on its own. */
@Immutable
sealed interface SectionUi<out T> {
    data object Loading : SectionUi<Nothing>

    data class Content<T>(val value: T) : SectionUi<T>

    /** 5xx / timeout without cache: inline "Yükləmək alınmadı" with retry. */
    data object Error : SectionUi<Nothing>
}

@Immutable
data class ProfileHeaderUi(
    val displayName: String,
    val avatarUrl: String?,
    val rating: Int,
    /** Null: the "Qoşulub" line is hidden (OQ-060). */
    val createdAtEpochMs: Long?,
)

@Immutable
data class StatsUi(val stats: DuelStats, val hint: CalibrationHint)

/** One playable category with its level (PROF-10). [accent] is a category accent slot. */
@Immutable
data class CategoryLevelUi(
    val name: String,
    val iconKey: String,
    val accent: String,
    val level: Int,
    val fraction: Float,
    val xpToNext: Long,
)

/** Level, streak and category levels (PROF-09 … PROF-11), all server values. */
@Immutable
data class ProgressUi(
    val level: Int,
    val xpIntoLevel: Long,
    val xpForNextLevel: Long,
    val xpTotal: Long,
    val fraction: Float,
    val streakCurrent: Int,
    val streakBest: Int,
    /** Null hides the "today" line (a cache from an earlier Baku day, PROG-06). */
    val todayActive: Boolean?,
    val freezeAvailable: Boolean,
    val categories: List<CategoryLevelUi>,
)

/** Why the name dialog shows a field error (PROF-06). */
enum class NameError { REQUIRED, LENGTH, CHARACTERS, NOT_ALLOWED, INVALID, OFFLINE, FAILED }

@Immutable
data class EditNameUi(
    val value: String,
    val error: NameError? = null,
    val saving: Boolean = false,
) {
    /** Code points after normalization, for the "7/20" counter (OQ-042). */
    val length: Int get() = AuthValidator.displayNameLength(value)
    val overLimit: Boolean get() = length > AuthRules.DISPLAY_NAME_MAX_LENGTH
}

@Immutable
data class ProfileUiState(
    /** Null with [header] loading = skeleton. */
    val header: SectionUi<ProfileHeaderUi> = SectionUi.Loading,
    /** Shown always, also with the duel flag off (PROF-07, PROF-09). */
    val progress: SectionUi<ProgressUi> = SectionUi.Loading,
    val duelEnabled: Boolean = false,
    val stats: SectionUi<StatsUi> = SectionUi.Loading,
    val matches: SectionUi<List<MatchSummary>> = SectionUi.Loading,
    /**
     * PROF-07: no counted match yet, neither against players nor the bot: one empty card instead of
     * the sections.
     */
    val empty: Boolean = false,
    val showOfflineBanner: Boolean = false,
    /** Offline without any cache: full-screen state. */
    val fullScreenOffline: Boolean = false,
    val isRefreshing: Boolean = false,
    val editName: EditNameUi? = null,
)

sealed interface ProfileAction {
    data object Back : ProfileAction
    data object Refresh : ProfileAction
    data object RetryHeader : ProfileAction
    data object RetryStats : ProfileAction
    data object RetryMatches : ProfileAction
    data object RetryProgress : ProfileAction
    data object EditNameClicked : ProfileAction
    data class NameChanged(val value: String) : ProfileAction
    data object SaveName : ProfileAction
    data object DismissEdit : ProfileAction
    data object StartDuelClicked : ProfileAction
}

enum class ProfileMessage { NAME_SAVED, DUEL_NEEDS_INTERNET }

sealed interface ProfileEvent {
    data object NavigateBack : ProfileEvent
    data object OpenDuel : ProfileEvent
    data class ShowMessage(val message: ProfileMessage) : ProfileEvent
}

/**
 * Profile (PROF-01 … PROF-08): cached profile, stats and recent matches shown at once and reloaded
 * on open; each section fails on its own; the display name is edited with the OQ-042 rules and
 * `PATCH /v1/me`. Percentages, "Az məlumat" and the hint are chosen here from the server counts
 * (OQ-059).
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val historyRepository: DuelHistoryRepository,
    networkMonitor: NetworkMonitor,
    private val appConfig: AppConfig,
    private val progressRepository: ProgressRepository,
    private val clock: WallClock,
) : ViewModel() {

    private data class Local(
        val headerError: DataError? = null,
        val statsError: DataError? = null,
        val matchesError: DataError? = null,
        val progressError: DataError? = null,
        val loadsDone: Int = 0,
        val isRefreshing: Boolean = false,
        val editName: EditNameUi? = null,
    )

    private val local = MutableStateFlow(Local())
    private val isOnline: StateFlow<Boolean> =
        networkMonitor.isOnline.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = true)
    private var refreshJob: Job? = null

    private val _events = Channel<ProfileEvent>(Channel.BUFFERED)
    val events: Flow<ProfileEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<ProfileUiState> = combine(
        combine(profileRepository.profile, progressRepository.progress, ::Pair),
        historyRepository.stats,
        historyRepository.matches,
        isOnline,
        local,
    ) { (profile, progress), stats, matches, online, state ->
        buildState(profile, stats, matches, online, state)
            .copy(progress = section(progress?.toUi(clock.nowMs()), state.progressError))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ProfileUiState(duelEnabled = appConfig.duelEnabled))

    init {
        // PROF-01: always reloaded on open, so a just-finished duel appears.
        refresh(pull = false)
    }

    fun onAction(action: ProfileAction) {
        when (action) {
            ProfileAction.Back -> _events.trySend(ProfileEvent.NavigateBack)
            ProfileAction.Refresh -> refresh(pull = true)
            ProfileAction.RetryHeader -> launchLoad { loadHeader() }
            ProfileAction.RetryStats -> launchLoad { loadStats() }
            ProfileAction.RetryMatches -> launchLoad { loadMatches() }
            ProfileAction.RetryProgress -> launchLoad { loadProgress() }
            ProfileAction.EditNameClicked -> viewModelScope.launch {
                val name = profileRepository.profile.first()?.displayName ?: return@launch
                local.update { it.copy(editName = EditNameUi(name)) }
            }
            is ProfileAction.NameChanged -> local.update { state ->
                state.editName?.takeIf { !it.saving }?.let { state.copy(editName = it.copy(value = action.value, error = null)) }
                    ?: state
            }
            ProfileAction.SaveName -> saveName()
            ProfileAction.DismissEdit -> local.update { if (it.editName?.saving == true) it else it.copy(editName = null) }
            ProfileAction.StartDuelClicked -> _events.trySend(
                if (isOnline.value) ProfileEvent.OpenDuel else ProfileEvent.ShowMessage(ProfileMessage.DUEL_NEEDS_INTERNET),
            )
        }
    }

    private fun buildState(
        profile: Profile?,
        stats: DuelStats?,
        matches: List<MatchSummary>?,
        online: Boolean,
        state: Local,
    ): ProfileUiState {
        val duel = appConfig.duelEnabled
        val anyOffline = !online || listOf(state.headerError, state.statsError, state.matchesError).any { it == DataError.Offline }
        val header = section(profile?.let { ProfileHeaderUi(it.displayName, it.avatarUrl, it.rating, it.createdAtEpochMs) }, state.headerError)
        val statsSection = section(stats?.let { StatsUi(it, CalibrationRules.profileHint(it.calibration)) }, state.statsError)
        val noCache = profile == null && stats == null && matches == null
        return ProfileUiState(
            header = header,
            duelEnabled = duel,
            stats = statsSection,
            matches = section(matches, state.matchesError),
            // DUEL-28: bot matches alone are not "empty"; the record card shows the bot line.
            empty = duel && stats != null && stats.record.total == 0 && stats.botRecord.total == 0,
            showOfflineBanner = anyOffline && profile != null,
            fullScreenOffline = noCache && anyOffline && state.headerError != null,
            isRefreshing = state.isRefreshing,
            editName = state.editName,
        )
    }

    private fun <T> section(value: T?, error: DataError?): SectionUi<T> = when {
        value != null -> SectionUi.Content(value)
        error != null -> SectionUi.Error
        else -> SectionUi.Loading
    }

    private fun refresh(pull: Boolean) {
        if (refreshJob?.isActive == true) return
        local.update { it.copy(isRefreshing = pull) }
        refreshJob = viewModelScope.launch {
            val jobs = buildList {
                add(launch { loadHeader() })
                add(launch { loadProgress() })
                if (appConfig.duelEnabled) {
                    add(launch { loadStats() })
                    add(launch { loadMatches() })
                }
            }
            jobs.forEach { it.join() }
            local.update { it.copy(isRefreshing = false) }
        }
    }

    private fun launchLoad(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private suspend fun loadHeader() {
        val result = profileRepository.refresh()
        local.update { it.copy(headerError = (result as? DataResult.Failure)?.error) }
    }

    private suspend fun loadProgress() {
        val result = progressRepository.refresh()
        local.update { it.copy(progressError = (result as? DataResult.Failure)?.error) }
    }

    private suspend fun loadStats() {
        val result = historyRepository.refreshStats()
        local.update { it.copy(statsError = (result as? DataResult.Failure)?.error) }
    }

    private suspend fun loadMatches() {
        val result = historyRepository.refreshMatches()
        local.update { it.copy(matchesError = (result as? DataResult.Failure)?.error) }
    }

    /** PROF-06: validate (OQ-042), unchanged → close, offline → field error, then PATCH once. */
    private fun saveName() {
        val edit = local.value.editName ?: return
        if (edit.saving) return
        viewModelScope.launch {
            val current = profileRepository.profile.first()?.displayName
            val name = AuthValidator.normalizeDisplayName(edit.value)
            if (name == current) {
                local.update { it.copy(editName = null) }
                return@launch
            }
            val validation = when (AuthValidator.validateDisplayName(edit.value)) {
                DisplayNameError.Required -> NameError.REQUIRED
                DisplayNameError.Length -> NameError.LENGTH
                DisplayNameError.InvalidCharacters -> NameError.CHARACTERS
                null -> null
            }
            val error = validation ?: NameError.OFFLINE.takeIf { !isOnline.value }
            if (error != null) {
                setEdit { it.copy(error = error) }
                return@launch
            }
            setEdit { it.copy(saving = true, error = null) }
            when (val result = profileRepository.updateDisplayName(name)) {
                is DataResult.Success -> {
                    local.update { it.copy(editName = null) }
                    _events.send(ProfileEvent.ShowMessage(ProfileMessage.NAME_SAVED))
                }
                is DataResult.Failure -> setEdit { it.copy(saving = false, error = result.error.toNameError()) }
            }
        }
    }

    private fun setEdit(transform: (EditNameUi) -> EditNameUi) {
        local.update { state -> state.copy(editName = state.editName?.let(transform)) }
    }

    private fun DataError.toNameError(): NameError = when {
        this is DataError.Api && code == ApiErrorCode.DisplayNameNotAllowed -> NameError.NOT_ALLOWED
        this is DataError.Api && (code == ApiErrorCode.InvalidDisplayName || code == ApiErrorCode.ValidationFailed) ->
            NameError.INVALID
        this == DataError.Offline -> NameError.OFFLINE
        else -> NameError.FAILED
    }
}

/** Server values only; the categories keep the server's `sortOrder` and get the home accent slots. */
internal fun ProgressSummary.toUi(localNowMs: Long) = ProgressUi(
    level = level,
    xpIntoLevel = xpIntoLevel,
    xpForNextLevel = xpForNextLevel,
    xpTotal = xpTotal,
    fraction = RetentionFormat.fraction(xpIntoLevel, xpForNextLevel),
    streakCurrent = streakCurrent,
    streakBest = streakBest,
    todayActive = todayActive.takeUnless { isFromEarlierDay(localNowMs) },
    freezeAvailable = freezeAvailable,
    categories = categories.mapIndexed { index, category ->
        CategoryLevelUi(
            name = category.name,
            iconKey = category.iconKey,
            accent = CategoryAccent.resolve(index),
            level = category.level,
            fraction = RetentionFormat.fraction(category.xpIntoLevel, category.xpForNextLevel),
            xpToNext = (category.xpForNextLevel - category.xpIntoLevel).coerceAtLeast(0),
        )
    },
)

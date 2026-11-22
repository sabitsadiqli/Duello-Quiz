package com.sabit.duelloquiz.feature.settings.licenses

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.sabit.duelloquiz.core.common.AppConfig
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.data.licenses.LicenseRepository
import com.sabit.duelloquiz.core.model.OpenSourceComponent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One row of the licences list (licenses.md "Licence row"). */
@Immutable
data class LicenseRowUi(
    val id: String,
    val name: String,
    val version: String?,
    /** Licence names joined with ", "; null when the metadata has none (`settings_licenses_unknown`). */
    val licenseNames: String?,
)

@Immutable
data class LicensesUiState(
    val loading: Boolean = true,
    val assets: List<LicenseRowUi> = emptyList(),
    val libraries: List<LicenseRowUi> = emptyList(),
    /** The bundled metadata was unreadable: inline `settings_licenses_error` instead of the list. */
    val librariesError: Boolean = false,
    /** "DİNLƏMƏ SƏSLƏRİ" (OQ-120, licenses.md): only with the exams flag on. */
    val voices: List<VoiceCreditUi> = emptyList(),
)

/** A Piper TTS voice of the listening audio; [licence] null = public domain. */
@Immutable
data class VoiceCreditUi(val name: String, val licence: String?, val modelCardUrl: String)

/**
 * The voices allowed by OQ-120 (Sabit, 2026-11-17). Names and licences are proper names from the
 * Piper model cards (not translated); the CC BY attribution is the voice name with the link.
 */
internal val PiperVoices = listOf(
    VoiceCreditUi("en_GB-alba-medium", "CC BY 4.0", "https://huggingface.co/rhasspy/piper-voices/blob/main/en/en_GB/alba/medium/MODEL_CARD"),
    VoiceCreditUi(
        "en_US-libritts_r-medium · speaker 452",
        "CC BY 4.0",
        "https://huggingface.co/rhasspy/piper-voices/blob/main/en/en_US/libritts_r/medium/MODEL_CARD",
    ),
    VoiceCreditUi("en_GB-cori-high", null, "https://huggingface.co/rhasspy/piper-voices/blob/main/en/en_GB/cori/high/MODEL_CARD"),
)

sealed interface LicensesAction {
    data class RowClicked(val id: String) : LicensesAction
    data object BackClicked : LicensesAction
    data class VoiceClicked(val url: String) : LicensesAction
}

sealed interface LicensesEvent {
    data class OpenDetail(val id: String) : LicensesEvent
    data object NavigateBack : LicensesEvent
    data class OpenUrl(val url: String) : LicensesEvent
}

/** "Açıq mənbə lisenziyaları" list (SET-03, licenses.md). Everything is bundled: works offline. */
@HiltViewModel
class LicensesViewModel @Inject constructor(
    private val repository: LicenseRepository,
    private val appConfig: AppConfig = AppConfig("", 0, ""),
) : ViewModel() {

    private val _uiState = MutableStateFlow(LicensesUiState())
    val uiState: StateFlow<LicensesUiState> = _uiState.asStateFlow()

    private val _events = Channel<LicensesEvent>(Channel.BUFFERED)
    val events: Flow<LicensesEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val notices = repository.notices()
            _uiState.value = LicensesUiState(
                loading = false,
                assets = notices.assets.map { it.toRow() },
                libraries = notices.libraries.orEmpty().map { it.toRow() },
                librariesError = notices.libraries == null,
                voices = if (appConfig.examsEnabled) PiperVoices else emptyList(),
            )
        }
    }

    fun onAction(action: LicensesAction) {
        when (action) {
            is LicensesAction.RowClicked -> _events.trySend(LicensesEvent.OpenDetail(action.id))
            LicensesAction.BackClicked -> _events.trySend(LicensesEvent.NavigateBack)
            is LicensesAction.VoiceClicked -> _events.trySend(LicensesEvent.OpenUrl(action.url))
        }
    }
}

internal fun OpenSourceComponent.toRow() = LicenseRowUi(
    id = id,
    name = name,
    version = version,
    licenseNames = licenses.joinToString(", ") { it.name }.ifEmpty { null },
)

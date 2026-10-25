package com.sabit.duelloquiz.feature.settings.licenses

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
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

@Immutable
data class LicenseTextUi(
    val name: String,
    /** Licence text split on blank lines (one list item per paragraph); null = text missing. */
    val paragraphs: List<String>?,
)

@Immutable
data class LicenseDetailUi(
    val name: String,
    val author: String?,
    val version: String?,
    val websiteUrl: String?,
    /** First licence URL, for `settings_licenses_open_license`. */
    val licenseUrl: String?,
    val licenses: List<LicenseTextUi>,
)

@Immutable
data class LicenseDetailUiState(
    val loading: Boolean = true,
    /** Null after loading only for an unknown id (stale back stack after an app update). */
    val detail: LicenseDetailUi? = null,
)

sealed interface LicenseDetailAction {
    data object OpenWebsiteClicked : LicenseDetailAction
    data object OpenLicenseClicked : LicenseDetailAction
    data object BackClicked : LicenseDetailAction
}

sealed interface LicenseDetailEvent {
    data class OpenUrl(val url: String) : LicenseDetailEvent
    data object NavigateBack : LicenseDetailEvent
}

/** Licence detail (licenses.md "Layout — detail"); the id comes from the list. */
@HiltViewModel
class LicenseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: LicenseRepository,
) : ViewModel() {

    private val id: String = savedStateHandle.get<String>(ARG_ID).orEmpty()

    private val _uiState = MutableStateFlow(LicenseDetailUiState())
    val uiState: StateFlow<LicenseDetailUiState> = _uiState.asStateFlow()

    private val _events = Channel<LicenseDetailEvent>(Channel.BUFFERED)
    val events: Flow<LicenseDetailEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val detail = repository.component(id)?.toDetail()
            _uiState.value = LicenseDetailUiState(loading = false, detail = detail)
            if (detail == null) _events.send(LicenseDetailEvent.NavigateBack)
        }
    }

    fun onAction(action: LicenseDetailAction) {
        val detail = _uiState.value.detail
        when (action) {
            LicenseDetailAction.OpenWebsiteClicked ->
                detail?.websiteUrl?.let { _events.trySend(LicenseDetailEvent.OpenUrl(it)) }
            LicenseDetailAction.OpenLicenseClicked ->
                detail?.licenseUrl?.let { _events.trySend(LicenseDetailEvent.OpenUrl(it)) }
            LicenseDetailAction.BackClicked -> _events.trySend(LicenseDetailEvent.NavigateBack)
        }
    }

    internal companion object {
        /** Name of the [com.sabit.duelloquiz.feature.settings.navigation.LicenseDetailDestination] property. */
        const val ARG_ID = "id"
    }
}

private val BLANK_LINES = Regex("""\r?\n[ \t]*\r?\n\s*""")

internal fun splitParagraphs(text: String): List<String> =
    text.trim().split(BLANK_LINES).map { it.trim() }.filter { it.isNotEmpty() }

internal fun OpenSourceComponent.toDetail() = LicenseDetailUi(
    name = name,
    author = author,
    version = version,
    websiteUrl = websiteUrl,
    licenseUrl = licenses.firstNotNullOfOrNull { it.url },
    licenses = licenses.map { license ->
        LicenseTextUi(name = license.name, paragraphs = license.text?.let(::splitParagraphs)?.ifEmpty { null })
    },
)

package com.sabit.duelloquiz.feature.settings.licenses

import androidx.compose.runtime.Immutable
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
)

sealed interface LicensesAction {
    data class RowClicked(val id: String) : LicensesAction
    data object BackClicked : LicensesAction
}

sealed interface LicensesEvent {
    data class OpenDetail(val id: String) : LicensesEvent
    data object NavigateBack : LicensesEvent
}

/** "Açıq mənbə lisenziyaları" list (SET-03, licenses.md). Everything is bundled: works offline. */
@HiltViewModel
class LicensesViewModel @Inject constructor(
    private val repository: LicenseRepository,
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
            )
        }
    }

    fun onAction(action: LicensesAction) {
        when (action) {
            is LicensesAction.RowClicked -> _events.trySend(LicensesEvent.OpenDetail(action.id))
            LicensesAction.BackClicked -> _events.trySend(LicensesEvent.NavigateBack)
        }
    }
}

internal fun OpenSourceComponent.toRow() = LicenseRowUi(
    id = id,
    name = name,
    version = version,
    licenseNames = licenses.joinToString(", ") { it.name }.ifEmpty { null },
)

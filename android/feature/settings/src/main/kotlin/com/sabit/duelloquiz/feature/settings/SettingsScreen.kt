package com.sabit.duelloquiz.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabit.duelloquiz.core.common.openExternalUrl
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.ContentWidth
import com.sabit.duelloquiz.core.designsystem.component.DuelloConfirmDialog
import com.sabit.duelloquiz.core.designsystem.component.DuelloListRow
import com.sabit.duelloquiz.core.designsystem.component.DuelloRadioRow
import com.sabit.duelloquiz.core.designsystem.component.DuelloTopAppBar
import com.sabit.duelloquiz.core.designsystem.component.SectionHeader
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.util.ObserveAsEvents
import com.sabit.duelloquiz.core.model.ThemeMode
import com.sabit.duelloquiz.feature.settings.notifications.NotificationSectionRoute
import com.sabit.duelloquiz.feature.settings.notifications.NotificationSettingsMessage
import com.sabit.duelloquiz.feature.settings.notifications.NotificationSettingsViewModel
import kotlinx.coroutines.launch

@Composable
internal fun SettingsRoute(
    onBack: () -> Unit,
    onDeleteAccount: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    notificationsViewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            SettingsEvent.NavigateBack -> onBack()
            SettingsEvent.NavigateToDeleteAccount -> onDeleteAccount()
            is SettingsEvent.OpenUrl -> if (!context.openExternalUrl(event.url)) {
                scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.settings_link_open_failed)) }
            }
        }
    }

    SettingsScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        snackbarHostState = snackbarHostState,
        notificationSection = {
            NotificationSectionRoute(notificationsViewModel) { message ->
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        resources.getString(
                            when (message) {
                                NotificationSettingsMessage.SaveFailed -> R.string.settings_notif_save_failed
                                NotificationSettingsMessage.Offline -> R.string.settings_notif_offline
                            },
                        ),
                    )
                }
            }
        },
    )
}

@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    notificationSection: @Composable () -> Unit = {},
) {
    Scaffold(
        topBar = {
            DuelloTopAppBar(
                title = stringResource(R.string.settings_title),
                onBack = { onAction(SettingsAction.BackClicked) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        ContentWidth(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            LazyColumn {
                item(key = "theme") { ThemeSection(uiState.themeMode, onAction) }
                item(key = "language") { LanguageSection() }
                // SET-07: between "Dil" and "Hesab" (settings.md 4a–4d).
                item(key = "notifications") { notificationSection() }
                item(key = "account") { AccountSection(uiState, onAction) }
                item(key = "about") { AboutSection(uiState, onAction) }
                item(key = "bottom") { Spacer(Modifier.height(Spacing.xl)) }
            }
        }
    }

    if (uiState.showSignOutDialog) {
        DuelloConfirmDialog(
            title = stringResource(R.string.settings_sign_out_title),
            body = stringResource(R.string.settings_sign_out_body),
            confirmText = stringResource(R.string.settings_sign_out_confirm),
            dismissText = stringResource(R.string.common_cancel),
            onConfirm = { onAction(SettingsAction.SignOutConfirmed) },
            onDismiss = { onAction(SettingsAction.SignOutDismissed) },
        )
    }
}

@Composable
private fun ThemeSection(selected: ThemeMode, onAction: (SettingsAction) -> Unit) {
    Column {
        SectionHeader(stringResource(R.string.settings_section_theme))
        // Radio group semantics for TalkBack (SET-01).
        Column(Modifier.selectableGroup()) {
            DuelloRadioRow(
                text = stringResource(R.string.settings_theme_system),
                supporting = stringResource(R.string.settings_theme_system_supporting),
                selected = selected == ThemeMode.SYSTEM,
                onClick = { onAction(SettingsAction.ThemeSelected(ThemeMode.SYSTEM)) },
            )
            DuelloRadioRow(
                text = stringResource(R.string.settings_theme_light),
                selected = selected == ThemeMode.LIGHT,
                onClick = { onAction(SettingsAction.ThemeSelected(ThemeMode.LIGHT)) },
            )
            DuelloRadioRow(
                text = stringResource(R.string.settings_theme_dark),
                selected = selected == ThemeMode.DARK,
                onClick = { onAction(SettingsAction.ThemeSelected(ThemeMode.DARK)) },
            )
        }
    }
}

/** Only Azerbaijani in the MVP: a non-interactive, selected row (SET-02). */
@Composable
private fun LanguageSection() {
    Column {
        SectionHeader(stringResource(R.string.settings_section_language))
        DuelloListRow(
            headline = stringResource(R.string.settings_language_value),
            leadingIcon = DuelloIcons.Language,
            trailingIcon = DuelloIcons.Check,
            trailingIconTint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics(mergeDescendants = true) { selected = true },
        )
    }
}

@Composable
private fun AccountSection(uiState: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    Column {
        SectionHeader(stringResource(R.string.settings_section_account))
        val name = uiState.displayName
        val email = uiState.email
        if (name != null || email != null) {
            DuelloListRow(
                headline = name ?: email.orEmpty(),
                supporting = if (name != null && email != null) {
                    stringResource(R.string.settings_signed_in_as, email)
                } else {
                    null
                },
                leadingIcon = DuelloIcons.AccountCircle,
            )
        }
        DuelloListRow(
            headline = stringResource(R.string.settings_sign_out),
            leadingIcon = DuelloIcons.Logout,
            onClick = { onAction(SettingsAction.SignOutClicked) },
        )
        DuelloListRow(
            headline = stringResource(R.string.settings_delete_account),
            leadingIcon = DuelloIcons.Delete,
            trailingIcon = DuelloIcons.ChevronRight,
            destructive = true,
            onClick = { onAction(SettingsAction.DeleteAccountClicked) },
        )
    }
}

@Composable
private fun AboutSection(uiState: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    Column {
        SectionHeader(stringResource(R.string.settings_section_about))
        // No hidden actions on tap or long-press (SET-03).
        DuelloListRow(
            headline = stringResource(R.string.settings_version, uiState.versionName, uiState.versionCode),
            leadingIcon = DuelloIcons.Info,
        )
        DuelloListRow(
            headline = stringResource(R.string.settings_privacy_policy),
            leadingIcon = DuelloIcons.Policy,
            trailingIcon = DuelloIcons.OpenInNew,
            onClick = { onAction(SettingsAction.PrivacyPolicyClicked) },
        )
    }
}

private val PreviewState = SettingsUiState(
    themeMode = ThemeMode.SYSTEM,
    displayName = "Aysel",
    email = "aysel@example.com",
    versionName = "1.0.0",
    versionCode = 12,
)

@PreviewLightDark
@Composable
private fun SettingsPreview() {
    DuelloTheme { SettingsScreen(uiState = PreviewState, onAction = {}) }
}

@PreviewLightDark
@Composable
private fun SettingsSignOutDialogPreview() {
    DuelloTheme {
        SettingsScreen(uiState = PreviewState.copy(themeMode = ThemeMode.DARK, showSignOutDialog = true), onAction = {})
    }
}

package com.sabit.duelloquiz.feature.settings.delete

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.ContentWidth
import com.sabit.duelloquiz.core.designsystem.component.DuelloConfirmDialog
import com.sabit.duelloquiz.core.designsystem.component.DuelloDestructiveButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloNeutralButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloTopAppBar
import com.sabit.duelloquiz.core.designsystem.component.FormError
import com.sabit.duelloquiz.core.designsystem.component.ScreenHeadline
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.util.ObserveAsEvents

@Composable
internal fun DeleteAccountRoute(
    onBack: () -> Unit,
    viewModel: DeleteAccountViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            DeleteAccountEvent.NavigateBack -> onBack()
        }
    }
    // The request cannot be cancelled: back does nothing while it runs.
    BackHandler(enabled = uiState.isDeleting) {}

    DeleteAccountScreen(uiState = uiState, onAction = viewModel::onAction)
}

@Composable
internal fun DeleteAccountScreen(
    uiState: DeleteAccountUiState,
    onAction: (DeleteAccountAction) -> Unit,
) {
    Scaffold(
        topBar = {
            DuelloTopAppBar(
                onBack = { onAction(DeleteAccountAction.CancelClicked) },
                backEnabled = !uiState.isDeleting,
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            val minHeight = maxHeight
            ContentWidth {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = minHeight)
                        .padding(horizontal = Spacing.screenGutter),
                ) {
                    Icon(
                        painter = painterResource(DuelloIcons.Warning),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(Sizes.warningIcon),
                    )
                    Spacer(Modifier.height(Spacing.lg))
                    ScreenHeadline(stringResource(R.string.settings_delete_title))
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        text = stringResource(R.string.settings_delete_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        text = stringResource(R.string.settings_delete_body_matches),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = stringResource(R.string.settings_delete_body_backups),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.lg))
                    FormError(text = uiState.error?.let { stringResource(it.messageRes()) })
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.height(Spacing.xl))
                    DuelloDestructiveButton(
                        text = stringResource(R.string.settings_delete_confirm_button),
                        onClick = { onAction(DeleteAccountAction.DeleteClicked) },
                        loading = uiState.isDeleting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Spacing.md))
                    DuelloNeutralButton(
                        text = stringResource(R.string.common_cancel),
                        onClick = { onAction(DeleteAccountAction.CancelClicked) },
                        enabled = !uiState.isDeleting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (uiState.isDeleting) {
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            text = stringResource(R.string.settings_delete_in_progress),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.CenterHorizontally)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    Spacer(Modifier.height(Spacing.lg))
                }
            }
        }
    }

    if (uiState.showConfirmDialog) {
        DuelloConfirmDialog(
            title = stringResource(R.string.settings_delete_dialog_title),
            body = stringResource(R.string.settings_delete_dialog_body),
            confirmText = stringResource(R.string.settings_delete_dialog_confirm),
            dismissText = stringResource(R.string.common_cancel),
            onConfirm = { onAction(DeleteAccountAction.DialogConfirmed) },
            onDismiss = { onAction(DeleteAccountAction.DialogDismissed) },
            destructive = true,
        )
    }
}

private fun DeleteAccountError.messageRes(): Int = when (this) {
    DeleteAccountError.Offline -> R.string.settings_delete_offline
    DeleteAccountError.Failed -> R.string.settings_delete_failed
}

@PreviewLightDark
@Composable
private fun DeleteAccountDefaultPreview() {
    DuelloTheme { DeleteAccountScreen(uiState = DeleteAccountUiState(), onAction = {}) }
}

@PreviewLightDark
@Composable
private fun DeleteAccountDialogPreview() {
    DuelloTheme { DeleteAccountScreen(uiState = DeleteAccountUiState(showConfirmDialog = true), onAction = {}) }
}

@PreviewLightDark
@Composable
private fun DeleteAccountDeletingPreview() {
    DuelloTheme { DeleteAccountScreen(uiState = DeleteAccountUiState(isDeleting = true), onAction = {}) }
}

@PreviewLightDark
@Composable
private fun DeleteAccountErrorPreview() {
    DuelloTheme { DeleteAccountScreen(uiState = DeleteAccountUiState(error = DeleteAccountError.Offline), onAction = {}) }
}

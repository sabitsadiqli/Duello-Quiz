package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Elevation

/**
 * Confirmation dialog (components.md §9). No icon; confirm on the right; scrim tap and back
 * call [onDismiss] (the safe action). [destructive] colours the confirm action with error.
 */
@Composable
fun DuelloConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            DuelloTextButton(
                text = confirmText,
                onClick = onConfirm,
                contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        },
        dismissButton = { DuelloTextButton(text = dismissText, onClick = onDismiss) },
        title = { Text(text = title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(text = body, style = MaterialTheme.typography.bodyMedium) },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = Elevation.level0,
    )
}

@PreviewLightDark
@Composable
private fun ConfirmDialogPreview() {
    DuelloTheme {
        Surface {
            DuelloConfirmDialog(
                title = stringResource(R.string.settings_delete_dialog_title),
                body = stringResource(R.string.settings_delete_dialog_body),
                confirmText = stringResource(R.string.settings_delete_dialog_confirm),
                dismissText = stringResource(R.string.common_cancel),
                onConfirm = {},
                onDismiss = {},
                destructive = true,
            )
        }
    }
}

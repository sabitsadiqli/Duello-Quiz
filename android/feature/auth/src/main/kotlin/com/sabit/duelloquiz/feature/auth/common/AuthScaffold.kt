package com.sabit.duelloquiz.feature.auth.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.sabit.duelloquiz.core.auth.AuthRules
import com.sabit.duelloquiz.core.auth.DisplayNameError
import com.sabit.duelloquiz.core.auth.EmailError
import com.sabit.duelloquiz.core.auth.PasswordError
import com.sabit.duelloquiz.core.designsystem.component.ContentWidth
import com.sabit.duelloquiz.core.designsystem.component.DuelloTopAppBar
import com.sabit.duelloquiz.core.designsystem.theme.Spacing

/**
 * Shared layout of the email auth screens: back-only top app bar, scrollable column with the
 * screen gutters, content kept above the keyboard (imePadding), centred on wide screens.
 */
@Composable
internal fun AuthFormScaffold(
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = { DuelloTopAppBar(onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        ContentWidth(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenGutter),
            ) {
                content()
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}

@Composable
internal fun EmailError.text(): String = stringResource(messageRes())

@Composable
internal fun PasswordError.text(): String = when (this) {
    PasswordError.TooShort -> stringResource(messageRes(), AuthRules.PASSWORD_MIN_LENGTH)
    PasswordError.Required -> stringResource(messageRes())
}

@Composable
internal fun DisplayNameError.text(): String = when (this) {
    DisplayNameError.Length ->
        stringResource(messageRes(), AuthRules.DISPLAY_NAME_MIN_LENGTH, AuthRules.DISPLAY_NAME_MAX_LENGTH)
    else -> stringResource(messageRes())
}

@Composable
internal fun AuthFormError.text(): String = stringResource(messageRes())

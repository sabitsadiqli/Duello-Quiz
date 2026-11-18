package com.sabit.duelloquiz.feature.settings.licenses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.ContentWidth
import com.sabit.duelloquiz.core.designsystem.component.DuelloLoadingView
import com.sabit.duelloquiz.core.designsystem.component.DuelloTopAppBar
import com.sabit.duelloquiz.core.designsystem.component.SectionHeader
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.util.ObserveAsEvents

@Composable
internal fun LicensesRoute(
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    viewModel: LicensesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is LicensesEvent.OpenDetail -> onOpenDetail(event.id)
            LicensesEvent.NavigateBack -> onBack()
        }
    }
    LicensesScreen(uiState = uiState, onAction = viewModel::onAction)
}

@Composable
internal fun LicensesScreen(uiState: LicensesUiState, onAction: (LicensesAction) -> Unit) {
    Scaffold(
        topBar = {
            DuelloTopAppBar(
                title = stringResource(R.string.settings_licenses_title),
                onBack = { onAction(LicensesAction.BackClicked) },
            )
        },
    ) { innerPadding ->
        ContentWidth(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            if (uiState.loading) {
                DuelloLoadingView(caption = stringResource(R.string.common_loading))
            } else {
                LicensesList(uiState, onAction)
            }
        }
    }
}

@Composable
private fun LicensesList(uiState: LicensesUiState, onAction: (LicensesAction) -> Unit) {
    // The list keeps its scroll position when coming back from a detail (saveable LazyListState).
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "intro") {
            Text(
                text = stringResource(R.string.settings_licenses_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.md),
            )
        }
        item(key = "assets_header") { SectionHeader(stringResource(R.string.settings_licenses_section_assets)) }
        items(uiState.assets, key = { "asset_${it.id}" }) { row ->
            LicenseRow(row, onClick = { onAction(LicensesAction.RowClicked(row.id)) })
        }
        if (uiState.librariesError || uiState.libraries.isNotEmpty()) {
            item(key = "libraries_header") { SectionHeader(stringResource(R.string.settings_licenses_section_libraries)) }
        }
        if (uiState.librariesError) {
            item(key = "libraries_error") {
                Text(
                    text = stringResource(R.string.settings_licenses_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
            }
        } else {
            items(uiState.libraries, key = { "library_${it.id}" }) { row ->
                LicenseRow(row, onClick = { onAction(LicensesAction.RowClicked(row.id)) })
            }
        }
        item(key = "bottom") { Spacer(Modifier.height(Spacing.xl)) }
    }
}

@Composable
private fun LicenseRow(row: LicenseRowUi, onClick: () -> Unit) {
    val licenses = row.licenseNames ?: stringResource(R.string.settings_licenses_unknown)
    val supporting = if (row.version != null) {
        stringResource(
            R.string.settings_licenses_item_supporting,
            stringResource(R.string.settings_licenses_version, row.version),
            licenses,
        )
    } else {
        licenses
    }
    val description = if (row.version != null) {
        stringResource(R.string.settings_licenses_item_cd, row.name, row.version, licenses)
    } else {
        "${row.name}, $licenses"
    }
    ListItem(
        headlineContent = { Text(text = row.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = { Icon(painter = painterResource(DuelloIcons.ChevronRight), contentDescription = null) },
        colors = ListItemDefaults.colors(
            headlineColor = MaterialTheme.colorScheme.onSurface,
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
            trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick { onClick(); true }
            },
    )
}

internal val PreviewLicensesState = LicensesUiState(
    loading = false,
    assets = listOf(
        LicenseRowUi("asset:nunito", "Nunito", version = null, licenseNames = "SIL Open Font License 1.1"),
        LicenseRowUi("asset:material-symbols", "Material Symbols", version = null, licenseNames = "Apache License 2.0"),
    ),
    libraries = listOf(
        LicenseRowUi("io.coil-kt.coil3:coil", "Coil", version = "3.6.3", licenseNames = "Apache License 2.0"),
        LicenseRowUi(
            "org.jetbrains.kotlinx:kotlinx-serialization-json",
            "Kotlinx Serialization JSON",
            version = "1.11.0",
            licenseNames = "Apache License 2.0",
        ),
        LicenseRowUi("example:unknown", "Example Library", version = "1.0.0", licenseNames = null),
    ),
)

@PreviewLightDark
@Composable
private fun LicensesPreview() {
    DuelloTheme { LicensesScreen(uiState = PreviewLicensesState, onAction = {}) }
}

@PreviewLightDark
@Composable
private fun LicensesErrorPreview() {
    DuelloTheme {
        LicensesScreen(uiState = PreviewLicensesState.copy(libraries = emptyList(), librariesError = true), onAction = {})
    }
}

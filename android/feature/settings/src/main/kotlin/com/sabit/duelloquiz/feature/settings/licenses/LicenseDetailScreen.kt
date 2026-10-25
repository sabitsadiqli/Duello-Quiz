package com.sabit.duelloquiz.feature.settings.licenses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabit.duelloquiz.core.common.openExternalUrl
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.ContentWidth
import com.sabit.duelloquiz.core.designsystem.component.DuelloLoadingView
import com.sabit.duelloquiz.core.designsystem.component.DuelloTopAppBar
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.util.ObserveAsEvents
import kotlinx.coroutines.launch

@Composable
internal fun LicenseDetailRoute(
    onBack: () -> Unit,
    viewModel: LicenseDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is LicenseDetailEvent.OpenUrl -> if (!context.openExternalUrl(event.url)) {
                scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.settings_link_open_failed)) }
            }
            LicenseDetailEvent.NavigateBack -> onBack()
        }
    }
    LicenseDetailScreen(uiState = uiState, onAction = viewModel::onAction, snackbarHostState = snackbarHostState)
}

@Composable
internal fun LicenseDetailScreen(
    uiState: LicenseDetailUiState,
    onAction: (LicenseDetailAction) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        // No title: library names are often longer than the bar allows (licenses.md).
        topBar = { DuelloTopAppBar(onBack = { onAction(LicenseDetailAction.BackClicked) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        ContentWidth(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            val detail = uiState.detail
            if (detail == null) {
                if (uiState.loading) DuelloLoadingView(caption = stringResource(R.string.common_loading))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    header(detail, onAction)
                    detail.licenses.forEachIndexed { index, license -> licenseText(index, license) }
                    item(key = "bottom") { Spacer(Modifier.height(Spacing.xl)) }
                }
            }
        }
    }
}

private val GutterModifier = Modifier.padding(horizontal = Spacing.screenGutter)

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.header(detail: LicenseDetailUi, onAction: (LicenseDetailAction) -> Unit) {
    item(key = "name") {
        Text(
            text = detail.name,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = GutterModifier.semantics { heading() },
        )
    }
    detail.author?.let { author ->
        item(key = "author") {
            Text(
                text = author,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = GutterModifier.padding(top = Spacing.xs),
            )
        }
    }
    detail.version?.let { version ->
        item(key = "version") {
            Text(
                text = stringResource(R.string.settings_licenses_version, version),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = GutterModifier.padding(top = Spacing.xxs),
            )
        }
    }
    if (detail.websiteUrl != null || detail.licenseUrl != null) {
        item(key = "links") {
            FlowRow(Modifier.padding(start = Spacing.sm, end = Spacing.sm, top = Spacing.md)) {
                if (detail.websiteUrl != null) {
                    LinkButton(
                        text = stringResource(R.string.settings_licenses_open_website),
                        showOpenInNew = true,
                        onClick = { onAction(LicenseDetailAction.OpenWebsiteClicked) },
                    )
                }
                if (detail.licenseUrl != null) {
                    LinkButton(
                        text = stringResource(R.string.settings_licenses_open_license),
                        showOpenInNew = false,
                        onClick = { onAction(LicenseDetailAction.OpenLicenseClicked) },
                    )
                }
            }
        }
    }
}

private fun LazyListScope.licenseText(index: Int, license: LicenseTextUi) {
    item(key = "license_${index}_name") {
        Text(
            text = license.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = GutterModifier.padding(top = Spacing.lg, bottom = Spacing.sm),
        )
    }
    val paragraphs = license.paragraphs
    if (paragraphs == null) {
        item(key = "license_${index}_missing") {
            Text(
                text = stringResource(R.string.settings_licenses_text_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = GutterModifier,
            )
        }
        return
    }
    // One card split into paragraph items, so a long licence is not one huge text node.
    itemsIndexed(paragraphs, key = { paragraph, _ -> "license_${index}_p$paragraph" }) { paragraph, text ->
        val shape = cardSegmentShape(first = paragraph == 0, last = paragraph == paragraphs.lastIndex)
        SelectionContainer(
            GutterModifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
                .padding(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    top = if (paragraph == 0) Spacing.lg else Spacing.none,
                    bottom = if (paragraph == paragraphs.lastIndex) Spacing.lg else Spacing.md,
                ),
        ) {
            Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun cardSegmentShape(first: Boolean, last: Boolean): Shape {
    val medium = MaterialTheme.shapes.medium
    return when {
        first && last -> medium
        first -> medium.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
        last -> medium.copy(topStart = ZeroCornerSize, topEnd = ZeroCornerSize)
        else -> RectangleShape
    }
}

@Composable
private fun LinkButton(text: String, showOpenInNew: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = Sizes.minTouchTarget),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (showOpenInNew) {
            Spacer(Modifier.width(Spacing.xs))
            Icon(
                painter = painterResource(DuelloIcons.OpenInNew),
                contentDescription = null,
                modifier = Modifier.size(Sizes.iconSmall),
            )
        }
    }
}

private val PreviewDetail = LicenseDetailUi(
    name = "Kotlinx Serialization JSON",
    author = "JetBrains",
    version = "1.11.0",
    websiteUrl = "https://github.com/Kotlin/kotlinx.serialization",
    licenseUrl = "https://spdx.org/licenses/Apache-2.0.html",
    licenses = listOf(
        LicenseTextUi(
            name = "Apache License 2.0",
            paragraphs = listOf(
                "Apache License\nVersion 2.0, January 2004",
                "TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION",
                "1. Definitions.",
            ),
        ),
    ),
)

@PreviewLightDark
@Composable
private fun LicenseDetailPreview() {
    DuelloTheme { LicenseDetailScreen(uiState = LicenseDetailUiState(loading = false, detail = PreviewDetail), onAction = {}) }
}

@PreviewLightDark
@Composable
private fun LicenseDetailTextMissingPreview() {
    DuelloTheme {
        LicenseDetailScreen(
            uiState = LicenseDetailUiState(
                loading = false,
                detail = PreviewDetail.copy(
                    websiteUrl = null,
                    licenses = listOf(LicenseTextUi("Android Software Development Kit License", paragraphs = null)),
                ),
            ),
            onAction = {},
        )
    }
}

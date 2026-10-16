package com.sabit.duelloquiz.feature.quiz.review

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.ContentWidth
import com.sabit.duelloquiz.core.designsystem.component.DuelloLoadingView
import com.sabit.duelloquiz.core.designsystem.component.DuelloStateView
import com.sabit.duelloquiz.core.designsystem.component.DuelloTopAppBar
import com.sabit.duelloquiz.core.designsystem.component.ReviewThumbnail
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.util.ObserveAsEvents

@Composable
internal fun QuizReviewRoute(
    onBack: () -> Unit,
    viewModel: QuizReviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            QuizReviewEvent.NavigateBack -> onBack()
        }
    }
    QuizReviewScreen(uiState = uiState, onAction = viewModel::onAction)
}

@Composable
internal fun QuizReviewScreen(uiState: QuizReviewUiState, onAction: (QuizReviewAction) -> Unit) {
    Scaffold(
        topBar = {
            DuelloTopAppBar(
                title = stringResource(R.string.quiz_review_title),
                onBack = { onAction(QuizReviewAction.BackClicked) },
            )
        },
    ) { innerPadding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
        when (uiState) {
            QuizReviewUiState.Loading -> Box(contentAlignment = Alignment.Center, modifier = modifier) {
                DuelloLoadingView()
            }
            is QuizReviewUiState.Error -> Box(contentAlignment = Alignment.Center, modifier = modifier) {
                DuelloStateView(
                    icon = if (uiState.offline) DuelloIcons.CloudOff else DuelloIcons.Error,
                    title = stringResource(if (uiState.offline) R.string.error_offline else R.string.error_generic),
                    primaryActionText = stringResource(R.string.common_retry),
                    onPrimaryAction = { onAction(QuizReviewAction.RetryClicked) },
                )
            }
            is QuizReviewUiState.Content -> ContentWidth(modifier = modifier) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = Spacing.screenGutter, vertical = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(uiState.items, key = { it.questionId }) { item -> ReviewCard(item) }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(item: ReviewItemUi) {
    val game = MaterialTheme.gameColors
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { isTraversalGroup = true },
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(
                text = stringResource(R.string.quiz_review_question_number, item.position),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (item.thumbUrl != null) {
                    Spacer(Modifier.width(Spacing.md))
                    ReviewThumbnail(
                        url = item.thumbUrl,
                        dominantColorArgb = item.dominantColorArgb,
                        contentDescription = item.imageAlt ?: stringResource(R.string.quiz_image_cd_fallback),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = stringResource(R.string.quiz_review_your_answer),
                style = MaterialTheme.typography.labelMedium,
                color = game.wrong,
            )
            Spacer(Modifier.height(Spacing.xs))
            if (item.selectedText != null) {
                CompactOptionRow(item.selectedText, DuelloIcons.Close, game.wrongContainer, game.onWrongContainer)
            } else {
                CompactOptionRow(
                    text = stringResource(R.string.quiz_review_not_answered),
                    icon = DuelloIcons.TimerOff,
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.quiz_review_correct_answer),
                style = MaterialTheme.typography.labelMedium,
                color = game.correct,
            )
            Spacer(Modifier.height(Spacing.xs))
            CompactOptionRow(item.correctText, DuelloIcons.Check, game.correctContainer, game.onCorrectContainer)
            if (item.explanation != null) {
                Spacer(Modifier.height(Spacing.md))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = stringResource(R.string.quiz_review_explanation),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = item.explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Compact option row of the review (icon + text; meaning never relies on colour alone). */
@Composable
private fun CompactOptionRow(text: String, @DrawableRes icon: Int, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(Sizes.inlineIcon))
            Spacer(Modifier.width(Spacing.sm))
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@PreviewLightDark
@Composable
private fun QuizReviewPreview() {
    DuelloTheme {
        QuizReviewScreen(
            uiState = QuizReviewUiState.Content(
                listOf(
                    ReviewItemUi("q2", 2, "Azərbaycanın ərazisindən axan ən uzun çay hansıdır?", null, null, null, "Araz", "Kür", "Kür 1 515 km uzunluğundadır."),
                    ReviewItemUi("q5", 5, "Bakı hansı dənizin sahilindədir?", null, null, null, null, "Xəzər", null),
                ),
            ),
            onAction = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun QuizReviewErrorPreview() {
    DuelloTheme { QuizReviewScreen(uiState = QuizReviewUiState.Error(offline = true), onAction = {}) }
}

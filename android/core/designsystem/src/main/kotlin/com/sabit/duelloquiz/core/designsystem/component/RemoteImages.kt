package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import coil3.compose.AsyncImage
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors

/** 16:9 (size.questionImageAspect). */
private const val QUESTION_IMAGE_ASPECT = 16f / 9f

/**
 * Question image (quiz-question.md): full width, 16:9, max 200dp, `ContentScale.Fit` on the
 * image's dominant colour ([dominantColorArgb], fallback `game.imagePlaceholderFallback`) so the
 * box is calm while loading, without a spinner. Coil requests it at the displayed size and keeps
 * it in the disk cache. On failure the box keeps its colour and shows [failedText]; the question
 * stays answerable and the timer is never paused for the image.
 */
@Composable
fun QuestionImage(
    url: String,
    dominantColorArgb: Long?,
    contentDescription: String,
    failedText: String,
    modifier: Modifier = Modifier,
) {
    var failed by remember(url) { mutableStateOf(false) }
    val background = dominantColorArgb?.let { Color(it) } ?: MaterialTheme.gameColors.imagePlaceholderFallback
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = Sizes.questionImageMaxHeight)
            .aspectRatio(QUESTION_IMAGE_ASPECT)
            .clip(MaterialTheme.shapes.medium)
            .background(background)
            .semantics { this.contentDescription = contentDescription },
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onError = { failed = true },
            onSuccess = { failed = false },
            modifier = Modifier.fillMaxSize(),
        )
        if (failed) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.padding(Spacing.sm),
                ) {
                    Icon(
                        painter = painterResource(DuelloIcons.BrokenImage),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = failedText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Review thumbnail (quiz-result.md): size.reviewThumbnail, shape.small, dominant-colour box. */
@Composable
fun ReviewThumbnail(
    url: String,
    dominantColorArgb: Long?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val background = dominantColorArgb?.let { Color(it) } ?: MaterialTheme.gameColors.imagePlaceholderFallback
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(Sizes.reviewThumbnail)
            .clip(MaterialTheme.shapes.small)
            .background(background),
    )
}

@PreviewLightDark
@Composable
private fun QuestionImagePreview() {
    DuelloTheme {
        Surface {
            QuestionImage(
                url = "",
                dominantColorArgb = PREVIEW_COLOR,
                contentDescription = "",
                failedText = "",
                modifier = Modifier.padding(Spacing.lg),
            )
        }
    }
}

private const val PREVIEW_COLOR = 0xFF336699L

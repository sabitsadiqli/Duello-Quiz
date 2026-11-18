package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.daily.ResultSquareState
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DailyCardShape
import com.sabit.duelloquiz.core.designsystem.theme.Depth
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.GameSizes
import com.sabit.duelloquiz.core.designsystem.theme.LightColorScheme
import com.sabit.duelloquiz.core.designsystem.theme.LightGameColors
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.theme.gradients
import com.sabit.duelloquiz.core.designsystem.theme.tabularFigures
import com.sabit.duelloquiz.core.designsystem.theme.toBrush
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion
import kotlinx.coroutines.delay

/** components.md §28: decorative icon square on the gradient face, white at 18 %. */
private const val DailyDecorAlpha = 0.18f

/** components.md §27: dash length of the "not confirmed" square border. */
private const val PendingDashPx = 8f

/**
 * Result squares (components.md §27): one per daily question in order. [large] = the result share
 * card (40dp, numbers under the squares); compact = the home card (24dp). Glyphs are never removed
 * (colour is never the only signal). One TalkBack element ("Nəticələr: sual 1 düzgün, …").
 * [animate]: squares pop in left to right (motion.progression.resultSquaresEnter).
 */
@Composable
fun ResultSquaresRow(
    squares: List<ResultSquareState>,
    large: Boolean,
    modifier: Modifier = Modifier,
    animate: Boolean = false,
) {
    val items = squares.mapIndexed { index, square ->
        stringResource(R.string.daily_square_item_cd, index + 1, stringResource(square.descriptionRes()))
    }
    val description = stringResource(R.string.daily_squares_cd, items.joinToString(", "))
    val reducedMotion = rememberReducedMotion() || LocalInspectionMode.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (large) Spacing.sm else Spacing.xs),
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        squares.forEachIndexed { index, square ->
            val scale = remember { Animatable(if (animate && !reducedMotion) Motion.Progression.RESULT_SQUARES_FROM else 1f) }
            LaunchedEffect(animate) {
                if (animate && !reducedMotion) {
                    delay(index * Motion.Progression.RESULT_SQUARES_STAGGER_MS.toLong())
                    scale.animateTo(1f, Motion.bouncy())
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
            ) {
                ResultSquare(square, if (large) Sizes.resultSquare else Sizes.resultSquareCompact, large)
                if (large) {
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.labelSmall.tabularFigures(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultSquare(state: ResultSquareState, size: Dp, large: Boolean) {
    val game = MaterialTheme.gameColors
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraSmall
    val (face, glyphColor, icon) = when (state) {
        ResultSquareState.Correct -> Triple(game.correct, game.onCorrect, DuelloIcons.Check)
        ResultSquareState.Wrong -> Triple(game.wrong, game.onWrong, DuelloIcons.Close)
        ResultSquareState.None -> Triple(scheme.surfaceContainerHighest, scheme.onSurfaceVariant, DuelloIcons.Remove)
        ResultSquareState.Pending -> Triple(scheme.surfaceContainerHigh, scheme.onSurfaceVariant, DuelloIcons.CloudUpload)
    }
    val outline = scheme.outline
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .background(face, shape)
            .then(
                when (state) {
                    ResultSquareState.None -> Modifier.border(BorderStroke(Depth.border, outline), shape)
                    ResultSquareState.Pending -> Modifier.drawBehind {
                        val stroke = Depth.border.toPx()
                        drawRoundRect(
                            color = outline,
                            cornerRadius = CornerRadius(stroke * 2),
                            style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(PendingDashPx, PendingDashPx / 2))),
                        )
                    }
                    else -> Modifier
                },
            ),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = glyphColor,
            modifier = Modifier.size(if (large) GameSizes.resultSquareGlyph else GameSizes.resultSquareGlyphCompact),
        )
    }
}

private fun ResultSquareState.descriptionRes(): Int = when (this) {
    ResultSquareState.Correct -> R.string.daily_square_correct_cd
    ResultSquareState.Wrong -> R.string.daily_square_wrong_cd
    ResultSquareState.None -> R.string.daily_square_none_cd
    ResultSquareState.Pending -> R.string.daily_square_pending_cd
}

/** The faces of the home daily card (components.md §28, daily-challenge.md A). */
@Immutable
sealed interface DailyCardUi {
    data object Skeleton : DailyCardUi

    data class Available(val number: Int, val questionCount: Int, val starting: Boolean = false) : DailyCardUi

    data class InProgress(val number: Int, val starting: Boolean = false) : DailyCardUi

    /**
     * [squares] null: the response had no squares (no squares row, no share). [countdown] null =
     * the countdown reached 0 and the new day is loading. [shareEnabled] false while any square
     * is unconfirmed.
     */
    data class Finished(
        val number: Int,
        val correctCount: Int,
        val total: Int,
        val squares: List<ResultSquareState>?,
        val countdown: String?,
        val remainingHours: Int,
        val remainingMinutes: Int,
        val shareEnabled: Boolean,
    ) : DailyCardUi

    data object Unavailable : DailyCardUi

    /** Offline with a cache of an older day: never claim "finished" for a new day (DAILY-01). */
    data object OfflineUnknown : DailyCardUi

    /** `GET /v1/daily` failed without a usable cache: only the card retries (HOME-07). */
    data object Error : DailyCardUi
}

/**
 * Daily challenge card (components.md §28). Gradient face while there is something to do
 * (available, in progress), white face once the day is done or unknown. The same minimum height
 * in every state keeps home from jumping.
 */
@Composable
fun DailyCard(
    ui: DailyCardUi,
    onStart: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val minHeight = Modifier.heightIn(min = DailyCardMinHeight)
    when (ui) {
        DailyCardUi.Skeleton -> DailySkeleton(modifier.then(minHeight))
        is DailyCardUi.Available -> DailyGradientCard(
            number = ui.number,
            hint = pluralStringResource(R.plurals.home_daily_hint, ui.questionCount, ui.questionCount),
            action = stringResource(R.string.home_daily_start),
            description = pluralStringResource(R.plurals.home_daily_card_cd, ui.questionCount, ui.number, ui.questionCount),
            starting = ui.starting,
            onClick = onStart,
            modifier = modifier.then(minHeight),
        )
        is DailyCardUi.InProgress -> DailyGradientCard(
            number = ui.number,
            hint = stringResource(R.string.home_daily_in_progress_hint),
            action = stringResource(R.string.home_daily_resume),
            description = stringResource(R.string.home_daily_card_in_progress_cd, ui.number),
            starting = ui.starting,
            onClick = onStart,
            modifier = modifier.then(minHeight),
        )
        is DailyCardUi.Finished -> DailyFinishedCard(ui, onShare, modifier.then(minHeight))
        DailyCardUi.Unavailable -> DailyMessageCard(DuelloIcons.EventBusy, stringResource(R.string.home_daily_unavailable), null, modifier.then(minHeight))
        DailyCardUi.OfflineUnknown -> DailyMessageCard(DuelloIcons.CloudOff, stringResource(R.string.home_daily_offline_unknown), null, modifier.then(minHeight))
        DailyCardUi.Error -> DailyMessageCard(DuelloIcons.Error, stringResource(R.string.home_daily_error), onRetry, modifier.then(minHeight))
    }
}

/** Height of the "available" layout at font scale 1.0; larger fonts grow every face alike. */
private val DailyCardMinHeight = Sizes.categoryTileMinHeight + Sizes.buttonHeightLarge + Spacing.xl

@Composable
private fun DailyGradientCard(
    number: Int,
    hint: String,
    action: String,
    description: String,
    starting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val gradient = MaterialTheme.gradients.dailyHero
    PressableSurface(
        onClick = onClick,
        faceColor = gradient.start,
        faceBrush = gradient.toBrush(),
        depthColor = gradient.depth,
        contentColor = gradient.on,
        shape = DailyCardShape,
        depth = Depth.pressableLarge,
        interactive = !starting,
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = GameSizes.dailyCardPaddingVertical),
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = description
                if (!starting) onClick(label = action) { onClick(); true }
            },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.daily_title), style = MaterialTheme.typography.labelLarge, color = gradient.on)
                    Text(
                        text = stringResource(R.string.daily_number, number),
                        style = MaterialTheme.typography.headlineMedium.tabularFigures(),
                        color = gradient.on,
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(GameSizes.duelHeroDecor)
                        .background(Color.White.copy(alpha = DailyDecorAlpha), MaterialTheme.shapes.medium),
                ) {
                    Icon(
                        painter = painterResource(DuelloIcons.Today),
                        contentDescription = null,
                        tint = gradient.on,
                        modifier = Modifier.size(Sizes.iconLarge),
                    )
                }
            }
            Text(text = hint, style = MaterialTheme.typography.bodyMedium, color = gradient.on)
            // Same recipe as the duel hero card button: light-theme values in both themes.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LightColorScheme.primaryContainer, MaterialTheme.shapes.medium)
                    .padding(bottom = Depth.pressable)
                    .background(LightGameColors.pressableNeutral, MaterialTheme.shapes.medium)
                    .heightIn(min = Sizes.buttonHeight),
            ) {
                if (starting) {
                    CircularProgressIndicator(
                        color = LightGameColors.onPressableNeutral,
                        strokeWidth = Sizes.progressStroke,
                        modifier = Modifier.size(Sizes.inlineProgress),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Spacing.xl)) {
                        Icon(
                            painter = painterResource(DuelloIcons.PlayArrow),
                            contentDescription = null,
                            tint = LightGameColors.onPressableNeutral,
                            modifier = Modifier.size(Sizes.inlineIcon),
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = action,
                            style = MaterialTheme.typography.labelLarge,
                            color = LightGameColors.onPressableNeutral,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyFinishedCard(ui: DailyCardUi.Finished, onShare: () -> Unit, modifier: Modifier) {
    val description = stringResource(
        R.string.home_daily_card_finished_cd,
        ui.number,
        ui.correctCount,
        ui.total,
        ui.remainingHours,
        ui.remainingMinutes,
    )
    GameCard(
        faceColor = MaterialTheme.gameColors.pressableNeutral,
        contentPadding = PaddingValues(Spacing.lg),
        shape = DailyCardShape,
        modifier = modifier
            .fillMaxWidth()
            .semantics { isTraversalGroup = true },
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.daily_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.daily_number, ui.number),
                        style = MaterialTheme.typography.headlineMedium.tabularFigures(),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                Text(
                    text = stringResource(R.string.daily_score_short, ui.correctCount, ui.total),
                    style = MaterialTheme.typography.headlineMedium.tabularFigures(),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
            ui.squares?.let { squares ->
                Spacer(Modifier.height(Spacing.md))
                ResultSquaresRow(squares = squares, large = false)
            }
            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clearAndSetSemantics {}) {
                if (ui.countdown != null) {
                    Icon(
                        painter = painterResource(DuelloIcons.Schedule),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Sizes.iconSmall),
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        text = stringResource(R.string.home_daily_next_countdown, ui.countdown),
                        style = MaterialTheme.typography.titleSmall.tabularFigures(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                } else {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        strokeWidth = Sizes.progressStroke,
                        modifier = Modifier.size(Sizes.pillIcon),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = stringResource(R.string.home_daily_refreshing),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            if (ui.squares != null) {
                Spacer(Modifier.height(Spacing.md))
                ShareButton(enabled = ui.shareEnabled, onClick = onShare, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Neutral pressable "Paylaş" with the 20dp share icon (daily result card and finished home card). */
@Composable
fun ShareButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    DuelloButton(
        text = stringResource(R.string.daily_share),
        onClick = onClick,
        variant = ButtonVariant.Neutral,
        enabled = enabled,
        readableWhenDisabled = true,
        leading = {
            Icon(
                painter = painterResource(DuelloIcons.Share),
                contentDescription = null,
                tint = LocalContentColor.current,
                modifier = Modifier.size(Sizes.inlineIcon),
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun DailyMessageCard(icon: Int, text: String, onRetry: (() -> Unit)?, modifier: Modifier) {
    GameCard(
        faceColor = MaterialTheme.gameColors.pressableNeutral,
        contentPadding = PaddingValues(Spacing.lg),
        shape = DailyCardShape,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(GameSizes.duelHeroDecor)
                    .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium),
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(Sizes.iconLarge),
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = stringResource(R.string.daily_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                if (onRetry != null) {
                    DuelloTextButton(text = stringResource(R.string.common_retry), onClick = onRetry)
                }
            }
        }
    }
}

@Composable
private fun DailySkeleton(modifier: Modifier) {
    val alpha = rememberSkeletonAlpha()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = DailyCardShape,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha() }
            .clearAndSetSemantics {},
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(Spacing.xl)) {
            Box(Modifier.width(Sizes.skeletonLineWidthShort).height(Sizes.skeletonLineHeight).background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraSmall))
            Box(Modifier.width(Sizes.skeletonLineWidth).height(Sizes.skeletonLineHeight).background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraSmall))
            Box(Modifier.fillMaxWidth().height(Sizes.buttonHeight).background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium))
        }
    }
}

@PreviewLightDark
@Composable
private fun DailyCardPreview() {
    DuelloTheme {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg), modifier = Modifier.padding(Spacing.lg)) {
                DailyCard(DailyCardUi.Available(number = 12, questionCount = 5), {}, {}, {})
                DailyCard(
                    DailyCardUi.Finished(
                        number = 12,
                        correctCount = 3,
                        total = 5,
                        squares = listOf(
                            ResultSquareState.Correct, ResultSquareState.Wrong, ResultSquareState.Correct,
                            ResultSquareState.Correct, ResultSquareState.None,
                        ),
                        countdown = "05:12:33",
                        remainingHours = 5,
                        remainingMinutes = 12,
                        shareEnabled = true,
                    ),
                    {}, {}, {},
                )
                DailyCard(DailyCardUi.Unavailable, {}, {}, {})
                ResultSquaresRow(
                    squares = listOf(ResultSquareState.Correct, ResultSquareState.Pending, ResultSquareState.None),
                    large = true,
                )
            }
        }
    }
}

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
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.daily.ResultSquareState
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.Borders
import com.sabit.duelloquiz.core.designsystem.theme.CardShape
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.GameSizes
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.theme.numeralLarge
import com.sabit.duelloquiz.core.designsystem.theme.overline
import com.sabit.duelloquiz.core.designsystem.theme.tabularFigures
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion
import kotlinx.coroutines.delay

/**
 * Result squares (components.md §27): one per daily question in order. [large] = the result share
 * card (40dp, numbers under the squares); compact = 24dp inline. Glyphs are never removed
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
                    ResultSquareState.None -> Modifier.border(BorderStroke(Borders.emphasis, outline), shape)
                    ResultSquareState.Pending -> Modifier.dashedBorder(outline, shape, width = Borders.emphasis)
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

/** One answer box of the Gündəlik card (components.md §38). */
enum class AnswerBoxState {
    /** Not played yet: dashed outline, no glyph. */
    NotPlayed,
    Correct,
    Wrong,

    /** No answer (timeout, deadline). */
    None,

    /** Answered offline, not confirmed yet: dashed outline + `cloud_upload`. */
    Pending,

    /** Finished without per-question results in the response (OQ-D28): decorative neutral box. */
    Unknown,
}

/** The box of a result square (finished card). */
fun ResultSquareState.toAnswerBox(): AnswerBoxState = when (this) {
    ResultSquareState.Correct -> AnswerBoxState.Correct
    ResultSquareState.Wrong -> AnswerBoxState.Wrong
    ResultSquareState.None -> AnswerBoxState.None
    ResultSquareState.Pending -> AnswerBoxState.Pending
}

/** The faces of the Gündəlik challenge card (daily-tab.md A, components.md §37). */
@Immutable
sealed interface DailyCardUi {
    data object Skeleton : DailyCardUi

    data class Available(val number: Int, val questionCount: Int, val starting: Boolean = false) : DailyCardUi

    /** [boxes]: answered questions filled, the rest [AnswerBoxState.NotPlayed]. */
    data class InProgress(val number: Int, val boxes: List<AnswerBoxState>, val starting: Boolean = false) : DailyCardUi

    /**
     * [squares] null: the response had no per-question results (neutral boxes, no share).
     * [countdown] null = the countdown reached 0 and the new day is loading. [shareEnabled] false
     * while any square is unconfirmed.
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
    ) : DailyCardUi {
        val boxes: List<AnswerBoxState>
            get() = squares?.map { it.toAnswerBox() } ?: List(total) { AnswerBoxState.Unknown }
    }

    data object Unavailable : DailyCardUi

    /** Offline with a cache of an older day: never claim "finished" for a new day (DAILY-01). */
    data object OfflineUnknown : DailyCardUi

    /** `GET /v1/daily` failed without a usable cache: only the card retries. */
    data object Error : DailyCardUi
}

/** daily-tab.md A: 5 questions per challenge (the server's count wins when known). */
private const val DEFAULT_QUESTION_COUNT = 5

/**
 * Daily challenge card v3 (components.md §37, daily-tab.md A): a flat card with the overline,
 * the serif "#12" (finished: the score "4 / 5" on the same baseline), the hint, the answer boxes
 * and the action (primary pill "Başla" / "Davam et", or countdown + "Paylaş"). Every state keeps
 * the height of the available layout, so the streak section below never jumps. Available / in
 * progress: one TalkBack node, role Button; finished: a traversal group with the share button.
 */
@Composable
fun DailyChallengeCard(
    ui: DailyCardUi,
    onStart: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    animateBoxes: Boolean = false,
) {
    GameCard(
        shape = CardShape,
        contentPadding = PaddingValues(Spacing.lg),
        modifier = modifier.fillMaxWidth(),
    ) {
        // The invisible available layout reserves the same minimum height in every state; its
        // button is in the loading state, so it never takes a tap.
        if (ui !is DailyCardUi.Available) {
            Box(
                Modifier
                    .alpha(0f)
                    .clearAndSetSemantics {},
            ) { AvailableContent(number = 0, questionCount = DEFAULT_QUESTION_COUNT, starting = true, onStart = {}) }
        }
        when (ui) {
            DailyCardUi.Skeleton -> SkeletonContent()
            is DailyCardUi.Available -> {
                val action = stringResource(R.string.home_daily_start)
                val description = pluralStringResource(R.plurals.home_daily_card_cd, ui.questionCount, ui.number, ui.questionCount)
                Box(Modifier.actionNode(description, action, enabled = !ui.starting, onStart)) {
                    AvailableContent(ui.number, ui.questionCount, ui.starting, onStart)
                }
            }
            is DailyCardUi.InProgress -> {
                val action = stringResource(R.string.home_daily_resume)
                val description = stringResource(R.string.home_daily_card_in_progress_cd, ui.number)
                Column(Modifier.actionNode(description, action, enabled = !ui.starting, onStart)) {
                    CardHeader(number = ui.number, score = null)
                    Spacer(Modifier.height(Spacing.xs))
                    Hint(stringResource(R.string.home_daily_in_progress_hint))
                    Spacer(Modifier.height(Spacing.lg))
                    AnswerBoxes(ui.boxes)
                    Spacer(Modifier.height(Spacing.lg))
                    StartButton(action, starting = ui.starting, onClick = onStart)
                }
            }
            is DailyCardUi.Finished -> FinishedContent(ui, onShare, animateBoxes)
            DailyCardUi.Unavailable -> MessageContent(DuelloIcons.EventBusy, stringResource(R.string.home_daily_unavailable), null)
            DailyCardUi.OfflineUnknown -> MessageContent(DuelloIcons.CloudOff, stringResource(R.string.home_daily_offline_unknown), null)
            DailyCardUi.Error -> MessageContent(DuelloIcons.Error, stringResource(R.string.home_daily_error), onRetry)
        }
    }
}

/** One Button node for the whole available / in-progress card (daily-tab.md accessibility). */
private fun Modifier.actionNode(description: String, action: String, enabled: Boolean, onClick: () -> Unit): Modifier =
    clearAndSetSemantics {
        role = Role.Button
        contentDescription = description
        if (enabled) onClick(label = action) { onClick(); true }
    }

@Composable
private fun AvailableContent(number: Int, questionCount: Int, starting: Boolean, onStart: () -> Unit) {
    Column {
        CardHeader(number = number, score = null)
        Spacer(Modifier.height(Spacing.xs))
        Hint(pluralStringResource(R.plurals.home_daily_hint, questionCount, questionCount))
        Spacer(Modifier.height(Spacing.lg))
        AnswerBoxes(List(questionCount) { AnswerBoxState.NotPlayed })
        Spacer(Modifier.height(Spacing.lg))
        StartButton(stringResource(R.string.home_daily_start), starting = starting, onClick = onStart)
    }
}

@Composable
private fun CardHeader(number: Int?, score: String?) {
    Column {
        SectionOverline(stringResource(R.string.daily_tab_overline))
        if (number != null) {
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.daily_number, number),
                    style = MaterialTheme.typography.numeralLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .alignByBaseline(),
                )
                if (score != null) {
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        text = score,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StartButton(text: String, starting: Boolean, onClick: () -> Unit) {
    DuelloButton(
        text = text,
        onClick = onClick,
        variant = ButtonVariant.Primary,
        loading = starting,
        leading = {
            Icon(
                painter = painterResource(DuelloIcons.PlayArrow),
                contentDescription = null,
                tint = LocalContentColor.current,
                modifier = Modifier.size(Sizes.inlineIcon),
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FinishedContent(ui: DailyCardUi.Finished, onShare: () -> Unit, animateBoxes: Boolean) {
    val description = stringResource(
        R.string.home_daily_card_finished_cd,
        ui.number,
        ui.correctCount,
        ui.total,
        ui.remainingHours,
        ui.remainingMinutes,
    )
    Column(Modifier.semantics { isTraversalGroup = true }) {
        Column(Modifier.clearAndSetSemantics { contentDescription = description }) {
            CardHeader(number = ui.number, score = stringResource(R.string.daily_score_short, ui.correctCount, ui.total))
            Spacer(Modifier.height(Spacing.xs))
            Hint(pluralStringResource(R.plurals.home_daily_hint, ui.total, ui.total))
            Spacer(Modifier.height(Spacing.lg))
            AnswerBoxes(ui.boxes, animate = animateBoxes)
            Spacer(Modifier.height(Spacing.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    )
                }
            }
        }
        if (ui.squares != null) {
            Spacer(Modifier.height(Spacing.md))
            ShareButton(enabled = ui.shareEnabled, onClick = onShare, modifier = Modifier.fillMaxWidth())
            if (!ui.shareEnabled && ui.squares.any { it == ResultSquareState.Pending }) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.daily_share_pending),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MessageContent(icon: Int, text: String, onRetry: (() -> Unit)?) {
    Column {
        SectionOverline(stringResource(R.string.daily_tab_overline))
        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.icon),
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        if (onRetry != null) {
            Spacer(Modifier.height(Spacing.sm))
            DuelloTextButton(text = stringResource(R.string.common_retry), onClick = onRetry)
        }
    }
}

@Composable
private fun SkeletonContent() {
    val alpha = rememberSkeletonAlpha()
    Column(Modifier.clearAndSetSemantics {}) {
        SectionOverline(stringResource(R.string.daily_tab_overline))
        Spacer(Modifier.height(Spacing.sm))
        SkeletonBlock(alpha, Modifier.width(Sizes.skeletonLineWidthShort).height(Sizes.iconLarge))
        Spacer(Modifier.height(Spacing.sm))
        SkeletonBlock(alpha, Modifier.width(Sizes.skeletonLineWidth).height(Sizes.skeletonLineHeight))
        Spacer(Modifier.height(Spacing.lg))
        AnswerBoxes(List(DEFAULT_QUESTION_COUNT) { AnswerBoxState.NotPlayed })
        Spacer(Modifier.height(Spacing.lg))
        SkeletonBlock(
            alpha = alpha,
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(Sizes.buttonHeight),
        )
    }
}

/**
 * Answer boxes (components.md §38): one rounded square per question, `size.resultBox` (compact:
 * `resultBoxCompact`), gap `space.sm`. Not interactive; decorative inside the card node. With
 * [animate] the filled boxes pop in left to right once (motion.progression.resultSquaresEnter).
 */
@Composable
fun AnswerBoxes(
    boxes: List<AnswerBoxState>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    animate: Boolean = false,
) {
    val reducedMotion = rememberReducedMotion() || LocalInspectionMode.current
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = modifier.clearAndSetSemantics {}) {
        boxes.forEachIndexed { index, box ->
            val pop = animate && !reducedMotion && box != AnswerBoxState.NotPlayed
            val scale = remember { Animatable(if (pop) Motion.Progression.RESULT_SQUARES_FROM else 1f) }
            LaunchedEffect(pop) {
                if (pop) {
                    delay(index * Motion.Progression.RESULT_SQUARES_STAGGER_MS.toLong())
                    scale.animateTo(1f, Motion.bouncy())
                }
            }
            AnswerBox(
                state = box,
                size = if (compact) Sizes.resultBoxCompact else Sizes.resultBox,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
            )
        }
    }
}

@Composable
private fun AnswerBox(state: AnswerBoxState, size: Dp, modifier: Modifier = Modifier) {
    val game = MaterialTheme.gameColors
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val base = modifier.size(size)
    val styled = when (state) {
        AnswerBoxState.NotPlayed, AnswerBoxState.Pending -> base.dashedBorder(scheme.outline, shape)
        AnswerBoxState.Correct -> base.background(game.correct, shape)
        AnswerBoxState.Wrong -> base.background(game.wrong, shape)
        AnswerBoxState.None -> base
            .background(scheme.surfaceContainerHighest, shape)
            .border(BorderStroke(Borders.hairline, scheme.outline), shape)
        AnswerBoxState.Unknown -> base.background(scheme.surfaceContainerHighest, shape)
    }
    Box(contentAlignment = Alignment.Center, modifier = styled) {
        when (state) {
            AnswerBoxState.Correct -> BoxGlyph(DuelloIcons.Check, game.onCorrect, GameSizes.resultSquareGlyph)
            AnswerBoxState.Wrong -> BoxGlyph(DuelloIcons.Close, game.onWrong, GameSizes.resultSquareGlyph)
            AnswerBoxState.None -> BoxGlyph(DuelloIcons.Remove, scheme.onSurfaceVariant, GameSizes.resultSquareGlyph)
            AnswerBoxState.Pending -> BoxGlyph(DuelloIcons.CloudUpload, scheme.onSurfaceVariant, Sizes.iconSmall)
            AnswerBoxState.NotPlayed, AnswerBoxState.Unknown -> Unit
        }
    }
}

@Composable
private fun BoxGlyph(icon: Int, tint: androidx.compose.ui.graphics.Color, size: Dp) {
    Icon(painter = painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

/** Secondary pill "Paylaş" with the 20dp share icon (daily result and the finished Gündəlik card). */
@Composable
fun ShareButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    DuelloButton(
        text = stringResource(R.string.daily_share),
        onClick = onClick,
        variant = ButtonVariant.Secondary,
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

@PreviewLightDark
@Composable
private fun DailyChallengeCardPreview() {
    DuelloTheme {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg), modifier = Modifier.padding(Spacing.lg)) {
                DailyChallengeCard(DailyCardUi.Available(number = 12, questionCount = 5), {}, {}, {})
                DailyChallengeCard(
                    DailyCardUi.InProgress(
                        number = 12,
                        boxes = listOf(AnswerBoxState.Correct, AnswerBoxState.Pending) + List(3) { AnswerBoxState.NotPlayed },
                    ),
                    {}, {}, {},
                )
                DailyChallengeCard(
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
                DailyChallengeCard(DailyCardUi.Unavailable, {}, {}, {})
                DailyChallengeCard(DailyCardUi.Skeleton, {}, {}, {})
                ResultSquaresRow(
                    squares = listOf(ResultSquareState.Correct, ResultSquareState.Pending, ResultSquareState.None),
                    large = true,
                )
            }
        }
    }
}

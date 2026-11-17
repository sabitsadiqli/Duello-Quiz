package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelCardShape
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.GameSizes
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.theme.numeral
import com.sabit.duelloquiz.core.designsystem.theme.overline
import com.sabit.duelloquiz.core.designsystem.theme.tabularFigures
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Once-per-process flags for the hand-made touches (motion.game.squiggleDraw / stickerSettle:
 * "once per app start, not on tab switches or returns"). Plain flags: only read and written on
 * the main thread from composition effects.
 */
private object OncePerProcess {
    var squiggleDrawn = false
    var stickerSettled = false
}

/** components.md §35a: section label in `typography.custom.overline`, authored in capitals. */
@Composable
fun SectionOverline(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.overline,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * Sticker pill (components.md §34): rating on the home duel card only. `scheme.primary` pill,
 * labelMedium tabular `scheme.onPrimary`, rotated `size.stickerRotationDeg`; settles from 0° once
 * per app start (`motion.game.stickerSettle`). Not interactive, not focusable (its text is part of
 * the duel card's description).
 */
@Composable
fun Sticker(text: String, modifier: Modifier = Modifier) {
    val reducedMotion = rememberReducedMotion() || LocalInspectionMode.current
    val settle = !reducedMotion && !OncePerProcess.stickerSettled
    val rotation = remember { Animatable(if (settle) Motion.STICKER_SETTLE_FROM_DEG else Sizes.STICKER_ROTATION_DEG) }
    LaunchedEffect(Unit) {
        if (settle) {
            OncePerProcess.stickerSettled = true
            rotation.animateTo(Sizes.STICKER_ROTATION_DEG, Motion.gentle())
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.tabularFigures(),
        color = MaterialTheme.colorScheme.onPrimary,
        maxLines = 1,
        modifier = modifier
            .graphicsLayer { rotationZ = rotation.value }
            .clearAndSetSemantics {}
            .heightIn(min = Sizes.stickerMinHeight)
            .background(MaterialTheme.colorScheme.primary, FullShape)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .wrapContentHeightCentered(),
    )
}

/** Centres a single text line vertically inside its min height (sticker). */
private fun Modifier.wrapContentHeightCentered(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    val height = maxOf(placeable.height, constraints.minHeight)
    layout(placeable.width, height) { placeable.place(0, (height - placeable.height) / 2) }
}

/** The squiggle word of a greeting line: the range of the string annotation [SQUIGGLE_TAG]. */
const val SQUIGGLE_TAG = "squiggle"

/** Range of the first [SQUIGGLE_TAG] annotation in [text], or null. Pure, unit-tested. */
fun squiggleRange(text: AnnotatedString): TextRange? =
    text.getStringAnnotations(SQUIGGLE_TAG, 0, text.length).firstOrNull()?.let { TextRange(it.start, it.end) }

/**
 * Builds an [AnnotatedString] from a resource text read with `Resources.getText()`, keeping the
 * `<annotation squiggle="1">` span as a [SQUIGGLE_TAG] string annotation (components.md §36).
 */
fun squiggleAnnotated(text: CharSequence): AnnotatedString {
    val builder = AnnotatedString.Builder(text.toString())
    if (text is android.text.Spanned) {
        text.getSpans(0, text.length, android.text.Annotation::class.java)
            .firstOrNull { it.key == SQUIGGLE_TAG }
            ?.let { span -> builder.addStringAnnotation(SQUIGGLE_TAG, span.value, text.getSpanStart(span), text.getSpanEnd(span)) }
    }
    return builder.toAnnotatedString()
}

/**
 * Greeting (components.md §36, home.md): two serif lines (headlineSmall), the second with a
 * hand-drawn squiggle under its [SQUIGGLE_TAG] word in `scheme.primary`. The wave has a fixed
 * irregularity from [seed] (the Baku day), so it looks hand-drawn but stays the same all day. It
 * draws in once per app start; reduced motion shows it at once. One heading node; the squiggle is
 * decorative. Lines wrap, never truncate.
 */
@Composable
fun GreetingWithSquiggle(
    line1: String,
    line2: AnnotatedString,
    seed: Long,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion() || LocalInspectionMode.current
    val draw = !reducedMotion && !OncePerProcess.squiggleDrawn
    val progress = remember { Animatable(if (draw) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (draw) {
            OncePerProcess.squiggleDrawn = true
            delay(Motion.SQUIGGLE_DRAW_DELAY_MS.toLong())
            progress.animateTo(1f, tween(Motion.SQUIGGLE_DRAW_MS, easing = Motion.EmphasizedDecelerate))
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val range = remember(line2) { squiggleRange(line2) }
    val color = MaterialTheme.colorScheme.primary
    val style = MaterialTheme.typography.headlineSmall.copy(hyphens = Hyphens.None, lineBreak = LineBreak.Heading)
    Column(modifier = modifier.semantics(mergeDescendants = true) { heading() }) {
        Text(text = line1, style = style, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = line2,
            style = style,
            color = MaterialTheme.colorScheme.onSurface,
            onTextLayout = { layout = it },
            modifier = Modifier.drawBehind {
                val result = layout ?: return@drawBehind
                if (range == null || range.collapsed || range.end > result.layoutInput.text.length) return@drawBehind
                val line = result.getLineForOffset(range.start)
                val start = result.getBoundingBox(range.start).left
                val lastChar = (range.end - 1).coerceAtLeast(range.start)
                val end = if (result.getLineForOffset(lastChar) == line) result.getBoundingBox(lastChar).right else result.getLineRight(line)
                val overhang = GameSizes.squiggleOverhang.toPx()
                val path = squigglePath(
                    fromX = minOf(start, end) - overhang,
                    toX = maxOf(start, end) + overhang,
                    baselineY = result.getLineBaseline(line) + Sizes.squiggleOffsetBelowBaseline.toPx(),
                    amplitude = Sizes.squiggleAmplitude.toPx(),
                    wavelength = Sizes.squiggleWavelength.toPx(),
                    jitter = GameSizes.squiggleJitter.toPx(),
                    seed = seed,
                    progress = progress.value,
                )
                drawPath(path, color, style = Stroke(width = Sizes.squiggleStroke.toPx(), cap = StrokeCap.Round))
            },
        )
    }
}

/**
 * A sine-like wave from [fromX] to [toX] around [baselineY]; each half-wave (crest) gets a fixed
 * amplitude offset of up to ±[jitter] from [seed]. [progress] 0…1 draws it from start to end.
 */
internal fun squigglePath(
    fromX: Float,
    toX: Float,
    baselineY: Float,
    amplitude: Float,
    wavelength: Float,
    jitter: Float,
    seed: Long,
    progress: Float,
): Path {
    val path = Path()
    val width = (toX - fromX).coerceAtLeast(0f)
    val drawnTo = fromX + width * progress.coerceIn(0f, 1f)
    if (width <= 0f || wavelength <= 0f) return path
    val random = Random(seed)
    val crests = (width / (wavelength / 2f)).toInt() + 1
    val amplitudes = FloatArray(crests) { amplitude + (random.nextFloat() * 2f - 1f) * jitter }
    val step = 1f
    var x = fromX
    path.moveTo(x, baselineY)
    while (x < drawnTo) {
        x = minOf(x + step, drawnTo)
        val t = x - fromX
        val crest = (t / (wavelength / 2f)).toInt().coerceIn(0, crests - 1)
        path.lineTo(x, baselineY + amplitudes[crest] * sin(2.0 * PI * t / wavelength).toFloat())
    }
    return path
}

/**
 * Duel card v3 (components.md §6): the "inverted page" — a solid `inverseSurface` block
 * (`shape.component.duelCard`), "Duel" in serif, the hint, the duration and a paper CTA pill with
 * a trailing arrow, and the rating [Sticker] half outside the top edge. The whole card is one
 * touch target (ripple + press scale) and one TalkBack node ([contentDescription]); the pill is
 * not a separate focus stop. If duration and pill do not fit side by side, the pill moves under
 * the duration at full width.
 */
@Composable
fun DuelCard(
    hint: String,
    duration: String,
    stickerText: String?,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val game = MaterialTheme.gameColors
    val cta = stringResource(R.string.home_duel_cta)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                this.contentDescription = contentDescription
                role = Role.Button
                if (enabled) onClick(label = cta) { onClick(); true } else disabled()
            },
    ) {
        PressableSurface(
            onClick = onClick,
            faceColor = scheme.inverseSurface,
            contentColor = scheme.inverseOnSurface,
            shape = DuelCardShape,
            interactive = enabled,
            scaleOnPress = true,
            faceMinHeight = Sizes.duelCardMinHeight,
            contentPadding = PaddingValues(Spacing.lg),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.home_duel_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = scheme.inverseOnSurface,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(text = hint, style = MaterialTheme.typography.bodyMedium, color = scheme.inverseOnSurface)
                Spacer(Modifier.height(Spacing.lg))
                WrapWhenTight(
                    start = {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = Sizes.buttonHeight)) {
                            Icon(
                                painter = painterResource(DuelloIcons.Schedule),
                                contentDescription = null,
                                tint = game.inverseOnSurfaceVariant,
                                modifier = Modifier.size(GameSizes.duelCardClock),
                            )
                            Spacer(Modifier.width(Spacing.xs))
                            Text(
                                text = duration,
                                style = MaterialTheme.typography.labelLarge.tabularFigures(),
                                color = game.inverseOnSurfaceVariant,
                            )
                        }
                    },
                    end = {
                        // Looks like the "On ink" pill (components.md §1) but is part of the card's touch target.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .heightIn(min = Sizes.buttonHeight)
                                .background(scheme.surface, FullShape)
                                .padding(horizontal = Spacing.xl),
                        ) {
                            Text(text = cta, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface, maxLines = 1)
                            Spacer(Modifier.width(Spacing.sm))
                            Icon(
                                painter = painterResource(DuelloIcons.ArrowForward),
                                contentDescription = null,
                                tint = scheme.onSurface,
                                modifier = Modifier.size(Sizes.iconSmall),
                            )
                        }
                    },
                )
            }
        }
        if (stickerText != null) {
            Sticker(
                text = stickerText,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = Spacing.lg)
                    .centredOnTopEdge(),
            )
        }
    }
}

/** Moves the element up by half its height, so it sits centred on its parent's top edge. */
private fun Modifier.centredOnTopEdge(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { placeable.place(0, -placeable.height / 2) }
}

/**
 * [start] and [end] on one row (space between) when both fit, otherwise [end] under [start] at
 * full width (duel card bottom row at very large font scales).
 */
@Composable
private fun WrapWhenTight(start: @Composable () -> Unit, end: @Composable () -> Unit) {
    val gap = Spacing.sm
    Layout(contents = listOf(start, end), modifier = Modifier.fillMaxWidth()) { (startMeasurables, endMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val startPlaceable = startMeasurables.first().measure(loose)
        val endWidth = endMeasurables.first().maxIntrinsicWidth(constraints.maxHeight)
        val fits = startPlaceable.width + gap.roundToPx() + endWidth <= constraints.maxWidth
        if (fits) {
            val endPlaceable = endMeasurables.first().measure(loose)
            val height = maxOf(startPlaceable.height, endPlaceable.height)
            layout(constraints.maxWidth, height) {
                startPlaceable.place(0, (height - startPlaceable.height) / 2)
                endPlaceable.place(constraints.maxWidth - endPlaceable.width, (height - endPlaceable.height) / 2)
            }
        } else {
            val endPlaceable = endMeasurables.first().measure(Constraints.fixedWidth(constraints.maxWidth))
            val spacing = gap.roundToPx()
            layout(constraints.maxWidth, startPlaceable.height + spacing + endPlaceable.height) {
                startPlaceable.place(0, 0)
                endPlaceable.place(0, startPlaceable.height + spacing)
            }
        }
    }
}

/**
 * Category list row (components.md §5): serif numeral "01" in `scheme.primary`, line icon, name
 * (titleMedium, wraps, never ellipsized or hyphenated) and a chevron — no container; the list
 * draws hairline dividers between rows. Ripple + press scale. [loading]: the chevron becomes a
 * 20dp progress indicator; [enabled] false ignores taps (HOME-02). [enterIndex] non-null runs
 * `motion.game.categoryListEnter` once (staggered by index). One TalkBack node
 * ([contentDescription] = "Coğrafiya, 10 sual"); the numeral is not announced.
 */
@Composable
fun CategoryRow(
    position: Int,
    name: String,
    iconKey: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    enabled: Boolean = true,
    loadingContentDescription: String? = null,
    enterIndex: Int? = null,
) {
    val enter = rememberListEnter(enterIndex)
    val description = if (loading && loadingContentDescription != null) loadingContentDescription else contentDescription
    PressableSurface(
        onClick = onClick,
        faceColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RectangleShape,
        interactive = enabled && !loading,
        scaleOnPress = true,
        faceMinHeight = Sizes.categoryRowMinHeight,
        contentAlignment = Alignment.CenterStart,
        contentPadding = PaddingValues(vertical = Spacing.sm),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val progress = enter()
                alpha = progress
                translationY = (1f - progress) * Motion.CATEGORY_LIST_TRANSLATE_DP.dp.toPx()
            }
            .clearAndSetSemantics {
                this.contentDescription = description
                role = Role.Button
                if (enabled && !loading) onClick { onClick(); true } else disabled()
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = (position + 1).toString().padStart(2, '0'),
                style = MaterialTheme.typography.numeral,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.widthIn(min = Sizes.categoryNumeralWidth),
            )
            Icon(
                painter = painterResource(DuelloIcons.category(iconKey)),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.icon),
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium.copy(hyphens = Hyphens.None, lineBreak = LineBreak.Heading),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.sm))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(Sizes.icon)) {
                if (loading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onSurface,
                        strokeWidth = Sizes.progressStroke,
                        modifier = Modifier.size(Sizes.inlineProgress),
                    )
                } else {
                    Icon(
                        painter = painterResource(DuelloIcons.ChevronRight),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Sizes.icon),
                    )
                }
            }
        }
    }
}

/** Skeleton of a category row: numeral, icon square and a 120dp text bar (first load). */
@Composable
fun CategoryRowSkeleton(alpha: () -> Float, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.categoryRowMinHeight)
            .clearAndSetSemantics {},
    ) {
        SkeletonBlock(alpha, Modifier.width(Sizes.iconSmall).height(Sizes.skeletonLineHeight))
        Spacer(Modifier.width(Sizes.categoryNumeralWidth - Sizes.iconSmall))
        SkeletonBlock(alpha, Modifier.size(Sizes.icon))
        Spacer(Modifier.width(Spacing.md))
        SkeletonBlock(alpha, Modifier.width(Sizes.skeletonLineWidth).height(Sizes.skeletonLineHeight))
    }
}

/** motion.game.categoryListEnter: 0 → 1 once, staggered by [index]; 1 at once when null or reduced motion. */
@Composable
private fun rememberListEnter(index: Int?): () -> Float {
    val reducedMotion = rememberReducedMotion() || LocalInspectionMode.current
    val animate = index != null && !reducedMotion
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate) {
        if (animate) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = Motion.CATEGORY_LIST_ENTER_MS,
                    delayMillis = (index ?: 0) * Motion.CATEGORY_LIST_STAGGER_MS,
                    easing = Motion.EmphasizedDecelerate,
                ),
            )
        }
    }
    return { progress.value }
}

@PreviewLightDark
@Composable
private fun HomeComponentsPreview() {
    DuelloTheme {
        Surface {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                GreetingWithSquiggle(
                    line1 = stringResource(R.string.home_greeting_hello, "Aysel"),
                    line2 = squiggleAnnotated(LocalResources.current.getText(R.string.home_greeting_2)),
                    seed = 20_409L,
                )
                Spacer(Modifier.height(Spacing.xl))
                DuelCard(
                    hint = pluralStringResource(R.plurals.home_duel_hint, 7, 7),
                    duration = stringResource(R.string.home_duel_duration, 3),
                    stickerText = stringResource(R.string.home_duel_sticker_rating, 1240),
                    contentDescription = "",
                    onClick = {},
                )
                Spacer(Modifier.height(Spacing.homeSectionGap))
                SectionOverline(pluralStringResource(R.plurals.home_categories_overline, 10, 10))
                Spacer(Modifier.height(Spacing.sm))
                CategoryRow(position = 0, name = "Coğrafiya", iconKey = "geography", contentDescription = "", onClick = {})
                CategoryRow(position = 1, name = "Tarix", iconKey = "history", contentDescription = "", onClick = {}, loading = true)
                CategoryRowSkeleton(alpha = { 1f })
            }
        }
    }
}

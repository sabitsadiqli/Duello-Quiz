package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.theme.tabularFigures
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion

/** Container and content colours of Əminlik level 1–3 (components.md §7). */
@Composable
fun confidenceColors(level: Int): Pair<Color, Color> {
    val game = MaterialTheme.gameColors
    return when (level) {
        1 -> game.confidence1Container to game.onConfidence1Container
        2 -> game.confidence2Container to game.onConfidence2Container
        else -> game.confidence3Container to game.onConfidence3Container
    }
}

/**
 * Mini Əminlik badge (components.md §16): the level numeral on its level colour,
 * `size.duelBadge`, `shape.component.duelBadge` (small). Decorative unless [contentDescription].
 */
@Composable
fun ConfidenceBadge(
    level: Int,
    modifier: Modifier = Modifier,
    size: Dp = Sizes.duelBadge,
    contentDescription: String? = null,
) {
    val (container, content) = confidenceColors(level)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(container)
            .clearAndSetSemantics { if (contentDescription != null) this.contentDescription = contentDescription },
    ) {
        Text(text = level.toString(), style = MaterialTheme.typography.titleMedium.tabularFigures(), color = content)
    }
}

/** One calibration row: label, value text, optional bar fraction (null = track only). */
@Immutable
data class CalibrationRowSpec(
    val level: Int,
    val label: String,
    val value: String,
    val fraction: Float?,
    val contentDescription: String,
)

/**
 * Calibration card (components.md §20), a white game card: used by the Əminlik kartı (DUEL-21) and the profile
 * (PROF-05). Rows are stacked so nothing truncates at font scale 1.3; bars grow once (none with
 * reduced motion).
 */
@Composable
fun CalibrationCard(
    title: String,
    rows: List<CalibrationRowSpec>,
    hint: String,
    footnote: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    GameCard(modifier = modifier.fillMaxWidth()) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.lg))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                rows.forEachIndexed { index, row -> CalibrationRow(row, staggerIndex = index) }
            }
            Spacer(Modifier.height(Spacing.lg))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    painter = painterResource(DuelloIcons.Lightbulb),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Sizes.inlineIcon),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(text = hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(Spacing.md))
            Text(text = footnote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CalibrationRow(row: CalibrationRowSpec, staggerIndex: Int) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = row.contentDescription },
    ) {
        ConfidenceBadge(level = row.level)
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(text = row.value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xs))
            CalibrationBar(fraction = row.fraction, staggerIndex = staggerIndex)
        }
    }
}

@Composable
private fun CalibrationBar(fraction: Float?, staggerIndex: Int) {
    val reducedMotion = rememberReducedMotion()
    val target = fraction?.coerceIn(0f, 1f) ?: 0f
    val progress = remember { Animatable(if (reducedMotion) target else 0f) }
    LaunchedEffect(target, reducedMotion) {
        if (reducedMotion) {
            progress.snapTo(target)
        } else {
            progress.animateTo(
                target,
                tween(
                    durationMillis = Motion.MEDIUM_2,
                    delayMillis = staggerIndex * Motion.Duel.CALIBRATION_BAR_STAGGER_MS,
                    easing = Motion.EmphasizedDecelerate,
                ),
            )
        }
    }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = MaterialTheme.colorScheme.primary
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(Sizes.calibrationBarHeight)
            .clip(FullShape)
            .drawBehind {
                drawRect(track)
                if (fraction != null) drawRect(fill, size = Size(size.width * progress.value, size.height))
            },
    )
}

@PreviewLightDark
@Composable
private fun CalibrationCardPreview() {
    DuelloTheme {
        Surface {
            CalibrationCard(
                title = stringResource(R.string.duel_calibration_title),
                subtitle = stringResource(R.string.duel_calibration_subtitle),
                rows = listOf(
                    CalibrationRowSpec(1, stringResource(R.string.duel_calibration_level, 1), stringResource(R.string.duel_calibration_value, 1, 2), PREVIEW_HALF, ""),
                    CalibrationRowSpec(2, stringResource(R.string.duel_calibration_level, 2), stringResource(R.string.duel_calibration_value, 2, 3), PREVIEW_TWO_THIRDS, ""),
                    CalibrationRowSpec(3, stringResource(R.string.duel_calibration_level, 3), stringResource(R.string.duel_calibration_unused), null, ""),
                ),
                hint = stringResource(R.string.duel_calibration_hint_good),
                footnote = stringResource(R.string.duel_calibration_footnote),
                modifier = Modifier.padding(Spacing.lg),
            )
        }
    }
}

private const val PREVIEW_HALF = 0.5f
private const val PREVIEW_TWO_THIRDS = 0.66f

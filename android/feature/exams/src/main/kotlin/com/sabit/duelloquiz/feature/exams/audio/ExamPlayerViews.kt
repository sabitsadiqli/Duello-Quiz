package com.sabit.duelloquiz.feature.exams.audio

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.core.content.getSystemService
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.BannerVariant
import com.sabit.duelloquiz.core.designsystem.component.DuelloBanner
import com.sabit.duelloquiz.core.designsystem.component.DuelloTextButton
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.Borders
import com.sabit.duelloquiz.core.designsystem.theme.CardShape
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.GameSizes
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.theme.tabularFigures
import com.sabit.duelloquiz.feature.exams.attempt.ExamPlayerUi
import com.sabit.duelloquiz.feature.exams.attempt.PlayerCaption
import com.sabit.duelloquiz.feature.exams.common.ExamFormat
import kotlinx.coroutines.delay

/**
 * Attempt listening player (components.md §42): play/pause only, a progress line that cannot be
 * dragged, the play dots and texts, captions and the failure banner. [mini] = the 56dp row form
 * while the keyboard is open or the task is scrolled; tapping it outside the button expands.
 */
@Composable
fun ExamAttemptPlayer(
    player: ExamPlayerUi,
    onPlayPause: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    mini: Boolean = false,
    onExpand: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var volumeHint by remember { mutableStateOf(false) }
    LaunchedEffect(volumeHint) {
        while (volumeHint) {
            delay(VOLUME_POLL_MS)
            if (!context.mediaMuted()) volumeHint = false
        }
    }
    val enabled = !player.readOnly && !player.exhausted && !player.busy
    val playsText = when {
        player.exhausted -> stringResource(R.string.exam_audio_no_plays)
        player.playActive -> stringResource(R.string.exam_audio_play_n, player.currentPlayNumber, player.maxPlays)
        else -> pluralStringResource(R.plurals.exam_audio_plays_left, player.playsLeft, player.playsLeft)
    }
    val buttonLabel = if (player.playing) {
        stringResource(R.string.exam_audio_pause_cd)
    } else {
        stringResource(R.string.exam_audio_play_cd, player.playsUsed, player.maxPlays)
    }
    val onButton = {
        if (player.failed) {
            onRetry()
        } else {
            if (!player.playing && context.mediaMuted()) volumeHint = true
            onPlayPause()
        }
    }
    val elapsed = stringResource(R.string.exam_audio_elapsed, ExamFormat.clock(player.positionMs), ExamFormat.clock(player.durationMs))
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(scheme.surface)
            .border(BorderStroke(Borders.hairline, MaterialTheme.gameColors.cardBorder), CardShape)
            .then(if (mini) Modifier.clickable(onClick = onExpand) else Modifier)
            .padding(if (mini) Spacing.sm else Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayButton(
                playing = player.playing,
                busy = player.busy || player.caption == PlayerCaption.Preparing,
                retry = player.failed,
                enabled = enabled || player.failed,
                label = if (player.failed) stringResource(R.string.common_retry) else buttonLabel,
                size = if (mini) GameSizes.examAudioMiniButton else Sizes.examAudioPlayButton,
                onClick = onButton,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = "$elapsed. $playsText" }) {
                if (!mini) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.exam_audio_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = scheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        repeat(player.maxPlays) { index ->
                            PlayDot(available = index < player.maxPlays - player.playsUsed)
                            if (index < player.maxPlays - 1) Spacer(Modifier.width(Spacing.xs))
                        }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    ProgressLine(fraction = if (player.durationMs > 0) player.positionMs.toFloat() / player.durationMs else 0f)
                    Spacer(Modifier.height(Spacing.xs))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = elapsed,
                        style = MaterialTheme.typography.bodyMedium.tabularFigures(),
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = playsText, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
        }
        if (!mini) {
            val caption = when (player.caption) {
                PlayerCaption.None -> null
                PlayerCaption.CountsAfter -> stringResource(R.string.exam_audio_counts_after, player.countsAfterSeconds)
                PlayerCaption.Interrupted -> stringResource(R.string.exam_audio_interrupted)
                PlayerCaption.Preparing -> stringResource(R.string.exam_audio_preparing)
                PlayerCaption.Redownloading -> stringResource(R.string.exam_audio_redownloading)
                PlayerCaption.Restored -> stringResource(R.string.exam_audio_restored)
            }
            if (caption != null) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (player.failed) {
                Spacer(Modifier.height(Spacing.sm))
                DuelloBanner(
                    variant = BannerVariant.Warning,
                    icon = DuelloIcons.Warning,
                    body = stringResource(if (player.failedOffline) R.string.exam_audio_failed_offline else R.string.exam_audio_failed),
                )
            }
            if (volumeHint) {
                Spacer(Modifier.height(Spacing.sm))
                Text(text = stringResource(R.string.exam_audio_volume_hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/** Review player (components.md §42a): seek slider, ±10 s, "Yenidən dinlə" at the end; no counters. */
@Composable
fun ExamReviewPlayer(
    state: ReviewPlayerUi,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = state.durationMs.coerceAtLeast(1)
    val fraction = if (dragging) dragValue else (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val elapsed = stringResource(
        R.string.exam_audio_elapsed,
        ExamFormat.clock(if (dragging) (dragValue * duration).toLong() else state.positionMs),
        ExamFormat.clock(state.durationMs),
    )
    val seekLabel = stringResource(R.string.exam_audio_seek_cd)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(scheme.surface)
            .border(BorderStroke(Borders.hairline, MaterialTheme.gameColors.cardBorder), CardShape)
            .padding(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayButton(
                playing = state.playing,
                busy = state.loading,
                retry = false,
                ended = state.ended,
                enabled = !state.loading,
                label = when {
                    state.playing -> stringResource(R.string.exam_audio_pause_cd)
                    state.ended -> stringResource(R.string.exam_audio_replay_cd)
                    else -> stringResource(R.string.exam_audio_play_review_cd)
                },
                size = Sizes.examAudioPlayButton,
                onClick = onPlayPause,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.exam_audio_title), style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                Slider(
                    value = fraction,
                    onValueChange = {
                        dragging = true
                        dragValue = it
                    },
                    onValueChangeFinished = {
                        dragging = false
                        onSeek((dragValue * duration).toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = scheme.onSurface,
                        activeTrackColor = scheme.onSurface,
                        inactiveTrackColor = MaterialTheme.gameColors.timerTrack,
                    ),
                    modifier = Modifier.semantics {
                        contentDescription = seekLabel
                        stateDescription = elapsed
                        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                        // TalkBack adjustments step 5 s (EXAM-26).
                        setProgress { target ->
                            onSeek((target * duration).toLong())
                            true
                        }
                    },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = elapsed,
                style = MaterialTheme.typography.bodyMedium.tabularFigures(),
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onSkip(-SKIP_MS) }, enabled = !state.loading) {
                Icon(painterResource(DuelloIcons.Replay10), contentDescription = stringResource(R.string.exam_audio_back10_cd), tint = scheme.onSurfaceVariant)
            }
            IconButton(onClick = { onSkip(SKIP_MS) }, enabled = !state.loading) {
                Icon(painterResource(DuelloIcons.Forward10), contentDescription = stringResource(R.string.exam_audio_forward10_cd), tint = scheme.onSurfaceVariant)
            }
        }
        if (state.loading) {
            Text(text = stringResource(R.string.exam_audio_redownloading), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        if (state.failed) {
            Spacer(Modifier.height(Spacing.sm))
            DuelloBanner(
                variant = BannerVariant.Warning,
                icon = DuelloIcons.Warning,
                body = stringResource(R.string.exam_audio_failed_offline),
                actions = { DuelloTextButton(text = stringResource(R.string.common_retry), onClick = onPlayPause) },
            )
        }
    }
}

@Composable
private fun PlayButton(
    playing: Boolean,
    busy: Boolean,
    retry: Boolean,
    enabled: Boolean,
    label: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    ended: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val face = if (enabled) scheme.primary else scheme.onSurface.copy(alpha = DISABLED_FACE_ALPHA)
    val content = if (enabled) scheme.onPrimary else scheme.onSurface.copy(alpha = DISABLED_CONTENT_ALPHA)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(FullShape)
            .background(face)
            .clickable(enabled = enabled, onClickLabel = null, onClick = onClick)
            .clearAndSetSemantics { contentDescription = label },
    ) {
        if (busy) {
            CircularProgressIndicator(color = content, strokeWidth = Sizes.progressStroke, modifier = Modifier.size(Sizes.icon))
        } else {
            val icon = when {
                retry -> DuelloIcons.Refresh
                playing -> DuelloIcons.Pause
                ended -> DuelloIcons.Replay
                else -> DuelloIcons.PlayArrow
            }
            Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(GameSizes.examAudioIcon))
        }
    }
}

@Composable
private fun PlayDot(available: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(Sizes.examPlayDot)
            .clip(FullShape)
            .then(if (available) Modifier.background(scheme.onSurface) else Modifier.border(Borders.hairline, scheme.outline, FullShape)),
    )
}

@Composable
private fun ProgressLine(fraction: Float) {
    val track = MaterialTheme.gameColors.timerTrack
    val fill = MaterialTheme.colorScheme.onSurface
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(Sizes.examAudioProgressHeight)
            .clip(FullShape)
            .drawBehind {
                drawRect(track)
                drawRoundRect(fill, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height), cornerRadius = CornerRadius(size.height / 2))
            },
    )
}

private fun Context.mediaMuted(): Boolean =
    getSystemService<AudioManager>()?.getStreamVolume(AudioManager.STREAM_MUSIC) == 0

private const val VOLUME_POLL_MS = 1_000L
private const val SKIP_MS = 10_000L
private const val DISABLED_FACE_ALPHA = 0.12f
private const val DISABLED_CONTENT_ALPHA = 0.38f

@PreviewLightDark
@Composable
private fun ExamPlayerPreview() {
    DuelloTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.lg)) {
            ExamAttemptPlayer(
                player = ExamPlayerUi("g1", 2, 0, 1, 1, true, true, 7_000, 185_000, 10, PlayerCaption.CountsAfter, false, false, false, false, false),
                onPlayPause = {},
                onRetry = {},
            )
            ExamReviewPlayer(state = ReviewPlayerUi(playing = false, positionMs = 72_000, durationMs = 185_000), onPlayPause = {}, onSeek = {}, onSkip = {})
        }
    }
}

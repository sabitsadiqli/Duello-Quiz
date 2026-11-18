package com.sabit.duelloquiz.feature.duel.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.AvatarAccent
import com.sabit.duelloquiz.core.designsystem.component.DuelloAvatar
import com.sabit.duelloquiz.core.designsystem.component.DuelloNeutralButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloDuelButton
import com.sabit.duelloquiz.core.designsystem.component.InlineProgress
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.theme.tabularFigures
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion
import com.sabit.duelloquiz.feature.duel.SearchEndReason
import com.sabit.duelloquiz.feature.duel.SearchPhase
import com.sabit.duelloquiz.feature.duel.SearchUi

private const val ANNOUNCE_EVERY_SECONDS = 10
private val BOT_ANNOUNCE_AT = setOf(10, 5)
private const val PULSE_RINGS = 2

/** Matchmaking (duel-search.md): pulse around the own avatar, server-timed countdown, rotating tips. */
@Composable
internal fun SearchScreen(search: SearchUi, onCancel: () -> Unit) {
    val scroll = rememberScrollState()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        CloseBar(contentDescription = stringResource(R.string.duel_search_close_cd), onClose = onCancel)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .widthIn(max = Spacing.maxContentWidth)
                .padding(horizontal = Spacing.screenGutter),
        ) {
            Spacer(Modifier.height(Spacing.xl))
            SearchPulse(name = search.selfName, avatarUrl = search.selfAvatarUrl)
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = search.selfName.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = search.selfRating?.let { stringResource(R.string.home_rating, it) }.orEmpty(),
                style = MaterialTheme.typography.labelLarge.tabularFigures(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xl))
            Text(
                text = stringResource(if (search.showConnecting) R.string.duel_search_connecting else R.string.duel_search_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
            )
            Spacer(Modifier.height(Spacing.sm))
            SearchCountdown(search)
            BotHint(search.botHintSeconds)
            Spacer(Modifier.height(Spacing.xl))
            TipCard(search.tipIndex)
            Spacer(Modifier.height(Spacing.xl))
        }
        Box(
            modifier = Modifier
                .widthIn(max = Spacing.maxContentWidth)
                .padding(horizontal = Spacing.screenGutter)
                .navigationBarsPadding()
                .padding(bottom = Spacing.xl),
        ) {
            if (search.cancelling) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.buttonHeight)) {
                    InlineProgress()
                }
            } else {
                DuelloNeutralButton(
                    text = stringResource(R.string.duel_search_cancel),
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun CloseBar(contentDescription: String, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .heightIn(min = Sizes.topAppBarHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                painter = painterResource(DuelloIcons.Close),
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Two pulse rings behind the own avatar (motion.duel.searchPulse); static with reduced motion. */
@Composable
private fun SearchPulse(name: String?, avatarUrl: String?) {
    val reducedMotion = rememberReducedMotion()
    val primary = MaterialTheme.colorScheme.primary
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(Sizes.searchPulse).clearAndSetSemantics {}) {
        if (!reducedMotion) {
            val transition = rememberInfiniteTransition(label = "pulse")
            repeat(PULSE_RINGS) { ring ->
                val progress by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(Motion.Duel.SEARCH_PULSE_MS, easing = Motion.StandardDecelerate),
                        RepeatMode.Restart,
                        StartOffset(ring * Motion.Duel.SEARCH_PULSE_RING_OFFSET_MS),
                    ),
                    label = "ring$ring",
                )
                Box(
                    modifier = Modifier
                        .size(Sizes.searchPulse)
                        .graphicsLayer {
                            val scale = Motion.Duel.SEARCH_PULSE_SCALE_FROM + (1f - Motion.Duel.SEARCH_PULSE_SCALE_FROM) * progress
                            scaleX = scale
                            scaleY = scale
                            alpha = Motion.Duel.SEARCH_PULSE_ALPHA_FROM * (1f - progress)
                        }
                        .background(primary, FullShape),
                )
            }
        }
        DuelloAvatar(name = name, imageUrl = avatarUrl, size = Sizes.avatarXLarge, accent = AvatarAccent.Self)
    }
}

@Composable
private fun SearchCountdown(search: SearchUi) {
    val visible = search.phase == SearchPhase.SEARCHING && search.remainingSeconds > 0
    // DUEL-02: at most every 10 s (30/20/10); to the bot moment at 10 s and 5 s (duel-search.md).
    val announce = if (search.countdownToBot) {
        search.remainingSeconds in BOT_ANNOUNCE_AT
    } else {
        search.remainingSeconds % ANNOUNCE_EVERY_SECONDS == 0
    }
    val countdownCd = if (search.countdownToBot) {
        pluralStringResource(R.plurals.duel_search_bot_countdown_cd, search.remainingSeconds, search.remainingSeconds)
    } else {
        stringResource(R.string.duel_search_countdown_cd, search.remainingSeconds)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { alpha = if (visible) 1f else 0f }) {
        Text(
            text = stringResource(R.string.duel_search_countdown, search.remainingSeconds),
            style = MaterialTheme.typography.headlineMedium.tabularFigures(),
            color = MaterialTheme.gameColors.timerNormal,
            modifier = Modifier.semantics {
                contentDescription = countdownCd
                // At most every 10 s (30/20/10 s), never every second (DUEL-02).
                if (visible && announce) liveRegion = LiveRegionMode.Polite
            },
        )
        Spacer(Modifier.height(Spacing.sm))
        QueueBar(
            deadlineElapsedMs = search.deadlineElapsedMs,
            remainingSeconds = search.remainingSeconds,
            visible = visible,
            nominalMs = if (search.countdownToBot) Motion.Duel.NOMINAL_SEARCH_BOT_MS else Motion.Duel.NOMINAL_SEARCH_QUEUE_MS,
        )
    }
}

/**
 * duel-search.md 5a: what happens at 0 while a bot follows, and that "Ləğv et" is the opt-out
 * (OQ-111). Read once after the title; fades with the countdown, released on fallback.
 */
@Composable
private fun BotHint(seconds: Int?) {
    val reducedMotion = rememberReducedMotion()
    // Keeps the last value for the fade-out (the hint text never changes while shown).
    val last = remember { LastValue(seconds ?: 0) }
    if (seconds != null) last.value = seconds
    val shownSeconds = last.value
    val duration = if (reducedMotion) 0 else Motion.MEDIUM_2
    AnimatedVisibility(
        visible = seconds != null,
        enter = fadeIn(tween(duration)) + expandVertically(tween(duration)),
        exit = fadeOut(tween(duration)) + shrinkVertically(tween(duration)),
    ) {
        val text = pluralStringResource(
            R.plurals.duel_search_bot_hint,
            shownSeconds,
            shownSeconds,
            stringResource(R.string.duel_bot_name),
        )
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md)
                .clearAndSetSemantics {
                    contentDescription = text
                    liveRegion = LiveRegionMode.Polite
                },
        ) {
            Icon(
                painter = painterResource(DuelloIcons.SmartToy),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.inlineIcon),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QueueBar(deadlineElapsedMs: Long, remainingSeconds: Int, visible: Boolean, nominalMs: Int) {
    val inspection = LocalInspectionMode.current
    val nominal = nominalMs.toFloat()
    var fraction by remember(nominalMs) { mutableFloatStateOf(remainingSeconds * MS_PER_SECOND / nominal) }
    LaunchedEffect(deadlineElapsedMs, visible, nominalMs) {
        if (!visible || inspection) return@LaunchedEffect
        while (true) {
            withFrameMillis {
                fraction = ((deadlineElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0) / nominal).coerceIn(0f, 1f)
            }
        }
    }
    val track = MaterialTheme.gameColors.timerTrack
    val fill = MaterialTheme.gameColors.timerNormal
    Spacer(
        modifier = Modifier
            .width(Sizes.searchProgressWidth)
            .height(Sizes.progressBarHeight)
            .background(track, FullShape)
            .drawBehind { drawRect(fill, size = Size(size.width * fraction, size.height)) }
            .clearAndSetSemantics {},
    )
}

private const val MS_PER_SECOND = 1000f

/** Plain holder (not snapshot state): written during composition without causing recomposition. */
private class LastValue(var value: Int)

@Composable
private fun TipCard(index: Int) {
    val reducedMotion = rememberReducedMotion()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(
                text = stringResource(R.string.duel_search_tip_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xs))
            // The card is as tall as the tallest tip, so it never jumps.
            Box {
                TIPS.forEach { tip ->
                    Text(
                        text = stringResource(tip),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .graphicsLayer { alpha = 0f }
                            .clearAndSetSemantics {},
                    )
                }
                Crossfade(
                    targetState = index,
                    animationSpec = tween(if (reducedMotion) 0 else Motion.MEDIUM_2),
                    label = "tip",
                ) {
                    Text(
                        text = stringResource(TIPS[it % TIPS.size]),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private val TIPS = listOf(R.string.duel_search_tip_1, R.string.duel_search_tip_2, R.string.duel_search_tip_3)

/** Timeout / stopped / disconnected (duel-search.md): state view with retry and home. */
@Composable
internal fun SearchEndedScreen(reason: SearchEndReason, retrying: Boolean, onRetry: () -> Unit, onHome: () -> Unit) {
    val (icon, title, body) = when (reason) {
        SearchEndReason.TIMEOUT -> Triple(DuelloIcons.PersonSearch, R.string.duel_search_timeout_title, R.string.duel_search_timeout_body)
        SearchEndReason.STOPPED -> Triple(DuelloIcons.PauseCircle, R.string.duel_search_stopped_title, R.string.duel_search_stopped_body)
        SearchEndReason.DISCONNECTED ->
            Triple(DuelloIcons.CloudOff, R.string.duel_search_disconnected_title, R.string.duel_search_disconnected_body)
    }
    DuelStateScaffold(
        icon = icon,
        title = stringResource(title),
        body = stringResource(body),
        onClose = onHome,
    ) {
        DuelloDuelButton(
            text = stringResource(R.string.duel_search_retry),
            onClick = onRetry,
            loading = retrying,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.md))
        DuelloNeutralButton(text = stringResource(R.string.common_home), onClick = onHome, modifier = Modifier.fillMaxWidth())
    }
}

@PreviewLightDark
@Composable
private fun SearchPreview() {
    DuelloTheme {
        SearchScreen(
            search = SearchUi(selfName = "Leyla", selfRating = 1000, phase = SearchPhase.SEARCHING, remainingSeconds = 24),
            onCancel = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun SearchBotPreview() {
    DuelloTheme {
        SearchScreen(
            search = SearchUi(
                selfName = "Leyla",
                selfRating = 1000,
                phase = SearchPhase.SEARCHING,
                remainingSeconds = 12,
                botHintSeconds = 15,
                countdownToBot = true,
            ),
            onCancel = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun SearchTimeoutPreview() {
    DuelloTheme { SearchEndedScreen(SearchEndReason.TIMEOUT, retrying = false, onRetry = {}, onHome = {}) }
}

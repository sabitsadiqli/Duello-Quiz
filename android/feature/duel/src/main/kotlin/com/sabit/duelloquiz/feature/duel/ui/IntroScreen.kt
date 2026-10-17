package com.sabit.duelloquiz.feature.duel.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
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
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.tabularFigures
import com.sabit.duelloquiz.core.designsystem.util.rememberReducedMotion
import com.sabit.duelloquiz.core.model.duel.DuelPlayer
import com.sabit.duelloquiz.feature.duel.IntroUi

/** Match found (duel-intro.md): both players and a 3-2-1 to the server's round-1 start. */
@Composable
internal fun IntroScreen(intro: IntroUi, onReconnect: () -> Unit, onLeaveFromOverlay: () -> Unit) {
    val reducedMotion = rememberReducedMotion()
    val enter = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(
            1f,
            tween(if (reducedMotion) Motion.Duel.REDUCED_MOTION_FADE_MS else Motion.Duel.INTRO_ENTER_MS, easing = Motion.EmphasizedDecelerate),
        )
    }
    val announcement = stringResource(R.string.duel_intro_cd, intro.opponent.displayName, intro.opponent.rating)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenGutter, vertical = Spacing.xl),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = Spacing.maxContentWidth)) {
                Text(
                    text = stringResource(R.string.duel_intro_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics {
                        heading()
                        liveRegion = LiveRegionMode.Assertive
                        contentDescription = announcement
                    },
                )
                Spacer(Modifier.height(Spacing.xxl))
                PlayerRow(
                    player = intro.you,
                    self = true,
                    modifier = Modifier.graphicsLayer {
                        alpha = enter.value
                        if (!reducedMotion) translationX = -(1f - enter.value) * size.width / SLIDE_FRACTION
                    },
                )
                Spacer(Modifier.height(Spacing.lg))
                DividerMark(scale = { Motion.Duel.INTRO_DIVIDER_SCALE_FROM + (1f - Motion.Duel.INTRO_DIVIDER_SCALE_FROM) * enter.value })
                Spacer(Modifier.height(Spacing.lg))
                PlayerRow(
                    player = intro.opponent,
                    self = false,
                    modifier = Modifier.graphicsLayer {
                        alpha = enter.value
                        if (!reducedMotion) translationX = (1f - enter.value) * size.width / SLIDE_FRACTION
                    },
                )
                Spacer(Modifier.height(Spacing.xxl))
                Text(
                    text = stringResource(R.string.duel_intro_starting),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.xs))
                AnimatedContent(
                    targetState = intro.countdown,
                    transitionSpec = { fadeIn(tween(Motion.SHORT_3)) togetherWith fadeOut(tween(Motion.SHORT_3)) },
                    label = "introCountdown",
                    modifier = Modifier.clearAndSetSemantics {},
                ) {
                    Text(
                        text = it.toString(),
                        style = MaterialTheme.typography.displayMedium.tabularFigures(),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    text = stringResource(R.string.duel_intro_rules),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        intro.overlay?.let { SelfOverlayCard(it, onReconnect = onReconnect, onLeave = onLeaveFromOverlay) }
    }
}

private const val SLIDE_FRACTION = 4

@Composable
private fun PlayerRow(player: DuelPlayer, self: Boolean, modifier: Modifier = Modifier) {
    val name = if (self) stringResource(R.string.duel_you) + ", " + player.displayName else player.displayName
    val rating = stringResource(R.string.home_rating, player.rating)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (self) Arrangement.Start else Arrangement.End,
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "$name, $rating" },
    ) {
        if (self) {
            DuelloAvatar(name = player.displayName, imageUrl = player.avatarUrl, size = Sizes.avatarXLarge, accent = AvatarAccent.Self)
            Spacer(Modifier.width(Spacing.lg))
        }
        Column(horizontalAlignment = if (self) Alignment.Start else Alignment.End, modifier = Modifier.weight(1f, fill = false)) {
            if (self) {
                Text(
                    text = stringResource(R.string.duel_you),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = player.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = rating,
                style = MaterialTheme.typography.labelLarge.tabularFigures(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!self) {
            Spacer(Modifier.width(Spacing.lg))
            DuelloAvatar(name = player.displayName, imageUrl = player.avatarUrl, size = Sizes.avatarXLarge, accent = AvatarAccent.Opponent)
        }
    }
}

@Composable
private fun DividerMark(scale: () -> Float) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clearAndSetSemantics {}) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(horizontal = Spacing.sm)
                .size(Sizes.introDivider)
                .graphicsLayer {
                    scaleX = scale()
                    scaleY = scale()
                }
                .background(MaterialTheme.colorScheme.primaryContainer, FullShape),
        ) {
            Icon(
                painter = painterResource(DuelloIcons.Bolt),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(Sizes.icon),
            )
        }
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@PreviewLightDark
@Composable
private fun IntroPreview() {
    DuelloTheme {
        IntroScreen(
            intro = IntroUi(PreviewData.you, PreviewData.opponent, countdown = 2),
            onReconnect = {},
            onLeaveFromOverlay = {},
        )
    }
}

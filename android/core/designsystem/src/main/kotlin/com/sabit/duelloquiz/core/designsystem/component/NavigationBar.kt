package com.sabit.duelloquiz.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.Borders
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.GameSizes
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors

/** One destination of [DuelloNavigationBar]: label, outlined and filled icon, optional badge text. */
@Immutable
data class NavigationItemUi(
    val label: String,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
    val selected: Boolean,
    /** Shows the 8dp badge dot; the text is appended to the item's accessible label. */
    val badgeDescription: String? = null,
)

/**
 * Bottom navigation bar (components.md §33): `scheme.surfaceContainer`, compact
 * `size.bottomBarHeight` + the system navigation inset below, 1dp `game.cardBorder` hairline on
 * top, no shadow. Three equal items: a 56 × 28dp indicator pill (`game.navIndicator` when
 * selected) holding the icon, then the label (labelMedium, Bold when selected, one line).
 * Ripple only, no scale. Each item is a Tab (selected state) in a selectable group; the badge dot
 * is read as part of the label.
 */
@Composable
fun DuelloNavigationBar(
    items: List<NavigationItemUi>,
    onItemClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = MaterialTheme.colorScheme.surfaceContainer
    val hairline = MaterialTheme.gameColors.cardBorder
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(container)
            .drawBehind {
                drawRect(hairline, topLeft = Offset.Zero, size = Size(size.width, Borders.hairline.toPx()))
            }
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.bottomBarHeight)
                .selectableGroup(),
        ) {
            items.forEachIndexed { index, item ->
                NavigationItem(item = item, onClick = { onItemClick(index) }, container = container, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NavigationItem(
    item: NavigationItemUi,
    onClick: () -> Unit,
    container: Color,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val content = if (item.selected) scheme.onSurface else scheme.onSurfaceVariant
    val interaction = remember { MutableInteractionSource() }
    val description = listOfNotNull(item.label, item.badgeDescription).joinToString(", ")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        // No fillMaxHeight here: the Row only has a minimum height, so a filling child would
        // take every pixel the Scaffold offers and the bar would cover the whole screen.
        modifier = modifier
            .heightIn(min = Sizes.bottomBarHeight)
            .selectable(
                selected = item.selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interaction,
                indication = null,
            )
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Tab
                selected = item.selected
                onClick { onClick(); true }
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = Sizes.navIndicatorWidth, height = Sizes.navIndicatorHeight)
                    .clip(FullShape)
                    .background(if (item.selected) MaterialTheme.gameColors.navIndicator else Color.Transparent, FullShape)
                    // A press anywhere on the item shows the ripple on the indicator (components.md §33).
                    .indication(interaction, ripple(color = content)),
            ) {
                Icon(
                    painter = painterResource(if (item.selected) item.selectedIcon else item.icon),
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(Sizes.icon),
                )
            }
            NavBadge(
                visible = item.badgeDescription != null,
                ringColor = container,
                dotColor = scheme.primary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = Sizes.icon / 2 + GameSizes.navBadgeOffset, y = -(Sizes.icon / 2) + GameSizes.navBadgeOffset),
            )
        }
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelMedium.let { if (item.selected) it.copy(fontWeight = FontWeight.W700) else it.copy(fontWeight = FontWeight.W500) },
            color = content,
            maxLines = 1,
        )
    }
}

@PreviewLightDark
@Composable
private fun NavigationBarPreview() {
    DuelloTheme {
        DuelloNavigationBar(
            items = listOf(
                NavigationItemUi(stringResource(R.string.nav_play), DuelloIcons.NavPlay, DuelloIcons.NavPlaySelected, selected = true),
                NavigationItemUi(
                    stringResource(R.string.nav_daily),
                    DuelloIcons.NavDaily,
                    DuelloIcons.NavDailySelected,
                    selected = false,
                    badgeDescription = stringResource(R.string.nav_daily_badge_cd),
                ),
                NavigationItemUi(stringResource(R.string.nav_profile), DuelloIcons.NavProfile, DuelloIcons.NavProfileSelected, selected = false),
            ),
            onItemClick = {},
        )
    }
}

/**
 * Badge dot of a navigation item (components.md §33). A top-level composable so that
 * [AnimatedVisibility] resolves to the receiver-less overload: inside the item's Column the
 * ColumnScope overload would be picked and violates the Compose DSL scope.
 */
@Composable
private fun NavBadge(
    visible: Boolean,
    ringColor: Color,
    dotColor: Color,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(Motion.SHORT_3)),
        exit = fadeOut(tween(Motion.SHORT_3)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(Sizes.navBadgeDot + GameSizes.navBadgeRing * 2)
                .border(GameSizes.navBadgeRing, ringColor, FullShape)
                .clip(FullShape)
                .background(ringColor, FullShape)
                .padding(GameSizes.navBadgeRing)
                .background(dotColor, FullShape),
        )
    }
}

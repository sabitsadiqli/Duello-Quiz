package com.sabit.duelloquiz.feature.home

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.BannerVariant
import com.sabit.duelloquiz.core.designsystem.component.CategoryGridRow
import com.sabit.duelloquiz.core.designsystem.component.CategoryTile
import com.sabit.duelloquiz.core.designsystem.component.CategoryTileSkeleton
import com.sabit.duelloquiz.core.designsystem.component.ContentWidth
import com.sabit.duelloquiz.core.designsystem.component.DailyCard
import com.sabit.duelloquiz.core.designsystem.component.DailyCardUi
import com.sabit.duelloquiz.core.designsystem.component.DuelHeroCard
import com.sabit.duelloquiz.core.designsystem.component.DuelloAvatar
import com.sabit.duelloquiz.core.designsystem.component.DuelloBanner
import com.sabit.duelloquiz.core.designsystem.component.DuelloStateView
import com.sabit.duelloquiz.core.designsystem.component.DuelloTextButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloTopAppBar
import com.sabit.duelloquiz.core.designsystem.component.GameCard
import com.sabit.duelloquiz.core.designsystem.component.HomeMessageBanner
import com.sabit.duelloquiz.core.designsystem.component.HomeMessageVariant
import com.sabit.duelloquiz.core.designsystem.component.InlineProgress
import com.sabit.duelloquiz.core.designsystem.component.LevelChipRow
import com.sabit.duelloquiz.core.designsystem.component.LevelChipSkeleton
import com.sabit.duelloquiz.core.designsystem.component.SkeletonBlock
import com.sabit.duelloquiz.core.designsystem.component.StreakBadge
import com.sabit.duelloquiz.core.designsystem.component.StreakBadgeSkeleton
import com.sabit.duelloquiz.core.designsystem.component.StreakBadgeState
import com.sabit.duelloquiz.core.designsystem.component.rememberCategoryGridColumns
import com.sabit.duelloquiz.core.designsystem.component.rememberSkeletonAlpha
import com.sabit.duelloquiz.core.designsystem.daily.DailyShareText
import com.sabit.duelloquiz.core.designsystem.daily.ResourceDailyShareStrings
import com.sabit.duelloquiz.core.designsystem.daily.shareDailyResult
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.GameSizes
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.categoryColors
import com.sabit.duelloquiz.core.designsystem.theme.tabularFigures
import com.sabit.duelloquiz.core.designsystem.util.ObserveAsEvents
import kotlinx.coroutines.launch

@Composable
internal fun HomeRoute(
    onOpenSettings: () -> Unit,
    onOpenQuiz: (categoryId: Int, categoryName: String, sessionId: String?) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenDuel: (resume: Boolean) -> Unit,
    onOpenDaily: (attemptId: String?) -> Unit,
    onOpenDailyResult: (attemptId: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAction(HomeAction.Resumed) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onAction(HomeAction.Paused) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is HomeEvent.NavigateToDaily -> onOpenDaily(event.attemptId)
            is HomeEvent.NavigateToDailyResult -> onOpenDailyResult(event.attemptId)
            is HomeEvent.ShareDaily -> DailyShareText.build(
                number = event.number,
                squares = event.squares,
                correctCount = event.correctCount,
                total = event.total,
                streakDays = event.streak,
                applicationId = context.packageName,
                strings = ResourceDailyShareStrings(resources),
            )?.let(context::shareDailyResult)
            HomeEvent.ScrollToDaily -> scope.launch {
                val info = listState.layoutInfo
                val card = info.visibleItemsInfo.firstOrNull { it.key == DAILY_KEY }
                val fullyVisible = card != null && card.offset >= info.viewportStartOffset &&
                    card.offset + card.size <= info.viewportEndOffset
                if (!fullyVisible) listState.animateScrollToItem(DAILY_ITEM_INDEX)
            }
            HomeEvent.NavigateToSettings -> onOpenSettings()
            HomeEvent.NavigateToProfile -> onOpenProfile()
            is HomeEvent.NavigateToDuel -> onOpenDuel(event.resume)
            is HomeEvent.NavigateToQuiz -> onOpenQuiz(event.categoryId, event.categoryName, event.sessionId)
            is HomeEvent.ShowMessage -> scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(
                    message = resources.getString(event.message.textRes()),
                    duration = if (event.message.isError()) SnackbarDuration.Long else SnackbarDuration.Short,
                )
            }
        }
    }

    HomeScreen(uiState = uiState, onAction = viewModel::onAction, snackbarHostState = snackbarHostState, listState = listState)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
) {
    Scaffold(
        topBar = {
            DuelloTopAppBar(
                title = stringResource(R.string.app_name),
                actions = {
                    IconButton(onClick = { onAction(HomeAction.SettingsClicked) }) {
                        Icon(
                            painter = painterResource(DuelloIcons.Settings),
                            contentDescription = stringResource(R.string.home_settings_cd),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
        if (uiState.categories == CategoriesUi.Offline) {
            // Offline without cache replaces header and list; the top app bar stays (home.md).
            Box(contentAlignment = Alignment.Center, modifier = contentModifier) {
                DuelloStateView(
                    icon = DuelloIcons.CloudOff,
                    title = stringResource(R.string.home_offline_title),
                    body = stringResource(R.string.home_offline_body),
                    primaryActionText = stringResource(R.string.common_retry),
                    onPrimaryAction = { onAction(HomeAction.RetryClicked) },
                )
            }
            return@Scaffold
        }
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { onAction(HomeAction.PullToRefresh) },
            modifier = contentModifier,
        ) {
            ContentWidth(modifier = Modifier.fillMaxSize()) {
                HomeContent(uiState = uiState, onAction = onAction, listState = listState)
            }
        }
    }
}

@Composable
private fun HomeContent(uiState: HomeUiState, onAction: (HomeAction) -> Unit, listState: LazyListState) {
    // categoryGridEnter runs only on the first appearance, not on return from a quiz.
    var gridEntered by rememberSaveable { mutableStateOf(false) }
    val content = uiState.categories as? CategoriesUi.Content
    LaunchedEffect(content != null) { if (content != null) gridEntered = true }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val contentWidth = maxWidth - Spacing.screenGutter * 2
        val names = remember(content) { content?.items?.map { it.name }.orEmpty() }
        val columns = rememberCategoryGridColumns(names, contentWidth)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = Spacing.screenGutter,
                end = Spacing.screenGutter,
                top = Spacing.sm,
                bottom = Spacing.xl,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "offline_banner") {
                CollapsingBanner(visible = uiState.showOfflineBanner) {
                    DuelloBanner(
                        variant = BannerVariant.Warning,
                        icon = DuelloIcons.CloudOff,
                        body = stringResource(R.string.common_offline_banner),
                        modifier = Modifier.padding(bottom = Spacing.md),
                    )
                }
            }
            item(key = "verification_banner") {
                VerificationBanner(banner = uiState.verificationBanner, onAction = onAction)
            }
            item(key = "profile_header") {
                ProfileHeader(
                    header = uiState.header,
                    onRetry = { onAction(HomeAction.RetryProfileClicked) },
                    onClick = { onAction(HomeAction.ProfileClicked) },
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            item(key = "home_message") {
                val message = uiState.message
                val shown = remember { MessageHolder() }
                if (message != null) shown.value = message
                CollapsingBanner(visible = message != null) {
                    val current = shown.value ?: return@CollapsingBanner
                    HomeMessageBanner(
                        variant = current.variant,
                        text = when (current.variant) {
                            HomeMessageVariant.StreakHint -> stringResource(R.string.home_streak_hint)
                            HomeMessageVariant.FreezeUsed -> stringResource(R.string.home_streak_freeze_used)
                            HomeMessageVariant.StreakLost -> pluralStringResource(R.plurals.home_streak_lost, current.days, current.days)
                        },
                        onDismiss = { onAction(HomeAction.DismissMessage) },
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }
            }
            // Daily challenge card (components.md §28): always rendered, same min height in every state.
            item(key = DAILY_KEY) {
                DailyCard(
                    ui = uiState.daily,
                    onStart = { onAction(HomeAction.DailyStartClicked) },
                    onShare = { onAction(HomeAction.DailyShareClicked) },
                    onRetry = { onAction(HomeAction.DailyRetryClicked) },
                    modifier = Modifier.padding(top = Spacing.lg),
                )
            }
            // Duel hero card (components.md §6): only behind the build flag (OQ-020); no placeholder.
            if (uiState.duelEnabled) {
                item(key = "duel_entry") {
                    DuelHeroCard(onClick = { onAction(HomeAction.DuelClicked) }, modifier = Modifier.padding(top = Spacing.lg))
                }
            }
            item(key = "categories_title") {
                Text(
                    text = stringResource(R.string.home_categories_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(top = Spacing.xl, bottom = Spacing.md)
                        .semantics { heading() },
                )
            }
            categoryItems(uiState, columns, animateEnter = !gridEntered, onAction = onAction)
        }
    }
}

private fun LazyListScope.categoryItems(
    uiState: HomeUiState,
    columns: Int,
    animateEnter: Boolean,
    onAction: (HomeAction) -> Unit,
) {
    when (val categories = uiState.categories) {
        CategoriesUi.Loading -> items(SKELETON_TILES / columns, key = { "skeleton_$it" }) {
            val alpha = rememberSkeletonAlpha()
            CategoryGridRow(
                columns = columns,
                cells = List(columns) { { modifier -> CategoryTileSkeleton(alpha, modifier, singleColumn = columns == 1) } },
                modifier = Modifier.padding(bottom = Spacing.gridGap),
            )
        }
        is CategoriesUi.Content -> {
            val rows = categories.items.withIndex().chunked(columns)
            items(rows, key = { row -> row.joinToString("_") { "category_${it.value.id}" } }) { row ->
                CategoryGridRow(
                    columns = columns,
                    cells = row.map { (index, category) ->
                        { modifier -> CategoryTileItem(uiState, category, columns, if (animateEnter) index else null, onAction, modifier) }
                    },
                    modifier = Modifier
                        .animateItem()
                        .padding(bottom = Spacing.gridGap),
                )
            }
        }
        CategoriesUi.Empty -> item(key = "empty") {
            DuelloStateView(
                icon = DuelloIcons.Inbox,
                title = stringResource(R.string.home_categories_empty),
                primaryActionText = stringResource(R.string.common_refresh),
                onPrimaryAction = { onAction(HomeAction.RetryClicked) },
            )
        }
        CategoriesUi.Error -> item(key = "error") {
            DuelloStateView(
                icon = DuelloIcons.Error,
                title = stringResource(R.string.home_categories_error),
                primaryActionText = stringResource(R.string.common_retry),
                onPrimaryAction = { onAction(HomeAction.RetryClicked) },
            )
        }
        CategoriesUi.Offline -> Unit // full screen, see HomeScreen
    }
}

@Composable
private fun CategoryTileItem(
    uiState: HomeUiState,
    category: CategoryUi,
    columns: Int,
    enterIndex: Int?,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier,
) {
    CategoryTile(
        name = category.name,
        iconKey = category.iconKey,
        supportingText = pluralStringResource(
            R.plurals.home_category_question_count,
            uiState.questionsPerQuiz,
            uiState.questionsPerQuiz,
        ),
        colors = categoryColors(category.accent),
        onClick = { onAction(HomeAction.CategoryClicked(category)) },
        singleColumn = columns == 1,
        loading = uiState.startingCategoryId == category.id,
        enabled = uiState.startingCategoryId == null,
        loadingContentDescription = stringResource(R.string.home_category_loading_cd, category.name),
        enterIndex = enterIndex,
        modifier = modifier,
    )
}

@Composable
private fun CollapsingBanner(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(Motion.MEDIUM_1, easing = Motion.EmphasizedDecelerate)) +
            fadeIn(tween(Motion.MEDIUM_1)),
        exit = shrinkVertically(tween(Motion.SHORT_4, easing = Motion.EmphasizedAccelerate)) +
            fadeOut(tween(Motion.SHORT_4)),
    ) { content() }
}

/** Banner per email-verification-banner.md; collapses when hidden. */
@Composable
private fun VerificationBanner(banner: VerificationBannerUi?, onAction: (HomeAction) -> Unit) {
    // Keep the last content while the exit animation runs.
    val shown = remember { BannerHolder() }
    if (banner != null) shown.value = banner
    CollapsingBanner(visible = banner != null) {
        val current = shown.value ?: return@CollapsingBanner
        DuelloBanner(
            variant = BannerVariant.Info,
            icon = DuelloIcons.Mail,
            title = stringResource(R.string.auth_verify_banner_title),
            body = stringResource(R.string.auth_verify_banner_body, current.email),
            onDismiss = { onAction(HomeAction.DismissVerificationBanner) },
            dismissContentDescription = stringResource(R.string.auth_verify_dismiss_cd),
            modifier = Modifier.padding(bottom = Spacing.md),
            actions = {
                if (current.isResending) {
                    InlineProgress(color = LocalContentColor.current)
                } else {
                    val countdown = current.resendCountdownSeconds
                    DuelloTextButton(
                        text = if (countdown > 0) {
                            stringResource(R.string.auth_verify_resend_countdown, countdown)
                        } else {
                            stringResource(R.string.auth_verify_resend)
                        },
                        onClick = { onAction(HomeAction.ResendVerificationClicked) },
                        enabled = countdown == 0,
                        contentColor = LocalContentColor.current,
                        readableWhenDisabled = true,
                    )
                }
            },
        )
    }
}

/** Plain holder; written during composition only to keep exit-animation content. */
private class BannerHolder {
    var value: VerificationBannerUi? = null
}

/** Same as [BannerHolder] for the streak message banner. */
private class MessageHolder {
    var value: HomeMessageUi? = null
}

/**
 * Profile header card (HOME-04, home.md v2): a white game card with avatar, name and rating;
 * skeleton until the profile is known; the whole card opens the profile (PROF-01).
 */
@Composable
private fun ProfileHeader(header: ProfileHeaderUi, onRetry: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val name = header.displayName
    val progress = header.progress as? HeaderProgressUi.Content
    val progressDescription = progress?.let {
        val streak = if (it.badge == StreakBadgeState.ActiveToday) {
            pluralStringResource(R.plurals.progress_streak_cd_active, it.streak, it.streak)
        } else {
            pluralStringResource(R.plurals.progress_streak_cd_inactive, it.streak, it.streak)
        }
        val freeze = if (it.freezeAvailable) ", " + stringResource(R.string.progress_streak_freeze_cd) else ""
        ", " + stringResource(R.string.progress_level_cd, it.level) + ", " + streak + freeze
    }.orEmpty()
    val profileDescription = if (name != null && header.rating != null) {
        stringResource(R.string.home_profile_cd, name, header.rating) + progressDescription
    } else {
        null
    }
    val openLabel = stringResource(R.string.profile_open_cd)
    GameCard(
        onClick = if (profileDescription != null) onClick else null,
        onClickLabel = openLabel,
        contentPadding = PaddingValues(Spacing.md),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (profileDescription != null) {
                    Modifier.clearAndSetSemantics {
                        contentDescription = profileDescription
                        role = Role.Button
                        onClick(label = openLabel) { onClick(); true }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val alpha = rememberSkeletonAlpha()
            if (name == null) {
                SkeletonBlock(alpha = alpha, shape = FullShape, modifier = Modifier.size(GameSizes.homeHeaderAvatar))
            } else {
                DuelloAvatar(name = name, imageUrl = header.avatarUrl, size = GameSizes.homeHeaderAvatar)
            }
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (name == null) {
                    SkeletonBlock(alpha, Modifier.width(Sizes.skeletonLineWidth).height(Sizes.skeletonLineHeight))
                } else {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                when {
                    header.rating != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(DuelloIcons.TrendingUp),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Sizes.pillIcon),
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        // home.md: above font scale 1.15 the rating line uses labelMedium so it fits.
                        val ratingStyle = if (LocalDensity.current.fontScale > COMPACT_RATING_FONT_SCALE) {
                            MaterialTheme.typography.labelMedium
                        } else {
                            MaterialTheme.typography.labelLarge
                        }
                        Text(
                            text = stringResource(R.string.home_rating, header.rating),
                            style = ratingStyle.tabularFigures(),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    header.showRetry -> DuelloTextButton(text = stringResource(R.string.common_retry), onClick = onRetry)
                    else -> SkeletonBlock(alpha, Modifier.width(Sizes.skeletonLineWidthShort).height(Sizes.skeletonLineHeight))
                }
                when (val headerProgress = header.progress) {
                    is HeaderProgressUi.Content -> LevelChipRow(level = headerProgress.level, fraction = headerProgress.fraction)
                    HeaderProgressUi.Skeleton -> LevelChipSkeleton(alpha)
                    HeaderProgressUi.Hidden -> Unit
                }
            }
            // HOME-08: the streak badge takes the trailing slot (replaces the chevron).
            when (val headerProgress = header.progress) {
                is HeaderProgressUi.Content -> {
                    Spacer(Modifier.width(Spacing.md))
                    StreakBadge(
                        days = headerProgress.streak,
                        state = headerProgress.badge,
                        freezeAvailable = headerProgress.freezeAvailable,
                        ignite = true,
                    )
                }
                HeaderProgressUi.Skeleton -> {
                    Spacer(Modifier.width(Spacing.md))
                    StreakBadgeSkeleton(alpha)
                }
                HeaderProgressUi.Hidden -> if (profileDescription != null) {
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

@StringRes
private fun HomeMessage.textRes(): Int = when (this) {
    HomeMessage.VerificationSent -> R.string.auth_verify_sent
    HomeMessage.VerificationDone -> R.string.auth_verify_done
    HomeMessage.TooManyRequests -> R.string.auth_error_too_many_requests
    HomeMessage.Offline -> R.string.error_offline
    HomeMessage.Generic -> R.string.error_generic
    HomeMessage.QuizNeedsInternet -> R.string.home_quiz_needs_internet
    HomeMessage.CategoryNotPlayable -> R.string.error_category_not_playable
    HomeMessage.RateLimited -> R.string.error_rate_limited
    HomeMessage.QuizInvalid -> R.string.error_quiz_invalid
    HomeMessage.DuelNeedsInternet -> R.string.home_duel_needs_internet
    HomeMessage.DuelLeft -> R.string.duel_left_snackbar
    HomeMessage.DailyNeedsInternet -> R.string.home_daily_needs_internet
}

private fun HomeMessage.isError(): Boolean =
    this != HomeMessage.VerificationSent && this != HomeMessage.VerificationDone && this != HomeMessage.DuelLeft

/** home.md: 6 skeleton tiles (3 rows of 2, or 6 rows in one column). */
private const val SKELETON_TILES = 6

/** LazyColumn key and index of the daily card: after the offline and verification banners, the header and the message item (all always present). */
private const val DAILY_KEY = "daily_card"
private const val DAILY_ITEM_INDEX = 4

/** home.md: the rating line switches to labelMedium above this font scale. */
private const val COMPACT_RATING_FONT_SCALE = 1.15f

private val previewCategories = listOf(
    CategoryUi(1, "Coğrafiya", "geography", "violet"),
    CategoryUi(2, "Tarix", "history", "blue"),
    CategoryUi(3, "Biologiya", "biology", "teal"),
    CategoryUi(4, "Fizika", "physics", "green"),
    CategoryUi(5, "İdman", "sports", "orange"),
)

private val previewHeader = ProfileHeaderUi(
    displayName = "Aysel",
    rating = 1000,
    progress = HeaderProgressUi.Content(level = 5, fraction = 0.54f, streak = 7, badge = StreakBadgeState.ActiveToday, freezeAvailable = true),
)

@PreviewLightDark
@Composable
private fun HomeContentPreview() {
    DuelloTheme {
        HomeScreen(
            uiState = HomeUiState(
                header = previewHeader,
                categories = CategoriesUi.Content(previewCategories),
                startingCategoryId = 2,
                duelEnabled = true,
                verificationBanner = VerificationBannerUi(email = "aysel@example.com"),
                daily = DailyCardUi.Available(number = 12, questionCount = 5),
                message = HomeMessageUi(HomeMessageVariant.StreakHint),
            ),
            onAction = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun HomeLoadingPreview() {
    DuelloTheme { HomeScreen(uiState = HomeUiState(), onAction = {}) }
}

@PreviewLightDark
@Composable
private fun HomeOfflineCachedPreview() {
    DuelloTheme {
        HomeScreen(
            uiState = HomeUiState(
                header = previewHeader,
                categories = CategoriesUi.Content(previewCategories),
                showOfflineBanner = true,
            ),
            onAction = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun HomeEmptyPreview() {
    DuelloTheme { HomeScreen(uiState = HomeUiState(header = previewHeader, categories = CategoriesUi.Empty), onAction = {}) }
}

@PreviewLightDark
@Composable
private fun HomeErrorPreview() {
    DuelloTheme {
        HomeScreen(
            uiState = HomeUiState(header = ProfileHeaderUi(showRetry = true), categories = CategoriesUi.Error),
            onAction = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun HomeOfflinePreview() {
    DuelloTheme { HomeScreen(uiState = HomeUiState(categories = CategoriesUi.Offline), onAction = {}) }
}

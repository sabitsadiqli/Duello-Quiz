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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.BannerVariant
import com.sabit.duelloquiz.core.designsystem.component.CategoryRow
import com.sabit.duelloquiz.core.designsystem.component.CategoryRowSkeleton
import com.sabit.duelloquiz.core.designsystem.component.ContentWidth
import com.sabit.duelloquiz.core.designsystem.component.DuelCard
import com.sabit.duelloquiz.core.designsystem.component.DuelloAvatar
import com.sabit.duelloquiz.core.designsystem.component.DuelloBanner
import com.sabit.duelloquiz.core.designsystem.component.DuelloCompactBanner
import com.sabit.duelloquiz.core.designsystem.component.DuelloStateView
import com.sabit.duelloquiz.core.designsystem.component.DuelloTextButton
import com.sabit.duelloquiz.core.designsystem.component.ExamRow
import com.sabit.duelloquiz.core.designsystem.component.GreetingWithSquiggle
import com.sabit.duelloquiz.core.designsystem.component.InlineProgress
import com.sabit.duelloquiz.core.designsystem.component.SectionOverline
import com.sabit.duelloquiz.core.designsystem.component.SkeletonBlock
import com.sabit.duelloquiz.core.designsystem.component.StreakChip
import com.sabit.duelloquiz.core.designsystem.component.StreakChipSkeleton
import com.sabit.duelloquiz.core.designsystem.component.StreakDayState
import com.sabit.duelloquiz.core.designsystem.component.rememberSkeletonAlpha
import com.sabit.duelloquiz.core.designsystem.component.squiggleAnnotated
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Motion
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.util.ObserveAsEvents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/** Duel card facts (home.md, copy/az.md): 7 rounds, about 3 minutes. */
private const val DUEL_QUESTIONS = 7
private const val DUEL_MINUTES = 3

/** copy/az.md `home_greeting_1` … `_8`, in pool order. */
private val GreetingLines = intArrayOf(
    R.string.home_greeting_1, R.string.home_greeting_2, R.string.home_greeting_3, R.string.home_greeting_4,
    R.string.home_greeting_5, R.string.home_greeting_6, R.string.home_greeting_7, R.string.home_greeting_8,
)

@Composable
internal fun HomeRoute(
    onOpenQuiz: (categoryId: Int, categoryName: String, sessionId: String?) -> Unit,
    onOpenDuel: (resume: Boolean) -> Unit,
    onOpenDailyTab: () -> Unit,
    onOpenProfileTab: () -> Unit,
    scrollToTop: Flow<Unit>,
    onOpenExams: () -> Unit = {},
    onOpenExamAttempt: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val listState = rememberLazyListState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAction(HomeAction.Resumed) }
    // HOME-09: re-selecting the Oyna tab scrolls to the top; no reload is forced.
    LaunchedEffect(scrollToTop) { scrollToTop.collect { listState.animateScrollToItem(0) } }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            HomeEvent.NavigateToDailyTab -> onOpenDailyTab()
            HomeEvent.NavigateToProfileTab -> onOpenProfileTab()
            is HomeEvent.NavigateToDuel -> onOpenDuel(event.resume)
            is HomeEvent.NavigateToQuiz -> onOpenQuiz(event.categoryId, event.categoryName, event.sessionId)
            HomeEvent.NavigateToExams -> onOpenExams()
            is HomeEvent.NavigateToExamAttempt -> onOpenExamAttempt(event.attemptId)
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

/**
 * The Oyna tab (home.md v3): no top app bar; top row (Baku date, streak chip, avatar), compact
 * banners, the greeting with its squiggle, the duel card (flag on) and the typographic category
 * list. Fits 360 × 740 at font scale 1.0 without scrolling; scrolls otherwise.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
        if (uiState.categories == CategoriesUi.Offline) {
            // Offline without cache: the top row (date only) stays, the rest is one state view (home.md).
            Column(modifier = contentModifier.padding(horizontal = Spacing.screenGutter)) {
                TopRow(uiState, onAction, dateOnly = true)
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    DuelloStateView(
                        icon = DuelloIcons.CloudOff,
                        title = stringResource(R.string.home_offline_title),
                        body = stringResource(R.string.home_offline_body),
                        primaryActionText = stringResource(R.string.common_retry),
                        onPrimaryAction = { onAction(HomeAction.RetryClicked) },
                    )
                }
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
    // categoryListEnter runs only on the first appearance, not on return from a quiz or a tab switch.
    var listEntered by rememberSaveable { mutableStateOf(false) }
    val content = uiState.categories as? CategoriesUi.Content
    LaunchedEffect(content != null) { if (content != null) listEntered = true }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = Spacing.screenGutter,
            end = Spacing.screenGutter,
            top = Spacing.sm,
            bottom = Spacing.sm,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "top_row") { TopRow(uiState, onAction, dateOnly = false) }
        // Banner order: offline above verification, at most these two (home.md).
        item(key = "offline_banner") {
            CollapsingBanner(visible = uiState.showOfflineBanner) {
                DuelloCompactBanner(
                    variant = BannerVariant.Warning,
                    icon = DuelloIcons.CloudOff,
                    body = stringResource(R.string.common_offline_banner),
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
        item(key = "exam_banner") { ExamRunningBanner(uiState.exams, onAction) }
        item(key = "verification_banner") { VerificationBanner(banner = uiState.verificationBanner, onAction = onAction) }
        item(key = "greeting") { Greeting(uiState, modifier = Modifier.padding(top = Spacing.sm)) }
        // Duel card (components.md §6): only behind the build flag (OQ-020); no placeholder, no gap.
        if (uiState.duelEnabled) {
            item(key = "duel_card") { HomeDuelCard(uiState, onAction, modifier = Modifier.padding(top = Spacing.xl)) }
        }
        categoryItems(uiState, animateEnter = !listEntered, onAction = onAction)
        // HOME-10 (OQ-D40): appended after the categories; hidden with the flag off or nothing published.
        val exams = uiState.exams
        if (exams != null && exams.showSection) {
            item(key = "exams_overline") {
                SectionOverline(
                    text = stringResource(R.string.home_exams_overline),
                    modifier = Modifier.padding(top = Spacing.homeSectionGap, bottom = Spacing.sm),
                )
            }
            item(key = "exams_row") {
                val supporting = exams.runningMinutes?.let {
                    stringResource(R.string.home_exams_row_running, stringResource(R.string.exam_duration_min, it))
                } ?: stringResource(R.string.home_exams_row_supporting)
                ExamRow(
                    title = stringResource(R.string.home_exams_row_title),
                    supporting = supporting,
                    contentDescription = stringResource(R.string.home_exams_row_cd, supporting),
                    icon = DuelloIcons.Assignment,
                    onClick = { onAction(HomeAction.ExamsClicked) },
                )
            }
        }
    }
}

/** HOME-10: compact info banner under the top row while a sınaq runs (works offline, from Room). */
@Composable
private fun ExamRunningBanner(exams: HomeExamsUi?, onAction: (HomeAction) -> Unit) {
    val minutes = exams?.runningMinutes
    CollapsingBanner(visible = minutes != null) {
        if (minutes == null) return@CollapsingBanner
        DuelloCompactBanner(
            variant = BannerVariant.Info,
            icon = DuelloIcons.Timer,
            body = stringResource(R.string.home_exam_running_banner, stringResource(R.string.exam_duration_min, minutes)),
            onExpand = { onAction(HomeAction.ExamBannerClicked) },
            expandLabel = stringResource(R.string.exam_resume_click_cd),
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

/** Top row (home.md): date line (weight 1, wraps), streak chip → Gündəlik, 32dp avatar → Profil. */
@Composable
private fun TopRow(uiState: HomeUiState, onAction: (HomeAction) -> Unit, dateOnly: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.homeTopRowHeight),
    ) {
        val today = uiState.today
        Text(
            text = today?.let {
                stringResource(
                    R.string.home_date_line,
                    stringArrayResource(R.array.common_weekdays)[it.weekdayIndex],
                    it.dayOfMonth,
                    stringArrayResource(R.array.profile_months)[it.monthIndex],
                )
            }.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (dateOnly) return@Row
        Spacer(Modifier.width(Spacing.sm))
        when (val streak = uiState.streak) {
            is StreakChipUi.Content -> {
                val active = streak.state == StreakDayState.ActiveToday
                val base = if (active) {
                    pluralStringResource(R.plurals.progress_streak_cd_active, streak.days, streak.days)
                } else {
                    pluralStringResource(R.plurals.progress_streak_cd_inactive, streak.days, streak.days)
                }
                val description = if (streak.freezeAvailable) base + ", " + stringResource(R.string.progress_streak_freeze_cd) else base
                StreakChip(
                    days = streak.days,
                    state = streak.state,
                    freezeAvailable = streak.freezeAvailable,
                    contentDescription = description,
                    onClickLabel = stringResource(R.string.nav_daily_open_cd),
                    onClick = { onAction(HomeAction.StreakClicked) },
                    ignite = active && StreakIgnite.firstTimeToday(streak.dayKey),
                )
            }
            StreakChipUi.Skeleton -> StreakChipSkeleton(rememberSkeletonAlpha())
            StreakChipUi.Hidden -> Unit
        }
        Spacer(Modifier.width(Spacing.xs))
        val header = uiState.header
        val avatarLabel = stringResource(R.string.nav_profile_open_cd)
        IconButton(
            onClick = { onAction(HomeAction.AvatarClicked) },
            modifier = Modifier.semantics { contentDescription = avatarLabel },
        ) {
            if (!header.loaded && header.displayName == null) {
                SkeletonBlock(rememberSkeletonAlpha(), Modifier.size(Sizes.avatarSmall), shape = CircleShape)
            } else {
                // Without a name the avatar shows the person icon (components.md §13) and still opens Profil.
                DuelloAvatar(name = header.displayName, imageUrl = header.avatarUrl, size = Sizes.avatarSmall)
            }
        }
    }
}

@Composable
private fun Greeting(uiState: HomeUiState, modifier: Modifier = Modifier) {
    val today = uiState.today ?: return
    val resources = LocalResources.current
    val lineRes = GreetingLines[(today.greetingLine - 1).coerceIn(0, GreetingLines.lastIndex)]
    val line2 = remember(lineRes, resources) { squiggleAnnotated(resources.getText(lineRes)) }
    val name = uiState.header.firstName
    GreetingWithSquiggle(
        line1 = if (name != null) stringResource(R.string.home_greeting_hello, name) else stringResource(R.string.home_greeting_hello_no_name),
        line2 = line2,
        seed = today.seed,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun HomeDuelCard(uiState: HomeUiState, onAction: (HomeAction) -> Unit, modifier: Modifier = Modifier) {
    val hint = pluralStringResource(R.plurals.home_duel_hint, DUEL_QUESTIONS, DUEL_QUESTIONS)
    val sticker = uiState.header.rating?.let { stringResource(R.string.home_duel_sticker_rating, it) }
    val description = stringResource(
        R.string.home_duel_card_cd,
        hint,
        pluralStringResource(R.plurals.home_duel_duration_cd, DUEL_MINUTES, DUEL_MINUTES),
        sticker?.let { "$it." }.orEmpty(),
    )
    DuelCard(
        hint = hint,
        duration = stringResource(R.string.home_duel_duration, DUEL_MINUTES),
        stickerText = sticker,
        contentDescription = description,
        onClick = { onAction(HomeAction.DuelClicked) },
        enabled = uiState.startingCategoryId == null,
        modifier = modifier,
    )
}

private fun LazyListScope.categoryItems(
    uiState: HomeUiState,
    animateEnter: Boolean,
    onAction: (HomeAction) -> Unit,
) {
    val overlineTop = Spacing.homeSectionGap
    when (val categories = uiState.categories) {
        CategoriesUi.Loading -> {
            item(key = "categories_overline") { CategoriesOverline(uiState, Modifier.padding(top = overlineTop)) }
            items(count = SKELETON_ROWS, key = { index -> "skeleton_$index" }) { index ->
                val alpha = rememberSkeletonAlpha()
                Column {
                    if (index > 0) HorizontalDivider(color = MaterialTheme.gameColors.cardBorder)
                    CategoryRowSkeleton(alpha)
                }
            }
        }
        is CategoriesUi.Content -> {
            item(key = "categories_overline") { CategoriesOverline(uiState, Modifier.padding(top = overlineTop)) }
            itemsIndexed(categories.items, key = { _, category -> "category_${category.id}" }) { index, category ->
                Column(modifier = Modifier.animateItem()) {
                    // Hairline between rows only (none above the first, none below the last).
                    if (index > 0) HorizontalDivider(color = MaterialTheme.gameColors.cardBorder)
                    CategoryRow(
                        position = index,
                        name = category.name,
                        iconKey = category.iconKey,
                        contentDescription = category.name + ", " + pluralStringResource(
                            R.plurals.home_category_question_count,
                            uiState.questionsPerQuiz,
                            uiState.questionsPerQuiz,
                        ),
                        onClick = { onAction(HomeAction.CategoryClicked(category)) },
                        loading = uiState.startingCategoryId == category.id,
                        enabled = uiState.startingCategoryId == null,
                        loadingContentDescription = stringResource(R.string.home_category_loading_cd, category.name),
                        enterIndex = if (animateEnter) index else null,
                    )
                }
            }
        }
        // Empty: the overline is hidden (home.md States).
        CategoriesUi.Empty -> item(key = "empty") {
            DuelloStateView(
                icon = DuelloIcons.Inbox,
                title = stringResource(R.string.home_categories_empty),
                primaryActionText = stringResource(R.string.common_refresh),
                onPrimaryAction = { onAction(HomeAction.RetryClicked) },
                compact = true,
                modifier = Modifier.padding(top = overlineTop),
            )
        }
        CategoriesUi.Error -> item(key = "error") {
            DuelloStateView(
                icon = DuelloIcons.Error,
                title = stringResource(R.string.home_categories_error),
                primaryActionText = stringResource(R.string.common_retry),
                onPrimaryAction = { onAction(HomeAction.RetryClicked) },
                compact = true,
                modifier = Modifier.padding(top = overlineTop),
            )
        }
        CategoriesUi.Offline -> Unit // full screen, see HomeScreen
    }
}

@Composable
private fun CategoriesOverline(uiState: HomeUiState, modifier: Modifier = Modifier) {
    SectionOverline(
        text = pluralStringResource(R.plurals.home_categories_overline, uiState.questionsPerQuiz, uiState.questionsPerQuiz),
        modifier = modifier.padding(bottom = Spacing.sm),
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

/**
 * Email verification (email-verification-banner.md, v3 compact form): a one-line compact banner
 * with a chevron; tapping it expands the full banner (text, resend, close) in place.
 */
@Composable
private fun VerificationBanner(banner: VerificationBannerUi?, onAction: (HomeAction) -> Unit) {
    // Keep the last content while the exit animation runs.
    val shown = remember { BannerHolder() }
    if (banner != null) shown.value = banner
    var expanded by rememberSaveable { mutableStateOf(false) }
    CollapsingBanner(visible = banner != null) {
        val current = shown.value ?: return@CollapsingBanner
        if (!expanded) {
            DuelloCompactBanner(
                variant = BannerVariant.Info,
                icon = DuelloIcons.Mail,
                body = stringResource(R.string.auth_verify_banner_title),
                onExpand = { expanded = true },
                modifier = Modifier.padding(top = Spacing.sm),
            )
            return@CollapsingBanner
        }
        DuelloBanner(
            variant = BannerVariant.Info,
            icon = DuelloIcons.Mail,
            title = stringResource(R.string.auth_verify_banner_title),
            body = stringResource(R.string.auth_verify_banner_body, current.email),
            onDismiss = { onAction(HomeAction.DismissVerificationBanner) },
            dismissContentDescription = stringResource(R.string.auth_verify_dismiss_cd),
            modifier = Modifier.padding(top = Spacing.sm),
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

/**
 * motion.progression.streakIgnite on home: the flame ignites once per Baku day and process (the
 * first frame of the day that shows today as active), not on every return to the tab.
 */
internal object StreakIgnite {
    private var ignitedDay: String? = null

    fun firstTimeToday(dayKey: String): Boolean {
        if (ignitedDay == dayKey) return false
        ignitedDay = dayKey
        return true
    }
}

@StringRes
private fun HomeMessage.textRes(): Int = when (this) {
    HomeMessage.VerificationSent -> R.string.auth_verify_sent
    HomeMessage.VerificationDone -> R.string.auth_verify_done
    HomeMessage.TooManyRequests -> R.string.auth_error_too_many_requests
    HomeMessage.Offline -> R.string.error_offline
    HomeMessage.ServerUnreachable -> R.string.error_server_unreachable
    HomeMessage.Generic -> R.string.error_generic
    HomeMessage.QuizNeedsInternet -> R.string.home_quiz_needs_internet
    HomeMessage.CategoryNotPlayable -> R.string.error_category_not_playable
    HomeMessage.RateLimited -> R.string.error_rate_limited
    HomeMessage.QuizInvalid -> R.string.error_quiz_invalid
    HomeMessage.DuelNeedsInternet -> R.string.home_duel_needs_internet
    HomeMessage.DuelLeft -> R.string.duel_left_snackbar
}

private fun HomeMessage.isError(): Boolean =
    this != HomeMessage.VerificationSent && this != HomeMessage.VerificationDone && this != HomeMessage.DuelLeft

/** home.md: 5 skeleton rows on the first load. */
private const val SKELETON_ROWS = 5

private val previewCategories = listOf(
    CategoryUi(1, "Coğrafiya", "geography"),
    CategoryUi(2, "Tarix", "history"),
    CategoryUi(3, "Biologiya", "biology"),
    CategoryUi(4, "Fizika", "physics"),
    CategoryUi(5, "İdman", "sports"),
)

private val previewState = HomeUiState(
    header = ProfileHeaderUi(displayName = "Aysel Məmmədova", rating = 1240, loaded = true),
    today = TodayUi(weekdayIndex = 0, dayOfMonth = 16, monthIndex = 10, greetingLine = 1, seed = 20_408),
    streak = StreakChipUi.Content(days = 7, state = StreakDayState.ActiveToday, freezeAvailable = true, dayKey = "2026-11-16"),
    categories = CategoriesUi.Content(previewCategories),
    duelEnabled = true,
)

@PreviewLightDark
@Composable
private fun HomeContentPreview() {
    DuelloTheme { HomeScreen(uiState = previewState, onAction = {}) }
}

@PreviewLightDark
@Composable
private fun HomeLoadingPreview() {
    DuelloTheme { HomeScreen(uiState = HomeUiState(today = previewState.today, duelEnabled = true), onAction = {}) }
}

@PreviewLightDark
@Composable
private fun HomeOfflineCachedPreview() {
    DuelloTheme {
        HomeScreen(
            uiState = previewState.copy(
                showOfflineBanner = true,
                startingCategoryId = 2,
                verificationBanner = VerificationBannerUi(email = "aysel@example.com"),
            ),
            onAction = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun HomeEmptyPreview() {
    DuelloTheme { HomeScreen(uiState = previewState.copy(categories = CategoriesUi.Empty, duelEnabled = false), onAction = {}) }
}

@PreviewLightDark
@Composable
private fun HomeErrorPreview() {
    DuelloTheme {
        HomeScreen(uiState = previewState.copy(header = ProfileHeaderUi(loaded = true), categories = CategoriesUi.Error), onAction = {})
    }
}

@PreviewLightDark
@Composable
private fun HomeOfflinePreview() {
    DuelloTheme { HomeScreen(uiState = previewState.copy(categories = CategoriesUi.Offline), onAction = {}) }
}

/** Unused default for previews and tests that do not drive tab re-selection. */
internal val NoScrollToTop: Flow<Unit> = emptyFlow()

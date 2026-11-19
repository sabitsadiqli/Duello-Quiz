package com.sabit.duelloquiz.feature.home.daily

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.BannerVariant
import com.sabit.duelloquiz.core.designsystem.component.ContentWidth
import com.sabit.duelloquiz.core.designsystem.component.DailyCardUi
import com.sabit.duelloquiz.core.designsystem.component.DailyChallengeCard
import com.sabit.duelloquiz.core.designsystem.component.DuelloBanner
import com.sabit.duelloquiz.core.designsystem.component.DuelloCompactBanner
import com.sabit.duelloquiz.core.designsystem.component.DuelloSwitchRow
import com.sabit.duelloquiz.core.designsystem.component.DuelloTextButton
import com.sabit.duelloquiz.core.designsystem.component.DuelloTopAppBar
import com.sabit.duelloquiz.core.designsystem.component.SectionOverline
import com.sabit.duelloquiz.core.designsystem.component.SkeletonBlock
import com.sabit.duelloquiz.core.designsystem.component.StreakMessageBanner
import com.sabit.duelloquiz.core.designsystem.component.StreakMessageVariant
import com.sabit.duelloquiz.core.designsystem.component.WeekDayUi
import com.sabit.duelloquiz.core.designsystem.component.WeekDotState
import com.sabit.duelloquiz.core.designsystem.component.WeekRow
import com.sabit.duelloquiz.core.designsystem.component.rememberIgniteScale
import com.sabit.duelloquiz.core.designsystem.component.rememberSkeletonAlpha
import com.sabit.duelloquiz.core.designsystem.daily.DailyShareText
import com.sabit.duelloquiz.core.designsystem.daily.ResourceDailyShareStrings
import com.sabit.duelloquiz.core.designsystem.daily.shareDailyResult
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.gameColors
import com.sabit.duelloquiz.core.designsystem.util.ObserveAsEvents
import com.sabit.duelloquiz.core.designsystem.util.rememberNotificationPermissionRequester
import com.sabit.duelloquiz.core.designsystem.util.startSettingsIntent
import com.sabit.duelloquiz.core.model.ReminderType
import com.sabit.duelloquiz.core.notifications.NotificationSectionUi
import com.sabit.duelloquiz.core.notifications.NotificationSettingsAction
import com.sabit.duelloquiz.core.notifications.NotificationSettingsEvent
import com.sabit.duelloquiz.core.notifications.NotificationSettingsMessage
import com.sabit.duelloquiz.core.notifications.ReminderSwitchUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
internal fun DailyTabRoute(
    onOpenDaily: (attemptId: String?) -> Unit,
    onOpenDailyResult: (attemptId: String) -> Unit,
    scrollToTop: Flow<Unit>,
    viewModel: DailyTabViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val listState = rememberLazyListState()
    val requestPermission = rememberNotificationPermissionRequester { granted, blocked ->
        viewModel.onAction(DailyTabAction.Reminder(NotificationSettingsAction.PermissionResult(granted, blocked)))
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAction(DailyTabAction.Resumed) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onAction(DailyTabAction.Paused) }
    // HOME-09 / NOTIF-05: re-selecting the tab or a reminder tap scrolls to the top.
    LaunchedEffect(scrollToTop) { scrollToTop.collect { listState.animateScrollToItem(0) } }

    fun showSnackbar(text: Int, long: Boolean) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = resources.getString(text),
                duration = if (long) SnackbarDuration.Long else SnackbarDuration.Short,
            )
        }
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is DailyTabEvent.NavigateToDaily -> onOpenDaily(event.attemptId)
            is DailyTabEvent.NavigateToDailyResult -> onOpenDailyResult(event.attemptId)
            is DailyTabEvent.ShareDaily -> DailyShareText.build(
                number = event.number,
                squares = event.squares,
                correctCount = event.correctCount,
                total = event.total,
                streakDays = event.streak,
                applicationId = context.packageName,
                strings = ResourceDailyShareStrings(resources),
            )?.let(context::shareDailyResult)
            is DailyTabEvent.ShowMessage -> showSnackbar(
                text = when (event.message) {
                    DailyTabMessage.NeedsInternet -> R.string.home_daily_needs_internet
                    DailyTabMessage.Generic -> R.string.error_generic
                },
                long = true,
            )
            is DailyTabEvent.Reminder -> when (val reminder = event.event) {
                NotificationSettingsEvent.RequestPermission -> requestPermission()
                is NotificationSettingsEvent.OpenSettings -> context.startSettingsIntent(reminder.intent)
                is NotificationSettingsEvent.ShowMessage -> showSnackbar(
                    text = when (reminder.message) {
                        NotificationSettingsMessage.SaveFailed -> R.string.settings_notif_save_failed
                        NotificationSettingsMessage.Offline -> R.string.settings_notif_offline
                    },
                    long = true,
                )
            }
        }
    }

    DailyTabScreen(uiState = uiState, onAction = viewModel::onAction, snackbarHostState = snackbarHostState, listState = listState)
}

/**
 * The Gündəlik tab (daily-tab.md): top app bar "Gündəlik" (root tab, no navigation icon), the
 * compact offline banner, the challenge card, the streak section (title, best, freeze, week row,
 * one message) and the mirrored daily reminder switch. Pull-to-refresh reloads everything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DailyTabScreen(
    uiState: DailyTabUiState,
    onAction: (DailyTabAction) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
) {
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }
    Scaffold(
        topBar = { DuelloTopAppBar(title = stringResource(R.string.daily_tab_title), showDivider = scrolled) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { onAction(DailyTabAction.PullToRefresh) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            ContentWidth(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = Spacing.screenGutter, end = Spacing.screenGutter, bottom = Spacing.xl),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "offline_banner") {
                        AnimatedVisibility(visible = uiState.showOfflineBanner) {
                            DuelloCompactBanner(
                                variant = BannerVariant.Warning,
                                icon = DuelloIcons.CloudOff,
                                body = stringResource(R.string.common_offline_banner),
                                modifier = Modifier.padding(bottom = Spacing.sm),
                            )
                        }
                    }
                    item(key = "card") {
                        val animate = uiState.animateBoxes && uiState.card is DailyCardUi.Finished
                        LaunchedEffect(animate) { if (animate) onAction(DailyTabAction.BoxesAnimated) }
                        DailyChallengeCard(
                            ui = uiState.card,
                            onStart = { onAction(DailyTabAction.StartClicked) },
                            onShare = { onAction(DailyTabAction.ShareClicked) },
                            onRetry = { onAction(DailyTabAction.RetryCardClicked) },
                            animateBoxes = animate,
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                    }
                    item(key = "streak") {
                        StreakSection(
                            streak = uiState.streak,
                            message = uiState.message,
                            onAction = onAction,
                            modifier = Modifier.padding(top = Spacing.xl),
                        )
                    }
                    item(key = "reminder") {
                        ReminderSection(
                            ui = uiState.reminder,
                            onAction = { onAction(DailyTabAction.Reminder(it)) },
                            modifier = Modifier.padding(top = Spacing.xl),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreakSection(
    streak: StreakSectionUi,
    message: StreakMessageUi?,
    onAction: (DailyTabAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionOverline(stringResource(R.string.daily_tab_streak_overline))
        Spacer(Modifier.height(Spacing.sm))
        when (streak) {
            is StreakSectionUi.Loading -> {
                val alpha = rememberSkeletonAlpha()
                Column(Modifier.clearAndSetSemantics {}) {
                    SkeletonBlock(alpha, Modifier.width(Sizes.skeletonLineWidth).height(Sizes.icon))
                    Spacer(Modifier.height(Spacing.xs))
                    SkeletonBlock(alpha, Modifier.width(Sizes.skeletonLineWidthShort).height(Sizes.skeletonLineHeight))
                    if (streak.week.isNotEmpty()) {
                        Spacer(Modifier.height(Spacing.lg))
                        WeekRow(days = streak.week)
                    }
                }
            }
            StreakSectionUi.Error -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.profile_section_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                DuelloTextButton(text = stringResource(R.string.common_retry), onClick = { onAction(DailyTabAction.RetryStreakClicked) })
            }
            is StreakSectionUi.Content -> {
                // streak_week_viewed: the ViewModel logs it at most once per Baku day.
                LaunchedEffect(streak.todayEpochDay) { onAction(DailyTabAction.WeekRowShown) }
                StreakContent(streak)
            }
        }
        if (message != null) {
            Spacer(Modifier.height(Spacing.lg))
            StreakMessageBanner(
                variant = message.variant,
                text = when (message.variant) {
                    StreakMessageVariant.StreakLost -> pluralStringResource(R.plurals.home_streak_lost, message.days, message.days)
                    StreakMessageVariant.FreezeUsed -> stringResource(R.string.home_streak_freeze_used)
                    StreakMessageVariant.StreakHint -> stringResource(R.string.home_streak_hint)
                },
                onDismiss = { onAction(DailyTabAction.DismissMessage) },
            )
        }
    }
}

@Composable
private fun StreakContent(streak: StreakSectionUi.Content) {
    // First frame of a Baku day showing today as active: the flame ignites and today's dot fills once.
    val firstToday = remember(streak.dayKey, streak.todayActive) { streak.todayActive && TodayFill.firstTimeToday(streak.dayKey) }
    val description = stringResource(R.string.profile_streak_cd, streak.current, streak.best)
    Column(Modifier.clearAndSetSemantics { contentDescription = description }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val scale = rememberIgniteScale(firstToday)
            Icon(
                painter = painterResource(if (streak.todayActive) DuelloIcons.FlameFilled else DuelloIcons.Flame),
                contentDescription = null,
                tint = if (streak.todayActive) MaterialTheme.gameColors.streak else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(Sizes.icon)
                    .graphicsLayer {
                        scaleX = scale()
                        scaleY = scale()
                    },
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = if (streak.isNew) {
                    stringResource(R.string.profile_streak_empty)
                } else {
                    pluralStringResource(R.plurals.profile_streak_current, streak.current, streak.current)
                },
                style = if (streak.isNew) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        if (!streak.isNew) {
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = pluralStringResource(R.plurals.daily_tab_streak_best, streak.best, streak.best),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (streak.freezeAvailable) {
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(DuelloIcons.Shield),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(Sizes.pillIcon),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = stringResource(R.string.profile_streak_freeze_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Spacer(Modifier.height(Spacing.lg))
    WeekRow(days = streak.week, animateToday = firstToday)
}

/**
 * The daily reminder row (daily-tab.md C): mirrors only the daily reminder; the blocked banner
 * above it and the disabled switch exactly as in Parametrlər (notifications.md B).
 */
@Composable
private fun ReminderSection(ui: NotificationSectionUi, onAction: (NotificationSettingsAction) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionOverline(stringResource(R.string.daily_tab_reminder_overline))
        Spacer(Modifier.height(Spacing.xs))
        AnimatedVisibility(visible = ui.blocked) {
            DuelloBanner(
                variant = BannerVariant.Warning,
                icon = DuelloIcons.NotificationsOff,
                body = stringResource(R.string.settings_notif_blocked),
                modifier = Modifier.padding(vertical = Spacing.sm),
                actions = {
                    DuelloTextButton(
                        text = stringResource(R.string.settings_notif_open_system),
                        onClick = { onAction(NotificationSettingsAction.OpenSystemSettings) },
                        contentColor = LocalContentColor.current,
                    )
                },
            )
        }
        if (ui.loadError) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.settings_notif_load_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                DuelloTextButton(text = stringResource(R.string.common_retry), onClick = { onAction(NotificationSettingsAction.Retry) })
            }
            return@Column
        }
        val switch: ReminderSwitchUi = ui.daily
        DuelloSwitchRow(
            headline = stringResource(R.string.daily_tab_reminder),
            supporting = if (switch.channelOff) stringResource(R.string.settings_notif_channel_off) else stringResource(R.string.settings_notif_daily_supporting),
            checked = switch.checked,
            onCheckedChange = { onAction(NotificationSettingsAction.Toggle(ReminderType.DAILY, it)) },
            leadingIcon = DuelloIcons.Notifications,
            enabled = switch.enabled,
            loading = ui.loading,
            onDisabledClick = if (switch.channelOff) {
                { onAction(NotificationSettingsAction.OpenChannelSettings(ReminderType.DAILY)) }
            } else {
                null
            },
        )
    }
}

/** motion.progression.weekDotFill / streakIgnite: once per Baku day and process, not on every tab return. */
internal object TodayFill {
    private var filledDay: String? = null

    fun firstTimeToday(dayKey: String): Boolean {
        if (filledDay == dayKey) return false
        filledDay = dayKey
        return true
    }
}

private val previewWeek = listOf(
    WeekDayUi(0, WeekDotState.Active, isToday = false),
    WeekDayUi(1, WeekDotState.Active, isToday = false),
    WeekDayUi(2, WeekDotState.Freeze, isToday = false),
    WeekDayUi(3, WeekDotState.TodayPending, isToday = true),
    WeekDayUi(4, WeekDotState.Unknown, isToday = false),
    WeekDayUi(5, WeekDotState.Unknown, isToday = false),
    WeekDayUi(6, WeekDotState.Unknown, isToday = false),
)

private val previewState = DailyTabUiState(
    card = DailyCardUi.Available(number = 12, questionCount = 5),
    streak = StreakSectionUi.Content(
        current = 7,
        best = 12,
        todayActive = false,
        freezeAvailable = true,
        isNew = false,
        week = previewWeek,
        dayKey = "2026-11-19",
    ),
    message = StreakMessageUi(StreakMessageVariant.StreakHint),
    reminder = NotificationSectionUi(
        loading = false,
        daily = ReminderSwitchUi(checked = true, enabled = true, channelOff = false),
    ),
)

@PreviewLightDark
@Composable
private fun DailyTabPreview() {
    DuelloTheme { DailyTabScreen(uiState = previewState, onAction = {}) }
}

@PreviewLightDark
@Composable
private fun DailyTabFinishedOfflinePreview() {
    DuelloTheme {
        DailyTabScreen(
            uiState = previewState.copy(
                card = DailyCardUi.Finished(
                    number = 12,
                    correctCount = 4,
                    total = 5,
                    squares = null,
                    countdown = "05:12:33",
                    remainingHours = 5,
                    remainingMinutes = 12,
                    shareEnabled = false,
                ),
                message = null,
                showOfflineBanner = true,
                reminder = NotificationSectionUi(loading = false, blocked = true),
            ),
            onAction = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DailyTabLoadingPreview() {
    DuelloTheme { DailyTabScreen(uiState = DailyTabUiState(streak = StreakSectionUi.Loading(previewWeek.map { it.copy(state = WeekDotState.Unknown) })), onAction = {}) }
}

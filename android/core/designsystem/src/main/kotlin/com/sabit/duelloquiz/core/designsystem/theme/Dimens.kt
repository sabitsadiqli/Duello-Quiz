package com.sabit.duelloquiz.core.designsystem.theme

import androidx.compose.ui.unit.dp

/** spacing from design/tokens.json. Screens never use raw dp values. */
object Spacing {
    val none = 0.dp
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
    val huge = 64.dp

    /** spacing.layout */
    val screenGutter = 16.dp
    val screenGutterWide = 24.dp
    val wideBreakpoint = 600.dp
    val maxContentWidth = 560.dp
    val sectionGap = 24.dp
    val listItemGap = 12.dp
    val gridGap = 12.dp
}

/** size from design/tokens.json. */
object Sizes {
    val minTouchTarget = 48.dp
    val buttonHeight = 52.dp
    val buttonHeightLarge = 56.dp
    val textFieldHeight = 56.dp
    val iconSmall = 18.dp
    val icon = 24.dp
    val iconLarge = 32.dp
    val avatar = 40.dp
    val avatarLarge = 56.dp
    val topAppBarHeight = 64.dp
    val optionCardMinHeight = 60.dp
    val categoryTileMinHeight = 132.dp
    val categoryIconContainer = 48.dp
    val timerRing = 48.dp
    val timerStroke = 5.dp
    val progressBarHeight = 12.dp
    val questionImageMaxHeight = 200.dp
    val reviewThumbnail = 64.dp
    val confidenceChip = 104.dp
    val bannerMinHeight = 56.dp
    val splashLogo = 112.dp

    /** tokens 1.1.0: duel and profile (components.md §13–§23). */
    val avatarXLarge = 80.dp
    val duelBadge = 32.dp
    val duelPlayerPanelMinHeight = 100.dp
    val phaseSegmentHeight = 6.dp
    val searchPulse = 176.dp
    val calibrationBarHeight = 10.dp

    /** components.md §13: accent ring around duel avatars (avatarXLarge: 3dp). */
    val accentRing = 2.dp
    val accentRingLarge = 3.dp

    /** components.md §3 / §17: picker markers and status pill icons. */
    val pickerMarker = 20.dp
    val pillIcon = 16.dp
    val pillProgress = 14.dp
    val statusPillMinHeight = 28.dp

    /** components.md §7: chip 3 uses-left badge and the selection ring with its gap. */
    val usesLeftBadge = 24.dp
    val chipSelectedRing = 3.dp
    val chipSelectedRingGap = 2.dp

    /** components.md §16: reveal-face border; duel-intro.md bolt divider mark. */
    val revealBorder = 2.dp
    val introDivider = 40.dp

    /** duel-match.md: question image height after the question phase. */
    val questionImageCompactMaxHeight = 120.dp

    /** duel-search.md: width of the queue progress bar. */
    val searchProgressWidth = 160.dp

    /** profile.md: trailing column of a match history row. */
    val historyTrailingMinWidth = 72.dp

    /** components.md: loading indicator inside buttons, banner close icon, inline error icon. */
    val inlineProgress = 20.dp
    val inlineIcon = 20.dp
    val progressStroke = 2.dp
    val outlineThin = 1.dp

    /** components.md §11: full-screen state views. */
    val stateIllustration = 72.dp
    val stateIllustrationContainer = 120.dp
    val warningIcon = 48.dp

    /** components.md §11: loading state indicator. */
    val stateLoadingIndicator = 48.dp

    /** components.md §5: tile face min height in the single-column fallback, and the tile icon. */
    val categoryTileSingleColumnMinHeight = 88.dp
    val categoryTileIcon = 28.dp

    /** components.md §3: A–D letter badge of an option card. */
    val optionLetterBadge = 36.dp

    /** quiz-question.md: reserved status line under the options, and its 16dp indicator. */
    val statusLineHeight = 24.dp
    val statusIndicator = 16.dp

    /** tokens 2.1.0: retention (components.md §24–§28). */
    val resultSquare = 40.dp
    val resultSquareCompact = 24.dp
    val levelBadge = 56.dp
    val streakBadgeMinWidth = 56.dp
    val xpBarHeight = 12.dp
    val xpBarMiniHeight = 8.dp

    /** Skeleton text bars (home header, category card). */
    val skeletonLineHeight = 12.dp
    val skeletonLineWidth = 120.dp
    val skeletonLineWidthShort = 64.dp
}

/**
 * depth from design/tokens.json: the solid bottom edge of pressables and game cards
 * (components.md §0). Pressed, the face moves down by this amount and covers the edge.
 */
object Depth {
    val pressable = 4.dp
    val pressableLarge = 5.dp
    val card = 4.dp
    val optionCard = 4.dp
    val confidenceChip = 5.dp
    val categoryTile = 5.dp

    /** 2dp borders on neutral pressables, white cards and option cards. */
    val border = 2.dp

    /** components.md §3 / §16: the thicker border of selected, correct and wrong states. */
    val borderThick = 3.dp
}

/** v2 component sizes that tokens.json gives only inside components.md / screen specs. */
object GameSizes {
    /** components.md §4: timer pill icon and its padding. */
    val timerPillIcon = 20.dp
    val timerPillPaddingVertical = 6.dp

    /** components.md §4: glossy highlight stripe on the timer bar indicator. */
    val timerHighlightHeight = 3.dp
    val timerHighlightInset = 8.dp

    /** components.md §6: duel hero card padding and the decorative swords box. */
    val duelHeroPaddingVertical = 18.dp
    val duelHeroDecor = 56.dp

    /** components.md §7: Əminlik signal bars (3 bars, 6dp wide, 8/13/18dp tall, 3dp gap). */
    val signalBarWidth = 6.dp
    val signalBarGap = 3.dp
    val signalBarHeights = listOf(8.dp, 13.dp, 18.dp)

    /** components.md §7: corner badges of an Əminlik tile and the lock icon inside. */
    val tileCornerBadge = 24.dp
    val tileLockIcon = 14.dp
    val tileBadgeProgress = 16.dp

    /** home.md: avatar in the profile header card. */
    val homeHeaderAvatar = 48.dp

    /** quiz-result.md: review entry icon box; duel-result.md: hero icon; profile/rating card padding. */
    val reviewEntryIconBox = 44.dp
    val heroIcon = 40.dp
    val compactCardPadding = 14.dp

    /** duel player panel padding (components.md §16). */
    val playerPanelPadding = 10.dp

    /** components.md §24: streak flame, freeze dot (20dp circle, 14dp shield, 2dp ring). */
    val streakFlame = 28.dp
    val freezeDot = 20.dp
    val freezeDotIcon = 14.dp
    val freezeDotRing = 2.dp

    /** components.md §27: glyphs of the large and compact result squares. */
    val resultSquareGlyph = 20.dp
    val resultSquareGlyphCompact = 14.dp

    /** components.md §26 / §30: category level-up square, permission card circle, flame on profile. */
    val categoryLevelUpBox = 24.dp
    val permissionIconCircle = 48.dp
    val profileStreakFlame = 40.dp

    /** components.md §25: the mini XP bar moves under the level chip below this width. */
    val miniBarMinWidth = 48.dp

    /** components.md §28: daily card vertical padding (same frame as the duel hero card). */
    val dailyCardPaddingVertical = 18.dp
}

/** elevation from design/tokens.json (Material 3 tonal elevation levels). */
object Elevation {
    val level0 = 0.dp
    val level1 = 1.dp
    val level2 = 3.dp
    val level3 = 6.dp
    val level4 = 8.dp
    val level5 = 12.dp
}

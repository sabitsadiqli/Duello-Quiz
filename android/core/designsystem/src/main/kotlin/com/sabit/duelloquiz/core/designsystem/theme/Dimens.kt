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
}

/** size from design/tokens.json. */
object Sizes {
    val minTouchTarget = 48.dp
    val buttonHeight = 48.dp
    val buttonHeightLarge = 56.dp
    val textFieldHeight = 56.dp
    val iconSmall = 18.dp
    val icon = 24.dp
    val iconLarge = 32.dp
    val avatar = 40.dp
    val avatarLarge = 56.dp
    val topAppBarHeight = 64.dp
    val optionCardMinHeight = 56.dp
    val categoryTileMinHeight = 112.dp
    val categoryIconContainer = 48.dp
    val timerRing = 48.dp
    val timerStroke = 4.dp
    val progressBarHeight = 4.dp
    val questionImageMaxHeight = 200.dp
    val reviewThumbnail = 64.dp
    val confidenceChip = 88.dp
    val bannerMinHeight = 56.dp
    val splashLogo = 96.dp

    /** tokens 1.1.0: duel and profile (components.md §13–§23). */
    val avatarXLarge = 80.dp
    val duelBadge = 32.dp
    val duelPlayerPanelMinHeight = 100.dp
    val phaseSegmentHeight = 4.dp
    val searchPulse = 176.dp
    val calibrationBarHeight = 8.dp

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

    /** Glyph inside the placeholder logo mark (splashLogo circle). */
    val logoGlyph = 56.dp

    /** components.md §11: loading state indicator. */
    val stateLoadingIndicator = 48.dp

    /** components.md §5: full-width category card (the 112dp tile token is for a grid). */
    val categoryCardMinHeight = 72.dp

    /** components.md §3: A–D letter badge of an option card. */
    val optionLetterBadge = 32.dp

    /** quiz-question.md: reserved status line under the options, and its 16dp indicator. */
    val statusLineHeight = 24.dp
    val statusIndicator = 16.dp

    /** Skeleton text bars (home header, category card). */
    val skeletonLineHeight = 12.dp
    val skeletonLineWidth = 120.dp
    val skeletonLineWidthShort = 64.dp
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

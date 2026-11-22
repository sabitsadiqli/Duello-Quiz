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

    /** tokens 3.0.0: vertical gap between the home blocks (greeting, duel card, category section). */
    val homeSectionGap = 20.dp
}

/** size from design/tokens.json. */
object Sizes {
    val minTouchTarget = 48.dp
    val buttonHeight = 48.dp
    val buttonHeightLarge = 52.dp
    val textFieldHeight = 56.dp
    val iconSmall = 18.dp
    val icon = 24.dp
    val iconLarge = 32.dp
    val avatarSmall = 32.dp
    val avatar = 40.dp
    val avatarLarge = 56.dp
    val topAppBarHeight = 64.dp
    val optionCardMinHeight = 56.dp
    val categoryIconContainer = 40.dp
    val timerRing = 48.dp
    val timerStroke = 4.dp
    val progressBarHeight = 4.dp
    val questionImageMaxHeight = 200.dp
    val reviewThumbnail = 64.dp
    val confidenceChip = 104.dp
    val bannerMinHeight = 48.dp
    val bannerCompactHeight = 40.dp
    val splashLogo = 112.dp

    /** tokens 1.1.0: duel and profile (components.md §13–§23). */
    val avatarXLarge = 80.dp
    val duelBadge = 32.dp
    val duelPlayerPanelMinHeight = 100.dp
    val phaseSegmentHeight = 4.dp
    val searchPulse = 176.dp
    val calibrationBarHeight = 6.dp

    /** components.md §13: accent ring around duel avatars (avatarXLarge: 3dp). */
    val accentRing = 2.dp
    val accentRingLarge = 3.dp

    /** components.md §3 / §17: picker markers and status pill icons. */
    val pickerMarker = 20.dp
    val pillIcon = 16.dp
    val pillProgress = 14.dp
    val statusPillMinHeight = 28.dp

    /** tokens 2.2.0 / components.md §32, §13: Bot badge icon and min height; bot mark below this size uses the icon. */
    val botBadgeIcon = 14.dp
    val botBadgeMinHeight = 20.dp
    val brandMarkMin = 24.dp

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

    /** components.md §3: A–D letter badge of an option card (a circle in v3). */
    val optionLetterBadge = 32.dp

    /** quiz-question.md: reserved status line under the options, and its 16dp indicator. */
    val statusLineHeight = 24.dp
    val statusIndicator = 16.dp

    /** tokens 2.1.0: retention (components.md §24–§28). */
    val resultSquare = 40.dp
    val resultSquareCompact = 24.dp
    val levelBadge = 48.dp
    val streakBadgeMinWidth = 48.dp
    val xpBarHeight = 6.dp
    val xpBarMiniHeight = 4.dp

    /** tokens 3.0.0: home top row, bottom bar, category rows, duel card, sticker, squiggle, Gündəlik. */
    val homeTopRowHeight = 48.dp
    val bottomBarHeight = 64.dp
    val navIndicatorWidth = 56.dp
    val navIndicatorHeight = 28.dp
    val navBadgeDot = 8.dp
    val categoryRowMinHeight = 52.dp
    val categoryNumeralWidth = 32.dp
    val duelCardMinHeight = 152.dp
    val stickerMinHeight = 28.dp
    const val STICKER_ROTATION_DEG = -4f
    val squiggleStroke = 2.5.dp
    val squiggleAmplitude = 2.5.dp
    val squiggleWavelength = 11.dp
    val squiggleOffsetBelowBaseline = 4.dp
    val weekDot = 28.dp
    val resultBox = 44.dp
    val resultBoxCompact = 24.dp

    /** tokens 3.1.0: mock exams (components.md §39 … §46, "Exam rows"). */
    val examTaskCell = 48.dp
    const val EXAM_GRID_COLUMNS = 6
    val examFlagBadge = 16.dp
    val examAudioPlayButton = 56.dp
    val examAudioProgressHeight = 4.dp
    val examPlayDot = 8.dp
    val examWrittenFieldMinHeight = 120.dp
    val examPassageGutter = 28.dp
    val examMarkTileMinHeight = 56.dp
    val examRowMinHeight = 64.dp
    val scoreSplitBarHeight = 12.dp

    /** Skeleton text bars (home header, category card). */
    val skeletonLineHeight = 12.dp
    val skeletonLineWidth = 120.dp
    val skeletonLineWidthShort = 64.dp
}

/**
 * Border widths used by the v3 flat components (tokens 3.0.0 has no depth: no solid edges).
 * [hairline] = 1dp card / option / divider border, [emphasis] = 2dp selected, correct, wrong and
 * focus ring, [ring] = 3dp Əminlik selection ring.
 */
object Borders {
    val hairline = 1.dp
    val emphasis = 2.dp
    val ring = 3.dp

    /** components.md §38: dashed answer box / week dot border (1.5dp, dash 4dp, gap 3dp). */
    val dashed = 1.5.dp
    val dash = 4.dp
    val dashGap = 3.dp
}

/** Component sizes that tokens.json gives only inside components.md / screen specs. */
object GameSizes {
    /** components.md §4: timer pill icon and its padding. */
    val timerPillIcon = 18.dp
    val timerPillPaddingVertical = 6.dp

    /** components.md §7: Əminlik signal bars (3 bars, 6dp wide, 8/13/18dp tall, 3dp gap). */
    val signalBarWidth = 6.dp
    val signalBarGap = 3.dp
    val signalBarHeights = listOf(8.dp, 13.dp, 18.dp)

    /** components.md §7: corner badges of an Əminlik tile and the lock icon inside. */
    val tileCornerBadge = 24.dp
    val tileLockIcon = 14.dp
    val tileBadgeProgress = 16.dp

    /** quiz-result.md: review entry icon box; duel-result.md: hero icon; profile/rating card padding. */
    val reviewEntryIconBox = 44.dp
    val heroIcon = 40.dp
    val compactCardPadding = 14.dp

    /** duel player panel padding (components.md §16). */
    val playerPanelPadding = 10.dp

    /** components.md §35: streak chip flame and the freeze shield after the number. */
    val streakChipFlame = 20.dp
    val streakChipShield = 12.dp

    /** components.md §38a: glyphs inside a week dot (check / shield 14dp, dash 12dp). */
    val weekDotGlyph = 14.dp
    val weekDotDash = 12.dp

    /** components.md §6: duration clock icon and CTA arrow on the duel card. */
    val duelCardClock = 16.dp

    /** components.md §27: glyphs of the large and compact result squares. */
    val resultSquareGlyph = 20.dp
    val resultSquareGlyphCompact = 14.dp

    /** components.md §26 / §30: category level-up square, permission card circle, flame on profile. */
    val categoryLevelUpBox = 24.dp
    val permissionIconCircle = 48.dp
    val profileStreakFlame = 40.dp

    /** components.md §25: the mini XP bar moves under the level chip below this width. */
    val miniBarMinWidth = 48.dp

    /** components.md §33: badge dot offset and the ring in the bar colour around it. */
    val navBadgeOffset = 2.dp
    val navBadgeRing = 2.dp

    /** components.md §39 / §40 / §42 / §44: exam glyph sizes, the timer pill and the segmented toggle. */
    val examTimerPillHeight = 32.dp
    val examTimerPillWidth = 91.dp
    val examTimerPillWidthLarge = 105.dp
    val examFlagGlyph = 12.dp
    val examCellEditGlyph = 12.dp
    val examLegendCell = 16.dp
    val examAudioIcon = 28.dp
    val examAudioMiniButton = 40.dp
    val examAudioMiniHeight = 56.dp
    val examMarkCheck = 16.dp
    val examCriterionChipMinWidth = 36.dp
    val examSegmentHeight = 40.dp
    val examReviewFlag = 16.dp
    val examSplitLegendSquare = 12.dp
    val examSplitGap = 2.dp
    val examMarkedUnderline = 1.5.dp
    val examGapWidth = 40.dp

    /** components.md §36: the squiggle starts and ends this far outside the word; ±crest jitter. */
    val squiggleOverhang = 2.dp
    val squiggleJitter = 0.4.dp
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

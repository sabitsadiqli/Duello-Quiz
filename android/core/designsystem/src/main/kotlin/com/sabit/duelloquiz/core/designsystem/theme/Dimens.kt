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

    /** components.md: loading indicator inside buttons, banner close icon, inline error icon. */
    val inlineProgress = 20.dp
    val inlineIcon = 20.dp
    val progressStroke = 2.dp
    val outlineThin = 1.dp

    /** components.md §11: full-screen state views. */
    val stateIllustration = 72.dp
    val stateIllustrationContainer = 120.dp
    val warningIcon = 48.dp
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

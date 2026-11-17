package com.sabit.duelloquiz.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** shape from design/tokens.json 3.0.0 (extraSmall 4 … extraLarge 18). */
internal val DuelloShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(18.dp),
)

/** shape.full ("50%") for pills (buttons, chips, sticker), avatars, dots and progress bars. */
val FullShape = CircleShape

/** shape.component.card / dailyCard / progressCard (large, 16dp): flat hairline cards. */
val CardShape = RoundedCornerShape(16.dp)

/** shape.component.duelCard and the v3 result heroes (extraLarge, 18dp). */
val DuelCardShape = RoundedCornerShape(18.dp)

/** shape.component.confidencePanel / bottomSheet: extraLarge, top corners only. */
val ConfidencePanelShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)

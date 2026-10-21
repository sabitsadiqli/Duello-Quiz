package com.sabit.duelloquiz.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** shape from design/tokens.json 2.0.0 (extraSmall 8 … extraLarge 28). */
internal val DuelloShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** shape.full ("50%") for pills, avatars and progress bars. */
val FullShape = CircleShape

/**
 * Hero cards (components.md §6 duel hero, quiz-result.md score hero, duel-result.md outcome
 * hero): `shape.extraLarge` − 4 = 24dp.
 */
val HeroShape = RoundedCornerShape(24.dp)

/** shape.component.confidencePanel: extraLarge, top corners only. */
val ConfidencePanelShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

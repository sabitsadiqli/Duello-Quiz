package com.sabit.duelloquiz.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** A [Brush] for this gradient token: CSS-style angle (135° = top-start to bottom-end). */
fun GradientColors.toBrush(): Brush = AngledLinearGradient(start, end, angleDegrees)

/**
 * Linear gradient at a CSS angle (0° points up, clockwise), sized to the drawn area like CSS:
 * the gradient line passes through the centre and its length makes the corners hit the stops.
 * A data class, so equal tokens give equal brushes (stable for Compose).
 */
@Immutable
private data class AngledLinearGradient(
    val startColor: Color,
    val endColor: Color,
    val angleDegrees: Float,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val radians = Math.toRadians(angleDegrees.toDouble())
        val dx = sin(radians).toFloat()
        val dy = -cos(radians).toFloat()
        val halfLength = (abs(size.width * dx) + abs(size.height * dy)) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        return LinearGradientShader(
            from = Offset(center.x - dx * halfLength, center.y - dy * halfLength),
            to = Offset(center.x + dx * halfLength, center.y + dy * halfLength),
            colors = listOf(startColor, endColor),
        )
    }
}

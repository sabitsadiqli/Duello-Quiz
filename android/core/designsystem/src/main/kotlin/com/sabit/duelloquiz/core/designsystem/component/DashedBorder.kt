package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import com.sabit.duelloquiz.core.designsystem.theme.Borders

/**
 * Dashed border inside the bounds of [shape] (components.md §3 queued option, §38 answer boxes,
 * §38a today's week dot): stroke [width], dash [dash], gap [gap]. One of the allowed hand-made
 * touches (components.md §36 table); do not use it elsewhere.
 */
fun Modifier.dashedBorder(
    color: Color,
    shape: Shape,
    width: Dp = Borders.dashed,
    dash: Dp = Borders.dash,
    gap: Dp = Borders.dashGap,
): Modifier = drawWithCache {
    val strokePx = width.toPx()
    val inset = strokePx / 2f
    val outline = shape.createOutline(Size(size.width - strokePx, size.height - strokePx), layoutDirection, this)
    val stroke = Stroke(
        width = strokePx,
        cap = StrokeCap.Butt,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx())),
    )
    // Drawn over the content, so a face background set further in does not hide it.
    onDrawWithContent {
        drawContent()
        translate(left = inset, top = inset) { drawOutline(outline, color, style = stroke) }
    }
}

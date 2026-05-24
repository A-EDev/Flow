package io.github.aedev.flow.ui.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

fun Modifier.fadingEdge(
    top: Dp? = null,
    bottom: Dp? = null
): Modifier = graphicsLayer(alpha = 0.99f)
    .drawWithContent {
        drawContent()
        if (top != null) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startY = 0f,
                    endY = top.toPx()
                ),
                blendMode = BlendMode.DstIn
            )
        }
        if (bottom != null) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = size.height - bottom.toPx(),
                    endY = size.height
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }

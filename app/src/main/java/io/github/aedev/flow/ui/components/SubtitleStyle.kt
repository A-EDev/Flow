package io.github.aedev.flow.ui.components

import androidx.compose.ui.graphics.Color

data class SubtitleStyle(
    val fontSize: Float = 14f,
    val textColor: Color = Color.White,
    val backgroundColor: Color = Color.Black.copy(alpha = 0.6f),
    val isBold: Boolean = true,
    val bottomPadding: Float = 48f
)

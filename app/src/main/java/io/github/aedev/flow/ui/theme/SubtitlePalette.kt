package io.github.aedev.flow.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The swatches the subtitle customiser offers. These are a fixed picker palette the user chooses
 * from and the renderer then honours verbatim, not theme roles: a caption colour must stay the one
 * the user picked whatever scheme the app is wearing.
 */
val SubtitleTextSwatches: List<Color> =
    listOf(
        Color.White,
        Color(0xFFFFF59D),
        Color(0xFF80DEEA),
        Color(0xFFA5D6A7),
        Color(0xFFFFCC80),
        Color(0xFFF8BBD0),
    )

val SubtitleBackgroundSwatches: List<Color> =
    listOf(
        Color.Black,
        Color(0xFF1F2937),
        Color(0xFF263238),
        Color(0xFF4E342E),
        Color(0xFF102A43),
        Color(0xFF37474F),
    )

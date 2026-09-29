package io.github.aedev.flow.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

private const val TONE_STEP = 4.0
private const val LIGHTEST_TONE = 98.0
private const val DARKEST_TONE = 2.0
private const val MID_TONE = 50.0

internal fun Color.tone(): Double {
    val lab = DoubleArray(3)
    ColorUtils.colorToLAB(toArgb(), lab)
    return lab[0]
}

internal fun Color.withTone(tone: Double): Color {
    val lab = DoubleArray(3)
    ColorUtils.colorToLAB(toArgb(), lab)
    return Color(ColorUtils.LABToColor(tone.coerceIn(0.0, 100.0), lab[1], lab[2]))
}

/** WCAG contrast ratio between two opaque colors. */
internal fun contrastRatio(
    a: Color,
    b: Color,
): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return maxOf(la, lb) / minOf(la, lb)
}

/** Moves [color] away from [surface] in tone until it clears [minRatio]. */
internal fun ensureContrastOn(
    color: Color,
    surface: Color,
    minRatio: Float,
): Color {
    val lighten = surface.tone() < MID_TONE
    var candidate = color
    var tone = candidate.tone()
    while (contrastRatio(candidate, surface) < minRatio && if (lighten) tone < LIGHTEST_TONE else tone > DARKEST_TONE) {
        tone += if (lighten) TONE_STEP else -TONE_STEP
        candidate = candidate.withTone(tone)
    }
    return candidate
}

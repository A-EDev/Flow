package io.github.aedev.flow.ui.screens.player.components

internal fun resolvePlayerQualityLabel(
    currentQuality: Int,
    effectiveQuality: Int,
    autoLabel: String,
    autoWithHeightLabel: String,
): String = when {
    currentQuality > 0 -> currentQuality.toString()
    effectiveQuality > 0 -> autoWithHeightLabel
    else -> autoLabel
}

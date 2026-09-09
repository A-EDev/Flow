package io.github.aedev.flow.ui.components.videoplayer.controls

import io.github.aedev.flow.player.quality.QualityManager

internal fun resolvePlayerQualityLabel(
    currentQuality: Int,
    effectiveQuality: Int,
    autoLabel: String,
    autoWithHeightLabel: String,
): String =
    when {
        currentQuality > 0 -> currentQuality.toString()
        effectiveQuality > 0 -> autoWithHeightLabel
        else -> autoLabel
    }

internal fun compactPlayerQualityLabel(qualityLabel: String): String {
    val height =
        Regex("""\d+""")
            .find(qualityLabel)
            ?.value
            ?.toIntOrNull()
            ?.let(QualityManager::normalizeQualityHeight)
    return when (height) {
        2160 -> "4K"
        1440 -> "QHD"
        1080 -> "FHD"
        720 -> "HD"
        480 -> "SD"
        360 -> "360p"
        240 -> "240p"
        144 -> "144p"
        null -> qualityLabel
        else -> "${height}p"
    }
}

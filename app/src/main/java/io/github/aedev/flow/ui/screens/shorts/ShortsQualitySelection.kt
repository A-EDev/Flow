package io.github.aedev.flow.ui.screens.shorts

import io.github.aedev.flow.data.shorts.ShortVideoQuality
import io.github.aedev.flow.player.stream.VideoCodecUtils

internal fun findActiveShortQuality(
    qualities: List<ShortVideoQuality>,
    currentVideoUrl: String?,
    activeVideoWidth: Int,
    activeVideoHeight: Int,
    activeCodecKey: String?
): ShortVideoQuality? {
    currentVideoUrl
        ?.takeIf { it.isNotBlank() }
        ?.let { url -> qualities.firstOrNull { it.videoUrl == url } }
        ?.let { return it }

    val dimensions = listOf(activeVideoWidth, activeVideoHeight).filter { it > 0 }
    val activeHeightClass = dimensions.minOrNull()
        ?.let(VideoCodecUtils::normalizeQualityHeight)
        ?.takeIf { it > 0 }
        ?: return null

    return qualities.firstOrNull { quality ->
        quality.heightClass == activeHeightClass &&
            activeCodecKey?.takeIf { it.isNotBlank() } == quality.codecKey
    } ?: qualities.firstOrNull { it.heightClass == activeHeightClass }
}

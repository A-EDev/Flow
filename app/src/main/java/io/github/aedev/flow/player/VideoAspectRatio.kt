package io.github.aedev.flow.player

internal const val DEFAULT_VIDEO_ASPECT_RATIO = 16f / 9f

private const val MAX_PIP_ASPECT_RATIO = 2.39f
private const val MIN_PIP_ASPECT_RATIO = 1f / MAX_PIP_ASPECT_RATIO

internal fun sanitizeDisplayAspectRatio(aspectRatio: Float): Float =
    aspectRatio
        .takeIf { it.isFinite() && it > 0f }
        ?: DEFAULT_VIDEO_ASPECT_RATIO

internal fun sourceVideoAspectRatio(dimensions: Iterable<Pair<Int, Int>>): Float? {
    // YouTube's smallest renditions can have visibly rounded dimensions. The largest
    // advertised rendition most closely represents the video's authored display ratio.
    val sourceDimensions = dimensions
        .filter { (width, height) -> width > 0 && height > 0 }
        .maxByOrNull { (width, height) -> width.toLong() * height.toLong() }
        ?: return null
    return sourceDimensions.first.toFloat() / sourceDimensions.second.toFloat()
}

internal fun sanitizePipAspectRatio(aspectRatio: Float): Float =
    sanitizeDisplayAspectRatio(aspectRatio)
        .coerceIn(MIN_PIP_ASPECT_RATIO, MAX_PIP_ASPECT_RATIO)

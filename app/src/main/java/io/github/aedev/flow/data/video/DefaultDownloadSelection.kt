package io.github.aedev.flow.data.video

/**
 * Picks what a download started without a dialog should fetch: the tallest quality at or below
 * the default download quality, and the preferred codec when that quality offers it.
 */
object DefaultDownloadSelection {
    /** [target] 0 means no cap. Falls back to the smallest quality above the cap when none fits under it. */
    fun pickHeight(
        heights: Collection<Int>,
        target: Int,
    ): Int? {
        val available = heights.filter { it > 0 }.distinct()
        if (available.isEmpty()) return null
        if (target <= 0) return available.max()
        return available.filter { it <= target }.maxOrNull() ?: available.min()
    }

    /** Codecs in the order to try: the preferred one first, then the download priority order. */
    fun rankCodecs(
        codecs: Collection<String>,
        preferred: String?,
    ): List<String> =
        codecs.distinct().sortedWith(
            compareBy<String> { if (it == preferred) 0 else 1 }
                .thenBy { DownloadStreamPolicy.DOWNLOAD_CODEC_PRIORITY[it] ?: Int.MAX_VALUE },
        )
}

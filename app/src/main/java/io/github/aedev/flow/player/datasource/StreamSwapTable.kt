package io.github.aedev.flow.player.datasource

import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.error.StreamDenialClassifier

/**
 * The file a googlevideo URL serves, read off the URL itself: the minted set it belongs to (`id`),
 * the format (`itag`) and the exact encode (`lmt`, `clen`). Two URLs with the same itag, lmt and
 * clen serve byte-identical files whichever client minted them, measured across VISIONOS,
 * ANDROID_VR and TV_TIZEN, so a byte range of one is the same byte range of the other.
 */
internal data class StreamFile(
    val setId: String,
    val itag: String,
    val lastModified: Long?,
    val length: Long?,
) {
    fun isSameFileAs(format: PlayerResponse.StreamingData.Format): Boolean =
        format.itag.toString() == itag &&
            lastModified != null &&
            format.lastModified == lastModified &&
            (length == null || format.contentLength == null || format.contentLength == length)

    companion object {
        fun of(url: String?): StreamFile? {
            val setId = StreamDenialClassifier.queryParam(url, "id")?.takeIf { it.isNotBlank() } ?: return null
            val itag = StreamDenialClassifier.itagOf(url) ?: return null
            return StreamFile(
                setId = setId,
                itag = itag,
                lastModified = StreamDenialClassifier.queryParam(url, "lmt")?.toLongOrNull(),
                length = StreamDenialClassifier.queryParam(url, "clen")?.toLongOrNull(),
            )
        }
    }
}

/**
 * Which video each playing stream URL belongs to, and which URL now serves a refused one.
 *
 * A refusal swaps a whole minted set at once: the audio and video loaders usually meet the wall
 * together, and the second finds its replacement already recorded. Replacements are keyed by the
 * exact file, not the itag: one answer can carry several files under one itag (dubbed tracks, DRC
 * copies), and a request is only redirected to the identical file, so its byte range stays valid.
 */
internal class StreamSwapTable(
    private val maxSwapsPerVideo: Int = MAX_SWAPS_PER_VIDEO,
) {
    private val videoBySet = boundedMap<String, String>(MAX_SETS)
    private val swaps = boundedMap<SwapKey, PlayerResponse.StreamingData.Format>(MAX_SWAPS)
    private val swapsByVideo = boundedMap<String, Int>(MAX_SETS)

    @Synchronized
    fun register(
        videoId: String,
        urls: Collection<String?>,
    ) {
        if (videoId.isBlank()) return
        urls.forEach { url -> StreamFile.of(url)?.let { videoBySet[it.setId] = videoId } }
    }

    @Synchronized
    fun videoFor(url: String?): String? = StreamFile.of(url)?.let { videoBySet[it.setId] }

    /** Whether [videoId] may still be swapped; checked before any replacement is fetched. */
    @Synchronized
    fun hasSwapsLeft(videoId: String): Boolean = (swapsByVideo[videoId] ?: 0) < maxSwapsPerVideo

    /** The URL that now serves [url]'s file, following later swaps of a replacement; null when none. */
    @Synchronized
    fun rewrite(url: String): String? {
        var current = url
        var hops = 0
        while (hops++ < maxSwapsPerVideo) {
            val file = StreamFile.of(current) ?: break
            val lastModified = file.lastModified ?: break
            current = swaps[SwapKey(file.setId, file.itag, lastModified)]?.takeIf { file.isSameFileAs(it) }?.url ?: break
        }
        return current.takeIf { it != url }
    }

    /**
     * Records [replacements] for the set [refusedUrl] belongs to. False when the refused file has no
     * twin among them or the video has used up its swaps, which leaves the refusal to the player.
     */
    @Synchronized
    fun record(
        refusedUrl: String,
        replacements: List<PlayerResponse.StreamingData.Format>,
    ): Boolean {
        // The audio and video loaders share one resolve; the second to record it spends nothing.
        if (rewrite(refusedUrl) != null) return true
        val refused = StreamFile.of(refusedUrl) ?: return false
        val videoId = videoBySet[refused.setId] ?: return false
        val used = swapsByVideo[videoId] ?: 0
        if (used >= maxSwapsPerVideo) return false
        if (replacements.none { refused.isSameFileAs(it) && !it.url.isNullOrEmpty() }) return false
        replacements.forEach { format ->
            val url = format.url?.takeIf { it.isNotEmpty() } ?: return@forEach
            val lastModified = format.lastModified ?: return@forEach
            swaps[SwapKey(refused.setId, format.itag.toString(), lastModified)] = format
            StreamFile.of(url)?.let { videoBySet[it.setId] = videoId }
        }
        swapsByVideo[videoId] = used + 1
        return true
    }

    private data class SwapKey(
        val setId: String,
        val itag: String,
        val lastModified: Long,
    )

    private companion object {
        const val MAX_SWAPS_PER_VIDEO = 3
        const val MAX_SETS = 64
        const val MAX_SWAPS = 512

        fun <K, V> boundedMap(limit: Int): MutableMap<K, V> =
            object : LinkedHashMap<K, V>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > limit
            }
    }
}

package io.github.aedev.flow.utils

object ThumbnailUrlResolver {
    private val youtubeVideoThumbnailPattern =
        Regex("""(?:https?:)?//(?:i\.ytimg\.com|img\.youtube\.com)/(?:vi|vi_webp)/([^/?#]+)/[^/?#]+""")

    fun buildHighQualityYoutubeThumbnail(videoId: String): String {
        val id = videoId.trim()
        return if (id.isEmpty()) "" else "https://i.ytimg.com/vi/$id/hq720.jpg"
    }

    fun buildFallbackYoutubeThumbnail(videoId: String): String {
        val id = videoId.trim()
        return if (id.isEmpty()) "" else "https://i.ytimg.com/vi/$id/hqdefault.jpg"
    }

    fun normalizeVideoThumbnail(videoId: String, rawUrl: String?): String {
        val raw = rawUrl?.trim().orEmpty()
        if (raw.isEmpty()) return buildHighQualityYoutubeThumbnail(videoId)

        val match = youtubeVideoThumbnailPattern.find(raw) ?: return raw
        val resolvedVideoId = match.groupValues.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?: videoId.trim()

        return buildHighQualityYoutubeThumbnail(resolvedVideoId).ifEmpty { raw }
    }

    fun fallbackVideoThumbnail(videoId: String, rawUrl: String?): String? {
        val raw = rawUrl?.trim().orEmpty()
        val resolvedVideoId = youtubeVideoThumbnailPattern.find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?: videoId.trim()

        val fallback = buildFallbackYoutubeThumbnail(resolvedVideoId)
        return fallback.takeIf { it.isNotEmpty() && it != raw }
    }

    fun isYoutubeVideoThumbnail(rawUrl: String?): Boolean {
        val raw = rawUrl?.trim().orEmpty()
        return youtubeVideoThumbnailPattern.containsMatchIn(raw)
    }
}

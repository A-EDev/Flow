package io.github.aedev.flow.utils

object ThumbnailUrlResolver {
    private val youtubeVideoThumbnailPattern =
        Regex("""(?:https?:)?//(?:i\.ytimg\.com|img\.youtube\.com)/(?:vi|vi_webp)/([^/?#]+)/[^/?#]+""")
    private val googleCdnSizePattern = Regex("""w\d+-h\d+""")
    private val googleCdnParamStartPattern = Regex("""=(?:w|s|h)""")

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

    fun resolveMusicThumbnail(videoId: String, rawUrl: String?, size: Int = 1080): String {
        val raw = rawUrl?.trim().orEmpty()
        val id = videoId.trim()

        if (raw.isEmpty()) return buildHighQualityYoutubeThumbnail(id)

        return when {
            isYoutubeVideoThumbnail(raw) -> normalizeVideoThumbnail(id, raw)
            raw.contains("googleusercontent.com") || raw.contains("ggpht.com") ->
                resizeImageThumbnail(raw, size, size)
            else -> raw
        }
    }

    fun resolveChannelBanner(rawUrl: String?, targetWidth: Int = 1060): String {
        val raw = rawUrl?.trim().orEmpty()
        if (raw.isEmpty()) return ""

        val isGoogleCdn = raw.contains("googleusercontent.com") || raw.contains("ggpht.com")
        if (!isGoogleCdn) return raw

        val sizeParamRegex = Regex("""=([wsh])\d+""")
        val match = sizeParamRegex.find(raw)
        if (match != null) {
            val paramType = match.groupValues[1]
            return raw.replaceFirst(match.value, "=$paramType$targetWidth")
        }

        val paramStart = googleCdnParamStartPattern.find(raw)?.range?.first
        return if (paramStart != null) {
            val baseUrl = raw.substring(0, paramStart)
            "$baseUrl=w$targetWidth"
        } else {
            "$raw=w$targetWidth"
        }
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

    fun resizeImageThumbnail(rawUrl: String?, width: Int? = null, height: Int? = null): String {
        val raw = rawUrl?.trim().orEmpty()
        if (raw.isEmpty() || (width == null && height == null)) return raw

        val isGoogleCdn = raw.contains("googleusercontent.com") || raw.contains("ggpht.com")
        val isYtimg = raw.contains("i.ytimg.com") || raw.contains("img.youtube.com")

        return when {
            isGoogleCdn -> resizeGoogleCdnThumbnail(raw, width, height)
            isYtimg -> resizeYoutubeThumbnail(raw, width ?: height ?: 0)
            else -> raw
        }
    }

    private fun resizeGoogleCdnThumbnail(rawUrl: String, width: Int?, height: Int?): String {
        val w = width ?: height ?: return rawUrl
        val h = height ?: width ?: return rawUrl

        if (googleCdnSizePattern.containsMatchIn(rawUrl)) {
            return rawUrl.replace(googleCdnSizePattern, "w$w-h$h")
        }

        val paramStart = googleCdnParamStartPattern.find(rawUrl)?.range?.first
        val baseUrl = if (paramStart != null) rawUrl.substring(0, paramStart) else rawUrl

        return if (width != null && height != null) {
            "$baseUrl=w$w-h$h-p-l90-rj"
        } else {
            "$baseUrl=s$w-p-l90-rj"
        }
    }

    private fun resizeYoutubeThumbnail(rawUrl: String, width: Int): String {
        return when {
            width > 480 -> rawUrl
                .replace("mqdefault.jpg", "hq720.jpg")
                .replace("hqdefault.jpg", "hq720.jpg")
                .replace("sddefault.jpg", "hq720.jpg")
                .replace("default.jpg", "hq720.jpg")
                .replace("mqdefault.webp", "hq720.jpg")
                .replace("hqdefault.webp", "hq720.jpg")
                .replace("sddefault.webp", "hq720.jpg")
                .replace("default.webp", "hq720.jpg")
            width > 320 -> rawUrl
                .replace("mqdefault.jpg", "hqdefault.jpg")
                .replace("default.jpg", "hqdefault.jpg")
                .replace("mqdefault.webp", "hqdefault.jpg")
                .replace("default.webp", "hqdefault.jpg")
            else -> rawUrl
        }
    }
}

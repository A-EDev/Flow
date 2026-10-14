package io.github.aedev.flow.data.video.downloader

/**
 * googlevideo URL handling for ranged downloads, on the raw query string: rebuilding these URLs
 * through a URI builder re-encodes parameters the signature covers.
 */
object YouTubeStreamUrls {
    fun isYouTubeStreamUrl(url: String): Boolean {
        val host =
            url
                .substringAfter("://", "")
                .substringBefore('/')
                .substringBefore('?')
                .lowercase()
        return host.contains("googlevideo.com") || (host.contains("youtube.com") && url.contains("videoplayback"))
    }

    /** The stream's full size from its `clen` parameter, or -1 when the URL has none. */
    fun extractClenFromUrl(url: String): Long = queryValue(url, "clen")?.toLongOrNull() ?: -1L

    /**
     * [url] asking for bytes [startByte]..[endByte] through googlevideo's `range` parameter. Any
     * `range` the extractor left on the URL is replaced, since it would cap what the CDN serves.
     */
    fun buildYouTubeBlockUrl(
        url: String,
        startByte: Long,
        endByte: Long,
    ): String {
        val base = withoutParam(url, "range")
        val separator = if ('?' in base) "&" else "?"
        return "$base${separator}range=$startByte-$endByte"
    }

    private fun queryValue(
        url: String,
        name: String,
    ): String? =
        url
            .substringAfter('?', "")
            .substringBefore('#')
            .split('&')
            .firstOrNull { it.substringBefore('=') == name }
            ?.substringAfter('=', "")

    private fun withoutParam(
        url: String,
        name: String,
    ): String {
        val query = url.substringAfter('?', "")
        if (query.isEmpty()) return url
        val kept = query.split('&').filterNot { it.substringBefore('=') == name }
        val path = url.substringBefore('?')
        return if (kept.isEmpty()) path else "$path?${kept.joinToString("&")}"
    }
}

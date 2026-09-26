package io.github.aedev.flow.data.video.downloader

import android.net.Uri

/** Query-string handling for YouTube stream URLs downloaded in byte ranges. */
internal object YouTubeStreamUrls {
    private const val UA_IOS = "com.google.ios.youtube/21.03.3 (iPad7,6; U; CPU iPadOS 17_7_10 like Mac OS X; en-US)"
    private const val UA_ANDROID = "com.google.android.youtube/21.03.38 (Linux; U; Android 14) gzip"
    private const val UA_ANDROID_VR =
        "com.google.android.apps.youtube.vr.oculus/1.61.48 " +
            "(Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)"

    fun resolveUserAgent(
        url: String,
        fallback: String,
    ): String =
        try {
            when (Uri.parse(url).getQueryParameter("c")?.uppercase()) {
                "IOS" -> UA_IOS
                "ANDROID", "ANDROID_CREATOR" -> UA_ANDROID
                "ANDROID_VR" -> UA_ANDROID_VR
                else -> fallback
            }
        } catch (_: Exception) {
            fallback
        }

    /**
     * YouTube CDN (googlevideo.com / youtube.com/videoplayback) streams embed a `range=0-N`
     * query parameter that caps how much content the CDN will serve per URL.
     * For parallel block downloads we must strip that cap and append `&range=X-Y` per-block,
     * matching how YouTube's official clients (and MusicPlayerUtils) do it.
     */
    fun isYouTubeStreamUrl(url: String): Boolean {
        return try {
            val host = Uri.parse(url).host ?: return false
            host.contains("googlevideo.com") ||
                (host.contains("youtube.com") && url.contains("videoplayback"))
        } catch (_: Exception) {
            false
        }
    }

    /**
     * YouTube embeds the true content length as a `clen=` query parameter.
     * This is the canonical size for the *entire* stream, even when the URL
     * has an embedded `range=` restriction that would cause a HEAD/GET to
     * return only a fragment.
     */
    fun extractClenFromUrl(url: String): Long =
        try {
            Uri.parse(url).getQueryParameter("clen")?.toLongOrNull() ?: -1L
        } catch (_: Exception) {
            -1L
        }

    /**
     * Return the URL with any embedded `range=` query parameter removed.
     * All other parameters (including `n` for throttle deobfuscation) are kept.
     */
    fun stripRangeParam(url: String): String {
        return try {
            val uri = Uri.parse(url)
            if (uri.getQueryParameter("range") == null) return url
            val builder = uri.buildUpon().clearQuery()
            uri.queryParameterNames
                .filter { it != "range" }
                .forEach { key ->
                    uri.getQueryParameters(key).forEach { value ->
                        builder.appendQueryParameter(key, value)
                    }
                }
            builder.build().toString()
        } catch (_: Exception) {
            url
        }
    }

    /**
     * Append `range=X-Y` as a query parameter for a YouTube block request.
     * Assumes the URL has already had its original `range=` stripped.
     */
    fun buildYouTubeBlockUrl(
        baseUrl: String,
        startByte: Long,
        endByte: Long,
    ): String {
        val sep = if (baseUrl.contains('?')) "&" else "?"
        return "${baseUrl}${sep}range=$startByte-$endByte"
    }
}

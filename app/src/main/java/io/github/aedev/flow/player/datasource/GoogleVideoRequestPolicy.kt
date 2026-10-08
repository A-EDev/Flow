package io.github.aedev.flow.player.datasource

import io.github.aedev.flow.innertube.models.YouTubeClient

/**
 * How a `googlevideo` request must look for the client that minted its URL (the URL's `c=`
 * parameter). Playback and downloads both fetch through this, because a user agent or header set
 * that does not match the minting client is a known cause of mid-stream 403s.
 */
object GoogleVideoRequestPolicy {
    /** Clients whose URLs a browser minted, and which therefore send browser CORS headers. */
    private val WEB_FAMILY_CLIENTS =
        setOf(
            "WEB",
            "MWEB",
            "WEB_REMIX",
            "WEB_CREATOR",
            "WEB_EMBEDDED_PLAYER",
            "TVHTML5",
            "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
        )

    fun userAgent(
        clientName: String?,
        fallback: String,
    ): String =
        when (clientName?.uppercase()) {
            "IOS" -> YouTubeClient.IPADOS.userAgent
            "ANDROID", "ANDROID_CREATOR" -> YouTubeClient.ANDROID.userAgent
            "ANDROID_VR" -> YouTubeClient.ANDROID_VR_1_61_48.userAgent
            "VISIONOS" -> YouTubeClient.VISIONOS.userAgent
            "TVHTML5" -> YouTubeClient.TV_TIZEN.userAgent
            "TVHTML5_SIMPLY_EMBEDDED_PLAYER" -> YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER.userAgent
            "MWEB" -> YouTubeClient.USER_AGENT_MWEB
            "WEB", "WEB_REMIX" -> YouTubeClient.USER_AGENT_WEB
            else -> fallback
        }

    /**
     * `Origin`, `Referer` and the `Sec-Fetch-*` triple are browser-only: a real visionOS or Android
     * VR client sends none of them, so they go only on URLs a web client minted.
     */
    fun headers(clientName: String?): Map<String, String> {
        val headers =
            linkedMapOf(
                // Media is already compressed and served in byte ranges, so identity keeps the
                // range arithmetic exact rather than saving anything.
                "Accept-Encoding" to "identity",
                "Accept" to "*/*",
            )
        val client = clientName?.uppercase()
        if (client == null || client in WEB_FAMILY_CLIENTS) {
            headers["Origin"] = "https://www.youtube.com"
            headers["Referer"] = "https://www.youtube.com/"
            headers["Sec-Fetch-Dest"] = "empty"
            headers["Sec-Fetch-Mode"] = "cors"
            headers["Sec-Fetch-Site"] = "cross-site"
        }
        return headers
    }
}

package io.github.aedev.flow.ui

import androidx.navigation.NavHostController
import java.net.URLDecoder

internal fun NavHostController.navigateToYoutubeChannel(channelIdOrHandle: String) {
    val targetUrl = youtubeChannelUrl(channelIdOrHandle) ?: return
    val currentUrl = currentBackStackEntry
        ?.takeIf { it.destination.route == "channel?url={channelUrl}" }
        ?.arguments
        ?.getString("channelUrl")
        ?.let { encodedUrl ->
            runCatching { URLDecoder.decode(encodedUrl, Charsets.UTF_8.name()) }
                .getOrDefault(encodedUrl)
        }
        ?.let(::youtubeChannelUrl)

    if (currentUrl == targetUrl) return
    youtubeChannelRoute(targetUrl)?.let(::navigate)
}

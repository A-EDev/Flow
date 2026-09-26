package io.github.aedev.flow.ui

import androidx.navigation.NavBackStackEntry
import io.github.aedev.flow.data.shorts.queue.ShortsQueueSource
import io.github.aedev.flow.ui.components.layout.navigation.FlowTab
import java.net.URI
import java.net.URLEncoder

/** The tab whose root screen [route] is, or null for every screen that is not a tab root. */
internal fun flowTabForDestination(
    route: String?,
    shortsSourceArg: String?,
): FlowTab? =
    when (route) {
        null -> null
        SHORTS_ROUTE_PATTERN -> FlowTab.Shorts.takeIf { ShortsQueueSource.decode(shortsSourceArg) == ShortsQueueSource.Feed }
        else -> FlowTab.entries.firstOrNull { it.route == route }
    }

internal fun NavBackStackEntry.flowTab(): FlowTab? = flowTabForDestination(destination.route, arguments?.getString(SHORTS_ROUTE_ARG))

/** Search is a tab, but it keeps the back-button layout of the other search screens, so no bar. */
internal fun FlowTab?.showsNavigationBar(): Boolean = this != null && this != FlowTab.Search

internal fun youtubeChannelUrl(channelIdOrHandle: String): String? {
    val value = channelIdOrHandle.trim()
    if (value.isEmpty()) return null
    return when {
        value.startsWith("http://") || value.startsWith("https://") -> normalizeYoutubeChannelUrl(value)
        value.startsWith("UC") -> "https://www.youtube.com/channel/$value"
        value.startsWith("@") -> "https://www.youtube.com/$value"
        else -> "https://www.youtube.com/@$value"
    }
}

/**
 * The channel id the nav route carried, or null when it carried an @handle, `/c/` or `/user/` link.
 * Browse answers 400 to those, so they are resolved to an id first.
 */
internal fun youtubeChannelBrowseId(channelIdOrUrl: String): String? {
    val value = channelIdOrUrl.trim()
    if (value.isEmpty()) return null
    if (value.startsWith("UC") && !value.contains('/')) return value

    val segments =
        youtubeChannelUrl(value)
            ?.substringAfter("youtube.com/", "")
            ?.split('/')
            ?.filter(String::isNotBlank)
            ?: return null
    return segments
        .takeIf { it.firstOrNull() == "channel" }
        ?.getOrNull(1)
        ?.takeIf { it.startsWith("UC") }
}

internal fun youtubeChannelRoute(channelIdOrHandle: String): String? =
    youtubeChannelUrl(channelIdOrHandle)?.let { channelUrl ->
        "channel?url=${URLEncoder.encode(channelUrl, Charsets.UTF_8.name())}"
    }

private fun normalizeYoutubeChannelUrl(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    val host = uri.host?.lowercase().orEmpty()
    if (host != "youtube.com" && !host.endsWith(".youtube.com")) return url

    val segments =
        uri.path
            .orEmpty()
            .split('/')
            .filter(String::isNotBlank)
    if (segments.isEmpty()) return url

    val channelValue =
        when {
            segments.first() == "channel" -> segments.getOrNull(1)
            segments.first().startsWith("@") -> segments.first()
            else -> null
        } ?: return url

    return when {
        channelValue.startsWith("UC") -> "https://www.youtube.com/channel/$channelValue"
        channelValue.startsWith("@") -> "https://www.youtube.com/$channelValue"
        else -> "https://www.youtube.com/@$channelValue"
    }
}

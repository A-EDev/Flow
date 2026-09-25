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
 * The browseId InnerTube wants, from whatever the nav route carried. A channel id and an @handle are
 * both valid browse targets, so a handle is kept rather than resolved through an extra request.
 */
internal fun youtubeChannelBrowseId(channelIdOrUrl: String): String? {
    val value = channelIdOrUrl.trim()
    if (value.isEmpty()) return null
    if (value.startsWith("UC") && !value.contains('/')) return value
    if (value.startsWith("@") && !value.contains('/')) return value

    val segments =
        youtubeChannelUrl(value)
            ?.substringAfter("youtube.com/", "")
            ?.split('/')
            ?.filter(String::isNotBlank)
            ?: return null
    return when {
        segments.firstOrNull() == "channel" -> segments.getOrNull(1)
        segments.firstOrNull()?.startsWith("@") == true -> segments.first()
        else -> null
    }?.takeIf(String::isNotBlank)
}

internal fun youtubeChannelRoute(channelIdOrHandle: String): String? =
    youtubeChannelUrl(channelIdOrHandle)?.let { channelUrl ->
        "channel?url=${URLEncoder.encode(channelUrl, Charsets.UTF_8.name())}"
    }

/**
 * The channel route an external link opens, or null when the link is not a `/channel/UC…` link.
 * InnerTube's browse rejects an @handle as a browseId (400), and `/c/` and `/user/` need a resolve
 * request the app does not make, so those fall through like any other unknown link.
 */
internal fun youtubeChannelDeepLinkRoute(url: String): String? =
    youtubeChannelBrowseId(url)
        ?.takeIf { it.startsWith("UC") }
        ?.let(::youtubeChannelRoute)

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

internal fun String.isLibraryOrSettingsRouteForMusicMiniPlayer(): Boolean =
    this == "library" ||
        this == "history" ||
        this == "playlists" ||
        this == "playlist" ||
        this == "downloads" ||
        this == "savedShorts" ||
        this == "recap" ||
        this == "recap_story" ||
        this == EQUALIZER_ROUTE ||
        startsWith("settings")

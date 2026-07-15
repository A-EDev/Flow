package io.github.aedev.flow.ui

import io.github.aedev.flow.data.local.DEFAULT_NAV_TAB_ORDER
import java.net.URI
import java.net.URLEncoder

internal data class NavigationVisibility(
    val home: Boolean = true,
    val shorts: Boolean = true,
    val music: Boolean = true,
    val search: Boolean = false,
    val categories: Boolean = false
)

internal fun visibleNavTabIndices(
    order: List<Int>,
    visibility: NavigationVisibility
): List<Int> {
    val enabled = buildSet {
        if (visibility.home) add(0)
        if (visibility.shorts) add(1)
        if (visibility.music) add(2)
        add(3)
        add(4)
        if (visibility.search) add(5)
        if (visibility.categories) add(6)
    }
    return (order + DEFAULT_NAV_TAB_ORDER).distinct().filter(enabled::contains)
}

internal fun resolveDefaultNavTabIndex(
    preferredIndex: Int,
    order: List<Int>,
    visibility: NavigationVisibility
): Int {
    val visible = visibleNavTabIndices(order, visibility)
    return preferredIndex.takeIf(visible::contains) ?: visible.first()
}

internal fun navRouteForIndex(index: Int): String = when (index) {
    0 -> "home"
    1 -> "shorts"
    2 -> "music"
    3 -> "subscriptions"
    4 -> "library"
    5 -> "search"
    6 -> "categories"
    else -> "home"
}

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

internal fun youtubeChannelRoute(channelIdOrHandle: String): String? =
    youtubeChannelUrl(channelIdOrHandle)?.let { channelUrl ->
        "channel?url=${URLEncoder.encode(channelUrl, Charsets.UTF_8.name())}"
    }

private fun normalizeYoutubeChannelUrl(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    val host = uri.host?.lowercase().orEmpty()
    if (host != "youtube.com" && !host.endsWith(".youtube.com")) return url

    val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
    if (segments.isEmpty()) return url

    val channelValue = when {
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

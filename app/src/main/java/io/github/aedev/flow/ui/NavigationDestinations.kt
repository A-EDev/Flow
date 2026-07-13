package io.github.aedev.flow.ui

import io.github.aedev.flow.data.local.DEFAULT_NAV_TAB_ORDER

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
        value.startsWith("http://") || value.startsWith("https://") -> value
        value.startsWith("UC") -> "https://www.youtube.com/channel/$value"
        value.startsWith("@") -> "https://www.youtube.com/$value"
        else -> "https://www.youtube.com/@$value"
    }
}

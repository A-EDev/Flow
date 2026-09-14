package io.github.aedev.flow.innertube.pages.channel

import io.github.aedev.flow.innertube.pages.arrayOrNull
import io.github.aedev.flow.innertube.pages.objectOrNull
import io.github.aedev.flow.innertube.pages.renderer.browseParams
import io.github.aedev.flow.innertube.pages.renderer.webCommandUrl
import io.github.aedev.flow.innertube.pages.stringOrNull
import io.github.aedev.flow.innertube.pages.youtubeText
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal fun JsonElement.toChannelTabs(): List<ChannelTabDescriptor> =
    objectOrNull()
        ?.get("contents")
        .objectOrNull()
        ?.get("twoColumnBrowseResultsRenderer")
        .objectOrNull()
        ?.get("tabs")
        .arrayOrNull()
        .orEmpty()
        .mapIndexedNotNull { index, tab ->
            val renderer =
                tab.objectOrNull()?.get("tabRenderer").objectOrNull()
                    ?: tab.objectOrNull()?.get("expandableTabRenderer").objectOrNull()
                    ?: return@mapIndexedNotNull null
            renderer.toTabDescriptor(isFirst = index == 0)
        }

private fun JsonObject.toTabDescriptor(isFirst: Boolean): ChannelTabDescriptor? {
    val endpoint = this["endpoint"].objectOrNull()
    val title = this["title"].youtubeText()?.trim()?.takeIf(String::isNotEmpty)
    val resolved = endpoint?.webCommandUrl().toChannelTabKind()
    // YouTube publishes the landing tab without a /featured suffix and sometimes without an
    // endpoint at all; it is always first.
    val kind = if (resolved == ChannelTabKind.Unknown && isFirst) ChannelTabKind.Home else resolved
    if (title == null && kind == ChannelTabKind.Unknown) return null
    return ChannelTabDescriptor(
        kind = kind,
        title = title.orEmpty(),
        params = endpoint?.browseParams(),
        selected = this["selected"].stringOrNull() == "true",
    )
}

/**
 * Resolved from the tab's own URL, never from its title — the title arrives in the request locale, so
 * matching on "Videos" collapses every non-English channel to one unrecognised tab.
 */
internal fun String?.toChannelTabKind(): ChannelTabKind {
    val segment =
        this
            ?.substringBefore('?')
            ?.trimEnd('/')
            ?.substringAfterLast('/')
            ?.lowercase()
            ?: return ChannelTabKind.Unknown
    // The channel root itself — "/@handle" or "/channel/UC…" — is the Home tab.
    if (segment.startsWith("@") || segment.startsWith("uc")) return ChannelTabKind.Home
    return when (segment) {
        "featured", "home" -> ChannelTabKind.Home
        "videos" -> ChannelTabKind.Videos
        "shorts" -> ChannelTabKind.Shorts
        "streams", "live" -> ChannelTabKind.Live
        "shows" -> ChannelTabKind.Shows
        "podcasts" -> ChannelTabKind.Podcasts
        "playlists" -> ChannelTabKind.Playlists
        "community", "posts" -> ChannelTabKind.Posts
        "releases" -> ChannelTabKind.Releases
        "store" -> ChannelTabKind.Store
        "search" -> ChannelTabKind.Search
        else -> ChannelTabKind.Unknown
    }
}

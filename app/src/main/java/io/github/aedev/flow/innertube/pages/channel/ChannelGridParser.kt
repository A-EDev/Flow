package io.github.aedev.flow.innertube.pages.channel

import io.github.aedev.flow.innertube.pages.channelItemContinuation
import io.github.aedev.flow.innertube.pages.channelSortOptions
import io.github.aedev.flow.innertube.pages.gridItemLists
import io.github.aedev.flow.innertube.pages.stringOrNull
import io.github.aedev.flow.innertube.pages.youtubeText
import kotlinx.serialization.json.JsonElement

/**
 * A grid tab, in any of the four shapes it arrives in — initial browse, sort switch, page append and
 * the older `richGridContinuation` — because [gridItemLists] already enumerates all four.
 *
 * Items come only from the grid's own lists, never from a walk of the whole response: the header
 * carries carousels of its own, and a document-order search picks those up as channel content.
 */
internal fun JsonElement.toChannelTabContent(
    kind: ChannelTabKind,
    fallbackOwner: ChannelOwner = ChannelOwner(),
): ChannelTabContent {
    val owner = resolveOwner(fallbackOwner)
    return ChannelTabContent(
        kind = kind,
        items =
            gridItemLists()
                .flatMap { list -> list.mapNotNull { it.toChannelItem(owner) } }
                .distinctBy { it.distinctKey() },
        filters = channelSortOptions(),
        continuation = channelItemContinuation(),
        owner = owner,
    )
}

/** Only the first browse carries the header; a continuation is anonymous, so the caller's owner wins. */
internal fun JsonElement.resolveOwner(fallback: ChannelOwner): ChannelOwner {
    val metadata = findRenderers("channelMetadataRenderer")["channelMetadataRenderer"]
    return ChannelOwner(
        id =
            metadata?.get("externalChannelId").stringOrNull()
                ?: metadata?.get("externalId").stringOrNull()
                ?: fallback.id,
        name = metadata?.get("title").youtubeText()?.takeIf(String::isNotBlank) ?: fallback.name,
        avatarUrl = metadata?.get("avatar").largestImageUrl() ?: fallback.avatarUrl,
    )
}

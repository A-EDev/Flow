package io.github.aedev.flow.innertube.pages.renderer

import io.github.aedev.flow.innertube.pages.arrayOrNull
import io.github.aedev.flow.innertube.pages.objectOrNull
import io.github.aedev.flow.innertube.pages.stringOrNull
import io.github.aedev.flow.innertube.pages.youtubeText
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The Home tab's shelves, in the order the channel arranged them.
 *
 * A shelf that parses to nothing is dropped here rather than rendered empty — the screen only ever
 * receives sections that have something in them.
 */
internal fun JsonElement.toFeedShelves(owner: FeedItemOwner): List<FeedShelf> {
    val sections = mutableListOf<FeedShelf>()
    objectOrNull()
        ?.get("contents")
        .arrayOrNull()
        .orEmpty()
        .forEach { entry ->
            val holders =
                entry
                    .objectOrNull()
                    ?.get("itemSectionRenderer")
                    .objectOrNull()
                    ?.get("contents")
                    .arrayOrNull()
                    ?: listOfNotNull(entry)
            holders.forEach { holder ->
                holder.objectOrNull()?.toSection(owner, sections.size)?.let(sections::add)
            }
        }
    return sections
}

private fun JsonObject.toSection(
    owner: FeedItemOwner,
    index: Int,
): FeedShelf? {
    this["channelVideoPlayerRenderer"].objectOrNull()?.let { trailer ->
        val item = FEED_ITEM_PARSERS.getValue("channelVideoPlayerRenderer").parse(trailer, owner) ?: return null
        return FeedShelf(
            id = "trailer",
            title = null,
            style = FeedShelfStyle.Trailer,
            items = listOf(item),
        )
    }

    val shelf =
        this["shelfRenderer"].objectOrNull()
            ?: this["reelShelfRenderer"].objectOrNull()
            ?: return null
    val items =
        shelf.shelfItems().mapNotNull { it.toFeedItem(owner) }.distinctBy { it.distinctKey() }
    if (items.isEmpty()) return null

    val title = shelf["title"].youtubeText()?.takeIf(String::isNotBlank)
    return FeedShelf(
        // Position-qualified: a channel may publish two shelves under one title, and a duplicate key
        // crashes the lazy list that renders them.
        id = "shelf:$index:${title.orEmpty()}",
        title = title,
        style = FeedShelfStyle.Carousel,
        items = items,
        moreParams = shelf["endpoint"].objectOrNull()?.browseParams(),
        morePlaylistId =
            shelf["endpoint"]
                .objectOrNull()
                ?.get("browseEndpoint")
                .objectOrNull()
                ?.get("browseId")
                .stringOrNull()
                ?.takeIf { it.startsWith("VL") }
                ?.removePrefix("VL"),
    )
}

private fun JsonObject.shelfItems(): List<JsonElement> =
    this["items"].arrayOrNull()
        ?: this["content"]
            .objectOrNull()
            ?.let { content ->
                content["horizontalListRenderer"].objectOrNull()?.get("items").arrayOrNull()
                    ?: content["expandedShelfContentsRenderer"].objectOrNull()?.get("items").arrayOrNull()
                    ?: content["gridRenderer"].objectOrNull()?.get("items").arrayOrNull()
            }
        ?: emptyList()

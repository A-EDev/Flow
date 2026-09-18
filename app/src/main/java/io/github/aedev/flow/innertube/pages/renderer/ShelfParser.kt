package io.github.aedev.flow.innertube.pages.renderer

import io.github.aedev.flow.innertube.pages.arrayOrNull
import io.github.aedev.flow.innertube.pages.objectOrNull
import io.github.aedev.flow.innertube.pages.stringOrNull
import io.github.aedev.flow.innertube.pages.youtubeText
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The shelves of a shelf page, in the order it arranged them — a channel's Home tab, or one of the
 * explore destinations, which wrap each shelf in a `richSectionRenderer` instead.
 *
 * A shelf that parses to nothing is dropped here rather than rendered empty — the screen only ever
 * receives sections that have something in them.
 */
internal fun JsonElement.toFeedShelves(owner: FeedItemOwner): List<FeedShelf> = feedShelfSequence(owner).toList()

/**
 * The same shelves, mapped one at a time. An explore destination arranges hundreds of items across
 * a dozen shelves, and a caller that paints as it goes should not pay for all of them to show the
 * first one.
 */
internal fun JsonElement.feedShelfSequence(owner: FeedItemOwner): Sequence<FeedShelf> =
    sequence {
        var index = 0
        objectOrNull()
            ?.get("contents")
            .arrayOrNull()
            .orEmpty()
            .forEach { entry ->
                entry.shelfHolders().forEach { holder ->
                    holder.objectOrNull()?.toFeedShelf(owner, index)?.let {
                        yield(it)
                        index++
                    }
                }
            }
    }

/** One list entry can hold a shelf directly, or wrap one (or several) in a section container. */
private fun JsonElement.shelfHolders(): List<JsonElement> {
    val node = objectOrNull() ?: return listOf(this)
    node["itemSectionRenderer"]
        .objectOrNull()
        ?.get("contents")
        .arrayOrNull()
        ?.let { return it }
    node["richSectionRenderer"]
        .objectOrNull()
        ?.get("content")
        ?.let { return listOf(it) }
    return listOf(this)
}

/**
 * One shelf, whatever container it arrived in. Search and the channel Home tab both call this.
 *
 * [index] position-qualifies the id: one search response carries three shelves all titled "Shorts",
 * and a duplicate key crashes the lazy list that renders them.
 */
internal fun JsonObject.toFeedShelf(
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

    this["gridShelfViewModel"].objectOrNull()?.let { return it.toGridShelf(owner, index) }

    val shelf =
        this["shelfRenderer"].objectOrNull()
            ?: this["reelShelfRenderer"].objectOrNull()
            ?: this["richShelfRenderer"].objectOrNull()
            ?: return null
    val (style, entries) = shelf.shelfItems()
    val items = entries.mapNotNull { it.toFeedItem(owner) }.distinctBy { it.distinctKey() }
    if (items.isEmpty()) return null

    val title = shelf["title"].youtubeText()?.takeIf(String::isNotBlank)
    return FeedShelf(
        id = "shelf:$index:${title.orEmpty()}",
        title = title,
        style = style,
        items = items,
        collapsedItemCount = shelf.collapsedItemCount(),
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

private fun JsonObject.toGridShelf(
    owner: FeedItemOwner,
    index: Int,
): FeedShelf? {
    val items =
        this["contents"]
            .arrayOrNull()
            .orEmpty()
            .mapNotNull { it.toFeedItem(owner) }
            .distinctBy { it.distinctKey() }
    if (items.isEmpty()) return null
    val title =
        this["header"]
            .objectOrNull()
            ?.get("sectionHeaderViewModel")
            .objectOrNull()
            ?.get("headline")
            .youtubeText()
            ?.takeIf(String::isNotBlank)
    return FeedShelf(
        id = "grid:$index:${title.orEmpty()}",
        title = title,
        style = FeedShelfStyle.Grid,
        items = items,
        collapsedItemCount = this["minCollapsedItemCount"].stringOrNull()?.toIntOrNull(),
    )
}

/**
 * Only the two containers the channel never produced carry a style of their own; everything the
 * channel already rendered stays a carousel.
 */
private fun JsonObject.shelfItems(): Pair<FeedShelfStyle, List<JsonElement>> {
    this["items"].arrayOrNull()?.let { return FeedShelfStyle.Carousel to it }
    this["contents"].arrayOrNull()?.let { return FeedShelfStyle.Carousel to it }
    val content = this["content"].objectOrNull() ?: return FeedShelfStyle.Carousel to emptyList()
    content["verticalListRenderer"].objectOrNull()?.get("items").arrayOrNull()?.let {
        return FeedShelfStyle.List to it
    }
    val carousel =
        content["horizontalListRenderer"].objectOrNull()?.get("items").arrayOrNull()
            ?: content["expandedShelfContentsRenderer"].objectOrNull()?.get("items").arrayOrNull()
            ?: content["gridRenderer"].objectOrNull()?.get("items").arrayOrNull()
    return FeedShelfStyle.Carousel to carousel.orEmpty()
}

private fun JsonObject.collapsedItemCount(): Int? =
    this["content"]
        .objectOrNull()
        ?.get("verticalListRenderer")
        .objectOrNull()
        ?.get("collapsedItemCount")
        .stringOrNull()
        ?.toIntOrNull()

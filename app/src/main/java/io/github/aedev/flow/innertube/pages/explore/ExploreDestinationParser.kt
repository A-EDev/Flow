package io.github.aedev.flow.innertube.pages.explore

import io.github.aedev.flow.innertube.pages.arrayOrNull
import io.github.aedev.flow.innertube.pages.objectOrNull
import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import io.github.aedev.flow.innertube.pages.renderer.FeedItemOwner
import io.github.aedev.flow.innertube.pages.renderer.FeedShelf
import io.github.aedev.flow.innertube.pages.renderer.browseParams
import io.github.aedev.flow.innertube.pages.renderer.feedShelfSequence
import io.github.aedev.flow.innertube.pages.stringOrNull
import io.github.aedev.flow.innertube.pages.youtubeText
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * A destination landing page with none of its shelves read yet — the title and the category tabs,
 * which is everything the screen can show before an item has been mapped.
 */
internal fun JsonElement.toExploreDestinationShell(owner: FeedItemOwner = FeedItemOwner()): ExploreDestinationPage =
    ExploreDestinationPage(title = pageTitle(), tabs = exploreTabs(), owner = owner)

/**
 * The page's shelves, mapped one at a time so a caller can paint the first without waiting on the
 * rest.
 *
 * They are read out of the selected tab's own container rather than by walking the response: a
 * destination runs to megabytes and its header carries carousels a document-order search would pick
 * up as content. Most destinations arrive as a `richGridRenderer`; Gaming still uses the older
 * `sectionListRenderer`, so both are accepted.
 */
internal fun JsonElement.exploreShelves(owner: FeedItemOwner = FeedItemOwner()): Sequence<FeedShelf> =
    selectedTabContainer()
        ?.feedShelfSequence(owner)
        ?.map(FeedShelf::asScheduledStreams)
        .orEmpty()

/**
 * These destinations serve streams, so a row still ahead is a scheduled broadcast rather than a
 * premiere, and its line should count down the way the player's does. The shared item parser cannot
 * tell the two apart — YouTube's own label is localised — so the distinction is made here, where the
 * surface is known, and search and the channel tabs keep the wording they already had.
 */
private fun FeedShelf.asScheduledStreams(): FeedShelf =
    copy(
        items =
            items.map { item ->
                when {
                    item !is FeedItem.VideoItem -> item
                    !item.video.isUpcoming -> item
                    else -> FeedItem.VideoItem(item.video.copy(isScheduledLive = true))
                }
            },
    )

private fun JsonElement.pageTitle(): String? =
    objectOrNull()
        ?.get("header")
        .objectOrNull()
        ?.get("pageHeaderRenderer")
        .objectOrNull()
        ?.get("pageTitle")
        .stringOrNull()
        ?.takeIf(String::isNotBlank)

private fun JsonElement.exploreTabs(): List<ExploreTab> =
    browseTabs().mapNotNull { renderer ->
        val title = renderer["title"].youtubeText()?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
        val endpoint = renderer["endpoint"].objectOrNull()
        ExploreTab(
            title = title,
            browseId =
                endpoint
                    ?.get("browseEndpoint")
                    .objectOrNull()
                    ?.get("browseId")
                    .stringOrNull(),
            params = endpoint?.browseParams(),
            selected = renderer["selected"].stringOrNull() == "true",
        )
    }

/** The selected tab's content, falling back to the first — News marks a tab selected, Live does not. */
private fun JsonElement.selectedTabContainer(): JsonElement? {
    val tabs = browseTabs()
    val renderer = tabs.firstOrNull { it["selected"].stringOrNull() == "true" } ?: tabs.firstOrNull() ?: return null
    val content = renderer["content"].objectOrNull() ?: return null
    return content["richGridRenderer"] ?: content["sectionListRenderer"]
}

private fun JsonElement.browseTabs(): List<JsonObject> =
    objectOrNull()
        ?.get("contents")
        .objectOrNull()
        ?.get("twoColumnBrowseResultsRenderer")
        .objectOrNull()
        ?.get("tabs")
        .arrayOrNull()
        .orEmpty()
        .mapNotNull { tab ->
            tab.objectOrNull()?.get("tabRenderer").objectOrNull()
                ?: tab.objectOrNull()?.get("expandableTabRenderer").objectOrNull()
        }

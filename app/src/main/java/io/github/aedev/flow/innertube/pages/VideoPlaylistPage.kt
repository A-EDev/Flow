package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import io.github.aedev.flow.innertube.pages.renderer.FeedItemOwner
import io.github.aedev.flow.innertube.pages.renderer.findRenderers
import io.github.aedev.flow.innertube.pages.renderer.forEachObject
import io.github.aedev.flow.innertube.pages.renderer.largestImageUrl
import io.github.aedev.flow.innertube.pages.renderer.toFeedItem
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** One page of a YouTube playlist from the WEB `VL<id>` browse, and the token for the next. */
data class VideoPlaylistPage(
    val title: String? = null,
    val ownerName: String? = null,
    val ownerId: String? = null,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val videos: List<Video> = emptyList(),
    val continuation: String? = null,
)

/**
 * Reads the first page (header plus items) or a continuation (items only). Items arrive as video
 * lockups carrying their own channel; unavailable entries are simply absent from the response.
 */
internal fun JsonElement.toVideoPlaylistPage(): VideoPlaylistPage {
    val items = playlistItems()
    val header = findRenderers("pageHeaderRenderer", "pageHeaderViewModel", "playlistSidebarPrimaryInfoRenderer")
    val pageHeader = header["pageHeaderViewModel"]
    val sidebar = header["playlistSidebarPrimaryInfoRenderer"]
    val owner = pageHeader?.headerOwner()
    return VideoPlaylistPage(
        title =
            (
                header["pageHeaderRenderer"]?.get("pageTitle").stringOrNull()
                    ?: pageHeader
                        ?.get("title")
                        .objectOrNull()
                        ?.get("dynamicTextViewModel")
                        .objectOrNull()
                        ?.get("text")
                        .youtubeText()
            )?.takeIf(String::isNotBlank),
        ownerName = owner?.name,
        ownerId = owner?.id,
        description =
            pageHeader
                ?.get("description")
                .objectOrNull()
                ?.get("descriptionPreviewViewModel")
                .objectOrNull()
                ?.get("description")
                .youtubeText()
                ?: sidebar?.get("description").youtubeText(),
        thumbnailUrl =
            sidebar
                ?.get("thumbnailRenderer")
                .objectOrNull()
                ?.get("playlistVideoThumbnailRenderer")
                .objectOrNull()
                ?.get("thumbnail")
                .largestImageUrl(),
        videos =
            items
                .mapNotNull { it.toFeedItem(FeedItemOwner()) }
                .filterIsInstance<FeedItem.VideoItem>()
                .map { it.video }
                .distinctBy { it.id },
        continuation = items.lastOrNull().continuationToken(),
    )
}

/** Items of the first page's list, or of a continuation's append action. */
private fun JsonElement.playlistItems(): List<JsonElement> {
    val root = objectOrNull() ?: return emptyList()
    root["onResponseReceivedActions"].arrayOrNull()?.let { actions ->
        return actions.flatMap { action ->
            action
                .objectOrNull()
                ?.get("appendContinuationItemsAction")
                .objectOrNull()
                ?.get("continuationItems")
                .arrayOrNull()
                .orEmpty()
        }
    }
    var list: List<JsonElement> = emptyList()
    root["contents"].forEachObject { node ->
        if (list.isNotEmpty()) return@forEachObject
        val contents = node["itemSectionRenderer"].objectOrNull()?.get("contents").arrayOrNull()
        if (contents?.any { it.objectOrNull()?.containsKey("lockupViewModel") == true } == true) list = contents
    }
    return list
}

private fun JsonElement?.continuationToken(): String? =
    objectOrNull()
        ?.get("continuationItemRenderer")
        .objectOrNull()
        ?.get("continuationEndpoint")
        .objectOrNull()
        ?.get("continuationCommand")
        .objectOrNull()
        ?.get("token")
        .stringOrNull()
        ?.takeIf(String::isNotBlank)

/** "by Name" in the header's avatar stack, linked to the owner's channel. */
private fun JsonObject.headerOwner(): FeedItemOwner? {
    var owner: FeedItemOwner? = null
    this["metadata"].forEachObject { node ->
        if (owner != null) return@forEachObject
        val text = node["avatarStackViewModel"].objectOrNull()?.get("text").objectOrNull() ?: return@forEachObject
        val name =
            text
                .youtubeText()
                ?.removePrefix("by ")
                ?.trim()
                ?.takeIf(String::isNotBlank) ?: return@forEachObject
        var channelId = ""
        text.forEachObject { run ->
            if (channelId.isEmpty()) {
                run["browseEndpoint"]
                    .objectOrNull()
                    ?.get("browseId")
                    .stringOrNull()
                    ?.let { channelId = it }
            }
        }
        owner = FeedItemOwner(id = channelId, name = name)
    }
    return owner
}

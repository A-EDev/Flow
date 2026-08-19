package io.github.aedev.flow.innertube.pages

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Lightweight shorts extracted from a web-client response. Shorts live in a `reelShelfRenderer`
 * that the long-form search extractor ignores, so we parse them here.
 *
 * Also used by [ChannelShortsPage] — a channel's Shorts tab ships the same `shortsLockupViewModel`
 * as search does, so the two share one parser rather than each carrying its own copy.
 */
data class SearchShortItem(
    val id: String,
    val title: String,
    val viewCount: Long,
    /** The frame YouTube picked. Blank when the renderer carried none; callers derive one from [id]. */
    val thumbnailUrl: String = "",
)

fun JsonObject.toSearchShorts(): List<SearchShortItem> {
    val shorts = mutableListOf<SearchShortItem>()
    collectSearchShorts(this, shorts)
    return shorts.distinctBy { it.id }
}

private fun collectSearchShorts(
    element: JsonElement,
    shorts: MutableList<SearchShortItem>,
) {
    when (element) {
        is JsonArray -> {
            element.forEach { collectSearchShorts(it, shorts) }
        }

        is JsonObject -> {
            element.parseReelItem()?.let(shorts::add)
            element.values.forEach { collectSearchShorts(it, shorts) }
        }

        else -> {
            Unit
        }
    }
}

private fun JsonObject.parseReelItem(): SearchShortItem? {
    this["reelItemRenderer"].objectOrNull()?.let { r ->
        val id = r["videoId"].stringOrNull() ?: return null
        return SearchShortItem(
            id,
            r["headline"].youtubeText() ?: "",
            parseYouTubeViewCount(r["viewCountText"].youtubeText()),
        )
    }
    this["shortsLockupViewModel"].objectOrNull()?.let { vm ->
        val url =
            vm["onTap"]
                .objectOrNull()
                ?.get("innertubeCommand")
                .objectOrNull()
                ?.get("commandMetadata")
                .objectOrNull()
                ?.get("webCommandMetadata")
                .objectOrNull()
                ?.get("url")
                .stringOrNull()
                .orEmpty()
        val id = url.substringAfter("/shorts/", "").substringBefore("?").takeIf { it.isNotBlank() } ?: return null
        val overlay = vm["overlayMetadata"].objectOrNull()
        val title =
            overlay
                ?.get("primaryText")
                .objectOrNull()
                ?.get("content")
                .stringOrNull() ?: ""
        val views =
            overlay
                ?.get("secondaryText")
                .objectOrNull()
                ?.get("content")
                .stringOrNull()
        return SearchShortItem(id, title, parseYouTubeViewCount(views), vm.lockupThumbnail())
    }
    return null
}

/** Widest source wins: the lockup lists a portrait frame at a couple of sizes. */
private fun JsonObject.lockupThumbnail(): String =
    this["thumbnailViewModel"]
        .objectOrNull()
        ?.get("thumbnailViewModel")
        .objectOrNull()
        ?.get("image")
        .objectOrNull()
        ?.get("sources")
        .arrayOrNull()
        ?.mapNotNull { source ->
            val value = source.objectOrNull() ?: return@mapNotNull null
            val url = value["url"].stringOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            url to (value["width"].stringOrNull()?.toIntOrNull() ?: 0)
        }?.maxByOrNull { it.second }
        ?.first
        .orEmpty()

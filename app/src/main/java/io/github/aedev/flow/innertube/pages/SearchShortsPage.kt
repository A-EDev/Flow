package io.github.aedev.flow.innertube.pages

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Lightweight shorts extracted from a web-client search response. Shorts live in a
 * `reelShelfRenderer` that the long-form search extractor ignores, so we parse them here.
 */
data class SearchShortItem(
    val id: String,
    val title: String,
    val viewCount: Long,
)

fun JsonObject.toSearchShorts(): List<SearchShortItem> {
    val shorts = mutableListOf<SearchShortItem>()
    collectSearchShorts(this, shorts)
    return shorts.distinctBy { it.id }
}

private fun collectSearchShorts(element: JsonElement, shorts: MutableList<SearchShortItem>) {
    when (element) {
        is JsonArray -> element.forEach { collectSearchShorts(it, shorts) }
        is JsonObject -> {
            element.parseReelItem()?.let(shorts::add)
            element.values.forEach { collectSearchShorts(it, shorts) }
        }
        else -> Unit
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
        val url = vm["onTap"].objectOrNull()?.get("innertubeCommand").objectOrNull()
            ?.get("commandMetadata").objectOrNull()?.get("webCommandMetadata").objectOrNull()
            ?.get("url").stringOrNull().orEmpty()
        val id = url.substringAfter("/shorts/", "").substringBefore("?").takeIf { it.isNotBlank() } ?: return null
        val overlay = vm["overlayMetadata"].objectOrNull()
        val title = overlay?.get("primaryText").objectOrNull()?.get("content").stringOrNull() ?: ""
        val views = overlay?.get("secondaryText").objectOrNull()?.get("content").stringOrNull()
        return SearchShortItem(id, title, parseYouTubeViewCount(views))
    }
    return null
}

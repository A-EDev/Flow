package io.github.aedev.flow.innertube.pages

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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
    val sections = this["contents"].obj()?.get("twoColumnSearchResultsRenderer").obj()
        ?.get("primaryContents").obj()?.get("sectionListRenderer").obj()
        ?.get("contents").arr() ?: return emptyList()

    val shorts = mutableListOf<SearchShortItem>()
    for (section in sections) {
        val contents = section.obj()?.get("itemSectionRenderer").obj()?.get("contents").arr() ?: continue
        for (content in contents) {
            val c = content.obj() ?: continue
            // Current web layout: a grid of shortsLockupViewModel.
            c["gridShelfViewModel"].obj()?.get("contents").arr()
                ?.forEach { it.obj()?.parseReelItem()?.let(shorts::add) }
            // Older layout: reelShelfRenderer with reel/shorts items.
            c["reelShelfRenderer"].obj()?.get("items").arr()
                ?.forEach { it.obj()?.parseReelItem()?.let(shorts::add) }
        }
    }
    return shorts.distinctBy { it.id }
}

private fun JsonObject.parseReelItem(): SearchShortItem? {
    this["reelItemRenderer"].obj()?.let { r ->
        val id = r["videoId"].str() ?: return null
        return SearchShortItem(id, r["headline"].text() ?: "", parseAbbreviatedViews(r["viewCountText"].text()))
    }
    this["shortsLockupViewModel"].obj()?.let { vm ->
        val url = vm["onTap"].obj()?.get("innertubeCommand").obj()?.get("commandMetadata").obj()
            ?.get("webCommandMetadata").obj()?.get("url").str().orEmpty()
        val id = url.substringAfter("/shorts/", "").substringBefore("?").takeIf { it.isNotBlank() } ?: return null
        val overlay = vm["overlayMetadata"].obj()
        val title = overlay?.get("primaryText").obj()?.get("content").str() ?: ""
        val views = overlay?.get("secondaryText").obj()?.get("content").str()
        return SearchShortItem(id, title, parseAbbreviatedViews(views))
    }
    return null
}

private fun parseAbbreviatedViews(text: String?): Long {
    if (text.isNullOrBlank()) return 0
    val match = Regex("""([\d.,]+)\s*([KkMmBb])?""").find(text) ?: return 0
    val number = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return 0
    val mult = when (match.groupValues[2].lowercase()) {
        "k" -> 1_000.0
        "m" -> 1_000_000.0
        "b" -> 1_000_000_000.0
        else -> 1.0
    }
    return (number * mult).toLong()
}

// ── null-safe json helpers ──────────────────────────────────────────────────
private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull

/** Reads a YouTube text node in either `simpleText` or `runs[].text` form. */
private fun JsonElement?.text(): String? {
    val o = this.obj() ?: return null
    o["simpleText"].str()?.let { return it }
    return o["runs"].arr()?.joinToString("") { it.obj()?.get("text").str().orEmpty() }?.takeIf { it.isNotBlank() }
}

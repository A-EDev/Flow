package io.github.aedev.flow.innertube.pages

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * One entry of a channel tab's sort bar, exactly as YouTube sent it.
 *
 * The label already arrives in the request locale, and the token is the browse continuation that
 * returns the tab re-sorted. Nothing is hardcoded, so the app offers whatever sorts a channel
 * actually has — today Latest, Popular and Oldest on the Videos, Shorts and Live tabs.
 *
 * Sorting server-side is not cosmetic. Sorting a locally accumulated list can only order the pages
 * already fetched, so "Oldest" on a large channel really means "oldest of the first few hundred".
 */
data class ChannelSortOption(
    val label: String,
    val token: String,
    val selected: Boolean,
)

/** The `chipBarViewModel` above a channel tab's grid, wherever the response happens to nest it. */
internal fun JsonElement.channelSortOptions(): List<ChannelSortOption> {
    val bar = findFirstRenderer("chipBarViewModel")?.objectOrNull() ?: return emptyList()
    return bar["chips"]
        .arrayOrNull()
        .orEmpty()
        .mapNotNull { chip ->
            val model = chip.objectOrNull()?.get("chipViewModel").objectOrNull() ?: return@mapNotNull null
            val label = model["text"].youtubeText()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val token =
                model["tapCommand"]
                    .objectOrNull()
                    ?.get("innertubeCommand")
                    .objectOrNull()
                    ?.get("continuationCommand")
                    .objectOrNull()
                    ?.get("token")
                    .stringOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
            ChannelSortOption(
                label = label,
                token = token,
                selected = model["selected"].stringOrNull() == "true",
            )
        }
}

/**
 * The token for the *next page of items*.
 *
 * Scoped to the grid's own item list rather than searched for across the response: a channel page
 * carries other `continuationItemRenderer`s — the header's description and attribution each have
 * one — and a plain document-order search only finds the right one by accident of key ordering.
 */
internal fun JsonElement.channelItemContinuation(): String? =
    gridItemLists()
        .asSequence()
        .flatMap { it.asSequence() }
        .mapNotNull { item ->
            item
                .objectOrNull()
                ?.get("continuationItemRenderer")
                .objectOrNull()
                ?.get("continuationEndpoint")
                .objectOrNull()
                ?.get("continuationCommand")
                .objectOrNull()
                ?.get("token")
                .stringOrNull()
                ?.takeIf { it.isNotBlank() }
        }.firstOrNull()

/**
 * Every array that holds grid items, across the four shapes a channel tab arrives in: an initial
 * browse, a sort switch, a page append, and the older `richGridContinuation`.
 */
private fun JsonElement.gridItemLists(): List<JsonArray> {
    val lists = mutableListOf<JsonArray>()

    fun collect(node: JsonElement) {
        when (node) {
            is JsonObject -> {
                ITEM_LIST_HOLDERS.forEach { (holder, field) ->
                    node[holder]
                        .objectOrNull()
                        ?.get(field)
                        .arrayOrNull()
                        ?.let(lists::add)
                }
                node.values.forEach(::collect)
            }

            is JsonArray -> {
                node.forEach(::collect)
            }

            else -> {
                Unit
            }
        }
    }

    collect(this)
    return lists
}

private val ITEM_LIST_HOLDERS =
    listOf(
        "reloadContinuationItemsCommand" to "continuationItems",
        "appendContinuationItemsAction" to "continuationItems",
        "richGridContinuation" to "contents",
        "richGridRenderer" to "contents",
    )

/**
 * Depth-first search for a renderer by key. YouTube nests a channel grid differently on an initial
 * browse (`twoColumnBrowseResultsRenderer`), a sort switch (`reloadContinuationItemsCommand`) and a
 * page append (`appendContinuationItemsAction`), so walking beats spelling out three paths.
 */
internal fun JsonElement.findFirstRenderer(key: String): JsonElement? {
    when (this) {
        is JsonObject -> {
            this[key]?.let { return it }
            values.forEach { child -> child.findFirstRenderer(key)?.let { return it } }
        }

        is JsonArray -> {
            forEach { child -> child.findFirstRenderer(key)?.let { return it } }
        }

        else -> {
            Unit
        }
    }
    return null
}

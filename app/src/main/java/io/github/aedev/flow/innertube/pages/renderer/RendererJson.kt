package io.github.aedev.flow.innertube.pages.renderer

import io.github.aedev.flow.innertube.pages.bestThumbnailUrl
import io.github.aedev.flow.innertube.pages.normalizeImageUrl
import io.github.aedev.flow.innertube.pages.objectOrNull
import io.github.aedev.flow.innertube.pages.stringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal fun JsonElement?.largestImageUrl(): String? = bestThumbnailUrl()?.takeIf(String::isNotBlank)?.let(::normalizeImageUrl)

/**
 * First occurrence of each key, in one walk. A channel landing response runs to megabytes, so the
 * header's four renderers are collected together rather than by four separate searches.
 */
internal fun JsonElement?.findRenderers(vararg keys: String): Map<String, JsonObject> {
    val found = mutableMapOf<String, JsonObject>()
    forEachObject { node ->
        if (found.size == keys.size) return@forEachObject
        keys.forEach { key ->
            if (key !in found) node[key].objectOrNull()?.let { found[key] = it }
        }
    }
    return found
}

/** Depth-first, parents before children. */
internal fun JsonElement?.forEachObject(action: (JsonObject) -> Unit) {
    when (this) {
        is JsonObject -> {
            action(this)
            values.forEach { it.forEachObject(action) }
        }

        is JsonArray -> {
            forEach { it.forEachObject(action) }
        }

        else -> {
            Unit
        }
    }
}

internal fun JsonObject.browseParams(): String? =
    this["browseEndpoint"]
        .objectOrNull()
        ?.get("params")
        .stringOrNull()
        ?.takeIf(String::isNotBlank)

internal fun JsonObject.webCommandUrl(): String? =
    this["commandMetadata"]
        .objectOrNull()
        ?.get("webCommandMetadata")
        .objectOrNull()
        ?.get("url")
        .stringOrNull()
        ?.takeIf(String::isNotBlank)

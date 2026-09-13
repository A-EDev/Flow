package io.github.aedev.flow.innertube.pages.channel

import io.github.aedev.flow.innertube.pages.arrayOrNull
import io.github.aedev.flow.innertube.pages.objectOrNull
import io.github.aedev.flow.innertube.pages.stringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Renderers key their image lists `thumbnails`, view models key them `sources`, and an entry may
 * carry `url` or `uri`. One reader so no parser has to know which generation it is looking at.
 */
internal fun JsonElement?.largestImageUrl(): String? {
    val entries =
        objectOrNull()?.get("thumbnails").arrayOrNull()
            ?: objectOrNull()?.get("sources").arrayOrNull()
            ?: arrayOrNull()
            ?: return null
    return entries
        .maxByOrNull { entry ->
            val value = entry.objectOrNull()
            value?.get("width").dimension().toLong() * value?.get("height").dimension().toLong()
        }?.objectOrNull()
        ?.let { it["url"].stringOrNull() ?: it["uri"].stringOrNull() }
        ?.takeIf(String::isNotBlank)
        ?.let(::normalizeImageUrl)
}

/** A protocol-relative URL resolves as a file path and fails silently in Coil. */
internal fun normalizeImageUrl(url: String): String = if (url.startsWith("//")) "https:$url" else url

private fun JsonElement?.dimension(): Int = (this as? JsonPrimitive)?.intOrNull ?: 0

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

internal fun JsonObject.continuationToken(): String? =
    this["continuationEndpoint"]
        .objectOrNull()
        ?.get("continuationCommand")
        .objectOrNull()
        ?.get("token")
        .stringOrNull()
        ?.takeIf(String::isNotBlank)

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

package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.innertube.YouTube
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

internal fun JsonElement?.objectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonElement?.arrayOrNull(): JsonArray? = this as? JsonArray

internal fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

internal fun JsonElement?.booleanOrNull(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

/** Reads the primitive and structured text shapes used by YouTube renderers and entity payloads. */
internal fun JsonElement?.youtubeText(): String? {
    stringOrNull()?.let { return it }
    val value = objectOrNull() ?: return null
    value["simpleText"].stringOrNull()?.let { return it }
    value["content"].stringOrNull()?.let { return it }
    return value["runs"]
        .arrayOrNull()
        ?.joinToString("") { run ->
            val runValue = run.objectOrNull()
            runValue?.get("text").stringOrNull()
                ?: runValue?.get("content").stringOrNull().orEmpty()
        }?.takeIf { it.isNotBlank() }
}

/** A count in [hl]'s words, exact or abbreviated; 0 when the text carries none. */
internal fun parseYouTubeViewCount(
    text: String?,
    hl: String? = YouTube.locale.hl,
): Long = YouTubeCountParser.parse(text, hl) ?: 0L

internal fun normalizeImageUrl(url: String): String = if (url.startsWith("//")) "https:$url" else url

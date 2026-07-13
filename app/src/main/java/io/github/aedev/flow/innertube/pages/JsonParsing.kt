package io.github.aedev.flow.innertube.pages

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.schabi.newpipe.extractor.utils.Utils

internal fun JsonElement?.objectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonElement?.arrayOrNull(): JsonArray? = this as? JsonArray

internal fun JsonElement?.stringOrNull(): String? =
    (this as? JsonPrimitive)?.contentOrNull

/** Reads a YouTube text node in either `simpleText` or `runs[].text` form. */
internal fun JsonElement?.youtubeText(): String? {
    val value = objectOrNull() ?: return null
    value["simpleText"].stringOrNull()?.let { return it }
    return value["runs"].arrayOrNull()
        ?.joinToString("") { it.objectOrNull()?.get("text").stringOrNull().orEmpty() }
        ?.takeIf { it.isNotBlank() }
}

internal fun parseYouTubeViewCount(text: String?): Long {
    if (text.isNullOrBlank()) return 0L
    val hasAbbreviatedSuffix = Regex("""\d[\d.,]*\s*[KkMmBb]\b""").containsMatchIn(text)
    val exactDigits = Utils.removeNonDigitCharacters(text)
    if (!hasAbbreviatedSuffix && exactDigits.length >= 4) {
        exactDigits.toLongOrNull()?.let { return it }
    }
    return runCatching { Utils.mixedNumberWordToLong(text) }
        .getOrElse {
            val match = Regex("""([\d.,]+)\s*([KkMmBb])?""").find(text) ?: return 0L
            val number = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return 0L
            val multiplier = when (match.groupValues[2].lowercase()) {
                "k" -> 1_000.0
                "m" -> 1_000_000.0
                "b" -> 1_000_000_000.0
                else -> 1.0
            }
            (number * multiplier).toLong()
        }
}

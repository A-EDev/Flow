package io.github.aedev.flow.innertube.models.response

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One chapter of a video: where it starts, and the frame the chapter list shows for it. */
data class VideoChapter(
    val title: String,
    val startTimeSeconds: Int,
    val thumbnailUrl: String? = null,
)

/**
 * A video's chapters, read from the watch response.
 *
 * They arrive in an engagement panel keyed by [CHAPTERS_PANEL] rather than beside the description,
 * and each item carries its start as exact seconds in `onTap.watchEndpoint.startTimeSeconds`. The
 * "0:37" next to it is a display string in the viewer's own locale and is never parsed.
 *
 * Parsed defensively and by field name: chapters are decoration, so a shape change should drop them
 * rather than fail the watch page.
 */
object VideoChaptersParser {
    private const val CHAPTERS_PANEL = "engagement-panel-macro-markers-description-chapters"

    fun parse(watchNext: JsonElement?): List<VideoChapter> {
        val items = runCatching { findChapterItems(watchNext) }.getOrNull() ?: return emptyList()
        return items
            .mapNotNull { it.toChapter() }
            .sortedBy { it.startTimeSeconds }
    }

    private fun findChapterItems(watchNext: JsonElement?): List<JsonElement>? =
        watchNext
            ?.jsonObjectOrNull()
            ?.get("engagementPanels")
            ?.jsonArrayOrNull()
            ?.firstNotNullOfOrNull { panel ->
                val renderer =
                    panel
                        .jsonObjectOrNull()
                        ?.get("engagementPanelSectionListRenderer")
                        ?.jsonObjectOrNull()
                        ?.takeIf { it["panelIdentifier"]?.stringOrNull() == CHAPTERS_PANEL }
                renderer
                    ?.get("content")
                    ?.jsonObjectOrNull()
                    ?.get("macroMarkersListRenderer")
                    ?.jsonObjectOrNull()
                    ?.get("contents")
                    ?.jsonArrayOrNull()
            }

    private fun JsonElement.toChapter(): VideoChapter? {
        val item =
            jsonObjectOrNull()
                ?.get("macroMarkersListItemRenderer")
                ?.jsonObjectOrNull()
                ?: return null
        val title =
            item["title"]
                ?.jsonObjectOrNull()
                ?.textOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: return null
        val start =
            item["onTap"]
                ?.jsonObjectOrNull()
                ?.get("watchEndpoint")
                ?.jsonObjectOrNull()
                ?.get("startTimeSeconds")
                ?.intOrNull()
                ?: return null
        if (start < 0) return null
        return VideoChapter(
            title = title,
            startTimeSeconds = start,
            thumbnailUrl =
                item["thumbnail"]
                    ?.jsonObjectOrNull()
                    ?.get("thumbnails")
                    ?.jsonArrayOrNull()
                    ?.lastOrNull()
                    ?.jsonObjectOrNull()
                    ?.get("url")
                    ?.stringOrNull(),
        )
    }

    private fun Map<String, JsonElement>.textOrNull(): String? =
        this["simpleText"]?.stringOrNull()
            ?: this["runs"]
                ?.jsonArrayOrNull()
                ?.mapNotNull { it.jsonObjectOrNull()?.get("text")?.stringOrNull() }
                ?.joinToString("")
                ?.takeIf { it.isNotBlank() }

    private fun JsonElement.jsonObjectOrNull(): Map<String, JsonElement>? = runCatching { jsonObject }.getOrNull()

    private fun JsonElement.jsonArrayOrNull(): List<JsonElement>? = runCatching { jsonArray }.getOrNull()

    private fun JsonElement.stringOrNull(): String? = runCatching { jsonPrimitive.contentOrNull }.getOrNull()

    private fun JsonElement.intOrNull(): Int? =
        runCatching { jsonPrimitive.intOrNull }.getOrNull()
            ?: stringOrNull()?.toIntOrNull()
}

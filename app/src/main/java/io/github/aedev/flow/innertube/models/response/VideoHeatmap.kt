package io.github.aedev.flow.innertube.models.response

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One slice of the rewatch curve: how heavily [startMs] was replayed, on a 0..1 scale. */
data class HeatmapMarker(
    val startMs: Long,
    val durationMs: Long,
    val intensity: Float,
)

/** A stretch YouTube labels on the bar, normally the single "Most replayed" peak. */
data class HeatmapHighlight(
    val startMs: Long,
    val endMs: Long,
    val markerMs: Long,
    val label: String,
)

data class VideoHeatmap(
    val markers: List<HeatmapMarker>,
    val highlights: List<HeatmapHighlight>,
) {
    val isEmpty: Boolean get() = markers.isEmpty()
}

/**
 * The rewatch curve for a video, read from the watch response.
 *
 * It arrives as an entity mutation rather than a renderer, which is where it moved after the older
 * `heatmapRenderer` shape stopped being served. Parsed defensively and by field name rather than by
 * position: it is decoration, so a shape change should hide the graph, never fail the watch page.
 *
 * Absent on plenty of videos — anything too new or too quiet to have accumulated replay data, and
 * live streams — so an empty result is ordinary, not an error.
 */
object VideoHeatmapParser {
    private const val HEATMAP_MARKER_TYPE = "MARKER_TYPE_HEATMAP"

    fun parse(watchNext: JsonElement?): VideoHeatmap? {
        val markersList =
            runCatching { findHeatmapMarkersList(watchNext) }.getOrNull() ?: return null
        val markers =
            markersList["markers"]
                ?.jsonArrayOrNull()
                ?.mapNotNull { it.toMarker() }
                .orEmpty()
        if (markers.isEmpty()) return null
        val highlights =
            markersList["markersDecoration"]
                ?.jsonObjectOrNull()
                ?.get("timedMarkerDecorations")
                ?.jsonArrayOrNull()
                ?.mapNotNull { it.toHighlight() }
                .orEmpty()
        return VideoHeatmap(markers = markers, highlights = highlights)
    }

    private fun findHeatmapMarkersList(watchNext: JsonElement?): Map<String, JsonElement>? {
        val mutations =
            watchNext
                ?.jsonObjectOrNull()
                ?.get("frameworkUpdates")
                ?.jsonObjectOrNull()
                ?.get("entityBatchUpdate")
                ?.jsonObjectOrNull()
                ?.get("mutations")
                ?.jsonArrayOrNull()
                ?: return null
        return mutations.firstNotNullOfOrNull { mutation ->
            val markersList =
                mutation
                    .jsonObjectOrNull()
                    ?.get("payload")
                    ?.jsonObjectOrNull()
                    ?.get("macroMarkersListEntity")
                    ?.jsonObjectOrNull()
                    ?.get("markersList")
                    ?.jsonObjectOrNull()
            markersList?.takeIf { it["markerType"]?.stringOrNull() == HEATMAP_MARKER_TYPE }
        }
    }

    private fun JsonElement.toMarker(): HeatmapMarker? {
        val obj = jsonObjectOrNull() ?: return null
        // startMillis and durationMillis arrive as strings, intensityScoreNormalized as a number.
        val start = obj["startMillis"]?.longOrNull() ?: return null
        val duration = obj["durationMillis"]?.longOrNull() ?: return null
        val intensity = obj["intensityScoreNormalized"]?.floatOrNull() ?: return null
        if (duration <= 0L) return null
        return HeatmapMarker(
            startMs = start,
            durationMs = duration,
            intensity = intensity.coerceIn(0f, 1f),
        )
    }

    private fun JsonElement.toHighlight(): HeatmapHighlight? {
        val obj = jsonObjectOrNull() ?: return null
        val start = obj["visibleTimeRangeStartMillis"]?.longOrNull() ?: return null
        val end = obj["visibleTimeRangeEndMillis"]?.longOrNull() ?: return null
        if (end <= start) return null
        val label =
            obj["label"]
                ?.jsonObjectOrNull()
                ?.get("runs")
                ?.jsonArrayOrNull()
                ?.firstNotNullOfOrNull { it.jsonObjectOrNull()?.get("text")?.stringOrNull() }
                ?.takeIf { it.isNotBlank() }
                ?: return null
        return HeatmapHighlight(
            startMs = start,
            endMs = end,
            markerMs = obj["decorationTimeMillis"]?.longOrNull() ?: ((start + end) / 2),
            label = label,
        )
    }

    private fun JsonElement.jsonObjectOrNull(): Map<String, JsonElement>? = runCatching { jsonObject }.getOrNull()

    private fun JsonElement.jsonArrayOrNull(): List<JsonElement>? = runCatching { jsonArray }.getOrNull()

    private fun JsonElement.stringOrNull(): String? = runCatching { jsonPrimitive.contentOrNull }.getOrNull()

    private fun JsonElement.longOrNull(): Long? = stringOrNull()?.toLongOrNull()

    private fun JsonElement.floatOrNull(): Float? =
        runCatching { jsonPrimitive.doubleOrNull?.toFloat() }.getOrNull()
            ?: stringOrNull()?.toFloatOrNull()
}

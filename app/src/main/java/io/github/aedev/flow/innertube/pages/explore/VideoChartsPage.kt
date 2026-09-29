package io.github.aedev.flow.innertube.pages.explore

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.pages.arrayOrNull
import io.github.aedev.flow.innertube.pages.objectOrNull
import io.github.aedev.flow.innertube.pages.stringOrNull
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.ZoneId

/**
 * A YouTube Charts chart: 30 ranked entries, no continuation.
 *
 * Distinct from [io.github.aedev.flow.innertube.pages.ChartsPage], which is the music charts on
 * `music.youtube.com`. These come from `charts.youtube.com` as plain JSON rather than renderers, and
 * they carry no view count and no relative upload date — only a release date and a rank.
 */
data class VideoChartsPage(
    val chartType: String,
    val country: String,
    val entries: List<Video> = emptyList(),
)

internal fun JsonElement.toVideoChartsPage(
    chartType: String,
    country: String,
): VideoChartsPage =
    VideoChartsPage(
        chartType = chartType,
        country = country,
        entries =
            chartLists()
                .flatMap { list -> list["videoViews"].arrayOrNull().orEmpty() }
                .mapIndexedNotNull { index, entry -> entry.objectOrNull()?.toChartVideo(index) }
                .distinctBy { it.id },
    )

private fun JsonElement.chartLists(): List<JsonObject> =
    objectOrNull()
        ?.get("contents")
        .objectOrNull()
        ?.get("sectionListRenderer")
        .objectOrNull()
        ?.get("contents")
        .arrayOrNull()
        .orEmpty()
        .mapNotNull {
            it
                .objectOrNull()
                ?.get("musicAnalyticsSectionRenderer")
                .objectOrNull()
                ?.get("content")
                .objectOrNull()
        }.flatMap { it["videos"].arrayOrNull().orEmpty() }
        .mapNotNull { it.objectOrNull() }

private fun JsonObject.toChartVideo(index: Int): Video? {
    if (this["isAvailable"]?.boolOrNull() == false) return null
    val id = this["id"].stringOrNull()?.takeIf(String::isNotBlank) ?: return null
    val title = this["title"].stringOrNull()?.takeIf(String::isNotBlank) ?: return null
    val rank = this["chartEntryMetadata"].objectOrNull()?.get("currentPosition")?.intOrNull() ?: (index + 1)
    val released = this["releaseDate"].objectOrNull()?.toLocalDate()
    return Video(
        id = id,
        title = title,
        channelName = this["channelName"].stringOrNull().orEmpty(),
        channelId = this["externalChannelId"].stringOrNull().orEmpty(),
        thumbnailUrl = ThumbnailUrlResolver.normalizeVideoThumbnail(id, this["thumbnail"].chartThumbnailUrl()),
        duration = this["videoDuration"]?.intOrNull() ?: 0,
        // The chart carries no view count at all; a card renders the rank and the duration instead.
        viewCount = 0L,
        uploadDate = released?.toString().orEmpty(),
        timestamp = released?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: 0L,
        badges = listOf("#$rank"),
    )
}

/** Charts ship the sizes ascending, so the last is the largest. */
private fun JsonElement?.chartThumbnailUrl(): String? =
    objectOrNull()
        ?.get("thumbnails")
        .arrayOrNull()
        ?.lastOrNull()
        .objectOrNull()
        ?.get("url")
        .stringOrNull()

private fun JsonObject.toLocalDate(): LocalDate? {
    val year = this["year"]?.intOrNull() ?: return null
    val month = this["month"]?.intOrNull() ?: return null
    val day = this["day"]?.intOrNull() ?: return null
    return runCatching { LocalDate.of(year, month, day) }.getOrNull()
}

private fun JsonElement.intOrNull(): Int? = (this as? JsonPrimitive)?.content?.toIntOrNull()

private fun JsonElement.boolOrNull(): Boolean? = (this as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

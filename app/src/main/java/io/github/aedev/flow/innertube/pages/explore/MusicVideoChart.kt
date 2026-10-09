package io.github.aedev.flow.innertube.pages.explore

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.models.PlaylistItem
import io.github.aedev.flow.innertube.pages.ChartsPage

/** A country's trending music videos, in chart order, under the title YouTube gives the chart. */
data class MusicVideoChart(
    val title: String,
    val entries: List<Video> = emptyList(),
)

/**
 * The playlist behind a country's trending music video chart.
 *
 * Chart titles are localized, so the chart is told apart by its id: the trending chart is the one
 * auto-generated (`OLAK5uy_`) playlist among the video charts, while the daily, top-100 and genre
 * charts are curated `PL` playlists. A country YouTube does not chart gets the global charts, which
 * have no trending list, so the first video chart stands in.
 */
internal fun ChartsPage.trendingVideoChart(): PlaylistItem? {
    val playlists =
        sections
            .filter { it.chartType == ChartsPage.ChartType.PLAYLISTS }
            .flatMap { it.items }
            .filterIsInstance<PlaylistItem>()
    return playlists.firstOrNull { it.id.startsWith(TRENDING_PLAYLIST_PREFIX) } ?: playlists.firstOrNull()
}

/** Each entry carries its position as a badge, the way chart rows are drawn. */
internal fun List<Video>.ranked(): List<Video> =
    distinctBy { it.id }.mapIndexed { index, video -> video.copy(badges = listOf("#${index + 1}") + video.badges) }

private const val TRENDING_PLAYLIST_PREFIX = "OLAK5uy_"

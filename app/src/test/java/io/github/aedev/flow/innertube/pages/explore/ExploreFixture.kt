package io.github.aedev.flow.innertube.pages.explore

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assume.assumeTrue

/**
 * Real trimmed captures of the explore surfaces, taken 2026-09-18 against the live endpoints.
 *
 * These are **not** tracked: a destination response runs to megabytes and even trimmed the set came
 * to 1.4 MB, which is not something to carry in the repository. Regenerate them with
 * `notes/innertube-video-responses/probe_explore_endpoints.py` then `trim_explore_fixtures.py`.
 * Without them these tests report as skipped rather than failing — so CI does not cover this parser,
 * and a change to it has to be run locally against fresh captures.
 *
 * Never hand-write one. Four channel tabs once shipped empty because their fixtures agreed with the
 * parser's assumptions instead of with YouTube.
 */
internal object ExploreFixture {
    const val DESTINATION_LIVE = "destination_live"
    const val DESTINATION_SPORTS = "destination_sports"
    const val DESTINATION_NEWS = "destination_news"
    const val DESTINATION_MUSIC = "destination_music"
    const val DESTINATION_LEARNING = "destination_learning"
    const val DESTINATION_MOVIES_UNAVAILABLE = "destination_movies_unavailable"
    const val SHELF_SEE_ALL = "shelf_see_all"
    const val SHELF_SEE_ALL_CONTINUATION = "shelf_see_all_continuation"
    const val GAMING_TRENDING = "gaming_trending"
    const val CHARTS_TRENDING_VIDEOS = "charts_trending_videos"
    const val CHARTS_TRENDING_MOVIES = "charts_trending_movies"
    const val CHARTS_UNSUPPORTED_COUNTRY = "charts_unsupported_country"
    const val TRENDING_DEAD = "trending_dead"

    private val json = Json { ignoreUnknownKeys = true }

    operator fun invoke(name: String): JsonObject {
        val stream = ExploreFixture::class.java.getResourceAsStream("/explore/$name.json")
        assumeTrue(
            "missing fixture explore/$name.json — regenerate with " +
                "notes/innertube-video-responses/trim_explore_fixtures.py",
            stream != null,
        )
        return json.parseToJsonElement(stream!!.bufferedReader().use { it.readText() }).jsonObject
    }
}

/**
 * The whole page at once. Production streams it — the shell, then a shelf at a time — but an
 * assertion wants it settled.
 */
internal fun JsonObject.toSettledExploreDestinationPage(): ExploreDestinationPage {
    val shell = toExploreDestinationShell()
    return shell.copy(shelves = exploreShelves(shell.owner).toList())
}

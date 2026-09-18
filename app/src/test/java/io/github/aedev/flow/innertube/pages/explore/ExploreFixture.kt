package io.github.aedev.flow.innertube.pages.explore

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Real trimmed captures of the explore surfaces, taken 2026-09-18 against the live endpoints.
 *
 * Never hand-write one of these. `notes/innertube-video-responses/probe_explore_endpoints.py` and
 * `trim_explore_fixtures.py` re-capture and re-trim them.
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
        val stream =
            requireNotNull(ExploreFixture::class.java.getResourceAsStream("/explore/$name.json")) {
                "missing fixture explore/$name.json"
            }
        return json.parseToJsonElement(stream.bufferedReader().use { it.readText() }).jsonObject
    }
}

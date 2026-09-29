package io.github.aedev.flow.innertube.pages

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Real trimmed captures of `POST /youtubei/v1/search`, taken 2026-09-14 against the live endpoint.
 *
 * Never hand-write one of these. Four channel tabs shipped empty because their fixtures agreed with
 * the parser's assumptions instead of with YouTube; `notes/innertube-video-responses/
 * probe_search_endpoints.py` re-captures them.
 */
internal object SearchFixture {
    const val ALL_SAM_SULEK = "all_sam_sulek"
    const val ALL_CONTINUATION = "all_continuation"
    const val ALL_LINUS_TECH_TIPS = "all_linus_tech_tips"
    const val HEADER_CHIPS_AND_FILTERS = "header_chips_and_filters"
    const val TYPE_VIDEOS = "type_videos"
    const val TYPE_CHANNELS = "type_channels"
    const val TYPE_PLAYLISTS = "type_playlists"
    const val TYPE_SHORTS = "type_shorts"
    const val TYPE_MOVIES = "type_movies"
    const val FEATURE_LIVE = "feature_live"
    const val DURATION_UNDER_3 = "duration_under_3"
    const val DURATION_3_TO_20 = "duration_3_to_20"
    const val DURATION_LEGACY_4_TO_20 = "duration_legacy_4_to_20"
    const val SORT_VIEW_COUNT = "sort_view_count"
    const val COMBINED_FILTERS = "combined_filters"
    const val NO_RESULTS = "no_results"
    const val SUMMARY_AND_CHAPTERS = "summary_and_chapters"

    private val json = Json { ignoreUnknownKeys = true }

    operator fun invoke(name: String): JsonObject {
        val stream =
            requireNotNull(SearchFixture::class.java.getResourceAsStream("/search/$name.json")) {
                "missing fixture search/$name.json"
            }
        return json.parseToJsonElement(stream.bufferedReader().use { it.readText() }).jsonObject
    }
}

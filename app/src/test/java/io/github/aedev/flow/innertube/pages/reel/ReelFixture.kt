package io.github.aedev.flow.innertube.pages.reel

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assume.assumeTrue

/**
 * Real trimmed captures of the reel endpoints, taken 2026-09-19 against the live API with Flow's
 * own client contexts. Not tracked (`app/src/test/resources/shorts/` is gitignored): regenerate with
 * `notes/innertube-video-responses/probe_shorts_endpoints.py`, `probe_shorts_extras.py`, then
 * `trim_shorts_fixtures.py`. Without them these tests report as skipped rather than failing.
 *
 * Never hand-write one. The previous reel parser read titles and counts off the sequence entries
 * for months; no client has ever put them there.
 */
internal object ReelFixture {
    const val SEQUENCE_SEEDED_IOS = "sequence_seeded_ios"
    const val SEQUENCE_SEEDED_WEB = "sequence_seeded_web"
    const val SEQUENCE_SEEDLESS_ANDROID = "sequence_seedless_android"
    const val SEQUENCE_CONTINUATION_ANDROID = "sequence_continuation_android"
    const val ITEM_WATCH_WEB_COLLAB = "item_watch_web_collab"
    const val ITEM_WATCH_WEB_SINGLE = "item_watch_web_single"
    const val ITEM_WATCH_MWEB = "item_watch_mweb"
    const val ITEM_WATCH_VISIONOS = "item_watch_visionos"
    const val ITEM_WATCH_ANDROID_VR = "item_watch_android_vr"
    const val ITEM_WATCH_ANDROID_ELEMENTS = "item_watch_android_elements"
    const val CHANNEL_SHORTS = "channel_shorts"
    const val SEARCH_SHORTS_FILTER = "search_shorts_filter"

    private val json = Json { ignoreUnknownKeys = true }

    operator fun invoke(name: String): JsonObject {
        val stream = ReelFixture::class.java.getResourceAsStream("/shorts/$name.json")
        assumeTrue(
            "missing fixture shorts/$name.json — regenerate with " +
                "notes/innertube-video-responses/trim_shorts_fixtures.py",
            stream != null,
        )
        return json.parseToJsonElement(stream!!.bufferedReader().use { it.readText() }).jsonObject
    }
}

package io.github.aedev.flow.data.repository

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.models.response.WatchMetadataResponse
import io.github.aedev.flow.innertube.pages.YouTubeCountParser
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * The fixture is a live `/next` trimmed to the primary and secondary info blocks, so the counts
 * below are the ones YouTube actually served for that video.
 *
 * These fields used to reach the screen as late metadata from the second extraction stack. With
 * that stack gone they come from here, and a card opened from the feed carries none of them.
 */
class WatchMetadataEnrichmentTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun response(): WatchMetadataResponse =
        json.decodeFromString(
            javaClass.classLoader!!
                .getResourceAsStream("watch_next_primary_info.json")!!
                .bufferedReader()
                .use { it.readText() },
        )

    private fun card() =
        Video(
            id = "3xngArcFpek",
            title = "From the card",
            channelName = "From the card",
            channelId = "UC_card",
            thumbnailUrl = "https://example.invalid/card.jpg",
            duration = 583,
            viewCount = 0L,
            uploadDate = "",
        )

    @Test
    fun `the exact like count is read from the like button`() {
        assertThat(response().likeCountText()).isEqualTo("26,383")
    }

    @Test
    fun `the merge fills the counts a card never carries`() {
        val merged = mergeWatchMetadata(card(), response())

        assertThat(merged).isNotNull()
        assertThat(merged!!.likeCount).isEqualTo(26_383L)
        assertThat(merged.viewCount).isEqualTo(1_827_343L)
        assertThat(merged.uploadDate).isEqualTo("Sep 15, 2026")
        assertThat(merged.description).isNotEmpty()
    }

    @Test
    fun `the timestamp comes from the relative form, not the date-only one`() {
        val response =
            json.decodeFromString<WatchMetadataResponse>(
                """
                {"contents":{"twoColumnWatchNextResults":{"results":{"results":{"contents":[
                {"videoPrimaryInfoRenderer":{"dateText":{"simpleText":"Sep 17, 2026"},
                 "relativeDateText":{"simpleText":"4 hours ago"}}}]}}}}}
                """.trimIndent(),
            )

        val merged = mergeWatchMetadata(card(), response)

        assertThat(response.relativeUploadDate()).isEqualTo("4 hours ago")
        // Midnight on that date would be hours further back than the upload actually was.
        val fourHours = 4L * 60 * 60 * 1000
        assertThat(System.currentTimeMillis() - merged!!.timestamp).isAtMost(fourHours + 60_000)
        assertThat(merged.uploadDate).isEqualTo("Sep 17, 2026")
    }

    @Test
    fun `the merge keeps what the card already held when the response has nothing better`() {
        val empty = json.decodeFromString<WatchMetadataResponse>("""{"contents":{}}""")

        assertThat(mergeWatchMetadata(card(), empty)).isNull()
    }

    private fun unsetLikeEntity(
        title: String,
        accessibilityText: String,
    ): WatchMetadataResponse =
        json.decodeFromString(
            """
            {"contents":{"twoColumnWatchNextResults":{"results":{"results":{"contents":[
            {"videoPrimaryInfoRenderer":{"dateText":{"simpleText":"Oct 25, 2009"},"videoActions":{"menuRenderer":{
            "topLevelButtons":[{"segmentedLikeDislikeButtonViewModel":{
            "likeCountEntity":{"key":"unset_like_count_entity_key"},
            "likeButtonViewModel":{"likeButtonViewModel":{"toggleButtonViewModel":{"toggleButtonViewModel":{
            "defaultButtonViewModel":{"buttonViewModel":{"title":"$title","accessibilityText":"$accessibilityText"}}}}}}}}]}}}}]}}}}}
            """.trimIndent(),
        )

    @Test
    fun `an unset count entity falls back to the like button's own label`() {
        val response = unsetLikeEntity("19M", "like this video along with 19,485,233 other people")

        val merged = mergeWatchMetadata(card(), response)

        assertThat(response.likeCountText()).isEqualTo("like this video along with 19,485,233 other people")
        assertThat(merged!!.likeCount).isEqualTo(19_485_233L)
    }

    @Test
    fun `the label reads in other languages too`() {
        val response = unsetLikeEntity("19\u202fM", "J'aime cette vidéo, comme 19\u202f485\u202f235 autres personnes")

        assertThat(YouTubeCountParser.parse(response.likeCountText(), "fr")).isEqualTo(19_485_235L)
    }

    @Test
    fun `a button whose likes are hidden still has no count`() {
        val response = unsetLikeEntity("Like", "like this video")

        assertThat(response.likeCountText()).isNull()
    }

    @Test
    fun `a response with no like button leaves the count alone`() {
        val noLikes =
            json.decodeFromString<WatchMetadataResponse>(
                """
                {"contents":{"twoColumnWatchNextResults":{"results":{"results":{"contents":[
                {"videoPrimaryInfoRenderer":{"dateText":{"simpleText":"Sep 15, 2026"}}}]}}}}}
                """.trimIndent(),
            )

        val merged = mergeWatchMetadata(card().copy(likeCount = 7L), noLikes)

        assertThat(noLikes.likeCountText()).isNull()
        assertThat(merged!!.likeCount).isEqualTo(7L)
    }
}

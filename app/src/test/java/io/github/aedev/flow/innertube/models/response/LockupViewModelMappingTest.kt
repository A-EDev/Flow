package io.github.aedev.flow.innertube.models.response

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * Exercised against a captured watch-next payload rather than a hand-built fixture: the fields this
 * mapper reads moved when YouTube replaced `compactVideoRenderer` with `lockupViewModel`, and a
 * fixture written from the model would have kept passing while the real shape drifted.
 */
class LockupViewModelMappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun response(): WatchMetadataResponse =
        javaClass.classLoader!!
            .getResourceAsStream("watch_next_related_lockups.json")!!
            .bufferedReader()
            .use { json.decodeFromString(it.readText()) }

    @Test
    fun `related lockups deserialize into compact videos`() {
        val videos = response().relatedVideos()

        assertThat(videos.map { it.videoId }).containsExactly("DdNbdhWmX04", "i_nj_vkD03g").inOrder()
    }

    @Test
    fun `channel id comes from the avatar browse endpoint`() {
        val videos = response().relatedVideos()

        assertThat(videos.map { it.channelId() })
            .containsExactly("UCOMwpjGPUrGGUS2Diq6UmVw", "UCXuqSBlHAE6Xw-yeJA0Tunw")
            .inOrder()
    }

    @Test
    fun `channel handle and avatar are carried through`() {
        val first = response().relatedVideos().first()

        assertThat(first.channelHandle).isEqualTo("/@ElHusamoX")
        assertThat(first.channelAvatarUrl).contains("yt3.ggpht.com")
    }

    @Test
    fun `view count is taken from the part with the play-arrow icon`() {
        val videos = response().relatedVideos()

        assertThat(videos.map { it.viewCountText?.text() }).containsExactly("342K", "1.6M").inOrder()
    }

    @Test
    fun `byline is the channel name and never the view count`() {
        val videos = response().relatedVideos()

        assertThat(videos.map { it.longBylineText?.text() })
            .containsExactly("El Husamo X", "Linus Tech Tips")
            .inOrder()
    }

    @Test
    fun `published text and duration survive the mapping`() {
        val first = response().relatedVideos().first()

        assertThat(first.publishedTimeText?.text()).isEqualTo("18h ago")
        assertThat(first.lengthText?.text()).isEqualTo("21:51")
    }

    @Test
    fun `a compact video renderer still resolves its channel id from the byline`() {
        val legacy =
            WatchMetadataResponse.CompactVideo(
                videoId = "legacy",
                longBylineText =
                    WatchMetadataResponse.Runs(
                        runs =
                            listOf(
                                WatchMetadataResponse.Runs.Run(
                                    text = "Some Channel",
                                    navigationEndpoint =
                                        WatchMetadataResponse.NavEndpoint(
                                            browseEndpoint =
                                                WatchMetadataResponse.NavEndpoint.BrowseEndpoint(
                                                    browseId = "UCLegacyChannelId",
                                                ),
                                        ),
                                ),
                            ),
                    ),
            )

        assertThat(legacy.channelId()).isEqualTo("UCLegacyChannelId")
    }
}

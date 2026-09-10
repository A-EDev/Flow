package io.github.aedev.flow.ui.screens.player.effects

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.screens.player.state.VideoPlayerUiState
import org.junit.Test
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType

/**
 * The watch-history write is described in exactly one place so the 3 s save, the 10 s loop and the
 * player host's dispose block cannot drift apart again. This pins that one description.
 */
class WatchHistoryEntryTest {
    private fun video(
        id: String = "vid_1",
        title: String = "Cached title",
        channelName: String = "Cached channel",
        channelId: String = "cached_channel",
        thumbnailUrl: String = "https://example.invalid/cached.jpg",
        isShort: Boolean = false,
    ): Video =
        Video(
            id = id,
            title = title,
            channelName = channelName,
            channelId = channelId,
            thumbnailUrl = thumbnailUrl,
            duration = 120,
            viewCount = 1L,
            uploadDate = "2026-01-01",
            isShort = isShort,
        )

    private fun streamInfo(
        name: String = "Extracted title",
        uploaderName: String? = "Extracted channel",
        uploaderUrl: String? = "https://www.youtube.com/channel/extracted_channel",
        streamType: StreamType = StreamType.VIDEO_STREAM,
        thumbnails: List<Image> = emptyList(),
    ): StreamInfo =
        StreamInfo(0, "https://example.invalid/watch", "https://example.invalid/watch", streamType, "vid_1", name, 0).apply {
            setUploaderName(uploaderName)
            setUploaderUrl(uploaderUrl)
            setThumbnails(thumbnails)
        }

    private fun image(
        url: String,
        height: Int,
    ): Image = Image(url, height, Image.WIDTH_UNKNOWN, Image.ResolutionLevel.UNKNOWN)

    private fun entry(
        uiState: VideoPlayerUiState,
        video: Video = video(),
        position: Long = 30_000L,
        duration: Long = 120_000L,
    ) = buildWatchHistoryEntry(video, uiState, position, duration)

    @Test
    fun `an extracted stream wins over the cached video for title channel and thumbnail`() {
        val result =
            entry(
                VideoPlayerUiState(
                    streamInfo =
                        streamInfo(
                            thumbnails =
                                listOf(
                                    image("https://example.invalid/small.jpg", 90),
                                    image("https://example.invalid/big.jpg", 720),
                                ),
                        ),
                ),
            )

        assertThat(result).isNotNull()
        assertThat(result!!.title).isEqualTo("Extracted title")
        assertThat(result.channelName).isEqualTo("Extracted channel")
        assertThat(result.channelId).isEqualTo("extracted_channel")
        assertThat(result.thumbnailUrl).isEqualTo("https://example.invalid/big.jpg")
        assertThat(result.videoId).isEqualTo("vid_1")
        assertThat(result.position).isEqualTo(30_000L)
        assertThat(result.duration).isEqualTo(120_000L)
        assertThat(result.isShort).isFalse()
    }

    @Test
    fun `a collaboration keeps the cached channel name over the extracted one`() {
        listOf("Alice and Bob", "Alice & Bob", "Alice x Bob", "Alice with Bob").forEach { cached ->
            val result = entry(VideoPlayerUiState(streamInfo = streamInfo()), video = video(channelName = cached))

            assertThat(result!!.channelName).isEqualTo(cached)
        }
    }

    @Test
    fun `a blank extracted channel name falls back to the cached one`() {
        val result = entry(VideoPlayerUiState(streamInfo = streamInfo(uploaderName = "   ")), video = video(channelName = "Solo"))

        assertThat(result!!.channelName).isEqualTo("Solo")
    }

    @Test
    fun `no stream info at all falls back to the cached video everywhere`() {
        val result = entry(VideoPlayerUiState())

        assertThat(result!!.title).isEqualTo("Cached title")
        assertThat(result.channelName).isEqualTo("Cached channel")
        assertThat(result.channelId).isEqualTo("cached_channel")
        assertThat(result.thumbnailUrl).isEqualTo("https://example.invalid/cached.jpg")
    }

    @Test
    fun `an empty cached thumbnail falls back to the youtube still`() {
        val result = entry(VideoPlayerUiState(), video = video(thumbnailUrl = ""))

        assertThat(result!!.thumbnailUrl).isEqualTo("https://i.ytimg.com/vi/vid_1/hq720.jpg")
    }

    @Test
    fun `a live stream is never written`() {
        assertThat(entry(VideoPlayerUiState(streamInfo = streamInfo(streamType = StreamType.LIVE_STREAM)))).isNull()
        assertThat(entry(VideoPlayerUiState(hlsUrl = "https://example.invalid/manifest.m3u8"))).isNull()
    }

    @Test
    fun `a zero duration or an empty title is never written`() {
        assertThat(entry(VideoPlayerUiState(), duration = 0L)).isNull()
        assertThat(entry(VideoPlayerUiState(), duration = -1L)).isNull()
        assertThat(entry(VideoPlayerUiState(), video = video(title = ""))).isNull()
    }

    @Test
    fun `the short flag comes from the cached video`() {
        assertThat(entry(VideoPlayerUiState(), video = video(isShort = true))!!.isShort).isTrue()
    }
}

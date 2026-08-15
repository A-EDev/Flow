package io.github.aedev.flow.player.stream

import io.github.aedev.flow.player.stream.StreamInfoVideoMapper.toFlowVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

private fun item(
    url: String,
    name: String = "A video",
    streamType: StreamType = StreamType.VIDEO_STREAM,
    duration: Long = 120,
    uploader: String = "A channel",
    uploaderUrl: String? = "https://www.youtube.com/channel/UC123",
): StreamInfoItem =
    StreamInfoItem(0, url, name, streamType).apply {
        this.duration = duration
        this.uploaderName = uploader
        this.uploaderUrl = uploaderUrl
    }

/**
 * These paths were unreachable from a test while they lived inside `EnhancedPlayerManager`; the
 * video-id parsing in particular has four URL shapes and a throwing branch.
 */
class StreamInfoVideoMapperTest {
    @Test
    fun `video id is parsed from every url shape youtube serves`() {
        assertEquals("dQw4w9WgXcQ", item("https://www.youtube.com/watch?v=dQw4w9WgXcQ").toFlowVideo().id)
        assertEquals("dQw4w9WgXcQ", item("https://youtu.be/dQw4w9WgXcQ").toFlowVideo().id)
        assertEquals("dQw4w9WgXcQ", item("https://www.youtube.com/shorts/dQw4w9WgXcQ").toFlowVideo().id)
        assertEquals("dQw4w9WgXcQ", item("https://example.invalid/dQw4w9WgXcQ").toFlowVideo().id)
    }

    @Test
    fun `trailing query parameters are stripped from the id`() {
        assertEquals("abc123", item("https://www.youtube.com/watch?v=abc123&t=42s").toFlowVideo().id)
        assertEquals("abc123", item("https://youtu.be/abc123?t=42").toFlowVideo().id)
        assertEquals("abc123", item("https://www.youtube.com/shorts/abc123?feature=share").toFlowVideo().id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an item with no parseable id is rejected rather than mapped to a blank video`() {
        item("").toFlowVideo()
    }

    @Test
    fun `a shorts url is flagged as a short and given the format ceiling when duration is missing`() {
        val short = item("https://www.youtube.com/shorts/abc123", duration = 0).toFlowVideo()

        assertTrue(short.isShort)
        assertEquals(60, short.duration)
    }

    @Test
    fun `a live stream reports zero duration regardless of what the item claims`() {
        val live =
            item(
                "https://www.youtube.com/watch?v=abc123",
                streamType = StreamType.LIVE_STREAM,
                duration = 9_999,
            ).toFlowVideo()

        assertTrue(live.isLive)
        assertEquals(0, live.duration)
    }

    @Test
    fun `a scheduled premiere is flagged upcoming`() {
        // StreamType.NONE is how the extractor reports a video that has not premiered yet.
        assertTrue(item("https://www.youtube.com/watch?v=abc123", streamType = StreamType.NONE).toFlowVideo().isUpcoming)
        assertFalse(item("https://www.youtube.com/watch?v=abc123").toFlowVideo().isUpcoming)
    }

    @Test
    fun `music is inferred from the uploader or the title`() {
        assertTrue(item("https://youtu.be/a1", uploader = "ArtistVEVO").toFlowVideo().isMusic)
        assertTrue(item("https://youtu.be/a2", uploader = "Artist - Topic").toFlowVideo().isMusic)
        assertTrue(item("https://youtu.be/a3", name = "Song (Official Music Video)").toFlowVideo().isMusic)
        assertTrue(item("https://youtu.be/a4", name = "Song (Official Audio)").toFlowVideo().isMusic)
        assertFalse(item("https://youtu.be/a5", name = "A tutorial", uploader = "Some channel").toFlowVideo().isMusic)
    }

    @Test
    fun `channel id is the last url segment, and blank when there is nothing to take`() {
        assertEquals("UC123", StreamInfoVideoMapper.extractChannelId("https://www.youtube.com/channel/UC123"))
        assertEquals("", StreamInfoVideoMapper.extractChannelId(null))
        assertEquals("", StreamInfoVideoMapper.extractChannelId(""))
        // No separator at all: substringAfterLast returns the input, which is not an id.
        assertEquals("", StreamInfoVideoMapper.extractChannelId("UC123"))
    }

    @Test
    fun `missing uploader url leaves the channel id blank rather than echoing the url`() {
        assertEquals("", item("https://youtu.be/a1", uploaderUrl = null).toFlowVideo().channelId)
    }
}

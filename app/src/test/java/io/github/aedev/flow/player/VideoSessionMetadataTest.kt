package io.github.aedev.flow.player

import io.github.aedev.flow.data.model.Video
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoSessionMetadataTest {
    @Test
    fun `video fields are preserved for the media session`() {
        val video =
            Video(
                id = "video-id",
                title = "Video title",
                channelName = "Channel name",
                channelId = "channel-id",
                thumbnailUrl = "https://example.com/thumbnail.jpg",
                duration = 60,
                viewCount = 100,
                uploadDate = "today",
            )

        assertEquals(
            VideoSessionMetadata(
                mediaId = "video-id",
                title = "Video title",
                artist = "Channel name",
                artworkUrl = "https://example.com/thumbnail.jpg",
            ),
            video.toVideoSessionMetadata(),
        )
    }
}

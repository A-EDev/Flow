package io.github.aedev.flow.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollaborationSignalsTest {
    @Test
    fun ordinaryChannelDoesNotNeedCollaboratorResolution() {
        assertFalse(video(channelName = "Flow Channel").needsCollaboratorResolution())
    }

    @Test
    fun commonChannelNameConnectorsDoNotTriggerResolution() {
        listOf(
            "Retro gaming with deadfred",
            "Binging with Babish",
            "Dungeons and Dragons",
        ).forEach { channelName ->
            assertFalse(video(channelName = channelName).needsCollaboratorResolution())
        }
    }

    @Test
    fun highConfidenceBylineTriggersResolution() {
        listOf(
            "First Artist & Second Artist",
            "First Artist x Second Artist",
            "First Artist feat. Second Artist",
            "First Artist ft. Second Artist",
        ).forEach { channelName ->
            assertTrue(video(channelName = channelName).needsCollaboratorResolution())
        }
    }

    @Test
    fun avatarStackTriggersResolutionWithoutCollaborationByline() {
        assertTrue(
            video(
                channelThumbnailUrls = listOf("first", "second"),
            ).needsCollaboratorResolution()
        )
    }

    @Test
    fun existingCollaboratorsSkipResolution() {
        assertFalse(
            video(
                channelName = "First Artist & Second Artist",
                collaborators = listOf(
                    VideoCollaborator(name = "First", channelId = "UC-first"),
                    VideoCollaborator(name = "Second", channelId = "UC-second"),
                ),
            ).needsCollaboratorResolution()
        )
    }

    private fun video(
        channelName: String = "Channel",
        channelThumbnailUrls: List<String> = emptyList(),
        collaborators: List<VideoCollaborator> = emptyList(),
    ) = Video(
        id = "video-id",
        title = "Video",
        channelName = channelName,
        channelId = "UC-channel",
        thumbnailUrl = "",
        duration = 60,
        viewCount = 0,
        uploadDate = "",
        channelThumbnailUrls = channelThumbnailUrls,
        collaborators = collaborators,
    )
}

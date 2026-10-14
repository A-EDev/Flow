package io.github.aedev.flow.data.repository

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import org.junit.Test

class ChannelMetadataNeedTest {
    private fun video(
        channelId: String = "UCkVfrGwV-iG9bSsgCbrNPxQ",
        avatar: String = "https://yt3.ggpht.com/avatar=s176",
    ) = Video(
        id = "v",
        title = "t",
        channelName = "Better Stack",
        channelId = channelId,
        thumbnailUrl = "",
        duration = 60,
        viewCount = 1,
        uploadDate = "",
        channelThumbnailUrl = avatar,
    )

    @Test
    fun `a real channel with a real avatar needs nothing`() {
        assertThat(video().needsChannelMetadata()).isFalse()
    }

    @Test
    fun `a channel page saved as the avatar counts as missing`() {
        assertThat(video(avatar = "https://www.youtube.com/channel/UCkVfrGwV-iG9bSsgCbrNPxQ").needsChannelMetadata()).isTrue()
    }

    @Test
    fun `a blank avatar or a placeholder channel counts as missing`() {
        assertThat(video(avatar = "").needsChannelMetadata()).isTrue()
        assertThat(video(channelId = "local").needsChannelMetadata()).isTrue()
    }
}

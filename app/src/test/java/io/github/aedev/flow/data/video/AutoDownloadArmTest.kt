package io.github.aedev.flow.data.video

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import org.junit.Test

class AutoDownloadArmTest {
    private val arm = AutoDownloadArm()
    private val video = video("vid_a")

    @Test
    fun `an opened video resolving as a video is handed over once`() {
        arm.arm(video, userOpened = true)
        arm.onLoadStarted("vid_a", token = 3)

        assertThat(arm.consume("vid_a", 3, AutoDownloadResolution.VIDEO)).isEqualTo(video)
        assertThat(arm.consume("vid_a", 3, AutoDownloadResolution.VIDEO)).isNull()
    }

    @Test
    fun `a video that only followed another is never handed over`() {
        arm.arm(video, userOpened = false)
        arm.onLoadStarted("vid_a", token = 3)

        assertThat(arm.consume("vid_a", 3, AutoDownloadResolution.VIDEO)).isNull()
    }

    @Test
    fun `a step from another load of the same video is ignored`() {
        arm.arm(video, userOpened = true)
        arm.onLoadStarted("vid_a", token = 3)

        assertThat(arm.consume("vid_a", 2, AutoDownloadResolution.VIDEO)).isNull()
        assertThat(arm.consume("vid_a", 3, AutoDownloadResolution.VIDEO)).isEqualTo(video)
    }

    @Test
    fun `nothing is handed over before the load starts`() {
        arm.arm(video, userOpened = true)

        assertThat(arm.consume("vid_a", 0, AutoDownloadResolution.VIDEO)).isNull()
    }

    @Test
    fun `opening another video cancels the first`() {
        arm.arm(video, userOpened = true)
        arm.onLoadStarted("vid_a", token = 3)
        arm.arm(video("vid_b"), userOpened = true)

        assertThat(arm.consume("vid_a", 3, AutoDownloadResolution.VIDEO)).isNull()
    }

    @Test
    fun `a load for another video cancels the arm`() {
        arm.arm(video, userOpened = true)
        arm.onLoadStarted("vid_b", token = 4)
        arm.onLoadStarted("vid_a", token = 5)

        assertThat(arm.consume("vid_a", 5, AutoDownloadResolution.VIDEO)).isNull()
    }

    @Test
    fun `moving on to the next video cancels the arm`() {
        arm.arm(video, userOpened = true)
        arm.onLoadStarted("vid_a", token = 3)
        arm.arm(video("vid_b"), userOpened = false)

        assertThat(arm.consume("vid_a", 3, AutoDownloadResolution.VIDEO)).isNull()
    }

    @Test
    fun `a failed load keeps the arm for its retry`() {
        arm.arm(video, userOpened = true)
        arm.onLoadStarted("vid_a", token = 3)

        assertThat(arm.consume("vid_a", 3, AutoDownloadResolution.FAILED)).isNull()
        arm.onLoadStarted("vid_a", token = 4)
        assertThat(arm.consume("vid_a", 4, AutoDownloadResolution.VIDEO)).isEqualTo(video)
    }

    @Test
    fun `a live, upcoming or local resolution spends the arm without a download`() {
        arm.arm(video, userOpened = true)
        arm.onLoadStarted("vid_a", token = 3)

        assertThat(arm.consume("vid_a", 3, AutoDownloadResolution.NOT_DOWNLOADABLE)).isNull()
        arm.onLoadStarted("vid_a", token = 4)
        assertThat(arm.consume("vid_a", 4, AutoDownloadResolution.VIDEO)).isNull()
    }

    private fun video(id: String) =
        Video(
            id = id,
            title = "Title $id",
            channelName = "Channel",
            channelId = "UC_channel",
            thumbnailUrl = "",
            duration = 300,
            viewCount = 0,
            uploadDate = "",
        )
}

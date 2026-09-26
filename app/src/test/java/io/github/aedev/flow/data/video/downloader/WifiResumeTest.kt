package io.github.aedev.flow.data.video.downloader

import io.github.aedev.flow.data.model.Video
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiResumeTest {
    private fun mission() =
        FlowDownloadMission(
            video =
                Video(
                    id = "id",
                    title = "t",
                    channelName = "c",
                    channelId = "local",
                    thumbnailUrl = "",
                    duration = 0,
                    viewCount = 0,
                    uploadDate = "",
                ),
            url = "https://example.com/v",
            quality = "720p",
            savePath = "/tmp/v.mp4",
            fileName = "v.mp4",
        )

    @Test
    fun `a download paused for Wi-Fi resumes whatever language its message is in`() {
        val mission =
            mission().apply {
                status = MissionStatus.PAUSED
                waitingForWifi = true
                error = "En attente du Wi-Fi"
            }
        assertTrue(mission.resumesWhenWifiReturns())
    }

    @Test
    fun `a download the user paused stays paused even if its message mentions Wi-Fi`() {
        val mission =
            mission().apply {
                status = MissionStatus.PAUSED
                error = "Waiting for WiFi"
            }
        assertFalse(mission.resumesWhenWifiReturns())
    }

    @Test
    fun `a running download is not resumed again`() {
        val mission =
            mission().apply {
                status = MissionStatus.RUNNING
                waitingForWifi = true
            }
        assertFalse(mission.resumesWhenWifiReturns())
    }
}

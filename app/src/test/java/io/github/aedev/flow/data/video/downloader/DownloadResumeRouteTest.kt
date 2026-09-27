package io.github.aedev.flow.data.video.downloader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DownloadResumeRouteTest {
    @Test
    fun `a download with stored stream URLs continues where it stopped`() {
        assertThat(downloadResumeRoute("https://rr1.googlevideo.com/videoplayback?itag=137"))
            .isEqualTo(DownloadResumeRoute.DIRECT)
    }

    @Test
    fun `a SABR download resolves a new session instead of fetching its placeholder URL`() {
        assertThat(downloadResumeRoute(sabrMissionUrl("dQw4w9WgXcQ"))).isEqualTo(DownloadResumeRoute.SABR_RERESOLVE)
    }

    @Test
    fun `a download the service no longer holds starts over`() {
        assertThat(downloadResumeRoute(null)).isEqualTo(DownloadResumeRoute.REQUEUE)
    }
}

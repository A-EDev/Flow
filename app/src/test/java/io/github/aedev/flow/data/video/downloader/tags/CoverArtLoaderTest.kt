package io.github.aedev.flow.data.video.downloader.tags

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CoverArtLoaderTest {
    @Test
    fun `cover size is a capped square for music and keeps aspect for video`() {
        assertThat(CoverArtLoader.coverSize(1280, 720, DownloadKind.MUSIC)).isEqualTo(720 to 720)
        assertThat(CoverArtLoader.coverSize(2000, 1500, DownloadKind.MUSIC)).isEqualTo(1200 to 1200)
        assertThat(CoverArtLoader.coverSize(1280, 720, DownloadKind.VIDEO)).isEqualTo(1280 to 720)
        assertThat(CoverArtLoader.coverSize(1920, 1080, DownloadKind.VIDEO)).isEqualTo(1280 to 720)
        assertThat(CoverArtLoader.coverSize(1080, 1920, DownloadKind.SHORT)).isEqualTo(720 to 1280)
    }
}

package io.github.aedev.flow.data.video.downloader.work

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DownloadValidatorTest {
    @Test
    fun `a file about as long as the video passes`() {
        assertThat(DownloadValidator.durationMatches(actualMs = 600_400, expectedMs = 600_000)).isTrue()
        assertThat(DownloadValidator.durationMatches(actualMs = 3_000, expectedMs = 7_000)).isTrue()
    }

    @Test
    fun `a file that stops early fails`() {
        assertThat(DownloadValidator.durationMatches(actualMs = 300_000, expectedMs = 600_000)).isFalse()
        assertThat(DownloadValidator.durationMatches(actualMs = 0, expectedMs = 600_000)).isFalse()
    }

    @Test
    fun `an unknown length is not held against the file`() {
        assertThat(DownloadValidator.durationMatches(actualMs = 1_000, expectedMs = 0)).isTrue()
    }
}

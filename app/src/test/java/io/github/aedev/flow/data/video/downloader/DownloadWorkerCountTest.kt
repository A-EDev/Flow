package io.github.aedev.flow.data.video.downloader

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadWorkerCountTest {
    @Test
    fun `every connection setting from one to eight gives valid worker counts`() {
        val expectedAudio = mapOf(1 to 1, 2 to 2, 3 to 2, 4 to 2, 5 to 2, 6 to 3, 7 to 3, 8 to 4)
        for (threads in 1..8) {
            assertEquals("video workers for $threads", threads, videoWorkerCount(threads))
            assertEquals("audio workers for $threads", expectedAudio.getValue(threads), audioWorkerCount(threads))
        }
    }

    @Test
    fun `a zero or negative setting still runs one worker per stream`() {
        assertEquals(1, videoWorkerCount(0))
        assertEquals(1, audioWorkerCount(0))
        assertEquals(1, audioWorkerCount(-3))
    }
}

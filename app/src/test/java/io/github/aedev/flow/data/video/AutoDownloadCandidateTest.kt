package io.github.aedev.flow.data.video

import io.github.aedev.flow.data.local.AutoDownloadMode
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoDownloadCandidateTest {
    private val queueable =
        AutoDownloadCandidate(
            mode = AutoDownloadMode.ALWAYS,
            onWifi = true,
            userOpened = true,
            isVod = true,
            isShort = false,
            isMusic = false,
            isLocal = false,
            existing = null,
        )

    @Test
    fun `an opened video with no download is queued`() {
        assertTrue(queueable.shouldAutoDownload())
    }

    @Test
    fun `off never queues`() {
        assertFalse(queueable.copy(mode = AutoDownloadMode.OFF).shouldAutoDownload())
        assertFalse(queueable.copy(mode = AutoDownloadMode.OFF, onWifi = false).shouldAutoDownload())
    }

    @Test
    fun `wifi mode queues on wifi only`() {
        assertTrue(queueable.copy(mode = AutoDownloadMode.WIFI).shouldAutoDownload())
        assertFalse(queueable.copy(mode = AutoDownloadMode.WIFI, onWifi = false).shouldAutoDownload())
    }

    @Test
    fun `always queues on mobile data`() {
        assertTrue(queueable.copy(onWifi = false).shouldAutoDownload())
    }

    @Test
    fun `a video that only followed another never queues`() {
        assertFalse(queueable.copy(userOpened = false).shouldAutoDownload())
    }

    @Test
    fun `live, upcoming, failed and already-local resolutions never queue`() {
        assertFalse(queueable.copy(isVod = false).shouldAutoDownload())
    }

    @Test
    fun `shorts, music and device files never queue`() {
        assertFalse(queueable.copy(isShort = true).shouldAutoDownload())
        assertFalse(queueable.copy(isMusic = true).shouldAutoDownload())
        assertFalse(queueable.copy(isLocal = true).shouldAutoDownload())
    }

    @Test
    fun `any existing download row is left alone`() {
        DownloadItemStatus.entries.forEach { status ->
            assertFalse("$status", queueable.copy(existing = status).shouldAutoDownload())
        }
    }

    @Test
    fun `paused, failed and cancelled downloads are never restarted`() {
        listOf(DownloadItemStatus.PAUSED, DownloadItemStatus.FAILED, DownloadItemStatus.CANCELLED).forEach { status ->
            assertFalse("$status", queueable.copy(existing = status).shouldAutoDownload())
        }
    }
}

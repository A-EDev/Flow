package io.github.aedev.flow.data.video.downloader

import org.junit.Assert.assertEquals
import org.junit.Test

class SettledNotificationTest {
    @Test
    fun `a finished download replaces its transient phase with the completion notice`() {
        assertEquals(
            SettledNotification.COMPLETE,
            settledNotificationFor(MissionStatus.FINISHED, tracked = true),
        )
    }

    @Test
    fun `a failed download keeps its own error on screen`() {
        assertEquals(
            SettledNotification.KEEP_STATE,
            settledNotificationFor(MissionStatus.FAILED, tracked = true),
        )
    }

    @Test
    fun `a paused download keeps its resume action on screen`() {
        assertEquals(
            SettledNotification.KEEP_STATE,
            settledNotificationFor(MissionStatus.PAUSED, tracked = true),
        )
    }

    @Test
    fun `a download torn down mid-merge takes its notification with it`() {
        assertEquals(
            SettledNotification.DISMISS,
            settledNotificationFor(MissionStatus.RUNNING, tracked = true),
        )
        assertEquals(
            SettledNotification.DISMISS,
            settledNotificationFor(MissionStatus.PENDING, tracked = true),
        )
    }

    @Test
    fun `a cancelled download never has a notification posted back over it`() {
        MissionStatus.entries.forEach { status ->
            assertEquals(
                "status=$status",
                SettledNotification.DISMISS,
                settledNotificationFor(status, tracked = false),
            )
        }
    }
}

package io.github.aedev.flow.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FlowCrashReportFormatterTest {

    @Test
    fun `oom report separates input state updates and final output`() {
        val report = FlowCrashReportFormatter.build(
            FlowCrashReportSnapshot(
                timestampMs = 1_800_000_000_000L,
                threadName = "main",
                threadId = 1L,
                exceptionClass = OutOfMemoryError::class.java.name,
                exceptionMessage = "Failed to allocate",
                stackTrace = """
                    java.lang.OutOfMemoryError: Failed to allocate
                        at io.github.aedev.flow.player.PlayerCacheManager.updateCache(PlayerCacheManager.kt:42)
                        at io.github.aedev.flow.service.VideoPlayerService.onStartCommand(VideoPlayerService.kt:121)
                        at android.app.ActivityThread.handleServiceArgs(ActivityThread.java:5000)
                """.trimIndent(),
                deviceInfo = "Model: Pixel Test",
                memoryInfo = "Max heap: 256 MB",
                breadcrumbs = listOf(
                    FlowCrashBreadcrumb(1_799_999_990_000L, "activity", "onStop pip=false"),
                    FlowCrashBreadcrumb(1_799_999_991_000L, "background-handoff", "handleBackgroundPlaybackOnStop"),
                    FlowCrashBreadcrumb(1_799_999_992_000L, "video-service", "onStartCommand action=null startId=1")
                )
            )
        )

        assertThat(report).contains("Crash Input")
        assertThat(report).contains("OutOfMemoryError: true")
        assertThat(report).contains("State/Cache Updates Before Crash")
        assertThat(report).contains("background-handoff: handleBackgroundPlaybackOnStop")
        assertThat(report).contains("Final Output")
        assertThat(report).contains("Top Stack Frames (same build):")
        assertThat(report).contains("PlayerCacheManager.updateCache")
    }

    @Test
    fun `top stack frames keeps only actionable frame lines`() {
        val frames = FlowCrashReportFormatter.topStackFrames(
            """
                java.lang.IllegalStateException: boom
                    at first.Frame.call(First.kt:1)
                    at second.Frame.call(Second.kt:2)
                Caused by: java.io.IOException: nope
                    at third.Frame.call(Third.kt:3)
            """.trimIndent(),
            limit = 3
        )

        assertThat(frames).containsExactly(
            "at first.Frame.call(First.kt:1)",
            "at second.Frame.call(Second.kt:2)",
            "Caused by: java.io.IOException: nope"
        ).inOrder()
    }
}

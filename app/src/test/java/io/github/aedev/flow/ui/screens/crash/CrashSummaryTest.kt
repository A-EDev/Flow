package io.github.aedev.flow.ui.screens.crash

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.utils.FlowCrashReportFormatter
import io.github.aedev.flow.utils.FlowCrashReportSnapshot
import org.junit.Test
import java.time.LocalDateTime

class CrashSummaryTest {
    private val report =
        FlowCrashReportFormatter.build(
            FlowCrashReportSnapshot(
                timestampMs = 0,
                threadName = "main",
                threadId = 2,
                exceptionClass = "java.lang.IllegalArgumentException",
                exceptionMessage = "Padding must be non-negative",
                stackTrace =
                    """
                    java.lang.IllegalArgumentException: Padding must be non-negative
                    	at androidx.compose.foundation.layout.PaddingKt.padding(Padding.kt:88)
                    	at io.github.aedev.flow.ui.screens.player.PlayerBottomBarKt.PlayerBottomBar(PlayerBottomBar.kt:212)
                    """.trimIndent(),
                deviceInfo = "  Model: 23049PCD8G\n  Manufacturer: Xiaomi\n  Android: 16 (SDK 36)\n  App Version: 2.2.5 (225)",
                memoryInfo = "  Used heap: 10 MB",
                breadcrumbs = emptyList(),
            ),
        )

    @Test
    fun `the summary points at the first frame in Flow's own code`() {
        val summary = parseCrashSummary(report)

        assertThat(summary.exception).isEqualTo("IllegalArgumentException")
        assertThat(summary.message).isEqualTo("Padding must be non-negative")
        assertThat(summary.location).isEqualTo("PlayerBottomBar.kt:212")
        assertThat(summary.appVersion).isEqualTo("2.2.5 (225)")
        assertThat(summary.device).isEqualTo("Xiaomi 23049PCD8G")
        assertThat(summary.time).isInstanceOf(LocalDateTime::class.java)
    }

    @Test
    fun `the issue link fills the bug form fields by id`() {
        val url = parseCrashSummary(report).issueUrl()

        assertThat(
            url,
        ).startsWith("https://github.com/A-EDev/Flow/issues/new?template=bug_report.yml&title=%5BBug%5D%3A%20IllegalArgumentException")
        assertThat(url).contains("&app-version=2.2.5&")
        assertThat(url).contains("&android-version=16%20%28SDK%2036%29&")
        assertThat(url).contains("&device=Xiaomi%2023049PCD8G&")
        assertThat(url).contains("&logs=IllegalArgumentException%3A%20Padding")
    }

    @Test
    fun `an unreadable report still gives a usable summary`() {
        val summary = parseCrashSummary("something went wrong")

        assertThat(summary.exception).isEqualTo("Crash")
        assertThat(summary.location).isNull()
    }
}

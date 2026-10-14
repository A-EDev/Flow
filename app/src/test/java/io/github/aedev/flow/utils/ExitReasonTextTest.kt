package io.github.aedev.flow.utils

import android.app.ApplicationExitInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneOffset

class ExitReasonTextTest {
    @Test
    fun `a native abort reads as one, with its time and importance`() {
        val line =
            ExitReasonText.line(
                reason = ApplicationExitInfo.REASON_CRASH_NATIVE,
                timestampMs = 1_791_400_000_000L,
                importance = 125,
                description = "crash",
                zone = ZoneOffset.UTC,
            )

        assertThat(line).isEqualTo("2026-10-07 19:06:40  native crash  importance=125  crash")
    }

    @Test
    fun `a missing description leaves no trailing gap`() {
        val line = ExitReasonText.line(ApplicationExitInfo.REASON_LOW_MEMORY, 0L, 400, null, ZoneOffset.UTC)

        assertThat(line).isEqualTo("1970-01-01 00:00:00  killed for low memory  importance=400")
    }

    @Test
    fun `a reason this build does not know is still reported`() {
        assertThat(ExitReasonText.reasonName(99)).isEqualTo("unknown (99)")
    }
}

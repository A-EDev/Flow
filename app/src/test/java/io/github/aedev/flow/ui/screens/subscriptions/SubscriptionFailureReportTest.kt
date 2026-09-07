package io.github.aedev.flow.ui.screens.subscriptions

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SubscriptionFailureReportTest {
    @Test
    fun `report pairs each failed channel with the reason recorded for it`() {
        val report =
            buildSubscriptionFailureReport(
                deviceInfo = "Model : Pixel\n",
                failedChannelNames = listOf("ManhwaCapped"),
                failedChannelIds = setOf("UCSBFWqxDMMz7uelyLVwq6YA"),
                failedChannelReasons =
                    mapOf("UCSBFWqxDMMz7uelyLVwq6YA" to "Channel: ContentNotAvailableException: This channel is not available"),
                sessionLogs = "W SomeTag: something",
            )

        assertThat(report).contains("ManhwaCapped  (UCSBFWqxDMMz7uelyLVwq6YA)")
        assertThat(report).contains("reason: Channel: ContentNotAvailableException: This channel is not available")
    }

    @Test
    fun `report says so when no reason was recorded for a failed channel`() {
        val report =
            buildSubscriptionFailureReport(
                deviceInfo = "",
                failedChannelNames = listOf("UC123"),
                failedChannelIds = setOf("UC123"),
                failedChannelReasons = emptyMap(),
                sessionLogs = "",
            )

        assertThat(report).contains("reason: not recorded")
        assertThat(report).contains("(no session logs captured)")
    }

    @Test
    fun `report still renders with no failed channels`() {
        val report =
            buildSubscriptionFailureReport(
                deviceInfo = "",
                failedChannelNames = emptyList(),
                failedChannelIds = emptySet(),
                failedChannelReasons = emptyMap(),
                sessionLogs = "logs",
            )

        assertThat(report).contains("Failed channels (0)")
        assertThat(report).contains("(none recorded)")
    }
}

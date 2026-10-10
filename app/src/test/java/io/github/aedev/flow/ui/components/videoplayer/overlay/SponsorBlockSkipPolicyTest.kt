package io.github.aedev.flow.ui.components.videoplayer.overlay

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.SponsorBlockAction
import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.data.sponsordetection.SponsorPredictedSpan
import org.junit.Test

class SponsorBlockSkipPolicyTest {
    private val outro =
        SponsorBlockSegment(
            category = "outro",
            segment = listOf(110f, 125f),
            uuid = "outro-id",
            actionType = "skip",
        )

    @Test
    fun `manual outro is visible while playback is active`() {
        val active =
            findActiveManualSponsorSegment(
                sponsorSegments = listOf(outro),
                currentPositionMs = 120_000L,
                skippedUuids = emptySet(),
                categoryActions = mapOf("outro" to SponsorBlockAction.SHOW_TOAST),
                playbackEnded = false,
            )

        assertThat(active).isEqualTo(outro)
    }

    @Test
    fun `manual outro is hidden after playback ends`() {
        val active =
            findActiveManualSponsorSegment(
                sponsorSegments = listOf(outro),
                currentPositionMs = 120_000L,
                skippedUuids = emptySet(),
                categoryActions = mapOf("outro" to SponsorBlockAction.SHOW_TOAST),
                playbackEnded = true,
            )

        assertThat(active).isNull()
    }

    @Test
    fun `on-device prediction gets a skip button even when the category auto-skips`() {
        val prediction = SponsorPredictedSpan("0", 10_000L, 20_000L, 0.9).asSponsorBlockSegment("video")

        val active =
            findActiveManualSponsorSegment(
                sponsorSegments = listOf(prediction),
                currentPositionMs = 15_000L,
                skippedUuids = emptySet(),
                categoryActions = mapOf("sponsor" to SponsorBlockAction.SKIP),
                playbackEnded = false,
            )

        assertThat(active).isEqualTo(prediction)
    }

    @Test
    fun `database sponsor segment that auto-skips gets no skip button`() {
        val sponsor = SponsorBlockSegment("sponsor", listOf(10f, 20f), "db-id", "skip")

        val active =
            findActiveManualSponsorSegment(
                sponsorSegments = listOf(sponsor),
                currentPositionMs = 15_000L,
                skippedUuids = emptySet(),
                categoryActions = mapOf("sponsor" to SponsorBlockAction.SKIP),
                playbackEnded = false,
            )

        assertThat(active).isNull()
    }
}

package io.github.aedev.flow.ui.screens.player.components

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.SponsorBlockAction
import io.github.aedev.flow.data.model.SponsorBlockSegment
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
    fun `ignored segments do not show a skip chip`() {
        val active =
            findActiveManualSponsorSegment(
                sponsorSegments = listOf(outro),
                currentPositionMs = 120_000L,
                skippedUuids = emptySet(),
                categoryActions = mapOf("outro" to SponsorBlockAction.IGNORE),
                playbackEnded = false,
            )

        assertThat(active).isNull()
    }

    @Test
    fun `exclusive access full segments do not show a skip chip`() {
        val exclusive =
            SponsorBlockSegment(
                category = "exclusive_access",
                segment = listOf(0f, 180f),
                uuid = "full-id",
                actionType = "full",
            )
        val active =
            findActiveManualSponsorSegment(
                sponsorSegments = listOf(exclusive),
                currentPositionMs = 1_000L,
                skippedUuids = emptySet(),
                categoryActions = mapOf("exclusive_access" to SponsorBlockAction.SKIP),
                playbackEnded = false,
            )

        assertThat(active).isNull()
    }
}

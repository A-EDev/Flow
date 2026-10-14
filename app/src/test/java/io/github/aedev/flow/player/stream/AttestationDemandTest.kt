package io.github.aedev.flow.player.stream

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.innertube.models.ResponseContext
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import org.junit.Test

/**
 * How the per-client probe reports YouTube's attestation demand. The shapes come from the captured
 * responses: every client carries a challenge, and ANDROID_VR alone marks it shared.
 */
class AttestationDemandTest {
    @Test
    fun `a response with no attestation block reports none`() {
        assertThat(demandFor(null)).isEqualTo("none")
    }

    @Test
    fun `a renderer with no challenge reports empty`() {
        assertThat(demandFor(PlayerResponse.Attestation(PlayerResponse.Attestation.PlayerAttestationRenderer())))
            .isEqualTo("empty")
    }

    @Test
    fun `a challenge reports its length`() {
        val demand = demandFor(attestation(challenge = "a=6&e=vid", shared = null))

        assertThat(demand).isEqualTo("yes(9c)")
    }

    @Test
    fun `a shared challenge is marked, because only ANDROID_VR sends one`() {
        val demand = demandFor(attestation(challenge = "a=6&e=vid", shared = true))

        assertThat(demand).isEqualTo("yes(9c,shared)")
    }

    @Test
    fun `an explicitly unshared challenge is not marked`() {
        assertThat(demandFor(attestation(challenge = "a=6", shared = false))).isEqualTo("yes(3c)")
    }

    private fun demandFor(attestation: PlayerResponse.Attestation?): String =
        with(InnerTubeVideoStreamExtractor) { response(attestation).attestationDemand() }

    private fun attestation(
        challenge: String,
        shared: Boolean?,
    ) = PlayerResponse.Attestation(
        PlayerResponse.Attestation.PlayerAttestationRenderer(challenge = challenge, useSharedChallenge = shared),
    )

    private fun response(attestation: PlayerResponse.Attestation?) =
        PlayerResponse(
            responseContext = ResponseContext(visitorData = null, serviceTrackingParams = null),
            playabilityStatus = PlayerResponse.PlayabilityStatus(status = "OK", reason = null),
            playerConfig = null,
            streamingData = null,
            videoDetails = null,
            playbackTracking = null,
            attestation = attestation,
        )
}

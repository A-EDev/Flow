package io.github.aedev.flow.innertube.models.response

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * The attestation block YouTube attaches to a player response, in the shape the probes captured
 * from every client on 2026-09-16.
 */
class PlayerAttestationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `the challenge and its shared flag are read`() {
        val response =
            json.decodeFromString<PlayerResponse>(
                """
                {"responseContext":{"visitorData":null,"serviceTrackingParams":null},"playabilityStatus":{"status":"OK","reason":null},"playerConfig":null,
                 "streamingData":null,"videoDetails":null,"playbackTracking":null,
                 "attestation":{"playerAttestationRenderer":{
                   "challenge":"a=6&a2=1&b=nonce&c=1789553682&d=28&e=3xngArcFpek&hh=binding",
                   "useSharedChallenge":true}}}
                """.trimIndent(),
            )

        val renderer = response.attestation?.playerAttestationRenderer
        assertThat(renderer?.challenge).contains("e=3xngArcFpek")
        assertThat(renderer?.challenge).contains("d=28")
        assertThat(renderer?.useSharedChallenge).isTrue()
    }

    @Test
    fun `a response without an attestation block still parses`() {
        val response =
            json.decodeFromString<PlayerResponse>(
                """
                {"responseContext":{"visitorData":null,"serviceTrackingParams":null},"playabilityStatus":{"status":"OK","reason":null},"playerConfig":null,
                 "streamingData":null,"videoDetails":null,"playbackTracking":null}
                """.trimIndent(),
            )

        assertThat(response.attestation).isNull()
    }

    @Test
    fun `the shared flag is optional`() {
        val response =
            json.decodeFromString<PlayerResponse>(
                """
                {"responseContext":{"visitorData":null,"serviceTrackingParams":null},"playabilityStatus":{"status":"OK","reason":null},"playerConfig":null,
                 "streamingData":null,"videoDetails":null,"playbackTracking":null,
                 "attestation":{"playerAttestationRenderer":{"challenge":"a=6&e=vid"}}}
                """.trimIndent(),
            )

        assertThat(response.attestation?.playerAttestationRenderer?.useSharedChallenge).isNull()
    }
}

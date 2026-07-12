package io.github.aedev.flow.player.sabr

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.player.sabr.core.SabrSessionState
import io.github.aedev.flow.player.sabr.proto.FormatBufferedRange
import io.github.aedev.flow.player.sabr.proto.FormatId
import org.junit.Test

class SabrPlayerResponseReloadTest {
    @Test
    fun `player response reload swaps credentials and resets enforcement-scoped state at the playhead`() {
        val state = SabrSessionState().apply {
            streamingUrl = "https://old.example/videoplayback"
            ustreamerConfig = byteArrayOf(1)
            poToken = "old-token"
            visitorId = "old-visitor"
            playheadPositionMs = 69_000
            requestSequence = 2
            playbackCookie = byteArrayOf(9, 9)
            audioBufferedRanges += FormatBufferedRange(
                formatId = FormatId(140, 1),
                startTimeMs = 65_000,
                durationMs = 10_000,
                startSequence = 10,
                endSequence = 11,
            )
        }

        state.applyPlayerResponseReload(
            streamingUrl = "https://fresh.example/videoplayback",
            ustreamerConfig = byteArrayOf(2, 3),
            poToken = "fresh-token",
            visitorId = "fresh-visitor",
            cpn = "FRESHCPN12345678",
        )

        // Fresh credentials from the reloaded player response.
        assertThat(state.streamingUrl).isEqualTo("https://fresh.example/videoplayback")
        assertThat(state.ustreamerConfig).isEqualTo(byteArrayOf(2, 3))
        assertThat(state.poToken).isEqualTo("fresh-token")
        assertThat(state.visitorId).isEqualTo("fresh-visitor")
        assertThat(state.cpn).isEqualTo("FRESHCPN12345678")

        // Playhead is preserved so playback resumes where it stalled.
        assertThat(state.playheadPositionMs).isEqualTo(69_000)

        // Enforcement-scoped state is reset — carrying it over draws
        // sabr.media_serving_enforcement_id_error from GVS.
        assertThat(state.requestSequence).isEqualTo(0)
        assertThat(state.playbackCookie).isEqualTo(ByteArray(0))
        assertThat(state.audioBufferedRanges).isEmpty()
    }
}

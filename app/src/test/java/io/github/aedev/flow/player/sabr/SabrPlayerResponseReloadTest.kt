package io.github.aedev.flow.player.sabr

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.player.sabr.core.SabrSessionState
import io.github.aedev.flow.player.sabr.proto.FormatBufferedRange
import io.github.aedev.flow.player.sabr.proto.FormatId
import org.junit.Test

class SabrPlayerResponseReloadTest {
    @Test
    fun `player response reload swaps credentials and resets enforcement-scoped state at the playhead`() {
        val state =
            SabrSessionState().apply {
                streamingUrl = "https://old.example/videoplayback"
                ustreamerConfig = byteArrayOf(1)
                poToken = "old-token"
                visitorId = "old-visitor"
                playheadPositionMs = 69_000
                requestSequence = 2
                playbackCookie = byteArrayOf(9, 9)
                selectedAudioItag = 140
                selectedAudioLmt = 1
                selectedVideoItag = 137
                selectedVideoLmt = 2
                audioTrackId = "old-track"
                initializedFormats += setOf(140, 137)
                audioBufferedRanges +=
                    FormatBufferedRange(
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
            audioItag = 140,
            audioLmt = 111,
            videoItag = 137,
            videoLmt = 222,
            audioTrackId = "fresh-track",
            audioXtags = "",
            videoXtags = "",
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

    @Test
    fun `player response reload re-stamps the selected formats from the fresh response`() {
        val state =
            SabrSessionState().apply {
                selectedAudioItag = 140
                selectedAudioLmt = 1_600_000_000
                selectedVideoItag = 137
                selectedVideoLmt = 1_600_000_001
                audioTrackId = "old-track"
                selectedAudioXtags = "old-xtags"
            }

        state.applyPlayerResponseReload(
            streamingUrl = "https://fresh.example/videoplayback",
            ustreamerConfig = ByteArray(0),
            poToken = "",
            visitorId = "",
            cpn = "",
            audioItag = 140,
            audioLmt = 1_700_000_000,
            videoItag = 137,
            videoLmt = 1_700_000_001,
            audioTrackId = "fresh-track",
            audioXtags = "acont=original:lang=en-US",
            videoXtags = "",
        )

        assertThat(state.selectedAudioFormatId).isEqualTo(FormatId(140, 1_700_000_000, "acont=original:lang=en-US"))
        assertThat(state.selectedVideoFormatId).isEqualTo(FormatId(137, 1_700_000_001))
        assertThat(state.audioTrackId).isEqualTo("fresh-track")
    }

    /**
     * Auto-dubbed videos repeat one audio itag across every language — 22 copies of itag 140 on the
     * video that surfaced this — so itag+lmt identifies nothing. Dropping xtags made GVS answer
     * sabr.no_audio_selected on every request until the session wedged.
     */
    @Test
    fun `selected audio format id carries xtags so dubs are distinguishable`() {
        val dubbed =
            SabrSessionState().apply {
                selectedAudioItag = 140
                selectedAudioLmt = 1_784_363_317_140_079
                selectedAudioXtags = "acont=dubbed-auto:lang=ar"
            }
        val original =
            SabrSessionState().apply {
                selectedAudioItag = 140
                selectedAudioLmt = 1_784_363_317_140_079
                selectedAudioXtags = "acont=original:lang=en-US"
            }

        assertThat(dubbed.selectedAudioFormatId).isNotEqualTo(original.selectedAudioFormatId)
        assertThat(original.selectedAudioFormatId.xtags).isEqualTo("acont=original:lang=en-US")
        assertThat(original.selectedAudioFormatId.encode()).isNotEqualTo(dubbed.selectedAudioFormatId.encode())
    }
}

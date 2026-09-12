package io.github.aedev.flow.data.sponsordetection

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SponsorCaptionLoaderTest {
    @Test
    fun `VTT parser keeps timestamps and removes YouTube word tags`() {
        val cues =
            parseWebVtt(
                """
                WEBVTT

                00:00:01.250 --> 00:00:03.500 align:start position:0%
                <00:00:01.250><c>Hello</c> &amp; welcome

                01:02:03.004 --> 01:02:04.100
                Sponsor message
                """.trimIndent(),
            )

        assertThat(cues)
            .containsExactly(
                DetectionTranscriptCue(1_250, 3_500, "Hello & welcome"),
                DetectionTranscriptCue(3_723_004, 3_724_100, "Sponsor message"),
            ).inOrder()
    }
}

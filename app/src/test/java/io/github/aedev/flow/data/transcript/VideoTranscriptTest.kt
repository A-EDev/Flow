package io.github.aedev.flow.data.transcript

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pins the two caption shapes the player already asks for: srv3 on authored tracks, vtt on the
 * auto-generated ones.
 */
class VideoTranscriptTest {
    @Test
    fun `reads an srv3 document into timed lines`() {
        val cues =
            VideoTranscript.parse(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <timedtext format="3">
                  <body>
                    <p t="0" d="2000"><s>Hello</s><s> there</s></p>
                    <p t="2500" d="1500">Second line</p>
                  </body>
                </timedtext>
                """.trimIndent(),
            )

        assertThat(cues.map { it.startMs }).containsExactly(0L, 2_500L).inOrder()
        assertThat(cues.map { it.text }).containsExactly("Hello there", "Second line").inOrder()
    }

    @Test
    fun `reads a vtt document and collapses the roll-up repeats`() {
        val cues =
            VideoTranscript.parse(
                """
                WEBVTT
                Kind: captions
                Language: en

                00:00:01.000 --> 00:00:03.000
                first line

                00:00:03.000 --> 00:00:05.000
                first line

                00:00:05.120 --> 00:00:07.000
                second <c>line</c>
                """.trimIndent(),
            )

        assertThat(cues.map { it.text }).containsExactly("first line", "second line").inOrder()
        assertThat(cues.first().startMs).isEqualTo(1_000L)
        assertThat(cues.last().startMs).isEqualTo(5_120L)
    }

    @Test
    fun `reads an hours-long vtt timestamp`() {
        val cues =
            VideoTranscript.parse(
                """
                WEBVTT

                01:02:03.400 --> 01:02:05.000
                late line
                """.trimIndent(),
            )

        assertThat(cues.single().startMs).isEqualTo(3_723_400L)
    }

    @Test
    fun `drops empty cues and returns nothing for an unreadable document`() {
        assertThat(VideoTranscript.parse("")).isEmpty()
        assertThat(VideoTranscript.parse("<timedtext format=\"3\"><body></body></timedtext>")).isEmpty()
        assertThat(
            VideoTranscript.parse(
                """
                WEBVTT

                00:00:01.000 --> 00:00:02.000

                """.trimIndent(),
            ),
        ).isEmpty()
    }
}

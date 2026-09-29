package io.github.aedev.flow.data.sponsordetection

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SponsorTranscriptInferenceTest {
    @Test
    fun `runtime config scales with available core count`() {
        assertThat(SponsorInferenceRuntimeConfig.forAvailableHardware(2))
            .isEqualTo(SponsorInferenceRuntimeConfig(batchSize = 2, intraOpThreads = 2))
        assertThat(SponsorInferenceRuntimeConfig.forAvailableHardware(6))
            .isEqualTo(SponsorInferenceRuntimeConfig(batchSize = 4, intraOpThreads = 3))
        assertThat(SponsorInferenceRuntimeConfig.forAvailableHardware(8))
            .isEqualTo(SponsorInferenceRuntimeConfig(batchSize = 4, intraOpThreads = 4))
    }

    @Test
    fun `incremental windows match encode-then-slice windows`() {
        val tokenizer = sponsorTestTokenizer()
        val cues =
            (0 until 400).map { index ->
                DetectionTranscriptCue(
                    startMs = index * 2_000L,
                    endMs = index * 2_000L + 1_900,
                    text = "episode $index is brought to you by acme and friends",
                )
            }
        val transcript = assembleSponsorTranscript(cues)
        val expected = encodeThenSliceWindows(transcript, tokenizer)
        val actual = buildSponsorWindows(transcript, tokenizer)

        assertThat(actual.size).isGreaterThan(1)
        assertThat(actual.map { it.index }).isEqualTo(expected.map { it.index })
        actual.zip(expected).forEach { (got, want) ->
            assertThat(got.inputIds.toList()).isEqualTo(want.inputIds.toList())
            assertThat(got.offsets).isEqualTo(want.offsets)
            assertThat(got.startMs).isEqualTo(want.startMs)
            assertThat(got.endMs).isEqualTo(want.endMs)
        }
    }

    @Test
    fun `window sequence can emit the first window before the last token`() {
        val tokenizer = sponsorTestTokenizer()
        val cues =
            (0 until 800).map { index ->
                DetectionTranscriptCue(index * 1_000L, index * 1_000L + 900, "word $index continues the podcast transcript")
            }
        val iterator = sponsorWindowSequence(assembleSponsorTranscript(cues), tokenizer).iterator()

        assertThat(iterator.hasNext()).isTrue()
        val first = iterator.next()
        assertThat(first.index).isEqualTo(0)
        assertThat(iterator.hasNext()).isTrue()
        assertThat(iterator.next().index).isEqualTo(1)
    }

    @Test
    fun `due windows around the playhead are selected before prefix windows`() {
        val windows =
            listOf(
                window(0, 0, 10_000),
                window(1, 8_000, 20_000),
                window(2, 90 * 60 * 1000L, 91 * 60 * 1000L),
                window(3, 91 * 60 * 1000L, 92 * 60 * 1000L),
            )

        val whileTokenizing =
            selectSponsorInferenceBatch(
                remaining = windows.take(2),
                positionMs = 90 * 60 * 1000L,
                tokenizerComplete = false,
            )
        val afterTokenizing =
            selectSponsorInferenceBatch(
                remaining = windows,
                positionMs = 90 * 60 * 1000L,
                tokenizerComplete = true,
            )
        val prefixAfterHorizon =
            selectSponsorInferenceBatch(
                remaining = windows.take(2),
                positionMs = 90 * 60 * 1000L,
                tokenizerComplete = true,
            )

        assertThat(whileTokenizing).isEmpty()
        assertThat(afterTokenizing.map { it.index }).containsExactly(2, 3).inOrder()
        assertThat(prefixAfterHorizon.map { it.index }).containsExactly(1, 0).inOrder()
    }

    @Test
    fun `start of playback prefers the earliest windows`() {
        val windows = (0 until 6).map { index -> window(index, index * 10_000L, index * 10_000L + 9_000) }

        val batch =
            selectSponsorInferenceBatch(
                remaining = windows,
                positionMs = 0,
                tokenizerComplete = false,
            )

        assertThat(batch.map { it.index }).containsExactly(0, 1, 2, 3).inOrder()
    }

    private fun window(
        index: Int,
        startMs: Long,
        endMs: Long,
    ) = SponsorInferenceWindow(
        index = index,
        inputIds = longArrayOf(1),
        attentionMask = longArrayOf(1),
        offsets = listOf(0 until 1),
        startMs = startMs,
        endMs = endMs,
    )

    private fun encodeThenSliceWindows(
        transcript: AssembledSponsorTranscript,
        tokenizer: SponsorTokenizer,
        maxLength: Int = SPONSOR_WINDOW_MAX_LENGTH,
        overlapTokens: Int = SPONSOR_WINDOW_OVERLAP_TOKENS,
    ): List<SponsorInferenceWindow> {
        val content = tokenizer.encode(transcript.text).let { it.subList(1, it.lastIndex) }
        val contentCapacity = maxLength - 2
        val step = contentCapacity - overlapTokens
        return buildList {
            var start = 0
            var windowIndex = 0
            while (start < content.size) {
                val selected = content.subList(start, minOf(start + contentCapacity, content.size))
                val tokens =
                    buildList {
                        add(SponsorToken(50_281, 0, 0))
                        addAll(selected)
                        add(SponsorToken(50_282, 0, 0))
                    }
                val offsets = tokens.map { it.startCodePoint until it.endCodePoint }
                val contentOffsets = offsets.filter { !it.isEmpty() }
                val times =
                    transcript.timestampsForSpan(
                        contentOffsets.first().first,
                        contentOffsets.last().last + 1,
                    )
                add(
                    SponsorInferenceWindow(
                        index = windowIndex,
                        inputIds = tokens.map { it.id }.toLongArray(),
                        attentionMask = LongArray(tokens.size) { 1L },
                        offsets = offsets,
                        startMs = times.first,
                        endMs = times.second,
                    ),
                )
                if (start + contentCapacity >= content.size) break
                start += step
                windowIndex++
            }
        }
    }
}

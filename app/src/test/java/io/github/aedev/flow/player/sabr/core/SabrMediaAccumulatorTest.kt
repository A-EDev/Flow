package io.github.aedev.flow.player.sabr.core

import io.github.aedev.flow.player.sabr.proto.MediaHeader
import io.github.aedev.flow.player.sabr.ump.UmpFrameDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SabrMediaAccumulatorTest {
    @Test
    fun audioBudgetIsSharedAcrossHeadersAndFinishReleasesItsBytes() {
        val accumulator = SabrMediaAccumulator()
        val halfLimit = SabrMediaAccumulator.AUDIO_LIMIT_BYTES / 2
        accumulator.begin(MediaHeader(headerId = 1, itag = 140), isAudio = true)
        accumulator.begin(MediaHeader(headerId = 2, itag = 140), isAudio = true)
        accumulator.append(media(1, halfLimit))
        accumulator.append(media(2, halfLimit))

        assertThrows(UmpFrameDecoder.ResourceLimitExceeded::class.java) {
            accumulator.append(media(2, 1))
        }

        val completed = accumulator.finish(1) ?: error("first segment should finish")
        assertEquals(halfLimit, completed.encodedData.size)
        accumulator.append(media(2, 1))
        assertEquals(halfLimit + 1, accumulator.finish(2)?.encodedData?.size ?: -1)
    }

    @Test
    fun replacementAndClearReleasePreviouslyCountedBytes() {
        val accumulator = SabrMediaAccumulator()
        val fullLimit = SabrMediaAccumulator.AUDIO_LIMIT_BYTES

        accumulator.begin(MediaHeader(headerId = 1, itag = 140), isAudio = true)
        accumulator.append(media(1, fullLimit))
        assertTrue(accumulator.begin(MediaHeader(headerId = 1, itag = 140, sequenceNumber = 2), isAudio = true))
        accumulator.append(media(1, fullLimit))
        assertEquals(fullLimit, accumulator.finish(1)?.encodedData?.size ?: -1)

        accumulator.begin(MediaHeader(headerId = 2, itag = 140), isAudio = true)
        accumulator.append(media(2, fullLimit))
        accumulator.clear()
        assertEquals(0, accumulator.incompleteCount)
        accumulator.begin(MediaHeader(headerId = 3, itag = 140), isAudio = true)
        accumulator.append(media(3, fullLimit))
        assertEquals(fullLimit, accumulator.finish(3)?.encodedData?.size ?: -1)
        assertEquals(0, accumulator.incompleteCount)
    }

    @Test
    fun onlyEightIncompleteHeadersCanBeRetained() {
        val accumulator = SabrMediaAccumulator()
        repeat(8) { id ->
            accumulator.begin(MediaHeader(headerId = id + 1, itag = 140), isAudio = true)
        }

        assertThrows(UmpFrameDecoder.ResourceLimitExceeded::class.java) {
            accumulator.begin(MediaHeader(headerId = 9, itag = 140), isAudio = true)
        }

        accumulator.clear()
        assertFalse(accumulator.begin(MediaHeader(headerId = 9, itag = 140), isAudio = true))
    }

    @Test
    fun overflowCarriesTheFailingHeaderItag() {
        val accumulator = SabrMediaAccumulator()
        accumulator.begin(MediaHeader(headerId = 1, itag = 251), isAudio = true)
        accumulator.append(media(1, SabrMediaAccumulator.AUDIO_LIMIT_BYTES))

        val error =
            assertThrows(UmpFrameDecoder.ResourceLimitExceeded::class.java) {
                accumulator.append(media(1, 1))
            }

        assertEquals(251, error.itag)
    }

    private fun media(
        headerId: Int,
        dataSize: Int,
    ): ByteArray =
        ByteArray(dataSize + 1).also { payload ->
            payload[0] = headerId.toByte()
        }
}

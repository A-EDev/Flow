package io.github.aedev.flow.player.sabr.core

import io.github.aedev.flow.player.sabr.network.SabrDataSource
import io.github.aedev.flow.player.sabr.ump.UmpPartType
import io.github.aedev.flow.player.sabr.ump.UmpVarInt
import io.github.aedev.flow.utils.protobuf.ProtobufWriter
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.ArrayDeque

class SabrStreamControllerCapacityTest {
    @Test
    fun oversizedAudioAccumulatorFailsAndFreshResponseCanRecover() =
        runBlocking {
            val responses =
                ArrayDeque(
                    listOf(
                        response(itag = 140, mediaBytes = ByteArray(4 * 1024 * 1024 + 1), includeEnd = false),
                        response(itag = 140, mediaBytes = byteArrayOf(4, 5, 6), includeEnd = true),
                    ),
                )
            val dataSource = mockk<SabrDataSource>(relaxed = true)
            every { dataSource.open(any(), any(), any()) } answers { ByteArrayInputStream(responses.removeFirst()) }
            val controller =
                SabrStreamController(
                    dataSource,
                    SabrSessionState().apply {
                        streamingUrl = "https://example.com/sabr"
                        videoId = "video"
                        selectedAudioItag = 140
                    },
                )
            val terminalError =
                async(start = CoroutineStart.UNDISPATCHED) {
                    controller.events.first { it is SabrEvent.Error } as SabrEvent.Error
                }

            controller.startSession()

            val error = terminalError.await()
            assertEquals(SabrStreamController.RESOURCE_LIMIT_ERROR, error.code)
            assertFalse(error.recoverable)
            assertEquals(140, error.itag)

            val recoveredSegment =
                async(start = CoroutineStart.UNDISPATCHED) {
                    controller.events.first { it is SabrEvent.SegmentReady } as SabrEvent.SegmentReady
                }
            controller.startSession()
            assertArrayEquals(byteArrayOf(4, 5, 6), recoveredSegment.await().segment.data)
            assertTrue(responses.isEmpty())
            controller.release()
        }

    private fun response(
        itag: Int,
        mediaBytes: ByteArray,
        includeEnd: Boolean,
    ): ByteArray {
        val header =
            ProtobufWriter.encode {
                writeInt32(1, 1)
                writeString(2, "video")
                writeInt32(3, itag)
                writeInt64(14, mediaBytes.size.toLong())
            }
        val media = byteArrayOf(1) + mediaBytes
        return buildList {
            add(frame(UmpPartType.MEDIA_HEADER, header))
            add(frame(UmpPartType.MEDIA, media))
            if (includeEnd) add(frame(UmpPartType.MEDIA_END, byteArrayOf(1)))
        }.fold(ByteArray(0)) { all, next -> all + next }
    }

    private fun frame(
        type: Int,
        payload: ByteArray,
    ): ByteArray = UmpVarInt.encode(type.toLong()) + UmpVarInt.encode(payload.size.toLong()) + payload
}

package io.github.aedev.flow.player.sabr

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.player.sabr.core.SabrRequestBuilder
import io.github.aedev.flow.player.sabr.core.SabrSessionState
import io.github.aedev.flow.player.sabr.proto.ProtobufReader
import org.junit.Test

/**
 * Guards the SABR `VideoPlaybackAbrRequest` wire format — specifically the fields that decide
 * playback quality on the server side. The regression these tests lock down: in auto mode the
 * builder used to emit `sticky_resolution = 0`, which the encoder then dropped entirely, so the
 * SABR server fell back to its lowest rung (~360p) no matter which format we preferred.
 */
class SabrRequestBuilderTest {

    private fun state(block: SabrSessionState.() -> Unit): SabrSessionState =
        SabrSessionState().apply {
            clientNameId = 1
            clientVersion = "2.20260101.00.00"
            osName = "Windows"
            osVersion = "10.0"
            ustreamerConfig = byteArrayOf(1, 2, 3)
            block()
        }

    private fun topLevel(bytes: ByteArray) = ProtobufReader(bytes).readAllFields()

    private fun clientAbrState(bytes: ByteArray): Map<Int, List<ProtobufReader.Field>> {
        val abrBytes = topLevel(bytes)[1]!!.first().asBytes()
        return ProtobufReader(abrBytes).readAllFields()
    }

    @Test
    fun `auto mode sends sticky_resolution derived from the selected video height`() {
        val s = state {
            selectedVideoItag = 137
            selectedVideoHeight = 1080
            selectedAudioItag = 251
            stickyResolution = 0 // auto
        }
        val abr = clientAbrState(SabrRequestBuilder.buildInitialRequest(s))

        // field 21 must be present and equal the selected video height (the fix)
        assertThat(abr[21]?.first()?.asInt()).isEqualTo(1080)
        // field 16 (last_manual_selected_resolution) is only for an explicit user pick
        assertThat(abr[16]).isNull()
    }

    @Test
    fun `low quality video is floored to 360p in sticky_resolution`() {
        val s = state {
            selectedVideoItag = 133
            selectedVideoHeight = 240
            stickyResolution = 0
        }
        val abr = clientAbrState(SabrRequestBuilder.buildInitialRequest(s))
        assertThat(abr[21]?.first()?.asInt()).isEqualTo(360)
    }

    @Test
    fun `user pinned quality drives both sticky and last_manual resolution`() {
        val s = state {
            selectedVideoItag = 137
            selectedVideoHeight = 1080
            stickyResolution = 720
        }
        val abr = clientAbrState(SabrRequestBuilder.buildInitialRequest(s))
        assertThat(abr[21]?.first()?.asInt()).isEqualTo(720)
        assertThat(abr[16]?.first()?.asInt()).isEqualTo(720)
    }

    @Test
    fun `playback_rate is always present as a fixed32 float`() {
        val s = state {
            selectedVideoItag = 137
            selectedVideoHeight = 1080
            playbackRate = 1.0f
        }
        val abr = clientAbrState(SabrRequestBuilder.buildInitialRequest(s))
        val field35 = abr[35]?.first()
        assertThat(field35).isNotNull()
        assertThat(field35!!.wireType).isEqualTo(ProtobufReader.WIRE_FIXED32)
        assertThat(Float.fromBits(field35.varintValue.toInt())).isEqualTo(1.0f)
    }

    @Test
    fun `streamer context carries web client info with hl and gl`() {
        val s = state {
            selectedVideoItag = 137
            selectedVideoHeight = 1080
        }
        val top = topLevel(SabrRequestBuilder.buildInitialRequest(s))
        val streamerCtx = ProtobufReader(top[19]!!.first().asBytes()).readAllFields()
        val clientInfo = ProtobufReader(streamerCtx[1]!!.first().asBytes()).readAllFields()

        assertThat(clientInfo[16]?.first()?.asInt()).isEqualTo(1) // WEB client id
        assertThat(clientInfo[21]?.first()?.asString()).isEqualTo("en-US") // hl
        assertThat(clientInfo[22]?.first()?.asString()).isEqualTo("US")    // gl
    }

    @Test
    fun `ustreamer config is sent as top-level bytes field 5`() {
        val s = state {
            selectedVideoItag = 137
            selectedVideoHeight = 1080
        }
        val top = topLevel(SabrRequestBuilder.buildInitialRequest(s))
        assertThat(top[5]?.first()?.asBytes()).isEqualTo(byteArrayOf(1, 2, 3))
    }

    @Test
    fun `preferred video and audio format ids are pinned in fields 17 and 16`() {
        val s = state {
            selectedVideoItag = 137
            selectedVideoHeight = 1080
            selectedAudioItag = 251
        }
        val top = topLevel(SabrRequestBuilder.buildInitialRequest(s))

        val prefVideo = ProtobufReader(top[17]!!.first().asBytes()).readAllFields()
        val prefAudio = ProtobufReader(top[16]!!.first().asBytes()).readAllFields()
        assertThat(prefVideo[1]?.first()?.asInt()).isEqualTo(137) // itag
        assertThat(prefAudio[1]?.first()?.asInt()).isEqualTo(251)
    }
}

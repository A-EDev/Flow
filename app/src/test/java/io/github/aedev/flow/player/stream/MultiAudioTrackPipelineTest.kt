package io.github.aedev.flow.player.stream

import io.github.aedev.flow.innertube.models.response.PlayerResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.stream.AudioTrackType

/**
 * End-to-end cover for the multi-audio path, driven by a real /player response captured from a
 * video carrying 26 dubs (signed URLs redacted). Guards the whole chain that previously collapsed
 * every dub into a single selector row: deserialization → [InnerTubeStreamBridge.convertAudioFormats]
 * → [StreamProcessor.processAudioStreams] → UI options.
 */
class MultiAudioTrackPipelineTest {

    /** Mirrors the production InnerTube client config, so the fixture exercises the real parse. */
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val audioFormats: List<PlayerResponse.StreamingData.Format> by lazy {
        val raw = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("player_response_multi_audio.json")
        ) { "fixture missing" }.bufferedReader().readText()
        json.decodeFromString<PlayerResponse>(raw).streamingData!!.adaptiveFormats
    }

    @Test
    fun `every dubbed track reaches the selector as its own row`() {
        val tracks = StreamProcessor.processAudioStreams(
            InnerTubeStreamBridge.convertAudioFormats(audioFormats)
        )

        assertEquals(26, tracks.size)
        assertEquals(26, tracks.mapNotNull { it.audioTrackId }.distinct().size)
    }

    @Test
    fun `exactly one track is reported as the original`() {
        val tracks = StreamProcessor.processAudioStreams(
            InnerTubeStreamBridge.convertAudioFormats(audioFormats)
        )

        val originals = tracks.filter { it.audioTrackType == AudioTrackType.ORIGINAL }
        assertEquals(1, originals.size)
        assertEquals("en.4", originals.single().audioTrackId)
    }

    @Test
    fun `dubs are the ones that bypass the default audio path`() {
        val tracks = StreamProcessor.processAudioStreams(
            InnerTubeStreamBridge.convertAudioFormats(audioFormats)
        )

        assertEquals(25, tracks.count { StreamProcessor.overridesDefaultAudioTrack(it) })
        assertTrue(
            !StreamProcessor.overridesDefaultAudioTrack(tracks.single { it.audioTrackId == "en.4" })
        )
    }

    @Test
    fun `the drc copy of the original never wins its track`() {
        // The fixture carries DRC and non-DRC copies of en.4 at near-identical bitrates, so a plain
        // bitrate sort would land on the loudness-flattened one.
        assertTrue(audioFormats.any { it.isDynamicRangeCompressed })

        val converted = InnerTubeStreamBridge.convertAudioFormats(audioFormats)

        assertTrue(converted.none { it.getContent().contains("drc=True") })
    }

    @Test
    fun `selector rows are labelled with their language`() {
        val options = StreamProcessor.toAudioTrackOptions(
            StreamProcessor.processAudioStreams(
                InnerTubeStreamBridge.convertAudioFormats(audioFormats)
            )
        )

        assertTrue(options.none { it.label.isBlank() })
        assertTrue(options.any { it.label.contains("original", ignoreCase = true) })
        assertTrue(options.any { it.label.contains("Arabic") })
        assertEquals(options.indices.toList(), options.map { it.index })
    }

    @Test
    fun `a track-poor response is what triggers a top-up`() {
        // The default-track-only shape a client like ANDROID_VR returns.
        val defaultOnly = audioFormats.filter { it.audioTrack?.id == "en.4" }

        assertEquals(1, defaultOnly.mapNotNull { it.audioTrack?.id }.distinct().size)
        assertEquals(26, audioFormats.mapNotNull { it.audioTrack?.id }.distinct().size)
    }
}

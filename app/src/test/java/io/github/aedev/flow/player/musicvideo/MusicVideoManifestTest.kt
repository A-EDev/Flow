package io.github.aedev.flow.player.musicvideo

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.dash.manifest.Representation
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.ui.screens.player.fakeInnerTubeFormat
import org.junit.Test

class MusicVideoManifestTest {
    private val format =
        fakeInnerTubeFormat(136, "video/mp4; codecs=\"avc1.4d401f\"", height = 720, width = 1280).copy(
            initRange = PlayerResponse.StreamingData.Format.Range("0", "739"),
            indexRange = PlayerResponse.StreamingData.Format.Range("740", "1399"),
        )

    @Test
    fun `the file is one segment read through its own init and index ranges`() {
        val manifest = MusicVideoManifest.manifest(format, durationMs = 263_000)!!

        assertThat(manifest.durationMs).isEqualTo(263_000)
        assertThat(manifest.dynamic).isFalse()
        val adaptationSet = manifest.getPeriod(0).adaptationSets.single()
        assertThat(adaptationSet.type).isEqualTo(C.TRACK_TYPE_VIDEO)
        val representation = adaptationSet.representations.single() as Representation.SingleSegmentRepresentation
        assertThat(representation.baseUrls.single().url).isEqualTo(format.url)
        assertThat(representation.format.sampleMimeType).isEqualTo(MimeTypes.VIDEO_H264)
        assertThat(representation.format.containerMimeType).isEqualTo(MimeTypes.VIDEO_MP4)
        assertThat(representation.format.height).isEqualTo(720)
        val init = representation.initializationUri!!
        assertThat(init.start).isEqualTo(0)
        assertThat(init.length).isEqualTo(740)
        val index = representation.indexUri!!
        assertThat(index.start).isEqualTo(740)
        assertThat(index.length).isEqualTo(660)
    }

    @Test
    fun `no manifest without the ranges or a length`() {
        assertThat(MusicVideoManifest.manifest(format.copy(indexRange = null), durationMs = 263_000)).isNull()
        assertThat(MusicVideoManifest.manifest(format, durationMs = 0)).isNull()
    }
}

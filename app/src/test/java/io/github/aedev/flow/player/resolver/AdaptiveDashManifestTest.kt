package io.github.aedev.flow.player.resolver

import android.app.Application
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.dash.manifest.Representation
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.player.quality.LadderTestStreams.h264
import io.github.aedev.flow.player.quality.LadderTestStreams.vp9
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.ByteArrayInputStream

/**
 * A ladder rung must be the stream exactly as the single-quality path already plays it, so each
 * one is checked against the manifest [ManifestGenerator] writes for that stream on its own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AdaptiveDashManifestTest {
    private val rungs = listOf(h264(360, 700_000), h264(720, 2_500_000), h264(1080, 4_500_000))

    private fun singleStreamManifest(stream: VideoStream): DashManifest {
        val xml = ManifestGenerator.generateProgressiveManifest(stream, stream.itagItem!!, 212)!!
        return DashManifestParser().parse(Uri.parse(stream.content), ByteArrayInputStream(xml.toByteArray()))
    }

    private fun DashManifest.videoRepresentations(): List<Representation> =
        getPeriod(0).adaptationSets.filter { it.type == C.TRACK_TYPE_VIDEO }.flatMap { it.representations }

    @Test
    fun `every rung is described exactly as the single-stream manifest describes it`() {
        val ladder = AdaptiveDashManifest.build(rungs, 212)!!

        val built = ladder.videoRepresentations()
        assertThat(built).hasSize(rungs.size)
        rungs.zip(built).forEach { (stream, rung) ->
            val single = singleStreamManifest(stream).videoRepresentations().single()
            assertThat(rung.baseUrls.single().url).isEqualTo(single.baseUrls.single().url)
            assertThat(rung.format.id).isEqualTo(single.format.id)
            assertThat(rung.format.containerMimeType).isEqualTo(single.format.containerMimeType)
            assertThat(rung.format.sampleMimeType).isEqualTo(single.format.sampleMimeType)
            assertThat(rung.format.codecs).isEqualTo(single.format.codecs)
            assertThat(rung.format.bitrate).isEqualTo(single.format.bitrate)
            assertThat(rung.format.width).isEqualTo(single.format.width)
            assertThat(rung.format.height).isEqualTo(single.format.height)
            assertThat(rung.format.frameRate).isEqualTo(single.format.frameRate)
            assertThat(rung.initializationUri).isEqualTo(single.initializationUri)
            assertThat(rung.indexUri).isEqualTo(single.indexUri)
        }
        assertThat(ladder.durationMs).isEqualTo(singleStreamManifest(rungs[0]).durationMs)
        assertThat(ladder.dynamic).isFalse()
    }

    @Test
    fun `all rungs share one video adaptation set`() {
        val ladder = AdaptiveDashManifest.build(rungs, 212)!!

        assertThat(ladder.getPeriod(0).adaptationSets).hasSize(1)
    }

    @Test
    fun `a WebM rung keeps its container`() {
        val ladder = AdaptiveDashManifest.build(listOf(vp9(360, 600_000), vp9(720, 2_000_000)), 212)!!

        val single = singleStreamManifest(vp9(360, 600_000)).videoRepresentations().single()
        assertThat(
            ladder
                .videoRepresentations()
                .first()
                .format.containerMimeType,
        ).isEqualTo(single.format.containerMimeType)
    }

    @Test
    fun `fewer than two describable rungs make no ladder`() {
        assertThat(AdaptiveDashManifest.build(rungs.take(1), 212)).isNull()
        assertThat(AdaptiveDashManifest.build(listOf(rungs[0], h264(720, 2_500_000, ranged = false)), 212)).isNull()
    }
}

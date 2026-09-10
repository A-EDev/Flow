package io.github.aedev.flow.player.stream

import io.github.aedev.flow.data.local.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.VideoStream

class VideoQualityOptionsTest {
    private fun video(
        id: String,
        resolution: String,
    ): VideoStream =
        VideoStream
            .Builder()
            .setId(id)
            .setContent("https://example.invalid/$id", true)
            .setMediaFormat(MediaFormat.MPEG_4)
            .setResolution(resolution)
            .setIsVideoOnly(true)
            .build()

    @Test
    fun `qualities are listed low to high with AUTO offered last`() {
        val streams = listOf(video("a", "720p"), video("b", "144p"), video("c", "1080p"))

        assertEquals(
            listOf(VideoQuality.Q_144P, VideoQuality.Q_720P, VideoQuality.Q_1080P, VideoQuality.AUTO),
            VideoQualityOptions.availableQualities(streams),
        )
    }

    @Test
    fun `the several streams of one quality class collapse onto a single row`() {
        val streams = listOf(video("a", "1080p"), video("b", "1080p60"), video("c", "1080p"))

        assertEquals(listOf(VideoQuality.Q_1080P, VideoQuality.AUTO), VideoQualityOptions.availableQualities(streams))
    }

    @Test
    fun `no streams still offers AUTO`() {
        assertEquals(listOf(VideoQuality.AUTO), VideoQualityOptions.availableQualities(emptyList()))
    }

    @Test
    fun `the resolved quality of a stream is its own row and of no stream is AUTO`() {
        assertEquals(VideoQuality.Q_480P, VideoQualityOptions.qualityOf(video("v", "480p")))
        assertEquals(VideoQuality.AUTO, VideoQualityOptions.qualityOf(null))
    }
}

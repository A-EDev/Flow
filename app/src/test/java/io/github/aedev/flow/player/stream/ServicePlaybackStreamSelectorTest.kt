package io.github.aedev.flow.player.stream

import io.github.aedev.flow.data.local.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.Locale

private fun audio(
    id: String,
    languageTag: String? = null,
    trackName: String? = null,
    trackType: AudioTrackType = AudioTrackType.DUBBED,
    bitrate: Int = 128_000,
): AudioStream =
    AudioStream
        .Builder()
        .setId(id)
        .setContent("https://example.invalid/$id", true)
        .setMediaFormat(MediaFormat.M4A)
        .setAverageBitrate(bitrate)
        .apply {
            setAudioTrackType(trackType)
            languageTag?.let { setAudioLocale(Locale.forLanguageTag(it)) }
            trackName?.let { setAudioTrackName(it) }
        }.build()

private fun video(
    id: String,
    resolution: String,
    videoOnly: Boolean = true,
    format: MediaFormat = MediaFormat.MPEG_4,
): VideoStream =
    VideoStream
        .Builder()
        .setId(id)
        .setContent("https://example.invalid/$id", true)
        .setMediaFormat(format)
        .setResolution(resolution)
        .setIsVideoOnly(videoOnly)
        .build()

class ServicePlaybackStreamSelectorTest {
    private fun select(
        audio: List<AudioStream> = emptyList(),
        videos: List<VideoStream> = emptyList(),
        quality: VideoQuality = VideoQuality.AUTO,
        language: String = "original",
    ) = ServicePlaybackStreamSelector.selectStreams(
        videoCandidates = videos,
        audioCandidatesAll = audio,
        preferredQuality = quality,
        preferredAudioLanguage = language,
    )

    @Test
    fun `a region qualified preference matches its track instead of falling back to the original`() {
        // Regression guard for the copy this path used to carry: it compared only
        // audioLocale.language ("pt"), so "pt-BR" never matched and the original was chosen.
        val original = audio("orig", languageTag = "en", trackType = AudioTrackType.ORIGINAL)
        val brazilian = audio("ptbr", languageTag = "pt-BR")

        val (_, picked) = select(audio = listOf(original, brazilian), language = "pt-BR")

        assertEquals("ptbr", picked?.id)
    }

    @Test
    fun `a plain language preference still matches`() {
        val original = audio("orig", languageTag = "en", trackType = AudioTrackType.ORIGINAL)
        val spanish = audio("es", languageTag = "es")

        assertEquals("es", select(audio = listOf(original, spanish), language = "es").second?.id)
    }

    @Test
    fun `original is preferred when no language is requested`() {
        val dubbed = audio("dub", languageTag = "es")
        val original = audio("orig", languageTag = "en", trackType = AudioTrackType.ORIGINAL)

        assertEquals("orig", select(audio = listOf(dubbed, original), language = "original").second?.id)
        assertEquals("orig", select(audio = listOf(dubbed, original), language = "").second?.id)
    }

    @Test
    fun `an unavailable language falls back rather than returning nothing`() {
        val original = audio("orig", languageTag = "en", trackType = AudioTrackType.ORIGINAL)
        val spanish = audio("es", languageTag = "es")

        val picked = select(audio = listOf(original, spanish), language = "ja").second

        assertNotNull(picked)
        assertEquals("orig", picked?.id)
    }

    @Test
    fun `the highest bitrate wins among tracks of the same language`() {
        val low = audio("low", languageTag = "en", trackType = AudioTrackType.ORIGINAL, bitrate = 64_000)
        val high = audio("high", languageTag = "en", trackType = AudioTrackType.ORIGINAL, bitrate = 256_000)

        assertEquals("high", select(audio = listOf(low, high), language = "original").second?.id)
    }

    @Test
    fun `duplicate urls do not change which track is chosen`() {
        val original = audio("orig", languageTag = "en", trackType = AudioTrackType.ORIGINAL)

        assertEquals("orig", select(audio = listOf(original, original), language = "original").second?.id)
    }

    @Test
    fun `AUTO quality leaves the video track to adaptive selection`() {
        val streams = listOf(video("v1", "1080p"), video("v2", "480p"))

        assertNull(select(videos = streams, audio = listOf(audio("a", trackType = AudioTrackType.ORIGINAL))).first)
    }

    @Test
    fun `an explicit quality picks the closest available height`() {
        val streams = listOf(video("v1080", "1080p"), video("v480", "480p"), video("v144", "144p"))

        val picked =
            select(
                videos = streams,
                audio = listOf(audio("a", trackType = AudioTrackType.ORIGINAL)),
                quality = VideoQuality.Q_480p,
            ).first

        assertEquals("v480", picked?.id)
    }

    @Test
    fun `with no audio at all a muxed stream is preferred so playback still has sound`() {
        val muxed = video("muxed", "360p", videoOnly = false)
        val videoOnly = video("vonly", "1080p", videoOnly = true)

        val (picked, audioPicked) = select(videos = listOf(videoOnly, muxed), audio = emptyList())

        assertNull(audioPicked)
        assertTrue("expected the muxed stream, got ${picked?.id}", picked?.id == "muxed")
    }
}

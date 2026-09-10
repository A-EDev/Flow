package io.github.aedev.flow.data.video

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.Locale

/**
 * Pins the download policy against real NewPipe streams. `bitrate` on an [AudioStream] only exists
 * through its [ItagItem], which is why the fixture takes the two bitrates separately: the helpers
 * treat them differently.
 */
class DownloadStreamPolicyTest {
    private fun audio(
        id: String,
        format: MediaFormat? = MediaFormat.M4A,
        averageBitrate: Int = AudioStream.UNKNOWN_BITRATE,
        itagBitrate: Int? = null,
        trackId: String? = null,
        trackName: String? = null,
        locale: Locale? = null,
        trackType: AudioTrackType? = null,
        content: String = "https://example.invalid/$id",
    ): AudioStream {
        val builder =
            AudioStream
                .Builder()
                .setId(id)
                .setContent(content, true)
                .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                .setAverageBitrate(averageBitrate)
        format?.let { builder.setMediaFormat(it) }
        trackId?.let { builder.setAudioTrackId(it) }
        trackName?.let { builder.setAudioTrackName(it) }
        locale?.let { builder.setAudioLocale(it) }
        trackType?.let { builder.setAudioTrackType(it) }
        itagBitrate?.let { bitrate ->
            val item = ItagItem(id.toIntOrNull() ?: 140, ItagItem.ItagType.AUDIO, format ?: MediaFormat.M4A, 0)
            item.bitrate = bitrate
            builder.setItagItem(item)
        }
        return builder.build()
    }

    /** The itag in [id] is what decides the codec: 137 is h264, 248/247 vp9, 399 av1. */
    private fun video(
        id: String,
        resolution: String,
        content: String = "https://example.invalid/$id",
    ): VideoStream =
        VideoStream
            .Builder()
            .setId(id)
            .setContent(content, true)
            .setMediaFormat(MediaFormat.MPEG_4)
            .setResolution(resolution)
            .setIsVideoOnly(true)
            .build()

    private fun build(
        innerTube: List<VideoStream> = emptyList(),
        videoOnly: List<VideoStream> = emptyList(),
        muxed: List<VideoStream> = emptyList(),
    ) = DownloadStreamPolicy.buildDownloadVideoStreams(innerTube, videoOnly, muxed)

    private fun kbps(stream: AudioStream) = DownloadStreamPolicy.audioBitrateKbps(stream)

    private fun label(format: MediaFormat) = DownloadStreamPolicy.audioFormatLabel(audio("0", format = format))

    private fun extension(format: MediaFormat?) = DownloadStreamPolicy.audioFileExtension(audio("0", format = format))

    private fun language(stream: AudioStream) = DownloadStreamPolicy.audioLanguageLabel(stream)

    private fun trackType(type: AudioTrackType?) =
        DownloadStreamPolicy.audioTrackTypeLabel(audio("0", trackType = type), originalLabel = "Original", dubbedLabel = "Dubbed")

    private fun merge(
        innerTube: List<AudioStream>,
        extractor: List<AudioStream>,
    ) = DownloadStreamPolicy.mergeAudioDownloadStreams(innerTube, extractor)

    private fun pick(
        codec: String,
        audio: List<AudioStream>,
        preferredLang: String? = null,
    ) = DownloadStreamPolicy.pickCompatibleAudioForVideo(codec, audio, preferredLang)

    @Test
    fun `bitrate is reported in kbps from the average bitrate first`() {
        assertThat(kbps(audio("140", averageBitrate = 128_000))).isEqualTo(128)
        assertThat(kbps(audio("140", averageBitrate = 128_000, itagBitrate = 160_000))).isEqualTo(128)
        assertThat(kbps(audio("251", itagBitrate = 160_000))).isEqualTo(160)
        assertThat(kbps(audio("140"))).isEqualTo(0)
        assertThat(kbps(audio("140", averageBitrate = 0))).isEqualTo(0)
    }

    @Test
    fun `values at or below 1000 are taken as kbps already`() {
        // Pins current behaviour: the unit is guessed from the magnitude, so 1000 stays 1000 while
        // 1001 collapses to 1.
        assertThat(kbps(audio("140", averageBitrate = 128))).isEqualTo(128)
        assertThat(kbps(audio("140", averageBitrate = 1_000))).isEqualTo(1_000)
        assertThat(kbps(audio("140", averageBitrate = 1_001))).isEqualTo(1)
    }

    @Test
    fun `format labels come from the mime type then the format name`() {
        assertThat(label(MediaFormat.WEBMA_OPUS)).isEqualTo("OPUS")
        assertThat(label(MediaFormat.OPUS)).isEqualTo("OPUS")
        assertThat(label(MediaFormat.WEBMA)).isEqualTo("WEBM")
        assertThat(label(MediaFormat.WEBM)).isEqualTo("WEBM")
        assertThat(label(MediaFormat.M4A)).isEqualTo("M4A")
        assertThat(label(MediaFormat.MPEG_4)).isEqualTo("M4A")
        assertThat(label(MediaFormat.MP3)).isEqualTo("MP3")
        assertThat(label(MediaFormat.OGG)).isEqualTo("OGG")
        assertThat(label(MediaFormat.FLAC)).isEqualTo("FLAC")
    }

    @Test
    fun `mp2 is labelled mp3 because both share the mpeg mime type`() {
        // Pins current behaviour.
        assertThat(label(MediaFormat.MP2)).isEqualTo("MP3")
    }

    @Test
    fun `an unknown format falls back to the given label`() {
        assertThat(DownloadStreamPolicy.audioFormatLabel(audio("0", format = null))).isEmpty()
        assertThat(DownloadStreamPolicy.audioFormatLabel(audio("0", format = null), unknownLabel = "?")).isEqualTo("?")
    }

    @Test
    fun `file extensions follow the container`() {
        assertThat(extension(MediaFormat.WEBMA_OPUS)).isEqualTo("webm")
        assertThat(extension(MediaFormat.WEBMA)).isEqualTo("webm")
        assertThat(extension(MediaFormat.OPUS)).isEqualTo("ogg")
        assertThat(extension(MediaFormat.OGG)).isEqualTo("ogg")
        assertThat(extension(MediaFormat.MP3)).isEqualTo("mp3")
        assertThat(extension(MediaFormat.MP2)).isEqualTo("mp3")
        assertThat(extension(MediaFormat.M4A)).isEqualTo("m4a")
        assertThat(extension(null)).isEqualTo("m4a")
    }

    @Test
    fun `formats without a mapped container default to m4a`() {
        // Pins current behaviour: a FLAC stream would be written with an m4a extension.
        assertThat(extension(MediaFormat.FLAC)).isEqualTo("m4a")
    }

    @Test
    fun `language label prefers the track name then the locale then the track id`() {
        assertThat(language(audio("140", trackName = "English (US)", locale = Locale.FRENCH, trackId = "fr.1"))).isEqualTo("English (US)")
        assertThat(language(audio("140", locale = Locale.FRENCH, trackId = "fr.1"))).isEqualTo(Locale.FRENCH.displayLanguage)
        assertThat(language(audio("140", trackId = "fr.1"))).isEqualTo("fr.1")
        assertThat(language(audio("140", trackName = "   ", trackId = "fr.1"))).isEqualTo("fr.1")
        assertThat(language(audio("140"))).isNull()
    }

    @Test
    fun `track type labels map original and dubbed to the given strings`() {
        assertThat(trackType(AudioTrackType.ORIGINAL)).isEqualTo("Original")
        assertThat(trackType(AudioTrackType.DUBBED)).isEqualTo("Dubbed")
        assertThat(trackType(null)).isNull()
    }

    @Test
    fun `other track types are title cased from the enum name`() {
        assertThat(trackType(AudioTrackType.DESCRIPTIVE)).isEqualTo("Descriptive")
        assertThat(trackType(AudioTrackType.SECONDARY)).isEqualTo("Secondary")
    }

    @Test
    fun `merge drops streams without content`() {
        val blank = audio("140", content = "")
        val kept = audio("251", format = MediaFormat.WEBMA_OPUS, averageBitrate = 160_000)

        assertThat(merge(listOf(blank), listOf(kept))).containsExactly(kept)
    }

    @Test
    fun `merge keeps the first of two streams with the same label bitrate track and language`() {
        val innerTube = audio("140", averageBitrate = 128_000, locale = Locale.ENGLISH, trackType = AudioTrackType.ORIGINAL)
        val extractor =
            audio(
                "140",
                averageBitrate = 128_400,
                locale = Locale.ENGLISH,
                trackType = AudioTrackType.ORIGINAL,
                content = "https://example.invalid/other",
            )

        assertThat(merge(listOf(innerTube), listOf(extractor))).containsExactly(innerTube)
    }

    @Test
    fun `merge keeps dubs apart even at the same bitrate`() {
        val en = audio("140", averageBitrate = 128_000, locale = Locale.ENGLISH, trackId = "en.0", trackType = AudioTrackType.ORIGINAL)
        val fr = audio("140", averageBitrate = 128_000, locale = Locale.FRENCH, trackId = "fr.1", trackType = AudioTrackType.DUBBED)

        assertThat(merge(listOf(en), listOf(fr))).containsExactly(en, fr).inOrder()
    }

    @Test
    fun `merge orders opus then m4a then mp3 then the rest and higher bitrates first within a format`() {
        val mp3 = audio("0", format = MediaFormat.MP3, averageBitrate = 320_000)
        val m4aLow = audio("139", averageBitrate = 48_000)
        val m4aHigh = audio("141", averageBitrate = 256_000)
        val opus = audio("251", format = MediaFormat.WEBMA_OPUS, averageBitrate = 160_000)
        val flac = audio("1", format = MediaFormat.FLAC, averageBitrate = 900_000)

        assertThat(merge(listOf(mp3, m4aLow), listOf(flac, m4aHigh, opus)))
            .containsExactly(opus, m4aHigh, m4aLow, mp3, flac)
            .inOrder()
    }

    @Test
    fun `mp4 containers take aac even when opus has the higher bitrate`() {
        val opus = audio("251", format = MediaFormat.WEBMA_OPUS, itagBitrate = 160_000)
        val aac = audio("140", itagBitrate = 128_000)

        listOf("h264", "hevc").forEach { codec ->
            assertThat(pick(codec, listOf(opus, aac))).isSameInstanceAs(aac)
        }
    }

    @Test
    fun `webm containers take opus even when aac has the higher bitrate`() {
        val opus = audio("250", format = MediaFormat.WEBMA_OPUS, itagBitrate = 70_000)
        val aac = audio("141", itagBitrate = 256_000)

        listOf("vp9", "av1", "vp8").forEach { codec ->
            assertThat(pick(codec, listOf(aac, opus))).isSameInstanceAs(opus)
        }
    }

    @Test
    fun `webm containers fall back to any audio when there is no opus`() {
        val aac = audio("140", itagBitrate = 128_000)

        assertThat(pick("vp9", listOf(aac))).isSameInstanceAs(aac)
    }

    @Test
    fun `mp4 containers have no last resort`() {
        // Pins current behaviour: an h264 download offered only opus audio gets no audio at all.
        val opus = audio("251", format = MediaFormat.WEBMA_OPUS, itagBitrate = 160_000)

        assertThat(pick("h264", listOf(opus))).isNull()
        assertThat(pick("hevc", listOf(opus))).isNull()
    }

    @Test
    fun `an empty list yields nothing for every container`() {
        assertThat(pick("h264", emptyList())).isNull()
        assertThat(pick("vp9", emptyList())).isNull()
    }

    @Test
    fun `the pick ranks by the itag bitrate not the average bitrate`() {
        // Pins current behaviour: the comparison reads AudioStream.bitrate, which only an ItagItem
        // populates, so a stream with a rich averageBitrate but no ItagItem ranks as 0 kbps.
        val richAverage = audio("140", averageBitrate = 256_000)
        val richItag = audio("139", averageBitrate = 48_000, itagBitrate = 50_000)

        assertThat(pick("h264", listOf(richAverage, richItag))).isSameInstanceAs(richItag)
    }

    @Test
    fun `streams without itag metadata tie and the first one wins`() {
        // Pins current behaviour.
        val first = audio("140", averageBitrate = 48_000)
        val second = audio("141", averageBitrate = 256_000)

        assertThat(pick("h264", listOf(first, second))).isSameInstanceAs(first)
        assertThat(pick("vp9", listOf(first, second))).isSameInstanceAs(first)
    }

    @Test
    fun `a preferred language wins over the original track`() {
        val en = audio("140", itagBitrate = 128_000, locale = Locale.ENGLISH, trackType = AudioTrackType.ORIGINAL)
        val fr = audio("140", itagBitrate = 128_000, locale = Locale.FRANCE, trackType = AudioTrackType.DUBBED)

        assertThat(pick("h264", listOf(en, fr), preferredLang = "fr")).isSameInstanceAs(fr)
        assertThat(pick("h264", listOf(en, fr), preferredLang = "FR")).isSameInstanceAs(fr)
        assertThat(pick("h264", listOf(en, fr), preferredLang = "fr-FR")).isSameInstanceAs(fr)
    }

    @Test
    fun `a regional preference does not match a bare language locale`() {
        // Pins current behaviour: "fr-FR" is compared against the locale's language ("fr") and its
        // full tag ("fr"), neither of which equals it, so the pick falls through to every track.
        val en = audio("140", itagBitrate = 128_000, locale = Locale.ENGLISH, trackType = AudioTrackType.ORIGINAL)
        val fr = audio("140", itagBitrate = 128_000, locale = Locale.FRENCH, trackType = AudioTrackType.DUBBED)

        assertThat(pick("h264", listOf(en, fr), preferredLang = "fr-FR")).isSameInstanceAs(en)
    }

    @Test
    fun `a preferred language with no match ignores the original flag`() {
        // Pins current behaviour: the fallback is every track by bitrate, not the original-first
        // policy used when there is no preference at all.
        val en = audio("140", itagBitrate = 128_000, locale = Locale.ENGLISH, trackType = AudioTrackType.ORIGINAL)
        val frLoud = audio("141", itagBitrate = 256_000, locale = Locale.FRENCH, trackType = AudioTrackType.DUBBED)

        assertThat(pick("h264", listOf(en, frLoud), preferredLang = "de")).isSameInstanceAs(frLoud)
    }

    @Test
    fun `without a preference the original track wins then non dubbed then anything`() {
        val original = audio("140", itagBitrate = 128_000, locale = Locale.ENGLISH, trackType = AudioTrackType.ORIGINAL)
        val dubbed = audio("141", itagBitrate = 256_000, locale = Locale.FRENCH, trackType = AudioTrackType.DUBBED)
        val untyped = audio("139", itagBitrate = 48_000)

        listOf(null, "", "original").forEach { preference ->
            assertThat(pick("h264", listOf(dubbed, original, untyped), preference)).isSameInstanceAs(original)
        }
        assertThat(pick("h264", listOf(dubbed, untyped))).isSameInstanceAs(untyped)
        assertThat(pick("h264", listOf(dubbed))).isSameInstanceAs(dubbed)
    }

    @Test
    fun `the container rule outranks the language filter`() {
        val frOpus =
            audio(
                "251",
                format = MediaFormat.WEBMA_OPUS,
                itagBitrate = 160_000,
                locale = Locale.FRENCH,
                trackType = AudioTrackType.DUBBED,
            )
        val enAac = audio("140", itagBitrate = 128_000, locale = Locale.ENGLISH, trackType = AudioTrackType.ORIGINAL)

        assertThat(pick("h264", listOf(frOpus, enAac), preferredLang = "fr")).isSameInstanceAs(enAac)
        assertThat(pick("vp9", listOf(frOpus, enAac), preferredLang = "en")).isSameInstanceAs(frOpus)
    }

    @Test
    fun `a stream with no format counts as aac compatible`() {
        // Pins current behaviour.
        val unknown = audio("0", format = null, itagBitrate = 1)

        assertThat(pick("h264", listOf(unknown))).isSameInstanceAs(unknown)
    }

    @Test
    fun `the ladder is one row per resolution and codec, tallest first and vp9 ahead of h264 ahead of av1`() {
        val vp91080 = video("248", "1080p")
        val h2641080 = video("137", "1080p")
        val av11080 = video("399", "1080p")
        val vp9720 = video("247", "720p")

        assertThat(build(innerTube = listOf(av11080, h2641080), videoOnly = listOf(vp91080), muxed = listOf(vp9720)))
            .containsExactly(vp91080, h2641080, av11080, vp9720)
            .inOrder()
    }

    @Test
    fun `a stream with no url never reaches the ladder`() {
        // The classic dialog used to list these and then do nothing when one was tapped.
        val playable = video("137", "720p")
        val urlless = video("248", "1080p", content = "")

        assertThat(build(innerTube = listOf(urlless), videoOnly = listOf(playable))).containsExactly(playable)
    }

    @Test
    fun `the first source wins when two carry the same resolution and codec`() {
        val innerTube = video("137", "1080p")
        val extractor = video("137", "1080p", content = "https://example.invalid/extractor")

        assertThat(build(innerTube = listOf(innerTube), videoOnly = listOf(extractor))).containsExactly(innerTube)
    }
}

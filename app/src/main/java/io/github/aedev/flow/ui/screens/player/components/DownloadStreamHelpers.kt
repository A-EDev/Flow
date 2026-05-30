package io.github.aedev.flow.ui.screens.player.components

import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType

object DownloadStreamHelpers {

    fun audioBitrateKbps(stream: AudioStream): Int {
        val raw = stream.averageBitrate.takeIf { it > 0 } ?: stream.bitrate
        return if (raw > 1000) raw / 1000 else raw.coerceAtLeast(0)
    }

    fun audioFormatLabel(stream: AudioStream): String {
        val mime = stream.format?.mimeType.orEmpty().lowercase()
        val name = stream.format?.name.orEmpty().lowercase()
        return when {
            "opus" in mime || "opus" in name -> "OPUS"
            "webm" in mime || "webm" in name -> "WEBM"
            "mp4" in mime || "m4a" in name -> "M4A"
            "mpeg" in mime || "mp3" in name -> "MP3"
            name.isNotBlank() -> name.uppercase()
            else -> "Audio"
        }
    }

    fun audioFileExtension(stream: AudioStream): String {
        val mime = stream.format?.mimeType.orEmpty().lowercase()
        val name = stream.format?.name.orEmpty().lowercase()
        return when {
            "webm" in mime || "webm" in name -> "webm"
            "ogg" in mime || "opus" in name -> "ogg"
            "mpeg" in mime || "mp3" in name -> "mp3"
            else -> "m4a"
        }
    }

    fun audioLanguageLabel(stream: AudioStream): String? {
        return stream.audioTrackName?.takeIf { it.isNotBlank() }
            ?: stream.audioLocale?.displayLanguage?.takeIf { it.isNotBlank() }
            ?: stream.audioTrackId?.takeIf { it.isNotBlank() }
    }

    fun audioTrackTypeLabel(stream: AudioStream): String? {
        return when (stream.audioTrackType) {
            AudioTrackType.ORIGINAL -> "Original"
            AudioTrackType.DUBBED -> "Dubbed"
            null -> null
            else -> stream.audioTrackType?.name?.lowercase()?.replaceFirstChar { it.uppercase() }
        }
    }

    private fun audioFormatSortRank(stream: AudioStream): Int {
        return when (audioFormatLabel(stream)) {
            "OPUS", "WEBM" -> 0
            "M4A" -> 1
            "MP3" -> 2
            else -> 3
        }
    }

    fun mergeAudioDownloadStreams(
        innerTubeStreams: List<AudioStream>,
        extractorStreams: List<AudioStream>
    ): List<AudioStream> {
        return (innerTubeStreams + extractorStreams)
            .filter { it.getContent().isNotBlank() }
            .distinctBy { stream ->
                listOf(
                    audioFormatLabel(stream),
                    audioBitrateKbps(stream).toString(),
                    stream.audioTrackId.orEmpty(),
                    stream.audioLocale?.toLanguageTag().orEmpty(),
                    stream.audioTrackType?.name.orEmpty()
                ).joinToString("|")
            }
            .sortedWith(
                compareBy<AudioStream> { audioFormatSortRank(it) }
                    .thenByDescending { audioBitrateKbps(it) }
                    .thenBy { it.audioLocale?.displayLanguage.orEmpty() }
            )
    }

    fun pickCompatibleAudioForVideo(
        videoCodecKey: String,
        allAudio: List<AudioStream>,
        preferredLang: String?
    ): AudioStream? {
        if (allAudio.isEmpty()) return null
        val isMp4Container = videoCodecKey == "h264" || videoCodecKey == "hevc"

        fun isAacCompatible(a: AudioStream): Boolean {
            val fmt = (a.format?.name ?: "").lowercase()
            val mime = (a.format?.mimeType ?: "").lowercase()
            return !fmt.contains("opus") && !fmt.contains("vorbis") &&
                !fmt.contains("webm") && !mime.contains("opus") &&
                !mime.contains("vorbis") && !mime.contains("webm")
        }

        val langFilteredAudio = if (!preferredLang.isNullOrEmpty() && preferredLang != "original") {
            val langMatches = allAudio.filter {
                it.audioLocale?.language.equals(preferredLang, ignoreCase = true) ||
                    it.audioLocale?.toLanguageTag().equals(preferredLang, ignoreCase = true)
            }
            if (langMatches.isNotEmpty()) langMatches else allAudio
        } else {
            val originals = allAudio.filter { it.audioTrackType == AudioTrackType.ORIGINAL }
            if (originals.isNotEmpty()) originals else {
                val nonDubbed = allAudio.filter { it.audioTrackType != AudioTrackType.DUBBED }
                if (nonDubbed.isNotEmpty()) nonDubbed else allAudio
            }
        }

        return if (isMp4Container) {
            langFilteredAudio.filter { isAacCompatible(it) }.maxByOrNull { it.bitrate }
                ?: allAudio.filter { isAacCompatible(it) }.maxByOrNull { it.bitrate }
        } else {
            val opusFilter: (AudioStream) -> Boolean = { a ->
                val fmt = a.format?.name ?: ""
                val mime = a.format?.mimeType ?: ""
                fmt.contains("webm", true) || mime.contains("audio/webm", true) ||
                    fmt.contains("opus", true) || mime.contains("opus", true)
            }
            langFilteredAudio.filter(opusFilter).maxByOrNull { it.bitrate }
                ?: allAudio.filter(opusFilter).maxByOrNull { it.bitrate }
                ?: langFilteredAudio.maxByOrNull { it.bitrate }
                ?: allAudio.maxByOrNull { it.bitrate }
        }
    }
}

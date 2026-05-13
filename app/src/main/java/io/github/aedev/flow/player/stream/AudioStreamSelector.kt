package io.github.aedev.flow.player.stream

import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import java.util.Locale

object AudioStreamSelector {

    fun selectPreferredAudioStream(
        streams: List<AudioStream>,
        preferredAudioLanguage: String,
        compatibilityFilter: ((AudioStream) -> Boolean)? = null
    ): AudioStream? {
        if (streams.isEmpty()) return null

        val compatibleStreams = compatibilityFilter
            ?.let { filter -> streams.filter(filter).ifEmpty { streams } }
            ?: streams

        val preferredCandidates = preferredCandidates(compatibleStreams, preferredAudioLanguage)
        return preferredCandidates.maxByOrNull { it.averageBitrate }
            ?: compatibleStreams.maxByOrNull { it.averageBitrate }
    }

    private fun preferredCandidates(
        streams: List<AudioStream>,
        preferredAudioLanguage: String
    ): List<AudioStream> {
        val normalizedPreference = preferredAudioLanguage.trim().lowercase(Locale.ROOT)

        if (normalizedPreference.isBlank() || normalizedPreference == "original") {
            val originals = streams.filter { it.audioTrackType == AudioTrackType.ORIGINAL }
            if (originals.isNotEmpty()) return originals

            val nonDubbed = streams.filter { it.audioTrackType != AudioTrackType.DUBBED }
            if (nonDubbed.isNotEmpty()) return nonDubbed

            return streams
        }

        val languageMatches = streams.filter { stream ->
            val localeLanguage = stream.audioLocale?.language.orEmpty()
            val localeTag = stream.audioLocale?.toLanguageTag().orEmpty()
            val trackName = stream.audioTrackName.orEmpty()
            localeLanguage.equals(normalizedPreference, ignoreCase = true) ||
                localeLanguage.startsWith(normalizedPreference, ignoreCase = true) ||
                localeTag.equals(normalizedPreference, ignoreCase = true) ||
                localeTag.startsWith(normalizedPreference, ignoreCase = true) ||
                trackName.contains(normalizedPreference, ignoreCase = true)
        }
        if (languageMatches.isNotEmpty()) return languageMatches

        val originals = streams.filter { it.audioTrackType == AudioTrackType.ORIGINAL }
        if (originals.isNotEmpty()) return originals

        val nonDubbed = streams.filter { it.audioTrackType != AudioTrackType.DUBBED }
        if (nonDubbed.isNotEmpty()) return nonDubbed

        return streams
    }
}
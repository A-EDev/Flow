package io.github.aedev.flow.data.recognition.audio

// Music recognizer is built based on Metrolist's implementation, view https://github.com/MetrolistGroup/Metrolist

/** Raw decoded PCM audio plus the properties needed to validate it before fingerprinting. */
data class DecodedAudio(
    val data: ByteArray,
    val channelCount: Int,
    val sampleRate: Int,
    val pcmEncoding: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DecodedAudio
        return data.contentEquals(other.data) &&
            channelCount == other.channelCount &&
            sampleRate == other.sampleRate &&
            pcmEncoding == other.pcmEncoding
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + channelCount
        result = 31 * result + sampleRate
        result = 31 * result + pcmEncoding
        return result
    }
}

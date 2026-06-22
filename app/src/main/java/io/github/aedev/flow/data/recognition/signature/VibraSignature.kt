package io.github.aedev.flow.data.recognition.signature

/** Entry point for generating a Shazam-compatible fingerprint from PCM audio. */
object VibraSignature {

    const val REQUIRED_SAMPLE_RATE = 16_000

    /** @param samples mono 16-bit signed little-endian PCM at 16 kHz. */
    fun fromI16(samples: ByteArray): String = ShazamSignatureGenerator.fromI16(samples)
}

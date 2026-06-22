package io.github.aedev.flow.data.recognition.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Music recognizer is built based on Metrolist's implementation, view https://github.com/MetrolistGroup/Metrolist

/** Linear-interpolation resampler that converts captured PCM to the fingerprint sample rate. */
object AudioResampler {

    suspend fun resample(
        decodedAudio: DecodedAudio,
        outputSampleRate: Int
    ): Result<DecodedAudio> = withContext(Dispatchers.Default) {
        if (decodedAudio.sampleRate == outputSampleRate) return@withContext Result.success(decodedAudio)

        try {
            val inputSamples = shortArrayFromByteArray(decodedAudio.data)
            val ratio = outputSampleRate.toDouble() / decodedAudio.sampleRate
            val outputLength = (inputSamples.size * ratio).toInt()
            val outputSamples = ShortArray(outputLength)

            for (i in 0 until outputLength) {
                ensureActive()
                val srcPos = i / ratio
                val srcIndex = srcPos.toInt()
                val fraction = srcPos - srcIndex
                outputSamples[i] = if (srcIndex + 1 < inputSamples.size) {
                    (inputSamples[srcIndex] * (1.0 - fraction) + inputSamples[srcIndex + 1] * fraction).toInt().toShort()
                } else {
                    inputSamples[srcIndex]
                }
            }

            Result.success(
                DecodedAudio(
                    data = byteArrayFromShortArray(outputSamples),
                    channelCount = decodedAudio.channelCount,
                    sampleRate = outputSampleRate,
                    pcmEncoding = decodedAudio.pcmEncoding
                )
            )
        } catch (e: Exception) {
            ensureActive()
            Result.failure(e)
        }
    }

    private fun shortArrayFromByteArray(data: ByteArray): ShortArray {
        val shorts = ShortArray(data.size / 2)
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return shorts
    }

    private fun byteArrayFromShortArray(shorts: ShortArray): ByteArray {
        val bytes = ByteArray(shorts.size * 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts)
        return bytes
    }
}

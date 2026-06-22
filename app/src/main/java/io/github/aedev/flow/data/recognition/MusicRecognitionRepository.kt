package io.github.aedev.flow.data.recognition

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.recognition.audio.AudioRecorder
import io.github.aedev.flow.data.recognition.audio.AudioResampler
import io.github.aedev.flow.data.recognition.audio.DecodedAudio
import io.github.aedev.flow.data.recognition.shazam.ShazamClient
import io.github.aedev.flow.data.recognition.signature.VibraSignature
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

// Music recognizer is built based on Metrolist's implementation, view https://github.com/MetrolistGroup/Metrolist

/**
 * Orchestrates one recognition: record → resample → fingerprint → query Shazam.
 * Owns the shared [status] so the screen reflects progress in real time.
 */
@Singleton
class MusicRecognitionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shazamClient: ShazamClient
) {
    private val _status = MutableStateFlow<RecognitionStatus>(RecognitionStatus.Ready)
    val status: StateFlow<RecognitionStatus> = _status.asStateFlow()

    fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun reset() {
        _status.value = RecognitionStatus.Ready
    }

    suspend fun recognize(): RecognitionStatus = withContext(Dispatchers.IO) {
        if (!hasRecordPermission()) {
            return@withContext RecognitionStatus.Error("Microphone access is needed to recognize music.")
                .also { _status.value = it }
        }

        _status.value = RecognitionStatus.Listening
        try {
            val audioData = AudioRecorder.record()
            _status.value = RecognitionStatus.Processing

            val decoded = DecodedAudio(
                data = audioData,
                channelCount = 1,
                sampleRate = AudioRecorder.RECORDING_SAMPLE_RATE,
                pcmEncoding = AudioRecorder.AUDIO_FORMAT
            )
            val resampled = AudioResampler.resample(decoded, VibraSignature.REQUIRED_SAMPLE_RATE)
                .getOrElse { return@withContext fail("Failed to process audio.") }

            require(
                resampled.channelCount == 1 &&
                    resampled.sampleRate == VibraSignature.REQUIRED_SAMPLE_RATE &&
                    resampled.pcmEncoding == AudioFormat.ENCODING_PCM_16BIT &&
                    ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN &&
                    resampled.data.isNotEmpty() && resampled.data.size % 2 == 0
            ) { "Invalid audio format for fingerprint generation" }

            val signature = VibraSignature.fromI16(resampled.data)
            val sampleDurationMs = (resampled.data.size / 2) * 1000L / VibraSignature.REQUIRED_SAMPLE_RATE

            shazamClient.recognize(signature, sampleDurationMs).fold(
                onSuccess = { _status.value = RecognitionStatus.Success(it) },
                onFailure = { error ->
                    val message = error.message ?: "Unknown error"
                    _status.value = if (message.contains("No match", ignoreCase = true)) {
                        RecognitionStatus.NoMatch("No match found. Try again with clearer audio.")
                    } else {
                        RecognitionStatus.Error(mapError(message))
                    }
                }
            )
            _status.value
        } catch (ce: CancellationException) {
            _status.value = RecognitionStatus.Ready
            throw ce
        } catch (e: Exception) {
            fail(e.message ?: "Recognition failed. Try again.")
        }
    }

    private fun fail(message: String): RecognitionStatus =
        RecognitionStatus.Error(message).also { _status.value = it }

    private fun mapError(message: String): String = when {
        message.contains("Too many requests", ignoreCase = true) ->
            "Recognition is busy. Try again in a moment."
        message.contains("timeout", ignoreCase = true) || message.contains("Unable to resolve host", ignoreCase = true) ->
            "Recognition needs an internet connection."
        else -> message
    }
}

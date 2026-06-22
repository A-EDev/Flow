package io.github.aedev.flow.data.recognition.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

// Music recognizer is built based on Metrolist's implementation, view https://github.com/MetrolistGroup/Metrolist

/** Captures a short mono 16-bit PCM clip from the microphone for fingerprinting. */
object AudioRecorder {

    const val RECORDING_SAMPLE_RATE = 44100
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private const val RECORDING_DURATION_MS = 12000L

    @SuppressLint("MissingPermission")
    suspend fun record(): ByteArray = withContext(Dispatchers.IO) {
        val bufferSize = AudioRecord.getMinBufferSize(RECORDING_SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        require(bufferSize > 0) { "Microphone is unavailable on this device" }

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            RECORDING_SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
        check(audioRecord.state == AudioRecord.STATE_INITIALIZED) { "Failed to initialize the microphone" }

        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(bufferSize)
        val startTime = System.currentTimeMillis()
        try {
            audioRecord.startRecording()
            while (System.currentTimeMillis() - startTime < RECORDING_DURATION_MS && isActive) {
                val bytesRead = audioRecord.read(buffer, 0, bufferSize)
                if (bytesRead > 0) outputStream.write(buffer, 0, bytesRead)
            }
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }

        // Respect cancellation triggered while the blocking read loop was running.
        currentCoroutineContext().ensureActive()
        outputStream.toByteArray().also {
            require(it.isNotEmpty()) { "No audio was captured" }
        }
    }
}

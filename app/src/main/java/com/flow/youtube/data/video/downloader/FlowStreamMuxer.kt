package com.flow.youtube.data.video.downloader

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

object FlowStreamMuxer {
    private const val TAG = "FlowStreamMuxer"
    private const val BUFFER_SIZE = 256 * 1024  // 256KB buffer (down from 1MB)

    /**
     * Muxes video and audio streams into a single MP4 file.
     * @param videoPath Path to the temporary video file
     * @param audioPath Path to the temporary audio file
     * @param outputPath Path where the final video should be saved
     * @param onProgress Optional callback with progress (0..1)
     * @return true if successful, false otherwise
     */
    fun mux(
        videoPath: String,
        audioPath: String,
        outputPath: String,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean {
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            // Validate input files exist
            if (!File(videoPath).exists()) {
                Log.e(TAG, "Video file not found: $videoPath")
                return false
            }
            if (!File(audioPath).exists()) {
                Log.e(TAG, "Audio file not found: $audioPath")
                return false
            }

            videoExtractor = MediaExtractor().apply { setDataSource(videoPath) }
            audioExtractor = MediaExtractor().apply { setDataSource(audioPath) }

            // Ensure output directory exists
            File(outputPath).parentFile?.mkdirs()

            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Select video track
            val videoTrackIndex = selectTrack(videoExtractor, "video/")
            if (videoTrackIndex < 0) {
                Log.e(TAG, "No video track found in $videoPath")
                return false
            }
            videoExtractor.selectTrack(videoTrackIndex)
            val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)
            val muxerVideoTrack = muxer.addTrack(videoFormat)

            // Select audio track
            val audioTrackIndex = selectTrack(audioExtractor, "audio/")
            if (audioTrackIndex < 0) {
                Log.e(TAG, "No audio track found in $audioPath")
                return false
            }
            audioExtractor.selectTrack(audioTrackIndex)
            val audioFormat = audioExtractor.getTrackFormat(audioTrackIndex)
            
            // Validate audio codec compatibility with MP4 container
            // MediaMuxer(MUXER_OUTPUT_MPEG_4) only supports AAC audio, NOT Opus/Vorbis
            val audioMime = audioFormat.getString(MediaFormat.KEY_MIME) ?: ""
            if (audioMime.contains("opus", ignoreCase = true) || 
                audioMime.contains("vorbis", ignoreCase = true) ||
                audioMime.contains("webm", ignoreCase = true)) {
                Log.e(TAG, "Audio codec '$audioMime' is incompatible with MP4 container. " +
                    "MediaMuxer requires AAC (audio/mp4a-latm). " +
                    "Ensure the download selects M4A/AAC audio for MP4 video.")
                return false
            }
            
            val muxerAudioTrack = muxer.addTrack(audioFormat)

            muxer.start()

            val buffer = ByteBuffer.allocate(BUFFER_SIZE)
            val bufferInfo = MediaCodec.BufferInfo()

            // Get durations for progress calc
            val videoDuration = videoFormat.getLongOrDefault(MediaFormat.KEY_DURATION, 0L)
            val audioDuration = audioFormat.getLongOrDefault(MediaFormat.KEY_DURATION, 0L)
            val totalDuration = maxOf(videoDuration, audioDuration)

            // Copy video samples
            copySamples(videoExtractor, muxer, muxerVideoTrack, buffer, bufferInfo) { timeUs ->
                if (totalDuration > 0) {
                    onProgress?.invoke(timeUs.toFloat() / totalDuration / 2f) // 0..0.5
                }
            }

            // Copy audio samples
            copySamples(audioExtractor, muxer, muxerAudioTrack, buffer, bufferInfo) { timeUs ->
                if (totalDuration > 0) {
                    onProgress?.invoke(0.5f + timeUs.toFloat() / totalDuration / 2f)
                }
            }

            muxer.stop()
            onProgress?.invoke(1f)
            Log.d(TAG, "Muxing successful: $outputPath (${File(outputPath).length()} bytes)")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Muxing failed", e)
            try {
                File(outputPath).takeIf { it.exists() }?.delete()
            } catch (cleanupErr: Exception) {
                Log.w(TAG, "Failed to clean up partial output", cleanupErr)
            }
            return false
        } finally {
            try { videoExtractor?.release() } catch (_: Exception) {}
            try { audioExtractor?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    /**
     * Extracts just the audio from a video file (no transcoding — raw copy).
     * Useful for audio-only downloads from a combined source.
     */
    fun extractAudio(inputPath: String, outputPath: String): Boolean {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            extractor = MediaExtractor().apply { setDataSource(inputPath) }

            val audioTrackIndex = selectTrack(extractor, "audio/")
            if (audioTrackIndex < 0) {
                Log.e(TAG, "No audio track found in $inputPath")
                return false
            }
            extractor.selectTrack(audioTrackIndex)
            val audioFormat = extractor.getTrackFormat(audioTrackIndex)

            File(outputPath).parentFile?.mkdirs()
            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val buffer = ByteBuffer.allocate(BUFFER_SIZE)
            val bufferInfo = MediaCodec.BufferInfo()
            copySamples(extractor, muxer, muxerTrack, buffer, bufferInfo)

            muxer.stop()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Audio extraction failed", e)
            try { File(outputPath).takeIf { it.exists() }?.delete() } catch (_: Exception) {}
            return false
        } finally {
            try { extractor?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    private fun selectTrack(extractor: MediaExtractor, mimePrefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith(mimePrefix)) {
                return i
            }
        }
        return -1
    }

    private fun copySamples(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        muxerTrackIndex: Int,
        buffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo,
        onSample: ((Long) -> Unit)? = null
    ) {
        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        while (true) {
            bufferInfo.offset = 0
            bufferInfo.size = extractor.readSampleData(buffer, 0)
            if (bufferInfo.size < 0) break

            bufferInfo.presentationTimeUs = extractor.sampleTime
            bufferInfo.flags = extractor.sampleFlags
            muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
            onSample?.invoke(bufferInfo.presentationTimeUs)
            extractor.advance()
        }
    }

    private fun MediaFormat.getLongOrDefault(key: String, default: Long): Long {
        return try {
            getLong(key)
        } catch (_: Exception) {
            default
        }
    }
}

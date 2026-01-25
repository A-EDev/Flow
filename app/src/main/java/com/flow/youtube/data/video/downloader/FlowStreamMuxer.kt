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

    /**
     * Muxes video and audio streams into a single MP4 file.
     * @param videoPath Path to the temporary video file
     * @param audioPath Path to the temporary audio file
     * @param outputPath Path where the final video should be saved
     * @return true if successful, false otherwise
     */
    fun mux(videoPath: String, audioPath: String, outputPath: String): Boolean {
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            videoExtractor = MediaExtractor().apply { setDataSource(videoPath) }
            audioExtractor = MediaExtractor().apply { setDataSource(audioPath) }

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
            val muxerAudioTrack = muxer.addTrack(audioFormat)

            muxer.start()

            // Max buffer size for samples
            val bufferSize = 1024 * 1024
            val buffer = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            // Copy video samples
            copySamples(videoExtractor, muxer, muxerVideoTrack, buffer, bufferInfo)

            // Copy audio samples
            copySamples(audioExtractor, muxer, muxerAudioTrack, buffer, bufferInfo)

            muxer.stop()
            Log.d(TAG, "Muxing successful: $outputPath")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Muxing failed", e)
            return false
        } finally {
            try {
                videoExtractor?.release()
                audioExtractor?.release()
                muxer?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Resource cleanup failed", e)
            }
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
        bufferInfo: MediaCodec.BufferInfo
    ) {
        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        while (true) {
            bufferInfo.offset = 0
            bufferInfo.size = extractor.readSampleData(buffer, 0)
            if (bufferInfo.size < 0) break

            bufferInfo.presentationTimeUs = extractor.sampleTime
            bufferInfo.flags = extractor.sampleFlags
            muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
            extractor.advance()
        }
    }
}

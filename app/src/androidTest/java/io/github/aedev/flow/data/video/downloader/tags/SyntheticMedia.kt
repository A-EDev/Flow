package io.github.aedev.flow.data.video.downloader.tags

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.annotation.OptIn
import androidx.media3.common.util.MediaFormatUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.FragmentedMp4Muxer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/** Encodes short synthetic streams on-device, shaped like YouTube's DASH inputs. */
@OptIn(UnstableApi::class)
internal object SyntheticMedia {
    const val WIDTH = 320
    const val HEIGHT = 240
    private const val FPS = 30
    private const val SAMPLE_RATE = 48_000
    private const val CHANNELS = 2
    private const val PCM_FRAMES_PER_BUFFER = 960
    private const val TIMEOUT_US = 10_000L

    fun hasEncoder(mime: String): Boolean =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
        }

    /** Fragmented MP4, the layout of YouTube's H.264/AV1/AAC DASH streams. */
    fun fragmentedMp4(
        out: File,
        mime: String,
        durationUs: Long = 2_000_000L,
    ) {
        FileOutputStream(out).use { stream ->
            val muxer = FragmentedMp4Muxer.Builder(stream.channel).setFragmentDurationMs(500).build()
            var track = -1
            encode(mime, durationUs) { event ->
                when (event) {
                    is Event.Format -> {
                        track = muxer.addTrack(MediaFormatUtil.createFormatFromMediaFormat(event.format))
                    }

                    is Event.Sample -> {
                        val flags = event.info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME
                        muxer.writeSampleData(track, event.data, BufferInfo(event.info.presentationTimeUs, event.info.size, flags))
                    }
                }
            }
            muxer.close()
        }
    }

    /** WebM, the layout of YouTube's VP9 and Opus DASH streams. */
    fun webm(
        out: File,
        mime: String,
        durationUs: Long = 2_000_000L,
    ) {
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM)
        var track = -1
        encode(mime, durationUs) { event ->
            when (event) {
                is Event.Format -> {
                    track = muxer.addTrack(event.format)
                    muxer.start()
                }

                is Event.Sample -> {
                    muxer.writeSampleData(track, event.data, event.info)
                }
            }
        }
        muxer.stop()
        muxer.release()
    }

    fun jpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    private sealed interface Event {
        class Format(
            val format: MediaFormat,
        ) : Event

        class Sample(
            val data: ByteBuffer,
            val info: MediaCodec.BufferInfo,
        ) : Event
    }

    private fun encode(
        mime: String,
        durationUs: Long,
        sink: (Event) -> Unit,
    ) {
        val isVideo = mime.startsWith("video/")
        val format =
            if (isVideo) {
                MediaFormat.createVideoFormat(mime, WIDTH, HEIGHT).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                    setInteger(MediaFormat.KEY_BIT_RATE, 500_000)
                    setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                }
            } else {
                MediaFormat.createAudioFormat(mime, SAMPLE_RATE, CHANNELS).apply {
                    setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                }
            }
        val frameUs = if (isVideo) 1_000_000L / FPS else PCM_FRAMES_PER_BUFFER * 1_000_000L / SAMPLE_RATE
        val inputCount = (durationUs / frameUs).toInt()
        val inputSize = if (isVideo) WIDTH * HEIGHT * 3 / 2 else PCM_FRAMES_PER_BUFFER * CHANNELS * 2
        val codec = MediaCodec.createEncoderByType(mime)
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var sent = 0
            var inputDone = false
            while (true) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val pts = sent * frameUs
                        if (sent == inputCount) {
                            codec.queueInputBuffer(index, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val buffer = codec.getInputBuffer(index)!!
                            buffer.clear()
                            val size = minOf(inputSize, buffer.capacity())
                            repeat(size) { buffer.put(((it + sent * 7) and 0xFF).toByte()) }
                            codec.queueInputBuffer(index, 0, size, pts, 0)
                            sent++
                        }
                    }
                }
                val out = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    sink(Event.Format(codec.outputFormat))
                } else if (out >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                        val buffer = codec.getOutputBuffer(out)!!
                        buffer.position(info.offset).limit(info.offset + info.size)
                        sink(Event.Sample(buffer, info))
                    }
                    codec.releaseOutputBuffer(out, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        } finally {
            codec.stop()
            codec.release()
        }
    }
}

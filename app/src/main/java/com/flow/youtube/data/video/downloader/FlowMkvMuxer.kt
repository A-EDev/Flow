package com.flow.youtube.data.video.downloader

import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal Matroska/MKV muxer for AV1 + audio raw-stream copy.
 *
 * Android's MediaMuxer only gained AV1-in-MPEG-4 support in API 34 (Android 14).
 * On older devices this class writes a valid .mkv file that ExoPlayer, VLC,
 * MX Player, and most modern Android media players can decode.
 *
 * Supports (no transcoding — raw sample copy only):
 *   Video : V_AV1, V_VP9, V_VP8, V_MPEG4/ISO/AVC, V_MPEGH/ISO/HEVC
 *   Audio : A_AAC, A_OPUS, A_VORBIS
 */
object FlowMkvMuxer {

    private const val TAG = "FlowMkvMuxer"
    private const val SAMPLE_BUF_SIZE   = 8 * 1024 * 1024   // 8 MB initial read buffer (AV1 ≥2000p keyframes can exceed 2 MB)
    private const val CLUSTER_LIMIT_MS  = 5_000L             // new cluster every ~5 s

    // ─────────────────── EBML element ID arrays (big-endian) ─────────────────────────

    private val ID_EBML            = ba(0x1A, 0x45, 0xDF, 0xA3)
    private val ID_DOCTYPE         = ba(0x42, 0x82)
    private val ID_DOCTYPE_VER     = ba(0x42, 0x87)
    private val ID_DOCTYPE_RDVER   = ba(0x42, 0x85)
    private val ID_SEGMENT         = ba(0x18, 0x53, 0x80, 0x67)
    private val ID_SEG_INFO        = ba(0x15, 0x49, 0xA9, 0x66)
    private val ID_TIMECODE_SCALE  = ba(0x2A, 0xD7, 0xB1)
    private val ID_DURATION        = ba(0x44, 0x89)
    private val ID_MUXING_APP      = ba(0x4D, 0x80)
    private val ID_WRITING_APP     = ba(0x57, 0x41)
    private val ID_TRACKS          = ba(0x16, 0x54, 0xAE, 0x6B)
    private val ID_TRACK_ENTRY     = ba(0xAE)
    private val ID_TRACK_NUMBER    = ba(0xD7)
    private val ID_TRACK_UID       = ba(0x73, 0xC5)
    private val ID_TRACK_TYPE      = ba(0x83)
    private val ID_CODEC_ID        = ba(0x86)
    private val ID_CODEC_PRIVATE   = ba(0x63, 0xA2)
    private val ID_VIDEO           = ba(0xE0)
    private val ID_PIXEL_WIDTH     = ba(0xB0)
    private val ID_PIXEL_HEIGHT    = ba(0xBA)
    private val ID_DISPLAY_WIDTH   = ba(0x54, 0xB0)
    private val ID_DISPLAY_HEIGHT  = ba(0x54, 0xBA)
    private val ID_AUDIO           = ba(0xE1)
    private val ID_SAMPLE_FREQ     = ba(0xB5)
    private val ID_CHANNELS        = ba(0x9F)
    private val ID_CLUSTER         = ba(0x1F, 0x43, 0xB6, 0x75)
    private val ID_CLUSTER_TC      = ba(0xE7)
    private val ID_SIMPLE_BLOCK    = ba(0xA3)

    // SeekHead and Cues — provide fast random-access seeking without linear scan
    private val ID_VOID                = ba(0xEC)
    private val ID_SEEK_HEAD           = ba(0x11, 0x4D, 0x9B, 0x74)
    private val ID_SEEK                = ba(0x4D, 0xBB)
    private val ID_SEEK_ID             = ba(0x53, 0xAB)
    private val ID_SEEK_POSITION       = ba(0x53, 0xAC)
    private val ID_CUES                = ba(0x1C, 0x53, 0xBB, 0x6B)
    private val ID_CUE_POINT           = ba(0xBB)
    private val ID_CUE_TIME            = ba(0xB3)
    private val ID_CUE_TRACK_POSITIONS = ba(0xB7)
    private val ID_CUE_TRACK           = ba(0xF7)
    private val ID_CUE_CLUSTER_POS     = ba(0xF1)

    /**
     * VINT "unknown / streaming" size marker for the top-level Segment element.
     * Using unknown size avoids a two-pass encode (we don't know total size up front).
     */
    private val SEGMENT_UNKNOWN_SIZE = byteArrayOf(
        0x01.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
        0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
    )

    /**
     * Exact byte size of the SeekHead element we always write.
     * 3 Seek entries with 8-byte SeekPosition each:
     *   3 × (ID_SEEK 2B + VINT 1B + (ID_SEEK_ID 2B + VINT 1B + 4B id) + (ID_SEEK_POSITION 2B + VINT 1B + 8B pos))
     *   = 3 × 21 = 63B body  +  ID_SEEK_HEAD 4B + VINT 1B  = 68B total.
     */
    private const val SEEK_HEAD_SIZE = 68

    /**
     * Mux [videoPath] and [audioPath] into a Matroska file at [outputPath].
     * Input format is auto-detected via [MediaExtractor] (typically fMP4).
     *
     * @return `true` on success.
     */
    fun mux(videoPath: String, audioPath: String, outputPath: String): Boolean {
        var videoEx: MediaExtractor? = null
        var audioEx: MediaExtractor? = null
        try {
            val vf = File(videoPath)
            val af = File(audioPath)
            if (!vf.exists() || vf.length() == 0L) {
                Log.e(TAG, "Video temp missing/empty: $videoPath (${vf.length()} B)")
                return false
            }
            if (!af.exists() || af.length() == 0L) {
                Log.e(TAG, "Audio temp missing/empty: $audioPath (${af.length()} B)")
                return false
            }
            Log.d(TAG, "FlowMkvMuxer: video=${vf.length()} B  audio=${af.length()} B  → $outputPath")

            videoEx = MediaExtractor().apply { setDataSource(videoPath) }
            audioEx = MediaExtractor().apply { setDataSource(audioPath) }

            val vIdx = selectTrack(videoEx, "video/")
            val aIdx = selectTrack(audioEx, "audio/")
            if (vIdx < 0) { Log.e(TAG, "No video track in $videoPath"); return false }
            if (aIdx < 0) { Log.e(TAG, "No audio track in $audioPath"); return false }

            videoEx.selectTrack(vIdx)
            audioEx.selectTrack(aIdx)

            val vFmt = videoEx.getTrackFormat(vIdx)
            val aFmt = audioEx.getTrackFormat(aIdx)

            val vMime = vFmt.getString(MediaFormat.KEY_MIME) ?: ""
            val aMime = aFmt.getString(MediaFormat.KEY_MIME) ?: ""

            val vCodecId = mimeToVideoMkvId(vMime)
                ?: run { Log.e(TAG, "Unsupported video MIME for MKV: $vMime"); return false }
            val aCodecId = mimeToAudioMkvId(aMime)
                ?: run { Log.e(TAG, "Unsupported audio MIME for MKV: $aMime"); return false }

            Log.d(TAG, "MKV codec IDs: video=$vCodecId  audio=$aCodecId")

            val vCsd      = readCsd(vFmt, "csd-0")
            val aCsd      = readCsd(aFmt, "csd-0")
            val width     = tryGet { vFmt.getInteger(MediaFormat.KEY_WIDTH) }  ?: 0
            val height    = tryGet { vFmt.getInteger(MediaFormat.KEY_HEIGHT) } ?: 0
            val sampleRate = tryGet { aFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) }   ?: 44100
            val channels   = tryGet { aFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) } ?: 2

            val vDurUs = tryGet { vFmt.getLong(MediaFormat.KEY_DURATION) } ?: 0L
            val aDurUs = tryGet { aFmt.getLong(MediaFormat.KEY_DURATION) } ?: 0L
            val durationMs = maxOf(vDurUs, aDurUs) / 1000L

            File(outputPath).parentFile?.mkdirs()

            var segmentDataStart = 0L
            var segInfoRelPos    = 0L
            var tracksRelPos     = 0L
            var cuesRelPos       = 0L
            val cuePoints        = mutableListOf<Pair<Long, Long>>()

            FileOutputStream(outputPath).use { fos ->
                val cos = CountingOutputStream(fos)
                val out = BufferedOutputStream(cos, 4 * 1024 * 1024)

                // 1. EBML file header
                writeEbmlHeader(out)

                // 2. Segment (unknown size)
                out.write(ID_SEGMENT)
                out.write(SEGMENT_UNKNOWN_SIZE)
                out.flush()
                segmentDataStart = cos.count 

                // 3. Void placeholder — will be overwritten with SeekHead after Cues are known
                writeVoid(out, SEEK_HEAD_SIZE)
                out.flush()

                // 4. SegmentInfo
                segInfoRelPos = cos.count - segmentDataStart
                writeSegmentInfo(out, durationMs)
                out.flush()

                // 5. Tracks
                tracksRelPos = cos.count - segmentDataStart
                writeTracks(
                    out,
                    vCodecId, vCsd, width, height,
                    aCodecId, aCsd, sampleRate, channels
                )
                out.flush()

                // 6. Clusters — also populates cuePoints with (timestampMs, segRelOffset)
                writeClustersCollectCues(out, cos, segmentDataStart, videoEx, audioEx, cuePoints)
                out.flush()

                // 7. Cues element written after all clusters
                cuesRelPos = cos.count - segmentDataStart
                out.write(buildCues(cuePoints))
                out.flush()
            }

            // 8. Seek back to the Void placeholder and overwrite it with the real SeekHead
            RandomAccessFile(outputPath, "rw").use { raf ->
                raf.seek(segmentDataStart)
                val seekHeadBytes = buildSeekHead(segInfoRelPos, tracksRelPos, cuesRelPos)
                check(seekHeadBytes.size == SEEK_HEAD_SIZE) {
                    "SeekHead size mismatch: ${seekHeadBytes.size} != $SEEK_HEAD_SIZE"
                }
                raf.write(seekHeadBytes)
            }

            Log.d(TAG, "FlowMkvMuxer: success → $outputPath (${File(outputPath).length()} B)")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "FlowMkvMuxer: mux failed", e)
            try { File(outputPath).let { if (it.exists()) it.delete() } } catch (_: Exception) {}
            return false
        } finally {
            try { videoEx?.release() } catch (_: Exception) {}
            try { audioEx?.release() } catch (_: Exception) {}
        }
    }

    // ──────────────────────────── EBML structure writers ─────────────────────────────

    private fun writeEbmlHeader(out: OutputStream) {
        val body = ByteArrayOutputStream().also { buf ->
            el(buf, ID_DOCTYPE,       "matroska".utf8())
            el(buf, ID_DOCTYPE_VER,   intBytes(4L, 1))
            el(buf, ID_DOCTYPE_RDVER, intBytes(2L, 1))
        }.toByteArray()
        writeEl(out, ID_EBML, body)
    }

    private fun writeSegmentInfo(out: OutputStream, durationMs: Long) {
        val appTag = "FlowMkvMuxer".utf8()
        val body = ByteArrayOutputStream().also { buf ->
            // TimecodeScale = 1 ms per timecode unit
            el(buf, ID_TIMECODE_SCALE, intBytes(1_000_000L, 4))
            el(buf, ID_MUXING_APP,  appTag)
            el(buf, ID_WRITING_APP, appTag)
            if (durationMs > 0L) el(buf, ID_DURATION, doubleBytes(durationMs.toDouble()))
        }.toByteArray()
        writeEl(out, ID_SEG_INFO, body)
    }

    private fun writeVoid(out: OutputStream, totalBytes: Int) {
        require(totalBytes >= 2 && totalBytes - 2 <= 126)
        out.write(0xEC)                            // ID_VOID
        out.write(0x80 or (totalBytes - 2))        // VINT-encoded fill length
        repeat(totalBytes - 2) { out.write(0) }
    }

    private fun buildSeekHead(segInfoRelPos: Long, tracksRelPos: Long, cuesRelPos: Long): ByteArray {
        fun oneSeek(elementId: ByteArray, relPos: Long): ByteArray {
            val idEl  = wrapEl(ID_SEEK_ID,       elementId)
            val posEl = wrapEl(ID_SEEK_POSITION, intBytes(relPos, 8))
            return wrapEl(ID_SEEK, idEl + posEl)
        }
        val body = oneSeek(ID_SEG_INFO, segInfoRelPos) +
                   oneSeek(ID_TRACKS,   tracksRelPos)  +
                   oneSeek(ID_CUES,     cuesRelPos)
        return wrapEl(ID_SEEK_HEAD, body)
    }

    /** Build the Cues element from collected (timestampMs, segmentRelativeOffset) pairs. */
    private fun buildCues(cuePoints: List<Pair<Long, Long>>): ByteArray {
        val buf = ByteArrayOutputStream()
        for ((timeMs, clusterSegRelPos) in cuePoints) {
            val cueTimeEl     = wrapEl(ID_CUE_TIME,  intBytes(timeMs, 4))
            val cueTrackEl    = wrapEl(ID_CUE_TRACK, intBytes(1L, 1))
            val cuePosEl      = wrapEl(ID_CUE_CLUSTER_POS, intBytes(clusterSegRelPos, 8))
            val cueTrackPosEl = wrapEl(ID_CUE_TRACK_POSITIONS, cueTrackEl + cuePosEl)
            buf.write(wrapEl(ID_CUE_POINT, cueTimeEl + cueTrackPosEl))
        }
        return wrapEl(ID_CUES, buf.toByteArray())
    }

    private fun writeTracks(
        out: OutputStream,
        videoCodecId: String, videoCsd: ByteArray?,
        width: Int, height: Int,
        audioCodecId: String, audioCsd: ByteArray?,
        sampleRate: Int, channels: Int
    ) {
        val videoTrackBody = ByteArrayOutputStream().also { buf ->
            el(buf, ID_TRACK_NUMBER, intBytes(1L, 1))
            el(buf, ID_TRACK_UID,    intBytes(1L, 4))
            el(buf, ID_TRACK_TYPE,   byteArrayOf(0x01))  
            el(buf, ID_CODEC_ID,     videoCodecId.utf8())
            if (videoCsd != null && videoCsd.isNotEmpty()) el(buf, ID_CODEC_PRIVATE, videoCsd)
            if (width > 0 && height > 0) {
                val videoEl = ByteArrayOutputStream().also { vBuf ->
                    el(vBuf, ID_PIXEL_WIDTH,    intBytes(width.toLong(),  2))
                    el(vBuf, ID_PIXEL_HEIGHT,   intBytes(height.toLong(), 2))
                    el(vBuf, ID_DISPLAY_WIDTH,  intBytes(width.toLong(),  2))
                    el(vBuf, ID_DISPLAY_HEIGHT, intBytes(height.toLong(), 2))
                }.toByteArray()
                el(buf, ID_VIDEO, videoEl)
            }
        }.toByteArray()

        val audioTrackBody = ByteArrayOutputStream().also { buf ->
            el(buf, ID_TRACK_NUMBER, intBytes(2L, 1))
            el(buf, ID_TRACK_UID,    intBytes(2L, 4))
            el(buf, ID_TRACK_TYPE,   byteArrayOf(0x02))  
            el(buf, ID_CODEC_ID,     audioCodecId.utf8())
            if (audioCsd != null && audioCsd.isNotEmpty()) el(buf, ID_CODEC_PRIVATE, audioCsd)
            val audioEl = ByteArrayOutputStream().also { aBuf ->
                el(aBuf, ID_SAMPLE_FREQ, floatBytes(sampleRate.toFloat()))
                el(aBuf, ID_CHANNELS,    intBytes(channels.toLong(), 1))
            }.toByteArray()
            el(buf, ID_AUDIO, audioEl)
        }.toByteArray()

        val tracksBody = wrapEl(ID_TRACK_ENTRY, videoTrackBody) +
                         wrapEl(ID_TRACK_ENTRY, audioTrackBody)
        writeEl(out, ID_TRACKS, tracksBody)
    }

    /**
     * Produce interleaved Cluster → SimpleBlock output, sorted ascending by presentation time.
     * A new cluster is started every [CLUSTER_LIMIT_MS] at a video keyframe boundary.
     */
    private fun writeClustersCollectCues(
        out: OutputStream,
        cos: CountingOutputStream,
        segDataStart: Long,
        videoEx: MediaExtractor,
        audioEx: MediaExtractor,
        cuePoints: MutableList<Pair<Long, Long>>
    ) {
        videoEx.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        audioEx.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

        var sampleBuf = ByteBuffer.allocate(SAMPLE_BUF_SIZE)

        fun readSampleBytes(ex: MediaExtractor): ByteArray? {
            sampleBuf.clear()
            val size = ex.readSampleData(sampleBuf, 0)
            if (size < 0) return null
            if (size > sampleBuf.capacity()) {
                Log.d(TAG, "Oversized sample ($size B > ${sampleBuf.capacity()} B) — growing buffer")
                sampleBuf = ByteBuffer.allocate(size + (size shr 2))
                val retry = ex.readSampleData(sampleBuf, 0)
                if (retry < 0) return null
                sampleBuf.rewind()
                return ByteArray(retry).also { sampleBuf.get(it, 0, retry) }
            }
            sampleBuf.rewind()
            return ByteArray(size).also { sampleBuf.get(it, 0, size) }
        }

        var videoDone = false
        var audioDone = false
        var videoTimeUs = readNextTime(videoEx) { videoDone = true }
        var audioTimeUs = readNextTime(audioEx) { audioDone = true }

        var clusterStartMs  = -1L
        var clusterBodyBuf  = ByteArrayOutputStream()

        fun flushCluster() {
            if (clusterStartMs < 0L || clusterBodyBuf.size() == 0) return
            out.flush()
            cuePoints.add(Pair(clusterStartMs, cos.count - segDataStart))
            val tcEl      = wrapEl(ID_CLUSTER_TC, intBytes(clusterStartMs, 4))
            val body      = tcEl + clusterBodyBuf.toByteArray()
            writeEl(out, ID_CLUSTER, body)
            clusterBodyBuf  = ByteArrayOutputStream()
            clusterStartMs  = -1L
        }

        fun appendBlock(trackNum: Int, timeUs: Long, isKey: Boolean, data: ByteArray) {
            val timeMs = timeUs / 1000L
            if (clusterStartMs < 0L) clusterStartMs = timeMs

            if ((timeMs - clusterStartMs) > CLUSTER_LIMIT_MS && isKey) {
                flushCluster()
                clusterStartMs = timeMs
            }

            val relMs: Int = (timeMs - clusterStartMs).coerceIn(-32768L, 32767L).toInt()

            val flags: Byte = if (isKey && trackNum == 1) 0x80.toByte() else 0x00.toByte()
            val header = byteArrayOf(
                (0x80 or trackNum).toByte(),
                ((relMs ushr 8) and 0xFF).toByte(),
                (relMs         and 0xFF).toByte(),
                flags
            )
            val blockData = header + data
            clusterBodyBuf.write(wrapEl(ID_SIMPLE_BLOCK, blockData))
        }

        while (!videoDone || !audioDone) {
            val takeVideo = !videoDone && (audioDone || videoTimeUs <= audioTimeUs)

            if (takeVideo) {
                val data = readSampleBytes(videoEx)
                if (data == null) {
                    videoDone = true; videoTimeUs = Long.MAX_VALUE
                } else {
                    val isKey = (videoEx.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                    appendBlock(1, videoTimeUs, isKey, data)
                    videoEx.advance()
                    videoTimeUs = readNextTime(videoEx) { videoDone = true }
                }
            } else {
                val data = readSampleBytes(audioEx)
                if (data == null) {
                    audioDone = true; audioTimeUs = Long.MAX_VALUE
                } else {
                    appendBlock(2, audioTimeUs, true, data)
                    audioEx.advance()
                    audioTimeUs = readNextTime(audioEx) { audioDone = true }
                }
            }
        }
        flushCluster()
    }

    // ────────────────────────────── codec ID maps ────────────────────────────────────

    private fun mimeToVideoMkvId(mime: String): String? {
        val m = mime.lowercase()
        return when {
            "av01" in m || "av1" in m                                    -> "V_AV1"
            "vp9"  in m || "vp09" in m                                   -> "V_VP9"
            "vp8"  in m || "vp08" in m                                   -> "V_VP8"
            "avc"  in m || "h264" in m || "h.264" in m                   -> "V_MPEG4/ISO/AVC"
            "hevc" in m || "hev1" in m || "hvc1" in m ||
                "h265" in m || "h.265" in m                              -> "V_MPEGH/ISO/HEVC"
            else -> null
        }
    }

    private fun mimeToAudioMkvId(mime: String): String? {
        val m = mime.lowercase()
        return when {
            "mp4a" in m || "aac" in m -> "A_AAC"
            "opus"    in m            -> "A_OPUS"
            "vorbis"  in m            -> "A_VORBIS"
            "mp3" in m || "mpeg" in m -> "A_MPEG/L3"
            else -> null
        }
    }

    // ─────────────────────────── EBML encoding helpers ───────────────────────────────

    /**
     * Write one EBML element (ID + VINT size + data) to a [ByteArrayOutputStream].
     * Using an explicit buffer parameter avoids Kotlin's private-extension-in-inline-lambda issue.
     */
    private fun el(buf: ByteArrayOutputStream, id: ByteArray, data: ByteArray) {
        buf.write(id)
        buf.write(encodeVint(data.size.toLong()))
        buf.write(data)
    }

    /** Write an EBML element directly to an [OutputStream]. */
    private fun writeEl(out: OutputStream, id: ByteArray, data: ByteArray) {
        out.write(id)
        out.write(encodeVint(data.size.toLong()))
        out.write(data)
    }

    /** Return a complete EBML element as a [ByteArray] (ID + VINT size + data). */
    private fun wrapEl(id: ByteArray, data: ByteArray): ByteArray =
        id + encodeVint(data.size.toLong()) + data

    /**
     * EBML variable-length integer encoding for element SIZE fields.
     * Follows the spec: leading bits indicate width; all-ones is "unknown size".
     */
    private fun encodeVint(value: Long): ByteArray = when {
        value < 0x7FL    -> byteArrayOf((0x80L or value).toByte())
        value < 0x3FFFL  -> byteArrayOf(
            (0x40L or (value shr 8)).toByte(),
            (value and 0xFFL).toByte()
        )
        value < 0x1FFFFFL -> byteArrayOf(
            (0x20L or (value shr 16)).toByte(),
            ((value shr 8)  and 0xFFL).toByte(),
            (value          and 0xFFL).toByte()
        )
        value < 0x0FFFFFFFL -> byteArrayOf(
            (0x10L or (value shr 24)).toByte(),
            ((value shr 16) and 0xFFL).toByte(),
            ((value shr 8)  and 0xFFL).toByte(),
            (value          and 0xFFL).toByte()
        )
        else -> byteArrayOf(
            0x01,
            ((value shr 48) and 0xFFL).toByte(),
            ((value shr 40) and 0xFFL).toByte(),
            ((value shr 32) and 0xFFL).toByte(),
            ((value shr 24) and 0xFFL).toByte(),
            ((value shr 16) and 0xFFL).toByte(),
            ((value shr 8)  and 0xFFL).toByte(),
            (value          and 0xFFL).toByte()
        )
    }

    /**
     * Encode [value] as a big-endian [byteCount]-byte integer.
     * Works by extracting the last [byteCount] bytes of a BE long representation.
     */
    private fun intBytes(value: Long, byteCount: Int): ByteArray =
        ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(value).array()
            .copyOfRange(8 - byteCount, 8)

    /** IEEE 754 single-precision float as 4 big-endian bytes. */
    private fun floatBytes(value: Float): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putFloat(value).array()

    /** IEEE 754 double-precision float as 8 big-endian bytes. */
    private fun doubleBytes(value: Double): ByteArray =
        ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putDouble(value).array()

    private fun String.utf8(): ByteArray = toByteArray(Charsets.UTF_8)

    // ─────────────────────────── MediaExtractor helpers ─────────────────────────────

    private fun selectTrack(ex: MediaExtractor, mimePrefix: String): Int {
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(mimePrefix)) return i
        }
        return -1
    }

    /**
     * Read the presentation time of the *current* sample.
     * If there is no more data (sampleTime == -1), call [onDone] and return [Long.MAX_VALUE].
     */
    private inline fun readNextTime(ex: MediaExtractor, onDone: () -> Unit): Long {
        val t = ex.sampleTime
        return if (t >= 0L) t else { onDone(); Long.MAX_VALUE }
    }

    /** Read [size] bytes from a [ByteBuffer] starting at position 0 (post-readSampleData). */
    private fun ByteBuffer.extractBytes(size: Int): ByteArray {
        rewind()
        return ByteArray(size).also { get(it, 0, size) }
    }

    private fun readCsd(fmt: MediaFormat, key: String): ByteArray? {
        return try {
            val bb = fmt.getByteBuffer(key) ?: return null
            ByteArray(bb.remaining()).also { bb.get(it) }.takeIf { it.isNotEmpty() }
        } catch (_: Exception) { null }
    }

    private inline fun <T> tryGet(block: () -> T): T? = try { block() } catch (_: Exception) { null }

    /** Create a ByteArray from vararg Int values (only low byte of each is used). */
    private fun ba(vararg v: Int): ByteArray = ByteArray(v.size) { v[it].toByte() }

    private class CountingOutputStream(private val delegate: OutputStream) : OutputStream() {
        var count = 0L
        override fun write(b: Int)                           { delegate.write(b);           count++        }
        override fun write(b: ByteArray)                     { delegate.write(b);           count += b.size }
        override fun write(b: ByteArray, off: Int, len: Int) { delegate.write(b, off, len); count += len   }
        override fun flush()  = delegate.flush()
        override fun close()  = delegate.close()
    }
}

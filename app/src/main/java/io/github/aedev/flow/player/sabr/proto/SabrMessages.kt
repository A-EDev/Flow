package io.github.aedev.flow.player.sabr.proto

data class FormatId(
    val itag: Int = 0,
    val lmt: Long = 0
) {
    fun encode(): ByteArray = ProtobufWriter.encode {
        if (itag != 0) writeInt32(1, itag)
        if (lmt != 0L) writeInt64(2, lmt)
    }

    companion object {
        fun decode(data: ByteArray): FormatId {
            val reader = ProtobufReader(data)
            var itag = 0
            var lmt = 0L
            reader.forEachField { field ->
                when (field.fieldNumber) {
                    1 -> itag = field.asInt()
                    2 -> lmt = field.asLong()
                }
            }
            return FormatId(itag, lmt)
        }
    }
}

data class MediaHeader(
    val headerId: Int = 0,
    val videoId: String = "",
    val itag: Int = 0,
    val lmt: Long = 0,
    val startDataRange: Long = 0,
    val sequenceNumber: Int = 0,
    val contentLength: Long = 0,
    val timeRangeStartMs: Long = 0,
    val durationMs: Long = 0,
    val formatId: FormatId? = null,
    val compressionType: Int = 0
) {
    companion object {
        fun decode(data: ByteArray): MediaHeader {
            val reader = ProtobufReader(data)
            var headerId = 0; var videoId = ""; var itag = 0; var lmt = 0L
            var startDataRange = 0L; var sequenceNumber = 0; var contentLength = 0L
            var timeRangeStartMs = 0L; var durationMs = 0L
            var formatId: FormatId? = null; var compressionType = 0

            reader.forEachField { field ->
                when (field.fieldNumber) {
                    1 -> headerId = field.asInt()
                    2 -> videoId = field.asString()
                    3 -> itag = field.asInt()
                    4 -> lmt = field.asLong()
                    6 -> startDataRange = field.asLong()
                    7 -> sequenceNumber = field.asInt()
                    8 -> timeRangeStartMs = field.asLong()
                    9 -> durationMs = field.asLong()
                    13 -> formatId = FormatId.decode(field.asBytes())
                    14 -> contentLength = field.asLong()
                    15 -> compressionType = field.asInt()
                }
            }
            return MediaHeader(headerId, videoId, itag, lmt, startDataRange,
                sequenceNumber, contentLength, timeRangeStartMs, durationMs,
                formatId, compressionType)
        }
    }
}

data class FormatInitializationMetadata(
    val formatId: FormatId? = null,
    val mimeType: String = "",
    val codecs: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val fps: Int = 0,
    val sampleRate: Int = 0,
    val channelCount: Int = 0,
    val bitrate: Long = 0,
    val durationMs: Long = 0,
    val initData: ByteArray = ByteArray(0),
    val segmentCount: Int = 0,
    val qualityLabel: String = ""
) {
    val isAudio: Boolean get() = mimeType.startsWith("audio/")
    val isVideo: Boolean get() = mimeType.startsWith("video/")

    companion object {
        fun decode(data: ByteArray): FormatInitializationMetadata {
            val reader = ProtobufReader(data)
            var formatId: FormatId? = null; var mimeType = ""; var codecs = ""
            var width = 0; var height = 0; var fps = 0
            var sampleRate = 0; var channelCount = 0; var bitrate = 0L
            var durationMs = 0L; var initData = ByteArray(0)
            var segmentCount = 0; var qualityLabel = ""

            reader.forEachField { field ->
                when (field.fieldNumber) {
                    1 -> formatId = FormatId.decode(field.asBytes())
                    2 -> mimeType = field.asString()
                    3 -> codecs = field.asString()
                    4 -> width = field.asInt()
                    5 -> height = field.asInt()
                    6 -> fps = field.asInt()
                    7 -> sampleRate = field.asInt()
                    8 -> channelCount = field.asInt()
                    9 -> bitrate = field.asLong()
                    10 -> durationMs = field.asLong()
                    11 -> initData = field.asBytes()
                    12 -> segmentCount = field.asInt()
                    13 -> qualityLabel = field.asString()
                }
            }
            return FormatInitializationMetadata(formatId, mimeType, codecs, width, height,
                fps, sampleRate, channelCount, bitrate, durationMs, initData, segmentCount,
                qualityLabel)
        }
    }
}

data class NextRequestPolicy(
    val backoffTimeMs: Long = 0,
    val playbackCookie: ByteArray = ByteArray(0),
    val maxRequestSize: Int = 0
) {
    companion object {
        fun decode(data: ByteArray): NextRequestPolicy {
            val reader = ProtobufReader(data)
            var backoffTimeMs = 0L
            var playbackCookie = ByteArray(0)
            var maxRequestSize = 0
            reader.forEachField { field ->
                when (field.fieldNumber) {
                    1 -> backoffTimeMs = field.asLong()
                    2 -> playbackCookie = field.asBytes()
                    3 -> maxRequestSize = field.asInt()
                }
            }
            return NextRequestPolicy(backoffTimeMs, playbackCookie, maxRequestSize)
        }
    }
}

data class SabrRedirect(val url: String = "") {
    companion object {
        fun decode(data: ByteArray): SabrRedirect {
            val reader = ProtobufReader(data)
            var url = ""
            reader.forEachField { field ->
                when (field.fieldNumber) {
                    1 -> url = field.asString()
                }
            }
            return SabrRedirect(url)
        }
    }
}

data class SabrError(
    val errorCode: Int = 0,
    val errorMessage: String = "",
    val isRecoverable: Boolean = false
) {
    companion object {
        fun decode(data: ByteArray): SabrError {
            val reader = ProtobufReader(data)
            var code = 0; var msg = ""; var recoverable = false
            reader.forEachField { field ->
                when (field.fieldNumber) {
                    1 -> code = field.asInt()
                    2 -> msg = field.asString()
                    3 -> recoverable = field.asBool()
                }
            }
            return SabrError(code, msg, recoverable)
        }
    }
}

data class SabrSeek(val seekTargetMs: Long = 0) {
    companion object {
        fun decode(data: ByteArray): SabrSeek {
            val reader = ProtobufReader(data)
            var target = 0L
            reader.forEachField { field ->
                when (field.fieldNumber) {
                    1 -> target = field.asLong()
                }
            }
            return SabrSeek(target)
        }
    }
}

data class SabrContextUpdate(val context: ByteArray = ByteArray(0)) {
    companion object {
        fun decode(data: ByteArray): SabrContextUpdate {
            val reader = ProtobufReader(data)
            var ctx = ByteArray(0)
            reader.forEachField { field ->
                when (field.fieldNumber) {
                    1 -> ctx = field.asBytes()
                }
            }
            return SabrContextUpdate(ctx)
        }
    }
}

data class StreamProtectionStatus(
    val status: Int = 0,
    val reason: String = ""
) {
    companion object {
        const val STATUS_OK = 1
        const val STATUS_REQUIRES_RELOAD = 2

        fun decode(data: ByteArray): StreamProtectionStatus {
            val reader = ProtobufReader(data)
            var status = 0; var reason = ""
            reader.forEachField { field ->
                when (field.fieldNumber) {
                    1 -> status = field.asInt()
                    2 -> reason = field.asString()
                }
            }
            return StreamProtectionStatus(status, reason)
        }
    }
}

data class PlaybackStartPolicy(val minBufferBeforePlaybackMs: Long = 0) {
    companion object {
        fun decode(data: ByteArray): PlaybackStartPolicy {
            val reader = ProtobufReader(data)
            var minBuffer = 0L
            reader.forEachField { field ->
                when (field.fieldNumber) {
                    1 -> minBuffer = field.asLong()
                }
            }
            return PlaybackStartPolicy(minBuffer)
        }
    }
}

//  ClientAbrState — field numbers per LuanRT/googlevideo `client_abr_state.proto`
data class ClientAbrState(
    val playerTimeMs: Long = 0,            // field 28 (playhead)
    val bandwidthEstimateBps: Long = 0,    // field 23
    val viewportWidthPx: Int = 0,          // field 18
    val viewportHeightPx: Int = 0,         // field 19
    val timeSinceLastSeekMs: Long = 0,     // field 29
    val enabledTrackTypesBitfield: Int = -1, // field 40 (-1 = unset → server defaults to audio+video)
    val drcEnabled: Boolean = false        // field 46
) {
    fun encode(): ByteArray = ProtobufWriter.encode {
        if (viewportWidthPx != 0) writeInt32(18, viewportWidthPx)
        if (viewportHeightPx != 0) writeInt32(19, viewportHeightPx)
        if (bandwidthEstimateBps != 0L) writeInt64(23, bandwidthEstimateBps)
        if (playerTimeMs != 0L) writeInt64(28, playerTimeMs)
        if (timeSinceLastSeekMs != 0L) writeInt64(29, timeSinceLastSeekMs)
        if (enabledTrackTypesBitfield >= 0) writeInt32(40, enabledTrackTypesBitfield)
        if (drcEnabled) writeBool(46, true)
    }
}

data class FormatBufferedRange(
    val formatId: FormatId,
    val startTimeMs: Long = 0,
    val durationMs: Long = 0,
    val startSequence: Int = 0,
    val endSequence: Int = 0
) {
    fun encode(): ByteArray = ProtobufWriter.encode {
        writeBytes(1, formatId.encode())
        if (startTimeMs != 0L) writeInt64(2, startTimeMs)
        if (durationMs != 0L) writeInt64(3, durationMs)
        if (startSequence != 0) writeInt32(4, startSequence)
        if (endSequence != 0) writeInt32(5, endSequence)
    }
}

data class ClientScreenInfo(
    val screenWidthPixels: Int = 0,
    val screenHeightPixels: Int = 0,
    val screenDensity: Float = 0f
) {
    fun encode(): ByteArray = ProtobufWriter.encode {
        if (screenWidthPixels != 0) writeInt32(1, screenWidthPixels)
        if (screenHeightPixels != 0) writeInt32(2, screenHeightPixels)
        if (screenDensity != 0f) writeFloat(3, screenDensity)
    }
}

/**
 * ClientInfo — nested inside [StreamerContext]. Field numbers per
 * LuanRT/googlevideo `streamer_context.proto`. For the WEB client, [clientName] = 1.
 */
data class ClientInfo(
    val clientName: Int = 1,          // field 16 (WEB = 1)
    val clientVersion: String = "",   // field 17
    val osName: String = "",          // field 18
    val osVersion: String = "",       // field 19
    val deviceMake: String = "",      // field 12
    val deviceModel: String = ""      // field 13
) {
    fun encode(): ByteArray = ProtobufWriter.encode {
        if (deviceMake.isNotEmpty()) writeString(12, deviceMake)
        if (deviceModel.isNotEmpty()) writeString(13, deviceModel)
        writeInt32(16, clientName)
        if (clientVersion.isNotEmpty()) writeString(17, clientVersion)
        if (osName.isNotEmpty()) writeString(18, osName)
        if (osVersion.isNotEmpty()) writeString(19, osVersion)
    }
}

// A single SABR context to send back to the server ({type, value})
data class SabrContext(
    val type: Int = 0,                   
    val value: ByteArray = ByteArray(0) 
) {
    fun encode(): ByteArray = ProtobufWriter.encode {
        if (type != 0) writeInt32(1, type)
        if (value.isNotEmpty()) writeBytes(2, value)
    }
}

/**
 * StreamerContext (request field 19) — carries the client identity AND the PoToken.
 * The GVS/streaming PoToken is sent HERE as base64-decoded bytes (field 2), not as a
 * top-level string nor a `&pot=` URL param
 */
data class StreamerContext(
    val clientInfo: ClientInfo? = null,            // field 1
    val poToken: ByteArray = ByteArray(0),         // field 2 (bytes)
    val playbackCookie: ByteArray = ByteArray(0),  // field 3
    val sabrContexts: List<SabrContext> = emptyList() // field 5 (repeated)
) {
    fun encode(): ByteArray = ProtobufWriter.encode {
        clientInfo?.let { writeBytes(1, it.encode()) }
        if (poToken.isNotEmpty()) writeBytes(2, poToken)
        if (playbackCookie.isNotEmpty()) writeBytes(3, playbackCookie)
        sabrContexts.forEach { writeBytes(5, it.encode()) }
    }
}

 // VideoPlaybackAbrRequest — field numbers per LuanRT/googlevideo `video_playback_abr_request.proto`
 
data class VideoPlaybackAbrRequest(
    val clientAbrState: ClientAbrState? = null,             // field 1
    val selectedFormatIds: List<FormatId> = emptyList(),    // field 2 (repeated)
    val bufferedRanges: List<FormatBufferedRange> = emptyList(), // field 3 (repeated)
    val playerTimeMs: Long = 0,                             // field 4
    val videoPlaybackUstreamerConfig: ByteArray = ByteArray(0), // field 5
    val streamerContext: StreamerContext? = null            // field 19
) {
    fun encode(): ByteArray = ProtobufWriter.encode {
        clientAbrState?.let { writeBytes(1, it.encode()) }
        selectedFormatIds.forEach { writeBytes(2, it.encode()) }
        bufferedRanges.forEach { writeBytes(3, it.encode()) }
        if (playerTimeMs != 0L) writeInt64(4, playerTimeMs)
        if (videoPlaybackUstreamerConfig.isNotEmpty()) writeBytes(5, videoPlaybackUstreamerConfig)
        streamerContext?.let { writeBytes(19, it.encode()) }
    }
}

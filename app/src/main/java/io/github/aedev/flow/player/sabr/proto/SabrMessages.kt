package io.github.aedev.flow.player.sabr.proto

import io.github.aedev.flow.utils.protobuf.ProtobufWriter

data class FormatId(
    val itag: Int = 0,
    val lmt: Long = 0,
    val xtags: String = ""
) {
    fun encode(): ByteArray = ProtobufWriter.encode {
        if (itag != 0) writeInt32(1, itag)
        if (lmt != 0L) writeInt64(2, lmt)
        if (xtags.isNotEmpty()) writeString(3, xtags)
    }

    companion object {
        fun decode(data: ByteArray): FormatId {
            val reader = ProtobufReader(data)
            var itag = 0
            var lmt = 0L
            var xtags = ""
            reader.forEachField { field ->
                when (field.fieldNumber) {
                    1 -> itag = field.asInt()
                    2 -> lmt = field.asLong()
                    3 -> xtags = field.asString()
                }
            }
            return FormatId(itag, lmt, xtags)
        }
    }
}

/**
 * MediaHeader — field numbers per LuanRT/googlevideo `media_header.proto`:
 * header_id=1, video_id=2, itag=3, lmt=4, xtags=5, start_range=6,
 * compression_algorithm=7, is_init_seg=8, sequence_number=9, bitrate_bps=10,
 * start_ms=11, duration_ms=12, format_id=13, content_length=14, time_range=15.
 */
data class MediaHeader(
    val headerId: Int = 0,
    val videoId: String = "",
    val itag: Int = 0,
    val lmt: Long = 0,
    val startDataRange: Long = 0,
    val isInitSegment: Boolean = false,
    val sequenceNumber: Int = 0,
    val bitrateBps: Long = 0,
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
            var startDataRange = 0L; var isInitSegment = false; var sequenceNumber = 0
            var bitrateBps = 0L; var contentLength = 0L
            var timeRangeStartMs = 0L; var durationMs = 0L
            var formatId: FormatId? = null; var compressionType = 0

            reader.forEachField { field ->
                when (field.fieldNumber) {
                    1 -> headerId = field.asInt()
                    2 -> videoId = field.asString()
                    3 -> itag = field.asInt()
                    4 -> lmt = field.asLong()
                    6 -> startDataRange = field.asLong()
                    7 -> compressionType = field.asInt()
                    8 -> isInitSegment = field.asBool()
                    9 -> sequenceNumber = field.asInt()
                    10 -> bitrateBps = field.asLong()
                    11 -> timeRangeStartMs = field.asLong()
                    12 -> durationMs = field.asLong()
                    13 -> formatId = FormatId.decode(field.asBytes())
                    14 -> contentLength = field.asLong()
                }
            }
            return MediaHeader(headerId, videoId, itag, lmt, startDataRange,
                isInitSegment, sequenceNumber, bitrateBps, contentLength,
                timeRangeStartMs, durationMs, formatId, compressionType)
        }
    }
}

/**
 * FormatInitializationMetadata — field numbers per LuanRT/googlevideo
 * `format_initialization_metadata.proto`: video_id=1, format_id=2, end_time_ms=3,
 * end_segment_number=4, mime_type=5, init_range=6, index_range=7, duration_units=9,
 * duration_timescale=10. There is NO init-data payload here — init segments arrive as
 * regular MEDIA parts whose MediaHeader has is_init_seg=true.
 *
 * codecs/width/height/initData are retained for API compatibility but are not part of
 * the wire message (codecs are embedded in [mimeType]).
 */
data class FormatInitializationMetadata(
    val videoId: String = "",
    val formatId: FormatId? = null,
    val endTimeMs: Long = 0,
    val endSegmentNumber: Long = 0,
    val mimeType: String = "",
    val durationUnits: Long = 0,
    val durationTimescale: Int = 0,
    val codecs: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val initData: ByteArray = ByteArray(0)
) {
    val isAudio: Boolean get() = mimeType.startsWith("audio/")
    val isVideo: Boolean get() = mimeType.startsWith("video/")

    val durationMs: Long
        get() = if (durationTimescale > 0) durationUnits * 1000L / durationTimescale else 0L

    companion object {
        fun decode(data: ByteArray): FormatInitializationMetadata {
            val reader = ProtobufReader(data)
            var videoId = ""; var formatId: FormatId? = null
            var endTimeMs = 0L; var endSegmentNumber = 0L; var mimeType = ""
            var durationUnits = 0L; var durationTimescale = 0

            reader.forEachField { field ->
                when (field.fieldNumber) {
                    1 -> videoId = field.asString()
                    2 -> formatId = FormatId.decode(field.asBytes())
                    3 -> endTimeMs = field.asLong()
                    4 -> endSegmentNumber = field.asLong()
                    5 -> mimeType = field.asString()
                    9 -> durationUnits = field.asLong()
                    10 -> durationTimescale = field.asInt()
                }
            }
            return FormatInitializationMetadata(videoId, formatId, endTimeMs,
                endSegmentNumber, mimeType, durationUnits, durationTimescale)
        }
    }
}

/**
 * NextRequestPolicy — field numbers per LuanRT/googlevideo `next_request_policy.proto`:
 * target_audio_readahead_ms=1, target_video_readahead_ms=2,
 * max_time_since_last_request_ms=3, backoff_time_ms=4, min_audio_readahead_ms=5,
 * min_video_readahead_ms=6, playback_cookie=7, video_id=8.
 */
data class NextRequestPolicy(
    val targetAudioReadaheadMs: Long = 0,
    val targetVideoReadaheadMs: Long = 0,
    val maxTimeSinceLastRequestMs: Long = 0,
    val backoffTimeMs: Long = 0,
    val playbackCookie: ByteArray = ByteArray(0),
    val videoId: String = ""
) {
    companion object {
        fun decode(data: ByteArray): NextRequestPolicy {
            val reader = ProtobufReader(data)
            var targetAudioReadaheadMs = 0L
            var targetVideoReadaheadMs = 0L
            var maxTimeSinceLastRequestMs = 0L
            var backoffTimeMs = 0L
            var playbackCookie = ByteArray(0)
            var videoId = ""
            reader.forEachField { field ->
                when (field.fieldNumber) {
                    1 -> targetAudioReadaheadMs = field.asLong()
                    2 -> targetVideoReadaheadMs = field.asLong()
                    3 -> maxTimeSinceLastRequestMs = field.asLong()
                    4 -> backoffTimeMs = field.asLong()
                    7 -> playbackCookie = field.asBytes()
                    8 -> videoId = field.asString()
                }
            }
            return NextRequestPolicy(targetAudioReadaheadMs, targetVideoReadaheadMs,
                maxTimeSinceLastRequestMs, backoffTimeMs, playbackCookie, videoId)
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

/**
 * SabrError — per LuanRT/googlevideo `sabr_error.proto`: type:string=1, code:int32=2.
 * There is no "recoverable" flag on the wire; SABR errors are retried with backoff and
 * only treated as fatal after repeated failures (see SabrOrchestrator).
 */
data class SabrError(
    val errorCode: Int = 0,
    val errorMessage: String = "",
    val isRecoverable: Boolean = true
) {
    companion object {
        fun decode(data: ByteArray): SabrError {
            val reader = ProtobufReader(data)
            var code = 0; var msg = ""
            reader.forEachField { field ->
                when (field.fieldNumber) {
                    1 -> msg = field.asString()
                    2 -> code = field.asInt()
                }
            }
            return SabrError(code, msg, isRecoverable = true)
        }
    }
}

/**
 * SabrSeek — per LuanRT/googlevideo `sabr_seek.proto`:
 * seek_media_time(ticks)=1, timescale=2, seek_source=3.
 */
data class SabrSeek(
    val seekMediaTimeTicks: Long = 0,
    val timescale: Int = 0
) {
    val seekTargetMs: Long
        get() = if (timescale > 0) seekMediaTimeTicks * 1000L / timescale else seekMediaTimeTicks

    companion object {
        fun decode(data: ByteArray): SabrSeek {
            val reader = ProtobufReader(data)
            var ticks = 0L; var timescale = 0
            reader.forEachField { field ->
                when (field.fieldNumber) {
                    1 -> ticks = field.asLong()
                    2 -> timescale = field.asInt()
                }
            }
            return SabrSeek(ticks, timescale)
        }
    }
}

/**
 * SabrContextUpdate — per LuanRT/googlevideo `sabr_context_update.proto`:
 * type=1, scope=2, value=3, send_by_default=4, write_policy=5
 * (write_policy: 1=OVERWRITE, 2=KEEP_EXISTING).
 */
data class SabrContextUpdate(
    val type: Int = 0,
    val scope: Int = 0,
    val value: ByteArray = ByteArray(0),
    val sendByDefault: Boolean = false,
    val writePolicy: Int = 0
) {
    companion object {
        const val WRITE_POLICY_KEEP_EXISTING = 2

        fun decode(data: ByteArray): SabrContextUpdate {
            val reader = ProtobufReader(data)
            var type = 0; var scope = 0; var value = ByteArray(0)
            var sendByDefault = false; var writePolicy = 0
            reader.forEachField { field ->
                when (field.fieldNumber) {
                    1 -> type = field.asInt()
                    2 -> scope = field.asInt()
                    3 -> value = field.asBytes()
                    4 -> sendByDefault = field.asBool()
                    5 -> writePolicy = field.asInt()
                }
            }
            return SabrContextUpdate(type, scope, value, sendByDefault, writePolicy)
        }
    }
}

/** Controls which previously supplied SABR contexts must be echoed on later requests. */
data class SabrContextSendingPolicy(
    val startTypes: List<Int> = emptyList(),
    val stopTypes: List<Int> = emptyList(),
    val discardTypes: List<Int> = emptyList()
) {
    companion object {
        fun decode(data: ByteArray): SabrContextSendingPolicy {
            val start = mutableListOf<Int>()
            val stop = mutableListOf<Int>()
            val discard = mutableListOf<Int>()
            ProtobufReader(data).forEachField { field ->
                val destination = when (field.fieldNumber) {
                    1 -> start
                    2 -> stop
                    3 -> discard
                    else -> null
                } ?: return@forEachField

                when (field.wireType) {
                    ProtobufReader.WIRE_VARINT -> destination += field.asInt()
                    ProtobufReader.WIRE_LENGTH_DELIMITED ->
                        destination += decodePackedVarints(field.asBytes())
                }
            }
            return SabrContextSendingPolicy(start, stop, discard)
        }

        private fun decodePackedVarints(data: ByteArray): List<Int> {
            val values = mutableListOf<Int>()
            var position = 0
            while (position < data.size) {
                var value = 0L
                var shift = 0
                while (position < data.size && shift < 64) {
                    val byte = data[position++].toInt() and 0xFF
                    value = value or ((byte and 0x7F).toLong() shl shift)
                    if (byte and 0x80 == 0) break
                    shift += 7
                }
                values += value.toInt()
            }
            return values
        }
    }
}

/**
 * StreamProtectionStatus — per LuanRT/googlevideo `stream_protection_status.proto`:
 * status=1, max_retries=2. Status semantics: 1=OK, 2=ATTESTATION_PENDING (server still
 * serves media for a grace window while waiting for a valid PoToken),
 * 3=ATTESTATION_REQUIRED (server has stopped serving media).
 */
data class StreamProtectionStatus(
    val status: Int = 0,
    val maxRetries: Int = 0
) {
    companion object {
        const val STATUS_OK = 1
        const val STATUS_ATTESTATION_PENDING = 2
        const val STATUS_ATTESTATION_REQUIRED = 3

        fun decode(data: ByteArray): StreamProtectionStatus {
            val reader = ProtobufReader(data)
            var status = 0; var maxRetries = 0
            reader.forEachField { field ->
                when (field.fieldNumber) {
                    1 -> status = field.asInt()
                    2 -> maxRetries = field.asInt()
                }
            }
            return StreamProtectionStatus(status, maxRetries)
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

/**
 * ClientAbrState — field numbers per LuanRT/googlevideo `client_abr_state.proto`.
 *
 * Two fields are what actually hold the quality on the server side and MUST be present on
 * every request: [stickyResolution] and
 * [playbackRate]. Omitting sticky_resolution makes the server fall back to its
 * lowest rung (~360p) regardless of the format we prefer — so the builder always supplies a
 * value of `max(selectedVideoHeight, 360)`.
 */
data class ClientAbrState(
    val playerTimeMs: Long = 0,            // field 28 (playhead)
    val bandwidthEstimateBps: Long = 0,    // field 23
    val viewportWidthPx: Int = 0,          // field 18
    val viewportHeightPx: Int = 0,         // field 19
    val lastManualSelectedResolution: Int = 0, // field 16 — user-picked quality height
    val stickyResolution: Int = 0,         // field 21 — pins server-side ABR to a resolution
    val timeSinceLastSeekMs: Long = 0,     // field 29
    val visibility: Int = 1,               // field 34 — 1 = foreground/visible
    val playbackRate: Float = 1.0f,        // field 35 — playback speed (fixed32/float)
    val enabledTrackTypesBitfield: Int = -1, // field 40 (-1 = unset → server defaults to audio+video)
    val drcEnabled: Boolean = false,       // field 46
    val audioTrackId: String = ""          // field 69 — selects the dub/original audio track
) {
    fun encode(): ByteArray = ProtobufWriter.encode {
        if (lastManualSelectedResolution != 0) writeInt32(16, lastManualSelectedResolution)
        if (viewportWidthPx != 0) writeInt32(18, viewportWidthPx)
        if (viewportHeightPx != 0) writeInt32(19, viewportHeightPx)
        if (stickyResolution != 0) writeInt32(21, stickyResolution)
        if (bandwidthEstimateBps != 0L) writeInt64(23, bandwidthEstimateBps)
        if (playerTimeMs != 0L) writeInt64(28, playerTimeMs)
        if (timeSinceLastSeekMs != 0L) writeInt64(29, timeSinceLastSeekMs)
        if (visibility != 0) writeInt32(34, visibility)
        writeFloat(35, playbackRate)
        if (enabledTrackTypesBitfield >= 0) writeInt32(40, enabledTrackTypesBitfield)
        if (drcEnabled) writeBool(46, true)
        if (audioTrackId.isNotEmpty()) writeString(69, audioTrackId)
    }
}

/**
 * TimeRange — nested message ({start_ticks=1, duration_ticks=2, timescale=3}) used by
 * [FormatBufferedRange] (field 6).
 */
data class TimeRange(
    val startTicks: Long = 0,
    val durationTicks: Long = 0,
    val timescale: Int = 1000
) {
    fun encode(): ByteArray = ProtobufWriter.encode {
        if (startTicks != 0L) writeInt64(1, startTicks)
        if (durationTicks != 0L) writeInt64(2, durationTicks)
        if (timescale != 0) writeInt32(3, timescale)
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
        if (durationMs > 0L) {
            writeBytes(6, TimeRange(startTicks = startTimeMs, durationTicks = durationMs, timescale = 1000).encode())
        }
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
    val deviceModel: String = "",     // field 13
    val hl: String = "en-US",         // field 21 — locale
    val gl: String = "US"             // field 22 — region
) {
    fun encode(): ByteArray = ProtobufWriter.encode {
        if (deviceMake.isNotEmpty()) writeString(12, deviceMake)
        if (deviceModel.isNotEmpty()) writeString(13, deviceModel)
        writeInt32(16, clientName)
        if (clientVersion.isNotEmpty()) writeString(17, clientVersion)
        if (osName.isNotEmpty()) writeString(18, osName)
        if (osVersion.isNotEmpty()) writeString(19, osVersion)
        if (hl.isNotEmpty()) writeString(21, hl)
        if (gl.isNotEmpty()) writeString(22, gl)
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
    val sabrContexts: List<SabrContext> = emptyList(), // field 5 (repeated)
    val unsentSabrContextTypes: List<Int> = emptyList() // field 6 (repeated int32)
) {
    fun encode(): ByteArray = ProtobufWriter.encode {
        clientInfo?.let { writeBytes(1, it.encode()) }
        if (poToken.isNotEmpty()) writeBytes(2, poToken)
        if (playbackCookie.isNotEmpty()) writeBytes(3, playbackCookie)
        sabrContexts.forEach { writeBytes(5, it.encode()) }
        unsentSabrContextTypes.forEach { writeInt32(6, it) }
    }
}

 // VideoPlaybackAbrRequest — field numbers per LuanRT/googlevideo `video_playback_abr_request.proto`
 
data class VideoPlaybackAbrRequest(
    val clientAbrState: ClientAbrState? = null,             // field 1
    val selectedFormatIds: List<FormatId> = emptyList(),    // field 2 (repeated, server-initialized formats)
    val bufferedRanges: List<FormatBufferedRange> = emptyList(), // field 3 (repeated)
    val playerTimeMs: Long = 0,                             // field 4
    val videoPlaybackUstreamerConfig: ByteArray = ByteArray(0), // field 5
    val preferredAudioFormatIds: List<FormatId> = emptyList(),  // field 16 (repeated)
    val preferredVideoFormatIds: List<FormatId> = emptyList(),  // field 17 (repeated)
    val streamerContext: StreamerContext? = null            // field 19
) {
    fun encode(): ByteArray = ProtobufWriter.encode {
        clientAbrState?.let { writeBytes(1, it.encode()) }
        selectedFormatIds.forEach { writeBytes(2, it.encode()) }
        bufferedRanges.forEach { writeBytes(3, it.encode()) }
        if (playerTimeMs != 0L) writeInt64(4, playerTimeMs)
        if (videoPlaybackUstreamerConfig.isNotEmpty()) writeBytes(5, videoPlaybackUstreamerConfig)
        preferredAudioFormatIds.forEach { writeBytes(16, it.encode()) }
        preferredVideoFormatIds.forEach { writeBytes(17, it.encode()) }
        streamerContext?.let { writeBytes(19, it.encode()) }
    }
}

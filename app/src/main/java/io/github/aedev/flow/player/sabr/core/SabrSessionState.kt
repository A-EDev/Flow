package io.github.aedev.flow.player.sabr.core

import io.github.aedev.flow.player.sabr.proto.FormatBufferedRange
import io.github.aedev.flow.player.sabr.proto.FormatId
import io.github.aedev.flow.player.sabr.proto.FormatInitializationMetadata
import io.github.aedev.flow.player.sabr.proto.NextRequestPolicy
import io.github.aedev.flow.player.sabr.proto.SabrContext
import io.github.aedev.flow.player.sabr.proto.SabrContextUpdate
import io.github.aedev.flow.player.sabr.proto.SabrRedirect

class SabrSessionState {
    var streamingUrl: String = ""
    var videoId: String = ""
    var durationMs: Long = 0

    var playheadPositionMs: Long = 0
    var playbackCookie: ByteArray = ByteArray(0)

    // SABR contexts keyed by type; only types flagged send-by-default are echoed back
    val sabrContexts = mutableMapOf<Int, SabrContext>()
    val sabrContextsToSend = mutableSetOf<Int>()

    var selectedAudioItag: Int = 0
    var selectedAudioLmt: Long = 0
    var selectedVideoItag: Int = 0
    var selectedVideoLmt: Long = 0
    var audioTrackId: String = ""

    // User-pinned quality (0 = auto / server ABR)
    var stickyResolution: Int = 0

    // -1 = unset (server default audio+video); 1 = audio-only
    var enabledTrackTypes: Int = -1

    var lastSeekAtMs: Long = 0
    var reloadToken: String? = null

    // Server-provided pacing targets (NEXT_REQUEST_POLICY); defaults until first policy
    var targetAudioReadaheadMs: Long = 15_000
    var targetVideoReadaheadMs: Long = 15_000
    var maxTimeSinceLastRequestMs: Long = 0

    val audioBufferedRanges = mutableListOf<FormatBufferedRange>()
    val videoBufferedRanges = mutableListOf<FormatBufferedRange>()

    var backoffDeadlineMs: Long = 0
    var redirectUrl: String? = null
    var requestSequence: Int = 0

    var ustreamerConfig: ByteArray = ByteArray(0)
    var poToken: String = ""
    var visitorId: String = ""

    var clientNameId: Int = 1
    var clientVersion: String = ""
    var osName: String = ""
    var osVersion: String = ""

    var screenWidthPixels: Int = 1920
    var screenHeightPixels: Int = 1080
    var screenDensity: Float = 2.0f
    var estimatedBandwidthBps: Long = 100_000_000

    fun poTokenBytes(): ByteArray {
        if (poToken.isEmpty()) return ByteArray(0)
        return try {
            val normalized = poToken.replace('-', '+').replace('_', '/')
            android.util.Base64.decode(normalized, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            ByteArray(0)
        }
    }

    val initSegments = mutableMapOf<Int, ByteArray>()
    val formatMetadata = mutableMapOf<Int, FormatInitializationMetadata>()

    // Itags the server has acknowledged via FORMAT_INITIALIZATION_METADATA
    val initializedFormats = mutableSetOf<Int>()

    // Dedup keys (itag << 32 | sequence); the server may re-send segments
    private val consumedSegments = mutableSetOf<Long>()
    private val consumedInitSegments = mutableSetOf<Int>()

    val effectiveUrl: String get() = redirectUrl ?: streamingUrl

    /** Returns true the first time a segment is seen; false for duplicates. */
    fun markSegmentConsumed(itag: Int, sequenceNumber: Int, isInit: Boolean): Boolean {
        return if (isInit) {
            consumedInitSegments.add(itag)
        } else {
            consumedSegments.add((itag.toLong() shl 32) or (sequenceNumber.toLong() and 0xFFFFFFFFL))
        }
    }

    /** Epoch ms when the GVS URL expires (`expire` query param), or 0 if unknown. */
    fun urlExpiresAtMs(): Long {
        val url = effectiveUrl
        val match = Regex("[?&]expire=(\\d+)").find(url) ?: return 0L
        return (match.groupValues[1].toLongOrNull() ?: return 0L) * 1000L
    }

    fun seekTo(positionMs: Long) {
        playheadPositionMs = positionMs
        lastSeekAtMs = System.currentTimeMillis()
        clearBufferedRanges()
        consumedSegments.clear()
    }

    val selectedAudioFormatId: FormatId get() = FormatId(selectedAudioItag, selectedAudioLmt)
    val selectedVideoFormatId: FormatId get() = FormatId(selectedVideoItag, selectedVideoLmt)

    fun updateFromNextRequestPolicy(policy: NextRequestPolicy) {
        if (policy.playbackCookie.isNotEmpty()) {
            playbackCookie = policy.playbackCookie
        }
        if (policy.backoffTimeMs > 0) {
            backoffDeadlineMs = System.currentTimeMillis() + policy.backoffTimeMs
        }
        if (policy.targetAudioReadaheadMs > 0) targetAudioReadaheadMs = policy.targetAudioReadaheadMs
        if (policy.targetVideoReadaheadMs > 0) targetVideoReadaheadMs = policy.targetVideoReadaheadMs
        if (policy.maxTimeSinceLastRequestMs > 0) maxTimeSinceLastRequestMs = policy.maxTimeSinceLastRequestMs
    }

    fun updateFromContextUpdate(update: SabrContextUpdate) {
        if (update.type == 0 && update.value.isEmpty()) return
        val existing = sabrContexts[update.type]
        if (existing != null && update.writePolicy == SabrContextUpdate.WRITE_POLICY_KEEP_EXISTING) {
            return
        }
        sabrContexts[update.type] = SabrContext(update.type, update.value)
        if (update.sendByDefault) {
            sabrContextsToSend.add(update.type)
        }
    }

    fun activeSabrContexts(): List<SabrContext> =
        sabrContextsToSend.mapNotNull { sabrContexts[it] }

    fun updateFromRedirect(redirect: SabrRedirect) {
        if (redirect.url.isNotEmpty()) {
            redirectUrl = redirect.url
        }
    }

    fun addBufferedRange(isAudio: Boolean, range: FormatBufferedRange) {
        val list = if (isAudio) audioBufferedRanges else videoBufferedRanges
        val last = list.lastOrNull()
        if (last != null &&
            last.formatId == range.formatId &&
            range.startSequence == last.endSequence + 1
        ) {
            list[list.size - 1] = last.copy(
                durationMs = last.durationMs + range.durationMs,
                endSequence = range.endSequence
            )
            return
        }
        list.add(range)
    }

    fun clearBufferedRanges() {
        audioBufferedRanges.clear()
        videoBufferedRanges.clear()
    }

    fun storeInitSegment(itag: Int, data: ByteArray) {
        initSegments[itag] = data
    }

    fun storeFormatMetadata(metadata: FormatInitializationMetadata) {
        val itag = metadata.formatId?.itag ?: return
        formatMetadata[itag] = metadata
        initializedFormats.add(itag)
        if (metadata.initData.isNotEmpty()) {
            storeInitSegment(itag, metadata.initData)
        }
    }

    fun reset() {
        playheadPositionMs = 0
        playbackCookie = ByteArray(0)
        sabrContexts.clear()
        sabrContextsToSend.clear()
        audioBufferedRanges.clear()
        videoBufferedRanges.clear()
        backoffDeadlineMs = 0
        redirectUrl = null
        requestSequence = 0
        initSegments.clear()
        formatMetadata.clear()
        initializedFormats.clear()
        consumedSegments.clear()
        consumedInitSegments.clear()
        lastSeekAtMs = 0
        reloadToken = null
    }
}

package io.github.aedev.flow.player.sabr.core

import io.github.aedev.flow.player.sabr.proto.FormatBufferedRange
import io.github.aedev.flow.player.sabr.proto.FormatId
import io.github.aedev.flow.player.sabr.proto.FormatInitializationMetadata
import io.github.aedev.flow.player.sabr.proto.NextRequestPolicy
import io.github.aedev.flow.player.sabr.proto.SabrContextUpdate
import io.github.aedev.flow.player.sabr.proto.SabrRedirect

class SabrSessionState {
    var streamingUrl: String = ""
    var videoId: String = ""
    var durationMs: Long = 0

    var playheadPositionMs: Long = 0
    var playbackCookie: ByteArray = ByteArray(0)
    var sabrContext: ByteArray = ByteArray(0)

    var selectedAudioItag: Int = 0
    var selectedAudioLmt: Long = 0
    var selectedVideoItag: Int = 0
    var selectedVideoLmt: Long = 0

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

    val effectiveUrl: String get() = redirectUrl ?: streamingUrl

    val selectedAudioFormatId: FormatId get() = FormatId(selectedAudioItag, selectedAudioLmt)
    val selectedVideoFormatId: FormatId get() = FormatId(selectedVideoItag, selectedVideoLmt)

    fun updateFromNextRequestPolicy(policy: NextRequestPolicy) {
        if (policy.playbackCookie.isNotEmpty()) {
            playbackCookie = policy.playbackCookie
        }
        if (policy.backoffTimeMs > 0) {
            backoffDeadlineMs = System.currentTimeMillis() + policy.backoffTimeMs
        }
    }

    fun updateFromContextUpdate(update: SabrContextUpdate) {
        if (update.context.isNotEmpty()) {
            sabrContext = update.context
        }
    }

    fun updateFromRedirect(redirect: SabrRedirect) {
        if (redirect.url.isNotEmpty()) {
            redirectUrl = redirect.url
        }
    }

    fun addBufferedRange(isAudio: Boolean, range: FormatBufferedRange) {
        val list = if (isAudio) audioBufferedRanges else videoBufferedRanges
        list.add(range)
    }

    fun storeInitSegment(itag: Int, data: ByteArray) {
        initSegments[itag] = data
    }

    fun storeFormatMetadata(metadata: FormatInitializationMetadata) {
        val itag = metadata.formatId?.itag ?: return
        formatMetadata[itag] = metadata
        if (metadata.initData.isNotEmpty()) {
            storeInitSegment(itag, metadata.initData)
        }
    }

    fun reset() {
        playheadPositionMs = 0
        playbackCookie = ByteArray(0)
        sabrContext = ByteArray(0)
        audioBufferedRanges.clear()
        videoBufferedRanges.clear()
        backoffDeadlineMs = 0
        redirectUrl = null
        requestSequence = 0
        initSegments.clear()
        formatMetadata.clear()
    }
}

package io.github.aedev.flow.player.sabr.core

import io.github.aedev.flow.player.sabr.proto.FormatInitializationMetadata

data class SabrSegment(
    val headerId: Int,
    val itag: Int,
    val videoId: String,
    val isAudio: Boolean,
    val timeRangeStartMs: Long,
    val durationMs: Long,
    val sequenceNumber: Int,
    val data: ByteArray,
)

sealed class SabrEvent {
    data class SegmentReady(
        val segment: SabrSegment,
    ) : SabrEvent()

    data class FormatInitialized(
        val metadata: FormatInitializationMetadata,
    ) : SabrEvent()

    data class Redirect(
        val newUrl: String,
    ) : SabrEvent()

    data class Error(
        val code: Int,
        val message: String,
        val recoverable: Boolean,
        /** The media itag the failure belongs to, when known, so the right format can be rejected. */
        val itag: Int? = null,
    ) : SabrEvent()

    data class BackoffRequired(
        val delayMs: Long,
    ) : SabrEvent()

    object EndOfTrack : SabrEvent()

    data class ReloadRequired(
        val reason: String,
        val reloadToken: String? = null,
    ) : SabrEvent()

    data class SeekDirective(
        val targetMs: Long,
    ) : SabrEvent()

    // required=false: grace window, refresh PoToken in background; true: media already cut
    data class AttestationNeeded(
        val required: Boolean,
    ) : SabrEvent()
}

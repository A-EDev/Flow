package com.flow.youtube.player.resolver

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.source.MediaSource

/**
 * Interface for resolving media sources for different types of streams.
 * Based on NewPipe's PlaybackResolver pattern.
 */
interface PlaybackResolver {

    /**
     * Resolves a media source for the given media item and stream info.
     */
    fun resolve(mediaItem: MediaItem, streamInfo: Any): MediaSource?

    /**
     * Checks if this resolver can handle the given stream type.
     */
    fun canHandle(streamInfo: Any): Boolean

    /**
     * Gets the priority of this resolver (higher = more preferred).
     */
    fun getPriority(): Int = 0
}

/**
 * Different types of media sources that can be resolved.
 */
enum class SourceType {
    LIVE_STREAM,
    VIDEO_WITH_SEPARATED_AUDIO,
    VIDEO_WITH_AUDIO_OR_AUDIO_ONLY,
    AUDIO_ONLY
}
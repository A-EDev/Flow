package io.github.aedev.flow.ui.screens.player.state

import io.github.aedev.flow.data.model.LiveChatMessage
import io.github.aedev.flow.player.state.EnhancedPlayerState
import io.github.aedev.flow.ui.components.FeedInvalidationBus

/*
 * What the player screen mirrors from outside itself: the player's own state, the live chat drip,
 * and the feed invalidation bus. Each is a pure fold of an external value into the screen state, so
 * the collectors that drive them stay one-liners and the decisions stay testable.
 */

/** The player state fields the screen shows directly. */
internal fun VideoPlayerUiState.mirrorPlayerState(playerState: EnhancedPlayerState): VideoPlayerUiState =
    copy(queueTitle = playerState.queueTitle)

/**
 * The video id the player is on that the screen has to load, or null when it has nothing to do.
 *
 * Two cases reach a load: the player moved to a video the screen knows nothing about (a queue
 * auto-advance, a deeplink, a restored media session), and the screen holds the same video but no
 * streams while the player holds no active ones either. A restored session is left alone unless the
 * player has already started playing it, and a load already in flight is never interrupted.
 */
internal fun VideoPlayerUiState.foreignVideoIdNeedingLoad(playerState: EnhancedPlayerState): String? {
    val videoId = playerState.currentVideoId ?: return null
    val hasActiveStreams = playerState.isPrepared || playerState.isBuffering
    val isSameVideoNeedsReload = !hasActiveStreams && streamInfo == null && cachedVideo?.id == videoId
    val isForeignVideo = videoId != streamInfo?.id && videoId != cachedVideo?.id
    val needsLoad =
        (isForeignVideo || isSameVideoNeedsReload) &&
            !isLoading &&
            (!isRestoredSession || !hasActiveStreams)
    return videoId.takeIf { needsLoad }
}

/** The live chat transcript and the two flags the chat panel reads. */
internal fun VideoPlayerUiState.applyLiveChat(
    messages: List<LiveChatMessage>,
    isLoading: Boolean,
    isAvailable: Boolean,
): VideoPlayerUiState =
    copy(
        liveChatMessages = messages,
        isLiveChatLoading = isLoading,
        isLiveChatAvailable = isAvailable,
    )

/** A video or a channel the user removed elsewhere leaves the related lane on this screen too. */
internal fun VideoPlayerUiState.applyFeedInvalidation(event: FeedInvalidationBus.Event): VideoPlayerUiState =
    when (event) {
        is FeedInvalidationBus.Event.NotInterested -> {
            copy(relatedVideos = relatedVideos.filter { it.id != event.videoId })
        }

        is FeedInvalidationBus.Event.ChannelBlocked -> {
            copy(relatedVideos = relatedVideos.filter { it.id != event.videoId && it.channelId != event.channelId })
        }

        else -> {
            this
        }
    }

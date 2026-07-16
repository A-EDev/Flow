package io.github.aedev.flow.player

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.selects.select

enum class PlaybackResolverReadiness {
    PLAYABLE,
    TERMINAL_WITHOUT_PLAYBACK,
    NEEDS_FALLBACK
}

sealed interface PlaybackResolverWinner<out Primary, out Fallback> {
    data class Primary<Primary>(val value: Primary) : PlaybackResolverWinner<Primary, Nothing>
    data class Fallback<Fallback>(val value: Fallback) : PlaybackResolverWinner<Nothing, Fallback>
}

object PlaybackStartupPolicy {
    fun classifyNewPipeResult(
        hasProgressiveVideo: Boolean,
        hasVideoOnly: Boolean,
        hasAudio: Boolean,
        hasDashManifest: Boolean,
        hasHlsManifest: Boolean,
        isKnownUpcoming: Boolean
    ): PlaybackResolverReadiness = when {
        isKnownUpcoming -> PlaybackResolverReadiness.TERMINAL_WITHOUT_PLAYBACK
        hasProgressiveVideo ||
            (hasVideoOnly && hasAudio) ||
            hasDashManifest ||
            hasHlsManifest -> PlaybackResolverReadiness.PLAYABLE
        else -> PlaybackResolverReadiness.NEEDS_FALLBACK
    }

    fun shouldDelaySecondaryContent(
        isPlaybackLoading: Boolean,
        currentVideoId: String?,
        requestedVideoId: String
    ): Boolean = isPlaybackLoading && currentVideoId == requestedVideoId
}

suspend fun <Primary, Fallback> awaitFirstPlaybackResolver(
    primary: Deferred<Primary>,
    fallback: Deferred<Fallback>
): PlaybackResolverWinner<Primary, Fallback> = select {
    primary.onAwait { PlaybackResolverWinner.Primary(it) }
    fallback.onAwait { PlaybackResolverWinner.Fallback(it) }
}

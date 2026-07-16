package io.github.aedev.flow.player

internal enum class ServicePlaybackCommitDecision {
    COMMIT,
    SKIP_ALREADY_PREPARED,
    SKIP_STALE_REQUEST,
}

internal object ServicePlaybackLoadPolicy {
    fun decide(
        requestedVideoId: String,
        playerVideoId: String?,
        globalVideoId: String?,
        isPreparedForRequestedVideo: Boolean,
    ): ServicePlaybackCommitDecision = when {
        playerVideoId != requestedVideoId || globalVideoId != requestedVideoId ->
            ServicePlaybackCommitDecision.SKIP_STALE_REQUEST
        isPreparedForRequestedVideo ->
            ServicePlaybackCommitDecision.SKIP_ALREADY_PREPARED
        else -> ServicePlaybackCommitDecision.COMMIT
    }
}

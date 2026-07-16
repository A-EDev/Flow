package io.github.aedev.flow.player

object BackgroundPlaybackPolicy {
    fun shouldEnterAutoPip(
        autoPipEnabled: Boolean,
        isVideoPlaying: Boolean,
        explicitBackgroundPlaybackActive: Boolean
    ): Boolean =
        autoPipEnabled &&
            isVideoPlaying &&
            !explicitBackgroundPlaybackActive

    fun shouldKeepPlaybackInBackground(
        backgroundPlaybackPreferenceEnabled: Boolean,
        explicitBackgroundPlaybackActive: Boolean,
        hasActiveVideo: Boolean
    ): Boolean =
        hasActiveVideo &&
            (backgroundPlaybackPreferenceEnabled || explicitBackgroundPlaybackActive)

    fun shouldReopenCurrentVideo(
        requestedVideoId: String,
        currentVideoId: String?,
        isBackgroundPlaybackMode: Boolean,
        isMiniPlayerCollapsed: Boolean,
        hasReusablePlayback: Boolean
    ): Boolean =
        (isBackgroundPlaybackMode || isMiniPlayerCollapsed) &&
            hasReusablePlayback &&
            requestedVideoId == currentVideoId
}

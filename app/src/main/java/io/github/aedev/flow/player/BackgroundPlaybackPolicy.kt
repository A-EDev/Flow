package io.github.aedev.flow.player

object BackgroundPlaybackPolicy {
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

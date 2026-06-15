package io.github.aedev.flow.player

data class AutoplayCountdownState(
    val isActive: Boolean = false,
    val secondsRemaining: Int = 0,
    val totalSeconds: Int = 0,
    val nextVideoTitle: String? = null,
    val nextVideoChannel: String? = null,
    val nextVideoThumbnailUrl: String? = null,
)

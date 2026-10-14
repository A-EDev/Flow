package io.github.aedev.flow.player

/**
 * Pause music when the device is muted and resume it when sound comes back, but only music this
 * pause stopped: a song the listener paused by hand stays paused when they turn the volume up.
 */
internal object MusicMutePause {
    enum class Action { PAUSE, RESUME, NONE }

    fun onVolume(
        silent: Boolean,
        enabled: Boolean,
        isPlaying: Boolean,
        pausedForMute: Boolean,
    ): Action =
        when {
            silent && enabled && isPlaying -> Action.PAUSE
            !silent && pausedForMute -> Action.RESUME
            else -> Action.NONE
        }
}

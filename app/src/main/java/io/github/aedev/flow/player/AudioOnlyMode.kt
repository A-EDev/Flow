package io.github.aedev.flow.player

import androidx.media3.common.Player

internal object BackgroundHandoffPolicy {
    fun needsServiceLayerReload(
        isSameVideo: Boolean,
        hasMediaItem: Boolean,
        playbackState: Int?,
        isPrepared: Boolean,
    ): Boolean = isSameVideo && (!hasMediaItem || playbackState == Player.STATE_IDLE || !isPrepared)
}

internal class AudioOnlyMode {
    var isActive: Boolean = false
        private set

    @Volatile
    var restorePending: Boolean = false
        private set

    var tracksDisabled: Boolean = false
        private set

    /** Resuming has to undo a deferred restore as well as an active audio-only mode. */
    val needsVideoRestore: Boolean
        get() = isActive || restorePending

    fun enter() {
        isActive = true
    }

    /**
     * Applies the mode a newly loaded set of streams was loaded for. A queue advance that happens
     * while backgrounded has to stay audio-only, or playback stalls waiting for a surface that is
     * not there.
     */
    fun applyStreams(keepAudioOnly: Boolean) {
        isActive = keepAudioOnly
    }

    /**
     * @param canRestore whether there is a display and surface to restore video output onto.
     * @return true when video output can be restored now; false when it was deferred until a
     *   surface comes back.
     */
    fun restore(canRestore: Boolean): Boolean {
        if (!canRestore) {
            restorePending = true
            return false
        }
        isActive = false
        restorePending = false
        return true
    }

    /** @return whether this changed anything, i.e. whether the track selector needs updating. */
    fun setTracksDisabled(disabled: Boolean): Boolean {
        if (tracksDisabled == disabled) return false
        tracksDisabled = disabled
        return true
    }

    /** Clears audio-only but not [restorePending], which only a completed [restore] clears. */
    fun reset() {
        isActive = false
    }
}

package io.github.aedev.flow.ui.screens.player.effects

import android.app.Activity
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel

/**
 * Shared background-audio transition used by the in-app headphones chip and the PiP
 * headphones action.
 *
 * Keeps the existing player, position and queue untouched, switches to audio-only with the
 * Media3 notification as the visible surface, hides the in-app video overlay through the
 * ViewModel dismiss flag, and sends Flow behind the current app while keeping it in Recents.
 */
object BackgroundAudioTransition {
    fun enterBackgroundAudio(
        activity: Activity?,
        viewModel: VideoPlayerViewModel,
    ) {
        viewModel.startBackgroundPlayback()
        runCatching { activity?.moveTaskToBack(true) }
    }
}

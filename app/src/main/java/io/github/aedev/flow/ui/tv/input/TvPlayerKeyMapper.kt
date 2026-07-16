package io.github.aedev.flow.ui.tv.input

import android.view.KeyEvent

/** Playback actions that can be triggered by dedicated remote media keys. */
enum class TvPlayerAction {
    TOGGLE_PLAYBACK,
    PLAY,
    PAUSE,
    SEEK_BACK,
    SEEK_FORWARD,
}

/** Keeps D-pad navigation available to Compose focus while handling media keys globally. */
object TvPlayerKeyMapper {
    fun map(keyCode: Int): TvPlayerAction? = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_HEADSETHOOK -> TvPlayerAction.TOGGLE_PLAYBACK
        KeyEvent.KEYCODE_MEDIA_PLAY -> TvPlayerAction.PLAY
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_STOP -> TvPlayerAction.PAUSE
        KeyEvent.KEYCODE_MEDIA_REWIND -> TvPlayerAction.SEEK_BACK
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> TvPlayerAction.SEEK_FORWARD
        else -> null
    }
}

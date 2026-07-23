package io.github.aedev.flow.ui.tv.input

import android.view.KeyEvent

/** Playback intents produced by remote keys on the TV player. */
enum class TvPlayerAction {
    TOGGLE_PLAYBACK,
    PLAY,
    PAUSE,
    SEEK_BACK,
    SEEK_FORWARD,
    NEXT,
    PREVIOUS,
    TOGGLE_CAPTIONS,
    SHOW_TRANSPORT,
    SHOW_UP_NEXT,
    SCRUB_BACK,
    SCRUB_FORWARD,
    COMMIT_SCRUB,
}

/**
 * Pure keyCode → intent tables. Repeat counting, acceleration, and focus routing
 * live in the player's state holders — this object stays a lookup.
 * D-pad navigation keys are never consumed by [map] so Compose focus keeps working.
 */
object TvPlayerKeyMapper {
    fun map(keyCode: Int): TvPlayerAction? = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_HEADSETHOOK -> TvPlayerAction.TOGGLE_PLAYBACK
        KeyEvent.KEYCODE_MEDIA_PLAY -> TvPlayerAction.PLAY
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_STOP -> TvPlayerAction.PAUSE
        KeyEvent.KEYCODE_MEDIA_REWIND -> TvPlayerAction.SEEK_BACK
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> TvPlayerAction.SEEK_FORWARD
        KeyEvent.KEYCODE_MEDIA_NEXT -> TvPlayerAction.NEXT
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> TvPlayerAction.PREVIOUS
        KeyEvent.KEYCODE_CAPTIONS -> TvPlayerAction.TOGGLE_CAPTIONS
        else -> null
    }

    /**
     * D-pad handling while the controls overlay is hidden. Center reveals the
     * transport (YouTube-TV behavior — no blind toggling), left/right start a
     * scrub with the seek bar focused, up reveals with the up-next rail focused.
     */
    fun mapDpadWhenControlsHidden(keyCode: Int): TvPlayerAction? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER -> TvPlayerAction.SHOW_TRANSPORT
        KeyEvent.KEYCODE_DPAD_LEFT -> TvPlayerAction.SCRUB_BACK
        KeyEvent.KEYCODE_DPAD_RIGHT -> TvPlayerAction.SCRUB_FORWARD
        KeyEvent.KEYCODE_DPAD_UP -> TvPlayerAction.SHOW_UP_NEXT
        KeyEvent.KEYCODE_DPAD_DOWN -> TvPlayerAction.SHOW_TRANSPORT
        else -> null
    }

    /**
     * D-pad handling while the seek bar itself is focused. Up/down stay unmapped
     * so Compose focus moves between the seek bar, transport row, and rail.
     */
    fun mapDpadWhenSeekBarFocused(keyCode: Int): TvPlayerAction? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> TvPlayerAction.SCRUB_BACK
        KeyEvent.KEYCODE_DPAD_RIGHT -> TvPlayerAction.SCRUB_FORWARD
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER -> TvPlayerAction.COMMIT_SCRUB
        else -> null
    }

}

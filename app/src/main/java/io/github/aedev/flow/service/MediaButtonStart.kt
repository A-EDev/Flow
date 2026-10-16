package io.github.aedev.flow.service

import android.content.Intent
import android.view.KeyEvent
import androidx.core.os.BundleCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Media3 safely stops a service that a media button started only when `onGetSession` returns null.
 * Our sessions exist from `onCreate` on, so a press that finds nothing to play has to be turned
 * down here, or nothing ever calls `startForeground` and the system kills the app (#1309).
 * [canResume] lets a Play press through when playback resumption has a saved queue to start; any
 * other key would start nothing, so it is still turned down.
 */
@UnstableApi
internal fun MediaSession.ControllerInfo.declinesMediaButtonStart(
    hasMediaItem: Boolean,
    canResume: Boolean = false,
): Boolean =
    shouldDeclineMediaButtonStart(
        controllerInfoType = connectionHints.getString(MediaSessionService.CONNECTION_HINT_KEY_CONTROLLER_INFO_TYPE),
        hasMediaItem = hasMediaItem,
        resumesOnPlay = canResume && isPlayKey(mediaButtonKeyCode()),
    )

internal fun shouldDeclineMediaButtonStart(
    controllerInfoType: String?,
    hasMediaItem: Boolean,
    resumesOnPlay: Boolean = false,
): Boolean = controllerInfoType == Intent.ACTION_MEDIA_BUTTON && !hasMediaItem && !resumesOnPlay

/** The keys Media3's own MediaButtonReceiver starts a service for: the ones that begin playback. */
internal fun isPlayKey(keyCode: Int?): Boolean =
    keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
        keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
        keyCode == KeyEvent.KEYCODE_HEADSETHOOK

@UnstableApi
private fun MediaSession.ControllerInfo.mediaButtonKeyCode(): Int? =
    connectionHints
        .getBundle(MediaSessionService.CONNECTION_HINT_KEY_INTENT_EXTRAS)
        ?.let { BundleCompat.getParcelable(it, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java) }
        ?.keyCode

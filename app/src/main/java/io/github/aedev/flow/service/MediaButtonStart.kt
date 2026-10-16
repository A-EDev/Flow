package io.github.aedev.flow.service

import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Media3 safely stops a service that a media button started only when `onGetSession` returns null.
 * Our sessions exist from `onCreate` on, so a press that finds nothing to play has to be turned
 * down here, or nothing ever calls `startForeground` and the system kills the app (#1309).
 */
@UnstableApi
internal fun MediaSession.ControllerInfo.declinesMediaButtonStart(hasMediaItem: Boolean): Boolean =
    shouldDeclineMediaButtonStart(
        controllerInfoType = connectionHints.getString(MediaSessionService.CONNECTION_HINT_KEY_CONTROLLER_INFO_TYPE),
        hasMediaItem = hasMediaItem,
    )

internal fun shouldDeclineMediaButtonStart(
    controllerInfoType: String?,
    hasMediaItem: Boolean,
): Boolean = controllerInfoType == Intent.ACTION_MEDIA_BUTTON && !hasMediaItem

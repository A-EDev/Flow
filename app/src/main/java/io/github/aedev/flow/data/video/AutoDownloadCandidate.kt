package io.github.aedev.flow.data.video

import io.github.aedev.flow.data.local.AutoDownloadMode
import io.github.aedev.flow.data.local.entity.DownloadItemStatus

/** What is known about an opened video once its streams resolve, for [shouldAutoDownload]. */
internal data class AutoDownloadCandidate(
    val mode: AutoDownloadMode,
    val onWifi: Boolean,
    val userOpened: Boolean,
    /** Resolved as an ordinary video: not live, not upcoming, not a copy already on the device. */
    val isVod: Boolean,
    val isShort: Boolean,
    val isMusic: Boolean,
    val isLocal: Boolean,
    val existing: DownloadItemStatus?,
)

/**
 * Whether an opened video is also saved for offline. A download row already there wins whatever its
 * state: a paused, failed or cancelled download was the viewer's own call, so it is never restarted.
 */
internal fun AutoDownloadCandidate.shouldAutoDownload(): Boolean =
    when {
        mode == AutoDownloadMode.OFF -> false
        mode == AutoDownloadMode.WIFI && !onWifi -> false
        !userOpened || !isVod -> false
        isShort || isMusic || isLocal -> false
        else -> existing == null
    }

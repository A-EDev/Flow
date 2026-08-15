package io.github.aedev.flow.ui.screens.shorts

import android.content.Context
import io.github.aedev.flow.data.local.VideoQuality
import io.github.aedev.flow.utils.NetworkState

/**
 * The height a Short is resolved at.
 *
 * This is not merely a display preference — it forms part of the key of the repository's
 * playback-stream cache, so the pager and the ViewModel's prefetch have to agree on it exactly or
 * the prefetch resolves one entry and the pager then misses it and resolves the stream a second
 * time. Both therefore derive it here rather than each computing it their own way.
 */
internal fun shortsTargetHeight(
    isWifi: Boolean,
    wifiQuality: VideoQuality,
    cellularQuality: VideoQuality,
): Int = if (isWifi) wifiQuality.height else cellularQuality.height

/**
 * Reads the active transport synchronously. Callers must not substitute a placeholder while a
 * network callback settles: a wrong answer here silently changes the cache key above.
 */
internal fun isOnWifi(context: Context): Boolean = NetworkState.isOnWifi(context)

package io.github.aedev.flow.ui.tv

import io.github.aedev.flow.data.local.VideoHistoryEntry

/**
 * Resume-bar fraction using the same thresholds as the mobile cards:
 * hidden below 3% progress, shown full from 90%.
 */
internal fun VideoHistoryEntry.tvWatchProgress(): Float? =
    when {
        duration <= 0L -> null
        progressPercentage < 3f -> null
        progressPercentage >= 90f -> 1f
        else -> progressPercentage / 100f
    }

package io.github.aedev.flow.ui.screens.music.collection

import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.music.model.PlaylistDetails
import java.time.Duration

/** How long a saved copy checked against every page stays trusted before it is checked again. */
internal val SavedCopyTtl: Duration = Duration.ofHours(6)

/**
 * Whether refreshing a saved copy is worth loading every page after [firstPage]. Only when the first
 * page or the stated song count disagrees with the saved songs, and the copy was not checked within
 * [SavedCopyTtl]; otherwise opening a long saved playlist would fetch all of it every time.
 */
internal fun needsFullRefresh(
    savedIds: List<String>,
    firstPage: PlaylistDetails,
    lastSyncedAtMillis: Long?,
    nowMillis: Long,
): Boolean {
    if (firstPage.continuation == null) return false
    val sinceSync = lastSyncedAtMillis?.let { nowMillis - it }
    if (sinceSync != null && sinceSync in 0 until SavedCopyTtl.toMillis()) return false
    val firstIds = firstPage.tracks.map { it.videoId }
    val total = firstPage.totalTrackCount
    return firstIds != savedIds.take(firstIds.size) || (total != null && total != savedIds.size)
}

/**
 * The songs a saved copy should be replaced with after loading [remote], or null to leave it alone.
 * Only a complete load may replace it, since a partial one would drop every song past its last page.
 */
internal fun savedCopyRefresh(
    savedIds: List<String>,
    remote: PlaylistDetails,
): List<MusicTrack>? =
    remote.tracks.takeIf { tracks ->
        remote.continuation == null && tracks.isNotEmpty() && tracks.map { it.videoId } != savedIds
    }

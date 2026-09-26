package io.github.aedev.flow.ui.screens.music.collection

import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.music.model.PlaylistDetails

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

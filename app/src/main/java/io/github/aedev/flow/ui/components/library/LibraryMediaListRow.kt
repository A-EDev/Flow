package io.github.aedev.flow.ui.components.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.ui.components.music.item.MusicTrackItem
import io.github.aedev.flow.ui.components.shared.MediaRow
import io.github.aedev.flow.ui.components.shared.MediaThumbnail

@Composable
internal fun LibraryMediaListRow(
    track: MusicTrack,
    isMusic: Boolean,
    title: String,
    onVideoClick: () -> Unit,
    onMusicClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    thumbnailUrl: String? = null,
    durationSeconds: Int? = null,
    action: @Composable () -> Unit,
) {
    if (isMusic) {
        MusicTrackItem(
            track = track,
            onClick = onMusicClick,
            showMenu = false,
            modifier = modifier,
            trailingContent = { action() },
        )
    } else {
        MediaRow(
            title = title,
            modifier = modifier,
            subtitle = subtitle,
            onClick = onVideoClick,
            trailing = { action() },
        ) {
            MediaThumbnail(
                videoId = track.videoId,
                thumbnailUrl = thumbnailUrl,
                durationSeconds = durationSeconds,
                showWatchProgress = true,
            )
        }
    }
}

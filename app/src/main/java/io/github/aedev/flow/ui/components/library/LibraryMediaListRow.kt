package io.github.aedev.flow.ui.components.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.ui.components.music.item.MusicTrackItem
import io.github.aedev.flow.ui.components.music.sheet.LocalMusicMenus
import io.github.aedev.flow.ui.components.shared.MediaRow
import io.github.aedev.flow.ui.components.shared.MediaThumbnail
import io.github.aedev.flow.ui.components.shared.quickactions.VideoQuickActionsBottomSheet

/**
 * A library entry as a row. Long press opens the item's menu, where [removeLabel] runs
 * [onRemove] as the screen's own remove, beside the inline [action] that does the same.
 */
@Composable
internal fun LibraryMediaListRow(
    track: MusicTrack,
    video: Video,
    isMusic: Boolean,
    title: String,
    onVideoClick: () -> Unit,
    onMusicClick: () -> Unit,
    removeLabel: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    thumbnailUrl: String? = null,
    durationSeconds: Int? = null,
    action: @Composable () -> Unit,
) {
    if (isMusic) {
        val musicMenus = LocalMusicMenus.current
        MusicTrackItem(
            track = track,
            onClick = onMusicClick,
            modifier = modifier,
            showMenu = false,
            trailingContent = { action() },
            onLongClick = { musicMenus.openSong(track) },
        )
    } else {
        var showMenu by remember { mutableStateOf(false) }
        MediaRow(
            title = title,
            modifier = modifier,
            subtitle = subtitle,
            onClick = onVideoClick,
            onLongClick = { showMenu = true },
            trailing = { action() },
        ) {
            MediaThumbnail(
                videoId = track.videoId,
                thumbnailUrl = thumbnailUrl,
                durationSeconds = durationSeconds,
                showWatchProgress = true,
            )
        }
        if (showMenu) {
            VideoQuickActionsBottomSheet(
                video = video,
                onDismiss = { showMenu = false },
                onRemoveFromCollection = onRemove,
                removeFromCollectionLabel = removeLabel,
            )
        }
    }
}

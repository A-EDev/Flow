package io.github.aedev.flow.ui.screens.library

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.QueuePlayNext
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.localmedia.LocalMediaItem
import io.github.aedev.flow.ui.components.shared.ArtworkThumbnail
import io.github.aedev.flow.ui.components.shared.quickactions.MediaDetailRows
import io.github.aedev.flow.ui.components.shared.quickactions.QuickActionsDefaults
import io.github.aedev.flow.ui.components.shared.quickactions.QuickActionsGroup
import io.github.aedev.flow.ui.components.shared.quickactions.QuickActionsHeader
import io.github.aedev.flow.ui.components.shared.quickactions.QuickActionsPageHeader
import io.github.aedev.flow.ui.components.shared.quickactions.QuickActionsPrimaryGroup
import io.github.aedev.flow.ui.components.shared.quickactions.QuickActionsSheet
import io.github.aedev.flow.ui.components.shared.quickactions.QuickPrimaryAction
import io.github.aedev.flow.ui.components.shared.quickactions.actionRow
import io.github.aedev.flow.utils.formatDurationMillis
import io.github.aedev.flow.utils.formatYouTubeRelativeTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class LocalMenuPage { Actions, Details }

/** What the file menu can do; the screen owns queueing, sharing and deleting. */
internal class LocalFileActions(
    val onPlayNext: (LocalMediaItem) -> Unit,
    val onAddToQueue: (LocalMediaItem) -> Unit,
    val onShare: (List<LocalMediaItem>) -> Unit,
    val onHideFolder: (LocalMediaItem) -> Unit,
    val onDelete: (List<LocalMediaItem>) -> Unit,
    val deleteMovesToTrash: Boolean,
)

/** The long-press menu for a file: queue, share, details, hide its folder or delete it. */
@Composable
internal fun LocalFileMenu(
    item: LocalMediaItem,
    actions: LocalFileActions,
    onDismiss: () -> Unit,
) {
    var page by rememberSaveable(item.id) { mutableStateOf(LocalMenuPage.Actions) }
    val context = LocalContext.current
    QuickActionsSheet(
        onDismiss = onDismiss,
        page = page,
        onBack = if (page == LocalMenuPage.Actions) null else ({ page = LocalMenuPage.Actions }),
    ) { sheet ->
        if (page == LocalMenuPage.Details) {
            QuickActionsPageHeader(
                title = stringResource(R.string.details_metadata),
                onBack = { page = LocalMenuPage.Actions },
                onClose = sheet::close,
            )
            MediaDetailRows(details = localFileDetails(item))
            return@QuickActionsSheet
        }
        QuickActionsHeader(
            title = item.title,
            subtitle =
                listOf(item.folderName, Formatter.formatShortFileSize(context, item.sizeBytes))
                    .filter(String::isNotBlank)
                    .joinToString(" ${stringResource(R.string.metadata_separator)} "),
        ) {
            if (item.isVideo) {
                LocalVideoThumbnail(item, isNew = false, modifier = Modifier, width = QuickActionsDefaults.VideoArtworkWidth)
            } else {
                ArtworkThumbnail(
                    thumbnailUrl = item.artworkUri,
                    size = QuickActionsDefaults.SquareArtworkSize,
                    placeholder = Icons.Outlined.MusicNote,
                )
            }
        }
        QuickActionsPrimaryGroup(
            listOf(
                QuickPrimaryAction(
                    icon = Icons.Outlined.QueuePlayNext,
                    label = stringResource(R.string.play_next),
                    onClick = {
                        actions.onPlayNext(item)
                        sheet.close()
                    },
                ),
                QuickPrimaryAction(
                    icon = Icons.AutoMirrored.Outlined.PlaylistPlay,
                    label = stringResource(R.string.add_to_queue),
                    onClick = {
                        actions.onAddToQueue(item)
                        sheet.close()
                    },
                ),
                QuickPrimaryAction(
                    icon = Icons.Outlined.Share,
                    label = stringResource(R.string.share),
                    onClick = {
                        actions.onShare(listOf(item))
                        sheet.close()
                    },
                ),
            ),
        )
        QuickActionsGroup(
            title = null,
            rows =
                listOfNotNull(
                    actionRow("details", Icons.Outlined.Info, stringResource(R.string.details_metadata)) { page = LocalMenuPage.Details },
                    item.folderName.takeIf { it.isNotBlank() }?.let { folder ->
                        actionRow("hide_folder", Icons.Outlined.FolderOff, stringResource(R.string.local_menu_hide_folder), folder) {
                            sheet.hideThen { actions.onHideFolder(item) }
                        }
                    },
                    actionRow(
                        key = "delete",
                        icon = Icons.Outlined.Delete,
                        title = stringResource(R.string.action_delete),
                        supporting =
                            stringResource(
                                if (actions.deleteMovesToTrash) R.string.local_menu_delete_trash else R.string.local_menu_delete_permanent,
                            ),
                        destructive = true,
                    ) { sheet.hideThen { actions.onDelete(listOf(item)) } },
                ),
        )
    }
}

/** Track details MediaStore doesn't keep, read from the file when the Details page opens. */
private data class TrackDetails(
    val codec: String? = null,
    val frameRate: Int? = null,
    val bitrateKbps: Long? = null,
)

@Composable
private fun localFileDetails(item: LocalMediaItem): List<Pair<String, String>> {
    val context = LocalContext.current
    val track by produceState(TrackDetails(), item.contentUri) { value = readTrackDetails(context, Uri.parse(item.contentUri)) }
    val lines = mutableListOf<Pair<String, String?>>()
    lines += stringResource(R.string.title_label) to item.title
    lines += stringResource(R.string.local_detail_folder) to item.path.ifBlank { item.folderName }
    if (item.width > 0 &&
        item.height > 0
    ) {
        lines +=
            stringResource(R.string.local_detail_resolution) to
            stringResource(R.string.local_detail_resolution_value, item.width, item.height)
    }
    lines += stringResource(R.string.duration) to item.durationMs.takeIf { it > 0 }?.let(::formatDurationMillis)
    lines += stringResource(R.string.file_size) to Formatter.formatShortFileSize(context, item.sizeBytes)
    lines += stringResource(R.string.mime_type) to item.mimeType.takeIf(String::isNotBlank)
    lines += stringResource(R.string.codec_label) to track.codec
    lines +=
        stringResource(R.string.local_detail_frame_rate) to
        track.frameRate?.let { stringResource(R.string.local_detail_fps, it.toString()) }
    lines += stringResource(R.string.bitrate_label) to track.bitrateKbps?.let { "$it ${stringResource(R.string.kbps)}" }
    lines += stringResource(R.string.local_detail_added) to item.dateAddedMs.takeIf { it > 0 }?.let { formatYouTubeRelativeTime(it) }
    if (!item.isVideo) {
        lines += stringResource(R.string.artist_label) to item.artist.takeIf(String::isNotBlank)
        lines += stringResource(R.string.album_label) to item.album.takeIf(String::isNotBlank)
    }
    return lines.mapNotNull { (label, value) -> value?.let { label to it } }
}

private suspend fun readTrackDetails(
    context: Context,
    uri: Uri,
): TrackDetails =
    withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val formats = (0 until extractor.trackCount).map(extractor::getTrackFormat)
            val main =
                formats.firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                    ?: formats.firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            TrackDetails(
                codec = main?.getString(MediaFormat.KEY_MIME)?.substringAfter('/'),
                frameRate = main?.numberOrNull(MediaFormat.KEY_FRAME_RATE)?.toInt(),
                bitrateKbps = main?.numberOrNull(MediaFormat.KEY_BIT_RATE)?.toLong()?.let { it / 1_000L },
            )
        } catch (_: Exception) {
            TrackDetails()
        } finally {
            extractor.release()
        }
    }

/** Containers store rates as either an int or a float; read whichever this one used. */
private fun MediaFormat.numberOrNull(key: String): Number? {
    if (!containsKey(key)) return null
    return runCatching { getInteger(key) }.getOrNull() ?: runCatching { getFloat(key) }.getOrNull()
}

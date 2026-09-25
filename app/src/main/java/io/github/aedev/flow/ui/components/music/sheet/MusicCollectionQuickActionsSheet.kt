package io.github.aedev.flow.ui.components.music.sheet

import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.layout.navigation.LocalMediaNavigator
import io.github.aedev.flow.ui.components.shared.ArtworkThumbnail
import io.github.aedev.flow.ui.components.shared.FlowNavRow
import io.github.aedev.flow.ui.components.shared.quickactions.QuickActionRow
import io.github.aedev.flow.ui.components.shared.quickactions.QuickActionsDefaults
import io.github.aedev.flow.ui.components.shared.quickactions.QuickActionsGroup
import io.github.aedev.flow.ui.components.shared.quickactions.QuickActionsHeader
import io.github.aedev.flow.ui.components.shared.quickactions.QuickActionsPrimaryGroup
import io.github.aedev.flow.ui.components.shared.quickactions.QuickActionsSheet
import io.github.aedev.flow.ui.components.shared.quickactions.QuickActionsViewModel
import io.github.aedev.flow.ui.components.shared.quickactions.QuickPrimaryAction
import io.github.aedev.flow.ui.components.shared.quickactions.actionRow
import io.github.aedev.flow.ui.components.shared.quickactions.sharedQuickActionsViewModel
import io.github.aedev.flow.ui.screens.music.CollectionPlayMode
import io.github.aedev.flow.ui.screens.music.MusicCollectionActionsViewModel
import io.github.aedev.flow.ui.screens.music.MusicPlayerViewModel
import io.github.aedev.flow.ui.screens.music.sharedMusicPlayerViewModel
import io.github.aedev.flow.utils.shareLink

/** An album or playlist's menu: play, shuffle or start a radio from it, keep it, share it or open it. */
@Composable
fun MusicCollectionQuickActionsSheet(
    item: MusicCollectionActionItem,
    onDismiss: () -> Unit,
    viewModel: MusicCollectionActionsViewModel = hiltViewModel(),
    player: MusicPlayerViewModel = sharedMusicPlayerViewModel(),
    quickActions: QuickActionsViewModel = sharedQuickActionsViewModel(),
) {
    val context = LocalContext.current
    val navigator = LocalMediaNavigator.current
    val isSaved by viewModel.isSaved.collectAsStateWithLifecycle()
    LaunchedEffect(item.id) { viewModel.loadSaved(item.id) }
    val announce: (Int) -> Unit = { quickActions.announce(it) }

    QuickActionsSheet(onDismiss = onDismiss) { sheet ->
        fun play(mode: CollectionPlayMode) {
            viewModel.play(item, mode, announce) { first, queue, asRadio ->
                player.loadAndPlayTrack(first, queue, sourceName = item.title, asRadio = asRadio)
            }
            sheet.close()
        }

        QuickActionsHeader(title = item.title, subtitle = item.subtitle) {
            ArtworkThumbnail(
                thumbnailUrl = item.thumbnailUrl,
                size = QuickActionsDefaults.SquareArtworkSize,
                placeholder = Icons.Outlined.Album,
            )
        }
        QuickActionsPrimaryGroup(
            listOf(
                QuickPrimaryAction(
                    icon = Icons.Filled.PlayArrow,
                    label = stringResource(R.string.play),
                    onClick = { play(CollectionPlayMode.Play) },
                ),
                QuickPrimaryAction(
                    icon = Icons.Outlined.Shuffle,
                    label = stringResource(R.string.shuffle),
                    onClick = { play(CollectionPlayMode.Shuffle) },
                ),
                QuickPrimaryAction(
                    icon = Icons.Outlined.Radio,
                    label = stringResource(R.string.start_radio),
                    onClick = { play(CollectionPlayMode.Radio) },
                ),
            ),
        )
        val savedLabel = stringResource(R.string.saved_to_library)
        QuickActionsGroup(
            title = null,
            rows =
                listOf(
                    QuickActionRow("library") { shape ->
                        FlowNavRow(
                            title = if (isSaved) savedLabel else stringResource(R.string.add_to_library),
                            leadingIcon = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            onClick = { viewModel.toggleSaved(item, announce) },
                            showChevron = false,
                            stateDescription = if (isSaved) savedLabel else null,
                            shape = shape,
                        )
                    },
                    actionRow("share", Icons.Outlined.Share, stringResource(R.string.share)) {
                        context.shareCollection(item)
                        sheet.close()
                    },
                    QuickActionRow("open") { shape ->
                        FlowNavRow(
                            title = stringResource(R.string.open),
                            leadingIcon = Icons.AutoMirrored.Outlined.OpenInNew,
                            onClick = {
                                if (item.isAlbum) navigator.openAlbum(item.id) else navigator.openMusicPlaylist(item.id)
                                sheet.close()
                            },
                            shape = shape,
                        )
                    },
                ),
        )
    }
}

private fun Context.shareCollection(item: MusicCollectionActionItem) = shareLink(this, item.shareUrl, item.title)

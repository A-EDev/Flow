package io.github.aedev.flow.ui.screens.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.glance.GlanceId
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.ui.components.shared.FlowSelectionRow
import io.github.aedev.flow.widget.playlist.PlaylistWidgetConfig
import io.github.aedev.flow.widget.playlist.PlaylistWidgetSource
import kotlinx.coroutines.launch

/** Picks the playlist one Playlist widget shows; choosing one saves it and closes. */
@Composable
internal fun PlaylistConfigScreen(
    glanceId: GlanceId,
    onDone: () -> Unit,
    viewModel: PlaylistPickerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var current by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(glanceId) { current = PlaylistWidgetConfig.load(context, glanceId) ?: PlaylistWidgetSource.DEFAULT_PLAYLIST_ID }
    val options = playlists ?: return

    SettingsPage(title = stringResource(R.string.widget_playlist_label), onBack = onDone) {
        group(key = "playlist.choose", header = R.string.widget_config_playlist) {
            options.forEach { playlist ->
                row("playlist.${playlist.id}") { shape ->
                    FlowSelectionRow(
                        title = playlist.name,
                        selected = playlist.id == current,
                        onClick = {
                            scope.launch {
                                PlaylistWidgetConfig.save(context, glanceId, playlist.id)
                                onDone()
                            }
                        },
                        supportingText =
                            pluralStringResource(
                                if (playlist.isMusic) R.plurals.widget_playlist_songs else R.plurals.widget_playlist_videos,
                                playlist.count,
                                playlist.count,
                            ),
                        shape = shape,
                    )
                }
            }
        }
    }
}

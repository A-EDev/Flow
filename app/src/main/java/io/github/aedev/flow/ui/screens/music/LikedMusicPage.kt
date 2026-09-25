package io.github.aedev.flow.ui.screens.music

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.FlowLoadingIndicator
import io.github.aedev.flow.ui.components.shared.quickactions.QuickActionUndo
import io.github.aedev.flow.ui.components.shared.quickactions.sharedQuickActionsViewModel
import kotlinx.coroutines.launch

/** Liked music on the music playlist page, where the heart on each song unlikes it with an Undo. */
@Composable
internal fun LikedMusicPage(
    onBackClick: () -> Unit,
    onTrackClick: (MusicTrack, List<MusicTrack>) -> Unit,
    onArtistClick: (String) -> Unit,
    onCollectionClick: (String) -> Unit,
    viewModel: LikedMusicViewModel = hiltViewModel(),
) {
    val details by viewModel.details.collectAsStateWithLifecycle()
    val quickActions = sharedQuickActionsViewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current =
        details ?: run {
            FlowLoadingIndicator()
            return
        }
    if (current.tracks.isEmpty()) {
        Scaffold(
            topBar = { FlowTopBar(title = current.title, onBack = onBackClick) },
            contentWindowInsets = WindowInsets(0.dp),
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            FlowEmptyState(
                title = stringResource(R.string.liked_music_empty_title),
                subtitle = stringResource(R.string.liked_music_empty_body),
                icon = Icons.Outlined.FavoriteBorder,
                modifier = Modifier.padding(padding),
            )
        }
        return
    }
    PlaylistPage(
        playlistDetails = current,
        onBackClick = onBackClick,
        onTrackClick = onTrackClick,
        onArtistClick = onArtistClick,
        onCollectionClick = onCollectionClick,
        onShareClick = null,
        onUnlikeTrack = { track ->
            scope.launch {
                val removed = viewModel.unlike(track)
                if (removed.isNotEmpty()) {
                    quickActions.announce(context.getString(R.string.removed_from_liked_music), QuickActionUndo.Unlike(removed))
                }
            }
        },
    )
}

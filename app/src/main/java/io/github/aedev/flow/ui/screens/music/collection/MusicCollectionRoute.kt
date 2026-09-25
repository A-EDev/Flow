package io.github.aedev.flow.ui.screens.music.collection

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.shared.FlowErrorState
import io.github.aedev.flow.ui.components.shared.FlowLoadingIndicator
import io.github.aedev.flow.ui.components.shared.quickactions.sharedQuickActionsViewModel
import io.github.aedev.flow.ui.screens.music.PlaylistPage

/** An album or playlist page: loading, the page itself, or an error with Retry and a way back. */
@Composable
fun MusicCollectionRoute(
    onBackClick: () -> Unit,
    onTrackClick: (track: MusicTrack, queue: List<MusicTrack>, sourceName: String) -> Unit,
    onArtistClick: (String) -> Unit,
    onCollectionClick: (String) -> Unit,
    viewModel: MusicCollectionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val details = state.details
    val quickActions = sharedQuickActionsViewModel()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { quickActions.announce(context.getString(it)) }
    }
    when {
        details != null -> {
            PlaylistPage(
                playlistDetails = details,
                onBackClick = onBackClick,
                onTrackClick = { track, queue -> onTrackClick(track, queue, details.title) },
                onArtistClick = onArtistClick,
                onCollectionClick = onCollectionClick,
                onLoadMore = viewModel::loadMore,
                isUserPlaylist = state.isOwn,
                isSaved = state.isSaved,
                onSaveToggle = viewModel::toggleSaved.takeIf { !state.isOwn && state.kind != MusicCollectionKind.DAILY_MIX },
            )
        }

        state.isLoading -> {
            FlowLoadingIndicator()
        }

        else -> {
            Scaffold(
                topBar = { FlowTopBar(title = "", onBack = onBackClick) },
                contentWindowInsets = WindowInsets(0.dp),
                containerColor = MaterialTheme.colorScheme.background,
            ) { padding ->
                FlowErrorState(
                    error = stringResource(R.string.error_failed_to_load_playlist),
                    onRetry = viewModel::retry,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

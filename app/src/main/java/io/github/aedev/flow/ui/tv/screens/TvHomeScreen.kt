package io.github.aedev.flow.ui.tv.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.VideoHistoryEntry
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.screens.home.HomeViewModel
import io.github.aedev.flow.ui.tv.components.TvFocusableCard
import io.github.aedev.flow.ui.tv.components.TvLoadingState
import io.github.aedev.flow.ui.tv.components.TvMessageState
import io.github.aedev.flow.ui.tv.components.TvVideoRow
import io.github.aedev.flow.ui.tv.toTvVideo

@Composable
fun TvHomeScreen(
    viewModel: HomeViewModel,
    onVideoClick: (Video) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.initialize(context.applicationContext)
    }
    DisposableEffect(viewModel) {
        viewModel.onHomeVisible()
        onDispose { viewModel.onHomeHidden() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(36.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.tv_home_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                TvFocusableCard(onClick = viewModel::refreshFeed) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Text(stringResource(R.string.action_refresh))
                    }
                }
            }
        }

        if (state.continueWatchingVideos.isNotEmpty()) {
            item {
                TvSection(
                    title = stringResource(R.string.tv_continue_watching),
                    videos = state.continueWatchingVideos.map(VideoHistoryEntry::toTvVideo),
                    onVideoClick = onVideoClick,
                )
            }
        }

        when {
            state.isLoading && state.videos.isEmpty() -> item { TvLoadingState() }
            state.error != null && state.videos.isEmpty() -> item {
                TvMessageState(
                    title = stringResource(R.string.tv_error_loading),
                    message = state.error,
                )
            }
            state.videos.isEmpty() -> item {
                TvMessageState(title = stringResource(R.string.tv_no_recommendations))
            }
            else -> item {
                TvSection(
                    title = stringResource(R.string.tv_home_recommended),
                    videos = state.videos,
                    onVideoClick = onVideoClick,
                )
            }
        }
    }
}
@Composable
private fun TvSection(
    title: String,
    videos: List<Video>,
    onVideoClick: (Video) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        TvVideoRow(videos = videos, onVideoClick = onVideoClick)
    }
}

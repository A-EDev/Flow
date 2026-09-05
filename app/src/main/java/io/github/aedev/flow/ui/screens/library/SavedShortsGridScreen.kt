package io.github.aedev.flow.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.ShortsCard
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.shared.FlowEmptyState

private val GridCellMinWidth = 160.dp
private val GridSpacing = 12.dp
private val GridContentPadding = PaddingValues(16.dp)

@Composable
fun SavedShortsGridScreen(
    onBackClick: () -> Unit,
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SavedShortsViewModel = hiltViewModel(),
) {
    val savedShorts by viewModel.savedShorts.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            FlowTopBar(
                title = stringResource(R.string.library_saved_shorts_label),
                onBack = onBackClick,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (savedShorts.isEmpty()) {
            FlowEmptyState(
                modifier = Modifier.padding(padding),
                title = stringResource(R.string.empty_saved_shorts),
                icon = Icons.Default.PlayArrow,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(GridCellMinWidth),
                contentPadding = GridContentPadding,
                horizontalArrangement = Arrangement.spacedBy(GridSpacing),
                verticalArrangement = Arrangement.spacedBy(GridSpacing),
                modifier = Modifier.padding(padding),
            ) {
                items(
                    items = savedShorts,
                    key = Video::id,
                    contentType = { "short" },
                ) { video ->
                    ShortsCard(
                        video = video,
                        onClick = { onVideoClick(video.id) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

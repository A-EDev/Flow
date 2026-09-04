package io.github.aedev.flow.ui.screens.likedvideos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.LikedVideoInfo
import io.github.aedev.flow.data.model.toMusicTrack
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.library.LikedRow
import io.github.aedev.flow.ui.components.library.emptyBodyRes
import io.github.aedev.flow.ui.components.library.emptyTitleRes
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.MediaKind
import io.github.aedev.flow.ui.components.shared.MediaKindSelector
import io.github.aedev.flow.ui.components.shared.animateMediaListItem
import kotlinx.coroutines.launch

private val ListContentPadding = PaddingValues(bottom = 80.dp)

@Composable
fun LikesScreen(
    onVideoClick: (MusicTrack) -> Unit,
    onBackClick: () -> Unit,
    onMusicClick: (MusicTrack, List<MusicTrack>) -> Unit = { track, _ -> onVideoClick(track) },
    modifier: Modifier = Modifier,
    viewModel: LikedVideosViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedKind by rememberSaveable { mutableStateOf(MediaKind.Videos) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val displayLikes =
        remember(uiState.likedVideos, selectedKind) {
            uiState.likedVideos.filter { it.isMusic == (selectedKind == MediaKind.Music) }
        }
    val musicQueue =
        remember(uiState.likedVideos) {
            uiState.likedVideos.filter { it.isMusic }.map { it.toMusicTrack() }
        }

    val removedLabel = stringResource(R.string.removed_from_likes)
    val undoLabel = stringResource(R.string.action_undo)
    val onUnlike: (LikedVideoInfo) -> Unit = { like ->
        viewModel.removeLike(like.videoId)
        scope.launch {
            val result =
                snackbarHostState.showSnackbar(
                    message = removedLabel,
                    actionLabel = undoLabel,
                )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreLike(like)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            FlowTopBar(
                title = stringResource(R.string.likes),
                onBack = onBackClick,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues),
        ) {
            MediaKindSelector(
                options = MediaKind.entries,
                selected = selectedKind,
                onSelected = { selectedKind = it },
                label = { stringResource(it.labelRes) },
                icon = { it.icon },
            )

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                displayLikes.isEmpty() -> {
                    FlowEmptyState(
                        modifier = Modifier.fillMaxSize(),
                        title = stringResource(selectedKind.emptyTitleRes()),
                        subtitle = stringResource(selectedKind.emptyBodyRes()),
                        icon = Icons.Outlined.ThumbUp,
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = ListContentPadding,
                    ) {
                        items(
                            items = displayLikes,
                            key = { it.videoId },
                            contentType = { if (it.isMusic) "track" else "video" },
                        ) { like ->
                            LikedRow(
                                like = like,
                                musicQueue = musicQueue,
                                onVideoClick = onVideoClick,
                                onMusicClick = onMusicClick,
                                onUnlike = { onUnlike(like) },
                                modifier = animateMediaListItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

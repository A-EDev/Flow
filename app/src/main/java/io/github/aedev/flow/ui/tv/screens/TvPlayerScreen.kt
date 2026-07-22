package io.github.aedev.flow.ui.tv.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.screens.player.components.VideoPlayerSurface
import io.github.aedev.flow.ui.screens.player.effects.KeepScreenOnEffect
import io.github.aedev.flow.ui.tv.components.TvFocusableCard
import io.github.aedev.flow.ui.tv.input.TvPlayerAction
import io.github.aedev.flow.ui.tv.input.TvPlayerKeyMapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val TV_SEEK_INCREMENT_MS = 10_000L

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvPlayerScreen(
    video: Video,
    viewModel: VideoPlayerViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val manager = remember { EnhancedPlayerManager.getInstance() }
    val playerState by manager.playerState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerFocusRequester = remember { FocusRequester() }
    val activity = LocalContext.current as? android.app.Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(video.id) {
        playerFocusRequester.requestFocus()
    }

    KeepScreenOnEffect(
        isPlaying = playerState.isPlaying || playerState.isBuffering,
        activity = activity,
        lifecycleOwner = lifecycleOwner,
    )

    fun perform(action: TvPlayerAction) {
        when (action) {
            TvPlayerAction.TOGGLE_PLAYBACK -> if (playerState.isPlaying) manager.pause() else manager.play()
            TvPlayerAction.PLAY -> manager.play()
            TvPlayerAction.PAUSE -> manager.pause()
            TvPlayerAction.SEEK_BACK -> manager.seekTo((manager.getCurrentPosition() - TV_SEEK_INCREMENT_MS).coerceAtLeast(0L))
            TvPlayerAction.SEEK_FORWARD -> manager.seekTo(manager.getCurrentPosition() + TV_SEEK_INCREMENT_MS)
        }
    }

    BackHandler(onBack = onClose)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .focusRequester(playerFocusRequester)
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val action = TvPlayerKeyMapper.map(event.nativeKeyEvent.keyCode) ?: return@onPreviewKeyEvent false
                perform(action)
                true
            },
    ) {
        VideoPlayerSurface(
            video = video,
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 48.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = video.channelName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TvPlaybackProgress(videoId = video.id, manager = manager)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerActionCard(
                    label = stringResource(R.string.tv_player_rewind),
                    icon = { Icon(Icons.Outlined.Replay10, contentDescription = null) },
                    onClick = { perform(TvPlayerAction.SEEK_BACK) },
                )
                PlayerActionCard(
                    label = if (playerState.isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                    icon = {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = null,
                        )
                    },
                    onClick = { perform(TvPlayerAction.TOGGLE_PLAYBACK) },
                )
                PlayerActionCard(
                    label = stringResource(R.string.tv_player_fast_forward),
                    icon = { Icon(Icons.Outlined.FastForward, contentDescription = null) },
                    onClick = { perform(TvPlayerAction.SEEK_FORWARD) },
                )
                PlayerActionCard(
                    label = stringResource(R.string.tv_player_close),
                    icon = { Icon(Icons.Outlined.Close, contentDescription = null) },
                    onClick = onClose,
                )
            }
        }

        if (uiState.isLoading || playerState.isBuffering) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        uiState.error?.let { error ->
            Text(
                text = error,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(24.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
private fun TvPlaybackProgress(
    videoId: String,
    manager: EnhancedPlayerManager,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var position by remember(videoId) { mutableLongStateOf(0L) }
    var duration by remember(videoId) { mutableLongStateOf(0L) }

    LaunchedEffect(videoId, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                position = manager.getCurrentPosition().coerceAtLeast(0L)
                duration = manager.getDuration().coerceAtLeast(0L)
                delay(1_000L)
            }
        }
    }

    LinearProgressIndicator(
        progress = {
            if (duration > 0L) {
                (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PlayerActionCard(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    TvFocusableCard(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Text(label)
        }
    }
}

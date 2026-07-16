package io.github.aedev.flow.ui.screens.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.aedev.flow.R
import io.github.aedev.flow.player.AutoplayCountdownState
import io.github.aedev.flow.player.EnhancedPlayerManager

@Composable
fun AutoplayCountdownOverlay(modifier: Modifier = Modifier) {
    val manager = remember { EnhancedPlayerManager.getInstance() }
    val state by manager.autoplayCountdown.collectAsState()

    var shown by remember { mutableStateOf(state) }
    LaunchedEffect(state) { if (state.isActive) shown = state }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = state.isActive,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                    .pointerInput(Unit) { detectTapGestures { } },
                contentAlignment = Alignment.Center
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    CountdownCard(
                        state = shown,
                        compactActions = maxWidth < 480.dp,
                        onCancel = { manager.cancelAutoplayCountdown() },
                        onRestart = { manager.restartFromAutoplayCountdown() },
                        onPlayNow = { manager.skipAutoplayCountdown() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
private fun CountdownCard(
    state: AutoplayCountdownState,
    compactActions: Boolean,
    onCancel: () -> Unit,
    onRestart: () -> Unit,
    onPlayNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = if (state.totalSeconds > 0) {
            state.secondsRemaining.toFloat() / state.totalSeconds.toFloat()
        } else 0f,
        animationSpec = tween(400),
        label = "autoplayCountdownProgress"
    )

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .padding(horizontal = 20.dp)
            .widthIn(max = if (compactActions) 420.dp else 560.dp)
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = state.secondsRemaining.coerceAtLeast(0).toString(),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.autoplay_countdown_up_next),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = state.nextVideoTitle?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.autoplay_countdown_next_video),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    state.nextVideoChannel?.takeIf { it.isNotBlank() }?.let { channel ->
                        Text(
                            text = channel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                state.nextVideoThumbnailUrl?.takeIf { it.isNotBlank() }?.let { thumb ->
                    Spacer(Modifier.width(12.dp))
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(thumb)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(72.dp)
                            .height(40.dp)
                            .clip(MaterialTheme.shapes.small)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            CountdownActions(
                compact = compactActions,
                onCancel = onCancel,
                onRestart = onRestart,
                onPlayNow = onPlayNow
            )
        }
    }
}

@Composable
private fun CountdownActions(
    compact: Boolean,
    onCancel: () -> Unit,
    onRestart: () -> Unit,
    onPlayNow: () -> Unit
) {
    val cancelLabel = stringResource(R.string.autoplay_countdown_cancel)
    val restartLabel = stringResource(R.string.autoplay_countdown_restart)
    val playNowLabel = stringResource(R.string.autoplay_countdown_play_now)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (compact) {
            Arrangement.SpaceEvenly
        } else {
            Arrangement.spacedBy(8.dp)
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (compact) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Rounded.Close, contentDescription = cancelLabel)
            }
            OutlinedIconButton(onClick = onRestart) {
                Icon(Icons.Rounded.Replay, contentDescription = restartLabel)
            }
            FilledIconButton(onClick = onPlayNow) {
                Icon(Icons.Rounded.SkipNext, contentDescription = playNowLabel)
            }
        } else {
            TextButton(onClick = onCancel) {
                Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(cancelLabel, maxLines = 1)
            }

            Spacer(Modifier.weight(1f))

            OutlinedButton(onClick = onRestart) {
                Icon(Icons.Rounded.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(restartLabel, maxLines = 1)
            }

            Button(onClick = onPlayNow) {
                Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(playNowLabel, maxLines = 1)
            }
        }
    }
}

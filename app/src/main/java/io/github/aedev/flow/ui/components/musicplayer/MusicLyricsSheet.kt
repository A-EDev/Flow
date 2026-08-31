package io.github.aedev.flow.ui.components.musicplayer

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.lyrics.LyricsEntry
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Full-screen lyrics surface presented over the expanded player. The enter spring and the
 * predictive-back gesture both drive [backProgress], which is only read inside graphicsLayer so
 * the scrub never recomposes the panel. The backdrop stays dark in every theme because the lyrics
 * renderer draws light text.
 */
@Composable
internal fun MusicLyricsSheet(
    visible: Boolean,
    backdropBaseColor: Color,
    accentColor: Color,
    lyrics: String?,
    syncedLyrics: List<LyricsEntry>,
    positionProvider: () -> Long,
    isLoading: Boolean,
    providerName: String,
    onSeekTo: (Long) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter =
            slideInVertically(
                initialOffsetY = { it / 5 },
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(durationMillis = 160)),
        exit =
            slideOutVertically(
                targetOffsetY = { it / 6 },
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
            ) + fadeOut(animationSpec = tween(durationMillis = 120)),
        modifier = modifier,
    ) {
        var backProgress by remember { mutableFloatStateOf(1f) }
        val scope = rememberCoroutineScope()
        val onDismissState = rememberUpdatedState(onDismiss)

        LaunchedEffect(Unit) {
            val enter = Animatable(backProgress)
            enter.animateTo(
                targetValue = 0f,
                animationSpec =
                    spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioLowBouncy,
                    ),
            ) { backProgress = value }
        }

        PredictiveBackHandler(enabled = visible) { progressFlow ->
            try {
                progressFlow.collect { backEvent ->
                    backProgress = backEvent.progress
                }
                scope.launch {
                    val commit = Animatable(backProgress)
                    commit.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 160),
                    ) { backProgress = value }
                    onDismissState.value()
                }
            } catch (_: CancellationException) {
                scope.launch {
                    val cancel = Animatable(backProgress)
                    cancel.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 250),
                    ) { backProgress = value }
                }
            }
        }

        val backdropColor = remember(backdropBaseColor) { lerp(backdropBaseColor, Color.Black, 0.3f) }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = backProgress * size.height * 0.06f
                        alpha = 1f - backProgress * 0.25f
                    }.background(backdropColor),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { onDismissState.value() }) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.close),
                            modifier = Modifier.size(30.dp),
                            tint = Color.White,
                        )
                    }
                    Text(
                        text = stringResource(R.string.lyrics),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    PlayerLyricsRefreshButton(
                        isLoading = isLoading,
                        accentColor = accentColor,
                        onRefresh = onRefresh,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                InlineLyricsPanel(
                    lyrics = lyrics,
                    syncedLyrics = syncedLyrics,
                    positionProvider = positionProvider,
                    isLoading = isLoading,
                    accentColor = accentColor,
                    onSeekTo = onSeekTo,
                    providerName = providerName,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                )
            }
        }
    }
}

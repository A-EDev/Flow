package io.github.aedev.flow.ui.components.musicplayer

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Full-screen lyrics surface presented over the expanded player. Show/hide is a manual
 * graphicsLayer animation over content that stays composed while [retainContent] holds, so only
 * the very first open pays the lyrics renderer's composition cost — and even that is deferred
 * past the slide so the transition itself never drops frames. When fully hidden the sheet is
 * parked off-screen, which also keeps it out of hit testing.
 */
@Composable
internal fun MusicLyricsSheet(
    visible: Boolean,
    retainContent: Boolean,
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
    val sheetShown = remember { Animatable(0f) }
    val backProgress = remember { Animatable(0f) }
    var panelComposed by remember { mutableStateOf(false) }
    val visibleState = rememberUpdatedState(visible)
    val onDismissState = rememberUpdatedState(onDismiss)
    val scope = rememberCoroutineScope()

    LaunchedEffect(visible) {
        if (visible) {
            launch {
                sheetShown.animateTo(
                    targetValue = 1f,
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                )
            }
            delay(140)
            panelComposed = true
        } else {
            sheetShown.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
            )
            backProgress.snapTo(0f)
        }
    }

    LaunchedEffect(retainContent) {
        if (!retainContent) panelComposed = false
    }

    PredictiveBackHandler(enabled = visible) { progressFlow ->
        try {
            progressFlow.collect { backEvent ->
                backProgress.snapTo(backEvent.progress)
            }
            backProgress.animateTo(1f, tween(durationMillis = 160))
            onDismissState.value()
        } catch (_: CancellationException) {
            scope.launch {
                backProgress.animateTo(0f, tween(durationMillis = 250))
            }
        }
    }

    val panelAlpha by animateFloatAsState(
        targetValue = if (panelComposed) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "lyricsPanelAlpha",
    )

    if (!visible && !panelComposed && !sheetShown.isRunning && sheetShown.value == 0f) return

    val backdropColor = remember(backdropBaseColor) { lerp(backdropBaseColor, Color.Black, 0.3f) }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .graphicsLayer {
                    val shown = sheetShown.value
                    val back = backProgress.value
                    if (shown <= 0.001f && !visibleState.value) {
                        alpha = 0f
                        translationY = size.height
                    } else {
                        alpha = (shown * (1f - back * 0.25f)).coerceIn(0f, 1f)
                        translationY = (1f - shown) * size.height / 5f + back * size.height * 0.06f
                    }
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

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            ) {
                if (panelComposed) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = panelAlpha },
                    ) {
                        InlineLyricsPanel(
                            lyrics = lyrics,
                            syncedLyrics = syncedLyrics,
                            positionProvider = positionProvider,
                            isLoading = isLoading,
                            accentColor = accentColor,
                            onSeekTo = onSeekTo,
                            providerName = providerName,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

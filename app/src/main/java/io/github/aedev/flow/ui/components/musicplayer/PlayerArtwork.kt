package io.github.aedev.flow.ui.components.musicplayer

import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.size.Precision
import io.github.aedev.flow.R
import kotlinx.coroutines.launch

@Composable
fun PlayerArtwork(
    thumbnailUrl: String?,
    isVideoMode: Boolean,
    isLoading: Boolean,
    hideArtwork: Boolean,
    hiddenArtworkColor: Color,
    player: Player?,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val artworkRequest =
        remember(thumbnailUrl) {
            ImageRequest
                .Builder(context)
                .data(thumbnailUrl)
                .allowHardware(false)
                .crossfade(true)
                .precision(Precision.EXACT)
                .size(1080)
                .build()
        }

    val scope = rememberCoroutineScope()
    val dragOffsetX = remember { Animatable(0f) }

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .pointerInput(Unit) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            totalDrag = 0f
                            scope.launch { dragOffsetX.stop() }
                        },
                        onDragEnd = {
                            val width = size.width.toFloat()
                            when {
                                totalDrag > 100 -> {
                                    scope.launch {
                                        dragOffsetX.animateTo(
                                            targetValue = width,
                                            animationSpec = tween(180, easing = FastOutSlowInEasing),
                                        )
                                        onSkipPrevious()
                                        dragOffsetX.snapTo(-width)
                                        dragOffsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
                                        )
                                    }
                                }

                                totalDrag < -100 -> {
                                    scope.launch {
                                        dragOffsetX.animateTo(
                                            targetValue = -width,
                                            animationSpec = tween(180, easing = FastOutSlowInEasing),
                                        )
                                        onSkipNext()
                                        dragOffsetX.snapTo(width)
                                        dragOffsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
                                        )
                                    }
                                }

                                else -> {
                                    scope.launch {
                                        dragOffsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec =
                                                spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium,
                                                ),
                                        )
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { dragOffsetX.animateTo(0f) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            totalDrag += dragAmount
                            change.consume()
                            scope.launch { dragOffsetX.snapTo(dragOffsetX.value + dragAmount * 0.6f) }
                        },
                    )
                },
    ) {
        if (isVideoMode) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        this.player = player
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        layoutParams =
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (hideArtwork) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(hiddenArtworkColor),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(0.42f),
                )
            }

            if (isLoading) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        } else {
            AsyncImage(
                model = artworkRequest,
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = dragOffsetX.value
                            alpha = (1f - kotlin.math.abs(dragOffsetX.value) / (size.width * 1.2f)).coerceIn(0f, 1f)
                        },
                contentScale = ContentScale.Crop,
            )

            if (isLoading) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }
}

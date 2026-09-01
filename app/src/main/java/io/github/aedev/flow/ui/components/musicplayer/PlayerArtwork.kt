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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
private fun rememberArtworkRequest(thumbnailUrl: String?): ImageRequest {
    val context = LocalContext.current
    return remember(thumbnailUrl) {
        ImageRequest
            .Builder(context)
            .data(thumbnailUrl)
            .allowHardware(false)
            .crossfade(true)
            .precision(Precision.EXACT)
            .size(1080)
            .build()
    }
}

/**
 * Artwork behaves like a pager: the neighbouring track's cover slides in with the drag, and a
 * long-press preview drives the same offset. A committed swipe leaves the neighbour centered
 * until the track actually changes, so the handoff to the new "current" cover is seamless.
 */
@Composable
fun PlayerArtwork(
    thumbnailUrl: String?,
    previousThumbnailUrl: String?,
    nextThumbnailUrl: String?,
    previewDirection: SkipDirection?,
    isVideoMode: Boolean,
    isLoading: Boolean,
    hideArtwork: Boolean,
    hiddenArtworkColor: Color,
    player: Player?,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val dragOffsetX = remember { Animatable(0f) }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }

        LaunchedEffect(thumbnailUrl) {
            dragOffsetX.snapTo(0f)
        }

        LaunchedEffect(previewDirection, widthPx) {
            when (previewDirection) {
                SkipDirection.NEXT -> {
                    if (nextThumbnailUrl != null) {
                        dragOffsetX.animateTo(-widthPx, spring(dampingRatio = 0.85f, stiffness = 380f))
                    }
                }

                SkipDirection.PREVIOUS -> {
                    if (previousThumbnailUrl != null) {
                        dragOffsetX.animateTo(widthPx, spring(dampingRatio = 0.85f, stiffness = 380f))
                    }
                }

                null -> {
                    if (dragOffsetX.value != 0f) {
                        dragOffsetX.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 380f))
                    }
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(previousThumbnailUrl != null, nextThumbnailUrl != null) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                scope.launch { dragOffsetX.stop() }
                            },
                            onDragEnd = {
                                val width = size.width.toFloat()
                                val threshold = width * 0.3f
                                val offset = dragOffsetX.value
                                when {
                                    offset < -threshold && nextThumbnailUrl != null -> {
                                        scope.launch {
                                            dragOffsetX.animateTo(
                                                targetValue = -width,
                                                animationSpec = tween(200, easing = FastOutSlowInEasing),
                                            )
                                            onSkipNext()
                                        }
                                    }

                                    offset > threshold && previousThumbnailUrl != null -> {
                                        scope.launch {
                                            dragOffsetX.animateTo(
                                                targetValue = width,
                                                animationSpec = tween(200, easing = FastOutSlowInEasing),
                                            )
                                            onSkipPrevious()
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
                                change.consume()
                                val target = dragOffsetX.value + dragAmount
                                val hasNeighbor =
                                    if (target < 0f) nextThumbnailUrl != null else previousThumbnailUrl != null
                                val resistance = if (hasNeighbor) 1f else 0.3f
                                val newOffset =
                                    (dragOffsetX.value + dragAmount * resistance)
                                        .coerceIn(-size.width.toFloat(), size.width.toFloat())
                                scope.launch { dragOffsetX.snapTo(newOffset) }
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
                // An unspecified color means the slot should stay fully invisible (immersive
                // background), keeping only the gesture area and the loading overlay.
                if (hiddenArtworkColor.isSpecified) {
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
                if (previousThumbnailUrl != null) {
                    AsyncImage(
                        model = rememberArtworkRequest(previousThumbnailUrl),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { translationX = dragOffsetX.value - widthPx },
                        contentScale = ContentScale.Crop,
                    )
                }
                if (nextThumbnailUrl != null) {
                    AsyncImage(
                        model = rememberArtworkRequest(nextThumbnailUrl),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { translationX = dragOffsetX.value + widthPx },
                        contentScale = ContentScale.Crop,
                    )
                }
                AsyncImage(
                    model = rememberArtworkRequest(thumbnailUrl),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationX = dragOffsetX.value },
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
}

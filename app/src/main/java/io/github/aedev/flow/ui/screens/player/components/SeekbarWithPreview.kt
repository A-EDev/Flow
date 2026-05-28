package io.github.aedev.flow.ui.screens.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.github.aedev.flow.data.model.SponsorBlockSegment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.player.seekbarpreview.SeekbarPreviewThumbnailHelper
import io.github.aedev.flow.ui.screens.player.util.VideoPlayerUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.core.graphics.BitmapCompat
import androidx.core.math.MathUtils
import org.schabi.newpipe.extractor.stream.StreamSegment
import kotlin.math.abs
import kotlin.math.roundToInt

// Custom seekbar with preview thumbnails
@Composable
fun SeekbarWithPreview(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    seekbarPreviewHelper: SeekbarPreviewThumbnailHelper? = null,
    chapters: List<StreamSegment> = emptyList(),
    sponsorSegments: List<SponsorBlockSegment> = emptyList(),
    duration: Long = 0L,
    bufferedValue: Float = 0f,
    edgeAligned: Boolean = false
) {
    val previewEnabled = false
    var previewPosition by remember { mutableFloatStateOf(0f) }
    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current

    val fallbackPreviewW = 240.dp
    val fallbackPreviewH = 135.dp
    val previewW = previewBitmap?.let { with(density) { it.width.toDp() } } ?: fallbackPreviewW
    val previewH = previewBitmap?.let { with(density) { it.height.toDp() } } ?: fallbackPreviewH
    
    var sliderWidth by remember { mutableFloatStateOf(0f) }
    var edgePointerActive by remember { mutableStateOf(false) }
    
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isInteracting = isPressed || isDragged || edgePointerActive
    
    // Internal value to keep the thumb following the finger smoothly
    var internalValue by remember { mutableFloatStateOf(value) }
    
    // Sync internal value with external value when not interacting
    LaunchedEffect(value) {
        if (!isInteracting) {
            internalValue = value
        }
    }

    // Async thumbnail loading with debouncing and better responsiveness
    LaunchedEffect(internalValue, isInteracting) {
        if (previewEnabled && isInteracting && seekbarPreviewHelper != null) {
            val durationMs = seekbarPreviewHelper.getPlayer().duration
            if (durationMs > 0) {
                // Round to nearest 2 seconds for better cache hits during scrub
                val positionMs = ((internalValue * durationMs) / 2000).toLong() * 2000

                val bitmap = withContext(Dispatchers.IO) {
                    try {
                        val rawBitmap = seekbarPreviewHelper.loadThumbnailForPosition(positionMs)
                        resizeBitmapLikeNewPipe(
                            source = rawBitmap,
                            baseViewWidthPx = sliderWidth.roundToInt().coerceAtLeast(1),
                            minWidthPx = with(density) { 10.dp.roundToPx() }
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                if (bitmap != null) {
                    previewBitmap = bitmap
                }
            }
        } else {
            previewBitmap = null
        }
    }

    LaunchedEffect(isInteracting) {
        if (previewEnabled && isInteracting && sliderWidth > 0f) {
            previewPosition = with(density) { (internalValue * sliderWidth).toDp().value }
        }
    }

    val trackHeight by animateDpAsState(
        targetValue = if (isInteracting) 10.dp else 4.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "trackHeight"
    )
    
    val thumbScale by animateFloatAsState(
        targetValue = if (isInteracting) 1.8f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "thumbScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (edgeAligned) 20.dp else 32.dp)
                .onGloballyPositioned { coordinates ->
                    sliderWidth = coordinates.size.width.toFloat()
                },
            contentAlignment = if (edgeAligned) Alignment.BottomCenter else Alignment.Center
        ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (edgeAligned) 20.dp else trackHeight)
        ) {
            val trackHeightPx = trackHeight.toPx()
            val width = size.width
            val trackTop = if (edgeAligned) size.height - trackHeightPx else 0f
            val trackCenterY = trackTop + trackHeightPx / 2f
            
            // Draw inactive track (background)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.15f),
                topLeft = Offset(0f, trackTop),
                size = Size(width, trackHeightPx),
                cornerRadius = CornerRadius(trackHeightPx / 2)
            )
            
            // Draw buffer track (the NewPipe feature)
            if (bufferedValue > 0f) {
                val bufferWidth = width * bufferedValue.coerceIn(0f, 1f)
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.5f), // Increased visibility for buffer
                    topLeft = Offset(0f, trackTop),
                    size = Size(bufferWidth, trackHeightPx),
                    cornerRadius = CornerRadius(trackHeightPx / 2)
                )
            }
            
            // Draw Sponsor Segments
            if (duration > 0) {
                sponsorSegments.forEach { segment ->
                     val startRatio = (segment.startTime.toFloat() * 1000f / duration.toFloat()).coerceIn(0f, 1f)
                     val endRatio = (segment.endTime.toFloat() * 1000f / duration.toFloat()).coerceIn(0f, 1f)
                     
                     if (endRatio > startRatio) {
                         val startX = startRatio * width
                         val endX = endRatio * width
                         val segWidth = endX - startX
                         
                         val segmentColor = when (segment.category) {
                             "sponsor" -> Color(0xFF00D100) // Green
                             "selfpromo" -> Color(0xFFFFFF00) // Yellow
                             "interaction" -> Color(0xFFFF00FF) // Magenta
                             "intro" -> Color(0xFF00FFFF) // Cyan
                             "outro" -> Color(0xFF00FFFF) // Cyan
                             "music_offtopic" -> Color(0xFFFF8000) // Orange
                             else -> Color(0xFF00D100)
                         }.copy(alpha = 0.5f)
                         
                         drawRoundRect(
                             color = segmentColor,
                             topLeft = Offset(startX, trackTop),
                             size = Size(segWidth, trackHeightPx),
                             cornerRadius = CornerRadius(trackHeightPx / 2)
                         )
                     }
                }
            }
            
            // Draw active track (progress)
            val activeWidth = width * internalValue
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(0f, trackTop),
                size = Size(activeWidth, trackHeightPx),
                cornerRadius = CornerRadius(trackHeightPx / 2)
            )
            
            // Draw Chapter Separators (Gaps)
            if (chapters.isNotEmpty() && duration > 0) {
                val gapWidth = 3.dp.toPx()
                
                chapters.forEach { chapter ->
                    if (chapter.startTimeSeconds > 0) {
                        val chapterStartMs = chapter.startTimeSeconds * 1000
                        val chapterProgress = chapterStartMs.toFloat() / duration.toFloat()
                        
                        if (chapterProgress in 0f..1f) {
                            val gapX = width * chapterProgress
                            
                            // Draw a clear line to simulate a gap
                            drawLine(
                                color = Color.Black.copy(alpha = 0.8f), 
                                start = Offset(gapX, trackTop), 
                                end = Offset(gapX, trackTop + trackHeightPx),
                                strokeWidth = gapWidth
                            )
                        }
                    }
                }
            }

            if (edgeAligned && thumbScale > 0f) {
                val thumbRadius = 7.dp.toPx() * thumbScale
                val thumbX = if (width > thumbRadius * 2f) {
                    (width * internalValue).coerceIn(thumbRadius, width - thumbRadius)
                } else {
                    width * internalValue
                }
                drawCircle(
                    color = primaryColor.copy(alpha = 0.24f),
                    radius = thumbRadius + 8.dp.toPx(),
                    center = Offset(thumbX, trackCenterY)
                )
                drawCircle(
                    color = Color.White,
                    radius = thumbRadius,
                    center = Offset(thumbX, trackCenterY)
                )
                drawCircle(
                    color = primaryColor,
                    radius = thumbRadius,
                    center = Offset(thumbX, trackCenterY),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                )
            }
        }

        // The actual slider
        @OptIn(ExperimentalMaterial3Api::class)
        Slider(
            value = internalValue,
            onValueChange = { newValue ->
                internalValue = newValue
                onValueChange(newValue)

                // Update preview position
                if (previewEnabled && seekbarPreviewHelper != null) {
                    previewPosition = with(density) { (newValue * sliderWidth).toDp().value }
                }
            },
            onValueChangeFinished = {
                onValueChangeFinished?.invoke()
            },
            modifier = if (edgeAligned) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            ),
            thumb = {
                if (edgeAligned) {
                    Spacer(modifier = Modifier.size(0.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .scale(thumbScale)
                            .background(Color.White, CircleShape)
                            .border(3.dp, primaryColor, CircleShape)
                            .then(
                                if (isInteracting) {
                                    Modifier.background(
                                        Brush.radialGradient(
                                            colors = listOf(primaryColor.copy(alpha = 0.4f), Color.Transparent),
                                            radius = 40f
                                        )
                                    )
                                } else Modifier
                            )
                    )
                }
            }
        )

        if (edgeAligned) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(enabled, valueRange, steps) {
                        if (!enabled) return@pointerInput

                        fun valueForX(x: Float): Float {
                            val width = size.width.toFloat().coerceAtLeast(1f)
                            val fraction = (x / width).coerceIn(0f, 1f)
                            val steppedFraction = if (steps > 0) {
                                val intervals = steps + 1
                                (fraction * intervals).roundToInt()
                                    .coerceIn(0, intervals)
                                    .toFloat() / intervals.toFloat()
                            } else {
                                fraction
                            }
                            return valueRange.start +
                                (valueRange.endInclusive - valueRange.start) * steppedFraction
                        }

                        fun updateValueFromX(x: Float) {
                            val newValue = valueForX(x)
                            if (abs(newValue - internalValue) > 0.0001f) {
                                internalValue = newValue
                                onValueChange(newValue)

                                if (previewEnabled && seekbarPreviewHelper != null) {
                                    previewPosition = with(density) { (newValue * sliderWidth).toDp().value }
                                }
                            }
                        }

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            edgePointerActive = true
                            down.consume()
                            updateValueFromX(down.position.x)

                            try {
                                var activePointerId = down.id
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == activePointerId }
                                        ?: event.changes.firstOrNull { it.pressed }
                                        ?: break

                                    activePointerId = change.id
                                    if (!change.pressed) {
                                        change.consume()
                                        break
                                    }

                                    if (change.positionChange() != Offset.Zero) {
                                        updateValueFromX(change.position.x)
                                    }
                                    change.consume()
                                }
                            } finally {
                                edgePointerActive = false
                                onValueChangeFinished?.invoke()
                            }
                        }
                    }
            )
        }
        } 

        val triangleH = 7.dp

        AnimatedVisibility(
            visible = previewEnabled && isInteracting,
            enter = fadeIn(tween(150)) + slideInVertically(
                initialOffsetY = { it / 2 }, animationSpec = tween(150)
            ),
            exit = fadeOut(tween(200)) + slideOutVertically(
                targetOffsetY = { it / 2 }, animationSpec = tween(200)
            ),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    val previewWidthPx = previewW.toPx()
                    val positionPx = previewPosition.dp.toPx()
                    val rawX = positionPx - previewWidthPx / 2f
                    val clampedX = rawX.coerceIn(0f, (sliderWidth - previewWidthPx).coerceAtLeast(0f))
                    val yPx = (previewH + triangleH + 4.dp).toPx()
                    IntOffset(clampedX.toInt(), -yPx.toInt())
                }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier.size(previewW, previewH),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black,
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.85f)),
                    shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val bmp = previewBitmap
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                                filterQuality = FilterQuality.High
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF1C1C1C))
                            )
                        }

                        Surface(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = VideoPlayerUtils.formatTime((internalValue * duration).toLong()),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(14.dp, triangleH)
                        .background(Color.White.copy(alpha = 0.85f), shape = GenericShape { size, _ ->
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width / 2f, size.height)
                            close()
                        })
                )
            }
        }
    }
}

private fun resizeBitmapLikeNewPipe(
    source: android.graphics.Bitmap,
    baseViewWidthPx: Int,
    minWidthPx: Int
): android.graphics.Bitmap {
    if (source.width <= 0 || source.height <= 0) return source

    val srcWidth = source.width
    val desiredWidth = Math.round(baseViewWidthPx / 4f)
    val newWidth = MathUtils.clamp(
        desiredWidth,
        minWidthPx,
        Math.round(srcWidth * 2.5f)
    )

    if (newWidth <= 0 || newWidth == srcWidth) {
        return source
    }

    val scaleFactor = newWidth.toFloat() / srcWidth.toFloat()
    val newHeight = (source.height * scaleFactor).roundToInt().coerceAtLeast(1)

    return try {
        BitmapCompat.createScaledBitmap(source, newWidth, newHeight, null, true)
    } catch (e: Exception) {
        source
    }
}

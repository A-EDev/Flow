package com.flow.youtube.ui.screens.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.SliderColors
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.flow.youtube.player.seekbarpreview.SeekbarPreviewThumbnailHelper
import com.flow.youtube.ui.screens.player.util.VideoPlayerUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamSegment

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
    duration: Long = 0L,
    bufferedValue: Float = 0f
) {
    var showPreview by remember { mutableStateOf(false) }
    var previewPosition by remember { mutableFloatStateOf(0f) }
    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    
    var sliderWidth by remember { mutableFloatStateOf(0f) }
    
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isInteracting = isPressed || isDragged
    
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
        if (isInteracting && seekbarPreviewHelper != null) {
            val durationMs = seekbarPreviewHelper.getPlayer().duration
            if (durationMs > 0) {
                // Round to nearest 2 seconds for better cache hits during scrub
                val positionMs = ((internalValue * durationMs) / 2000).toLong() * 2000
                
                withContext(Dispatchers.IO) {
                    try {
                        val bitmap = seekbarPreviewHelper.loadThumbnailForPosition(positionMs)
                        if (bitmap != null) {
                            previewBitmap = bitmap
                            showPreview = true
                        }
                    } catch (e: Exception) {
                        // Keep previous bitmap if error
                    }
                }
            }
        } else {
            // Delay hiding to make it feel smoother
            delay(300)
            if (!isInteracting) {
                showPreview = false
                previewBitmap = null
            }
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
            .height(56.dp)
            .onGloballyPositioned { coordinates ->
                sliderWidth = coordinates.size.width.toFloat()
            },
        contentAlignment = Alignment.Center
    ) {
        // Custom Track with Segments
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
        ) {
            val height = size.height
            val width = size.width
            
            // Draw inactive track (background)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.15f),
                size = Size(width, height),
                cornerRadius = CornerRadius(height / 2)
            )
            
            // Draw buffer track (the NewPipe feature)
            if (bufferedValue > 0f) {
                val bufferWidth = width * bufferedValue.coerceIn(0f, 1f)
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.25f), // Lighter than background, darker than active
                    size = Size(bufferWidth, height),
                    cornerRadius = CornerRadius(height / 2)
                )
            }
            
            // Draw active track (progress)
            val activeWidth = width * internalValue
            drawRoundRect(
                color = primaryColor,
                size = Size(activeWidth, height),
                cornerRadius = CornerRadius(height / 2)
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
                                start = Offset(gapX, 0f), 
                                end = Offset(gapX, height),
                                strokeWidth = gapWidth
                            )
                        }
                    }
                }
            }
        }

        // Preview thumbnail overlay - BIGGER and SLEEKER
        AnimatedVisibility(
            visible = showPreview && previewBitmap != null,
            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.9f, animationSpec = tween(200)) + slideInVertically(initialOffsetY = { 20 }),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.9f, animationSpec = tween(200)) + slideOutVertically(targetOffsetY = { 20 }),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (previewPosition - 110).dp, y = (-150).dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier
                        .size(220.dp, 124.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black,
                    border = BorderStroke(2.dp, Color.White.copy(alpha = 0.9f)),
                    shadowElevation = 12.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AndroidView(
                            factory = { ctx ->
                                android.widget.ImageView(ctx).apply {
                                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                                    setImageBitmap(previewBitmap)
                                }
                            },
                            update = { imageView ->
                                imageView.setImageBitmap(previewBitmap)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Time overlay on preview
                        Surface(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp)
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
                
                // Triangle pointer
                Box(
                    modifier = Modifier
                        .size(16.dp, 8.dp)
                        .background(Color.White, shape = GenericShape { size, _ ->
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width / 2f, size.height)
                            close()
                        })
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
                if (seekbarPreviewHelper != null) {
                    previewPosition = with(density) { (newValue * sliderWidth).toDp().value }
                }
            },
            onValueChangeFinished = {
                onValueChangeFinished?.invoke()
            },
            modifier = Modifier.fillMaxWidth(),
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
        )
    }
}

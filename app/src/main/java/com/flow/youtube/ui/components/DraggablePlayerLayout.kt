package com.flow.youtube.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Helper function for linear interpolation
private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

enum class PlayerSheetValue { Expanded, Collapsed }

class PlayerDraggableState(
    val offsetY: Animatable<Float, AnimationVector1D>,
    val maxOffset: Float,
    val scope: CoroutineScope
) {
    val currentValue: PlayerSheetValue
        get() = if (offsetY.value > maxOffset * 0.5f) PlayerSheetValue.Collapsed else PlayerSheetValue.Expanded

    val fraction: Float
        get() = (offsetY.value / maxOffset).coerceIn(0f, 1f)

    fun expand() {
        scope.launch {
            offsetY.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 350f))
        }
    }

    fun collapse() {
        scope.launch {
            offsetY.animateTo(maxOffset, spring(dampingRatio = 0.85f, stiffness = 350f))
        }
    }
    
    fun snapTo(target: PlayerSheetValue) {
        scope.launch {
            val targetOffset = if (target == PlayerSheetValue.Collapsed) maxOffset else 0f
            offsetY.snapTo(targetOffset)
        }
    }
}

@Composable
fun rememberPlayerDraggableState(maxOffset: Float): PlayerDraggableState {
    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(maxOffset) }
    return remember(maxOffset) {
        PlayerDraggableState(offsetY, maxOffset, scope)
    }
}

@Composable
fun DraggablePlayerLayout(
    state: PlayerDraggableState,
    videoContent: @Composable (Modifier) -> Unit,
    bodyContent: @Composable (Float) -> Unit,
    miniControls: @Composable (Float) -> Unit,
    progress: Float
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidth = with(density) { config.screenWidthDp.dp.toPx() }
    
    val fraction = state.fraction
   
    // Video Dimensions
    val videoHeight = screenWidth * (9f / 16f)
    
    // Mini Player Target Dimensions
    val miniScale = 0.55f
    val miniWidth = screenWidth * miniScale
    val margin = with(density) { 12.dp.toPx() } // Margin from edges
    
    // Calculate Target Position (Bottom-Right)
    // X goes from 0 to (Screen - MiniWidth - Margin)
    val targetX = screenWidth - miniWidth - margin
    
    // Pre-calculate px for graphicsLayer
    val elevationPx = with(density) { 16.dp.toPx() }
    
    Box(modifier = Modifier.fillMaxSize()) {
        
        // 1. Background Scrim - Fades out as we collapse
        if (fraction < 1f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(1f - fraction)
                    .background(MaterialTheme.colorScheme.background)
            )
        }

        // 2. Body Content - Fades out and moves down as we collapse
        if (fraction < 0.8f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = with(density) { videoHeight.toDp() })
                    .alpha(1f - fraction)
                    .graphicsLayer {
                        translationY = fraction * 100f // Parallax nudge
                    }
            ) {
                bodyContent(1f - fraction)
            }
        }

        // 3. Floating Video Player
        Box(
            modifier = Modifier
                .width(with(density) { config.screenWidthDp.dp })
                .height(with(density) { videoHeight.toDp() })
                .graphicsLayer(
                    // Translation Y: Controlled by Drag
                    translationY = state.offsetY.value,
                    
                    // Translation X: Interpolate to Right Edge
                    translationX = targetX * fraction,
                    
                    // Scale: Shrink to mini size
                    scaleX = lerpFloat(1f, miniScale, fraction),
                    scaleY = lerpFloat(1f, miniScale, fraction),
                    
                    // Pivot at Top-Left
                    transformOrigin = TransformOrigin(0f, 0f),
                    
                    // Shadow & Clip
                    shadowElevation = if (fraction > 0f) elevationPx else 0f,
                    shape = RoundedCornerShape(if (fraction > 0.1f) 16.dp else 0.dp),
                    clip = true
                )
                .background(Color.Black)
                .draggable(
                    state = rememberDraggableState { delta ->
                        state.scope.launch {
                            val newOffset = (state.offsetY.value + delta).coerceIn(0f, state.maxOffset)
                            state.offsetY.snapTo(newOffset)
                        }
                    },
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        val target = if (velocity > 800 || state.offsetY.value > state.maxOffset * 0.3f) {
                            state.maxOffset
                        } else {
                            0f
                        }
                        state.scope.launch {
                            state.offsetY.animateTo(target, spring(dampingRatio = 0.8f, stiffness = 300f))
                        }
                    }
                )
        ) {
            // Video Surface
            videoContent(Modifier.fillMaxSize())
            
            // Mini Controls Overlay
            if (fraction > 0.6f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f * fraction))
                        .alpha(fraction)
                ) {
                   miniControls(fraction)
                }
            }
            
            // Progress Bar 
            if (fraction > 0.8f) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp),
                    color = Color.Red,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}

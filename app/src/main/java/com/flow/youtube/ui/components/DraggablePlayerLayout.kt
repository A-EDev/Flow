package com.flow.youtube.ui.components

import android.content.res.Configuration
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
            offsetY.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 300f))
        }
    }

    fun collapse() {
        scope.launch {
            offsetY.animateTo(maxOffset, spring(dampingRatio = 0.82f, stiffness = 300f))
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
    // Use remember with keys removed for Animatable creation to avoid recreation on maxOffset change
    // We want to KEEP the current offset value but respect the NEW maxOffset constraints
    val offsetY = remember { Animatable(maxOffset) }

    // If maxOffset changes (e.g. orientation change), we need to decide where to snap
    // If we were collapsed (near old MaxOffset), snap to new MaxOffset.
    // If we were expanded (near 0), stay near 0.
    LaunchedEffect(maxOffset) {
        val current = offsetY.value
        // Heuristic: If we are closer to the "bottom" (> 50% down), snap to new bottom. (Collapsed)
        // Otherwise, stay at top. (Expanded)
        if (current > maxOffset * 0.25f) { // Using 25% to be safer for "stuck" scenarios 
             // We use a broader threshold because the "old maxOffset" (Landscape) might be small
             // compared to "new maxOffset" (Portrait).
             // Actually, if we are switching Landscape -> Portrait:
             // Old Max (e.g. 300) -> New Max (e.g. 1000).
             // If we were at 300 (Collapsed), 300 is < 1000 * 0.5. So simple thresholding fails.
             // We need to know if we were "Collapsed" based on previous state logic.
             // But we only have current value.
             // Let's assume if value > 0 and not explicitly expanded, we snap to bottom.
             if (current > 50f) { // arbitrary small threshold
                 offsetY.snapTo(maxOffset) 
             }
        } else {
             offsetY.snapTo(0f)
        }
    }

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
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    // Get Status Bar Height
    val statusBarHeight = WindowInsets.statusBars.getTop(density).toFloat()

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val fullScreenWidth = constraints.maxWidth.toFloat()
        val fullScreenHeight = constraints.maxHeight.toFloat()
        
        // --- 1. LANDSCAPE MODE ---
        // Only show fullscreen landscape IF the player is actually expanded.
        // If it's collapsed (mini player), we want it to behave like portrait mode (floating)
        if (isLandscape && state.currentValue == PlayerSheetValue.Expanded) {
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                videoContent(Modifier.fillMaxSize())
            }  
                
            return@BoxWithConstraints
        }

        // --- 2. PORTRAIT MODE ---
        
        val fraction = state.fraction
        
        // Target Dimensions for Mini Player
        val miniScale = 0.55f
        val miniWidth = fullScreenWidth * miniScale
        val miniHeight = miniWidth * (9f / 16f)
        val margin = with(density) { 12.dp.toPx() }
        
        // Portrait Video Height (16:9)
        val expandedVideoHeight = fullScreenWidth * (9f / 16f)
        
        // Calculate Positions
        val currentTopPadding = lerpFloat(statusBarHeight, 0f, fraction)
        
        // Target X (Right align)
        val targetX = fullScreenWidth - miniWidth - margin
        
        // Main Background (Scrim)
        if (fraction < 1f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(1f - fraction)
            ) {
                // Top Black Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { statusBarHeight.toDp() })
                        .background(Color.Black)
                )
                // Bottom Content Background
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(top = with(density) { statusBarHeight.toDp() })
                        .background(MaterialTheme.colorScheme.background)
                )
            }
        }

        // Body Content (Scrollable info)
        if (fraction < 0.8f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Push content down below the Video + Status Bar
                    .padding(top = with(density) { (expandedVideoHeight + statusBarHeight).toDp() })
                    .alpha(1f - fraction)
                    .graphicsLayer { translationY = fraction * 100f }
            ) {
                bodyContent(1f - fraction)
            }
        }

        // THE DRAGGABLE VIDEO PLAYER
        Box(
            modifier = Modifier
                .width(with(density) { fullScreenWidth.toDp() })
                .height(with(density) { expandedVideoHeight.toDp() })
                .graphicsLayer(
                    // Y Translation: The drag offset + the dynamic top padding
                    translationY = state.offsetY.value + currentTopPadding,
                    
                    // X Translation: Move to right
                    translationX = targetX * fraction,
                    
                    // Scale: Shrink
                    scaleX = lerpFloat(1f, miniScale, fraction),
                    scaleY = lerpFloat(1f, miniScale, fraction),
                    transformOrigin = TransformOrigin(0f, 0f),
                    
                    // Visuals
                    shadowElevation = with(density) { if (fraction > 0f) 16.dp.toPx() else 0f },
                    shape = RoundedCornerShape(if (fraction > 0.1f) 12.dp else 0.dp),
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
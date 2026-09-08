package io.github.aedev.flow.ui.components.videoplayer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.aedev.flow.ui.components.videoplayer.motion.cornerTargetX
import io.github.aedev.flow.ui.components.videoplayer.motion.cornerTargetY
import io.github.aedev.flow.ui.components.videoplayer.motion.miniResizeSpringSpec
import io.github.aedev.flow.ui.components.videoplayer.motion.playerExpandSpringSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class PlayerSheetValue { Expanded, Collapsed }

enum class MiniPlayerCorner { TopLeft, TopRight, BottomLeft, BottomRight }

class PlayerDraggableState(
    val offsetX: Animatable<Float, AnimationVector1D>,
    val offsetY: Animatable<Float, AnimationVector1D>,
    val expandFraction: Animatable<Float, AnimationVector1D>,
    val scope: CoroutineScope,
) {
    var corner by mutableStateOf(MiniPlayerCorner.BottomRight)
    var isDragging by mutableStateOf(false)
    val dragScale = Animatable(1f)

    /**
     * Zoom applied while dragging up to enter fullscreen. Separate from [dragScale] so the
     * mini-player's press effect and this cannot overwrite each other; they apply at opposite ends
     * of [expandFraction] and are read in the draw phase only.
     */
    val expandDragScale = Animatable(1f)

    var cachedTargetX by mutableFloatStateOf(0f)
    var cachedTargetY by mutableFloatStateOf(0f)

    val miniSizeScale = Animatable(1f)
    var isShrinkingToCorner by mutableStateOf(false)

    var miniVisualScale by mutableFloatStateOf(1f)

    /** True while the floating mini player is in wide (enlarged) mode. */
    val isInlineMode: Boolean get() = miniSizeScale.value > 1.5f

    private val currentValueState =
        derivedStateOf {
            if (expandFraction.targetValue > 0.5f) {
                PlayerSheetValue.Collapsed
            } else {
                PlayerSheetValue.Expanded
            }
        }

    val currentValue: PlayerSheetValue get() = currentValueState.value

    val fraction: Float get() = expandFraction.value

    fun expand() {
        corner = MiniPlayerCorner.BottomRight
        scope.launch {
            isShrinkingToCorner = false
            val anim = playerExpandSpringSpec
            launch { miniSizeScale.animateTo(1f, anim) }
            launch { expandFraction.animateTo(0f, anim) }
            launch { offsetX.animateTo(0f, anim) }
            launch { offsetY.animateTo(0f, anim) }
        }
    }

    /**
     * Expand the floating mini player to wide mode.
     */
    fun expandWide(
        screenWidth: Float = 0f,
        margin: Float = 0f,
        baseMiniWidth: Float = 0f,
        screenHeight: Float = 0f,
        minY: Float = 0f,
        bottomNavPad: Float = 0f,
        isTablet: Boolean = false,
        isFoldable: Boolean = false,
    ) {
        val maxWideFraction =
            when {
                isFoldable -> 0.55f
                isTablet -> 0.60f
                else -> 1.00f
            }
        val maxWideWidth =
            ((screenWidth * maxWideFraction) - (margin * 2f))
                .coerceAtLeast(baseMiniWidth)
        val effectiveBase = baseMiniWidth.coerceAtLeast(1f)
        val targetScale = (maxWideWidth / effectiveBase).coerceAtLeast(1f)
        val targetWidth = (effectiveBase * targetScale).coerceAtMost(maxWideWidth)
        val targetHeight = targetWidth * (9f / 16f)
        val targetMaxY =
            if (screenHeight > 0f) {
                (screenHeight - targetHeight - bottomNavPad - margin).coerceAtLeast(minY)
            } else {
                offsetY.value
            }

        val isLargeScreen = isTablet || isFoldable
        val targetX =
            if (isLargeScreen) {
                val newMaxX =
                    (screenWidth - targetWidth - margin)
                        .coerceAtLeast(margin)
                offsetX.value.coerceIn(margin, newMaxX)
            } else {
                ((screenWidth - targetWidth) / 2f).coerceAtLeast(margin)
            }
        val targetY =
            if (screenHeight > 0f) {
                offsetY.value.coerceIn(minY, targetMaxY)
            } else {
                offsetY.value
            }

        scope.launch {
            isShrinkingToCorner = false
            launch {
                miniSizeScale.animateTo(
                    targetScale,
                    miniResizeSpringSpec,
                )
            }
            launch {
                offsetX.animateTo(
                    targetX,
                    miniResizeSpringSpec,
                )
            }
            launch {
                offsetY.animateTo(
                    targetY,
                    miniResizeSpringSpec,
                )
            }
        }
    }

    fun collapse() {
        scope.launch {
            isShrinkingToCorner = false
            val anim = playerExpandSpringSpec
            if (cachedTargetX == 0f && cachedTargetY == 0f) {
                expandFraction.snapTo(1f)
            } else {
                launch { expandFraction.animateTo(1f, anim) }
                launch { offsetX.animateTo(cachedTargetX, anim) }
                launch { offsetY.animateTo(cachedTargetY, anim) }
            }
            launch { miniSizeScale.animateTo(1f, anim) }
        }
    }

    fun shrinkToCorner(
        baseMiniWidth: Float,
        screenWidth: Float,
        margin: Float,
        minY: Float,
        screenHeight: Float,
        bottomNavPad: Float,
    ) {
        val normalMiniWidth = baseMiniWidth
        val normalMiniHeight = normalMiniWidth * (9f / 16f)
        val normalMaxX = (screenWidth - normalMiniWidth - margin).coerceAtLeast(margin)
        val normalMaxY = (screenHeight - normalMiniHeight - bottomNavPad - margin).coerceAtLeast(minY)

        val targetX = cornerTargetX(corner, minX = margin, maxX = normalMaxX)
        val targetY = cornerTargetY(corner, minY = minY, maxY = normalMaxY)

        cachedTargetX = targetX
        cachedTargetY = targetY
        scope.launch {
            isShrinkingToCorner = true
            val anim = miniResizeSpringSpec
            try {
                val jobs =
                    listOf(
                        launch { miniSizeScale.animateTo(1f, anim) },
                        launch { offsetX.animateTo(targetX, anim) },
                        launch { offsetY.animateTo(targetY, anim) },
                    )
                jobs.forEach { it.join() }
            } finally {
                isShrinkingToCorner = false
            }
        }
    }

    fun snapTo(target: PlayerSheetValue) {
        scope.launch {
            val targetF = if (target == PlayerSheetValue.Collapsed) 1f else 0f
            expandFraction.snapTo(targetF)
            if (target == PlayerSheetValue.Expanded) {
                offsetX.snapTo(0f)
                offsetY.snapTo(0f)
            }
        }
    }
}

@Composable
fun rememberPlayerDraggableState(): PlayerDraggableState {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val expandFraction = remember { Animatable(1f) }

    return remember {
        PlayerDraggableState(offsetX, offsetY, expandFraction, scope)
    }
}

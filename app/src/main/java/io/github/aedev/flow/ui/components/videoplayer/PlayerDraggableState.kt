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
import io.github.aedev.flow.ui.components.videoplayer.motion.DraggablePlayerMotionController
import io.github.aedev.flow.ui.components.videoplayer.motion.MINI_SETTLE_DIP_HOLD_MS
import io.github.aedev.flow.ui.components.videoplayer.motion.cornerTargetX
import io.github.aedev.flow.ui.components.videoplayer.motion.cornerTargetY
import io.github.aedev.flow.ui.components.videoplayer.motion.miniResizeSpringSpec
import io.github.aedev.flow.ui.components.videoplayer.motion.miniSnapSpringSpec
import io.github.aedev.flow.ui.components.videoplayer.motion.playerExpandSpringSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
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

    /**
     * Extra downward travel a collapse settles through before lifting to the resting corner, so
     * the landing reads as a rubber band rather than a stop. Added to [offsetY] in the draw phase
     * only; the layout sets [settleDipPx] from the nav bar height.
     */
    val settleDip = Animatable(0f)
    var settleDipPx = 0f

    /** True only while no finger is down and nothing on the sheet is still moving. */
    val isSettled: Boolean
        get() =
            !isDragging &&
                !expandFraction.isRunning &&
                !offsetX.isRunning &&
                !offsetY.isRunning &&
                !settleDip.isRunning &&
                !miniSizeScale.isRunning &&
                !dragScale.isRunning &&
                !expandDragScale.isRunning

    internal val motion =
        DraggablePlayerMotionController(
            offsetX = offsetX,
            offsetY = offsetY,
            expandFraction = expandFraction,
            miniSizeScale = miniSizeScale,
        )

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
            launch { motion.resize { miniSizeScale.animateTo(1f, anim) } }
            launch { motion.animateDip { settleDip.animateTo(0f, anim) } }
            launch { motion.animateFraction { expandFraction.animateTo(0f, anim) } }
            launch {
                motion.moveOffsets {
                    launch { offsetX.animateTo(0f, anim) }
                    launch { offsetY.animateTo(0f, anim) }
                }
            }
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
        isLargeWindow: Boolean = false,
    ) {
        val maxWideFraction = if (isLargeWindow) 0.60f else 1.00f
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

        val targetX =
            if (isLargeWindow) {
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
            launch { motion.resize { miniSizeScale.animateTo(targetScale, miniResizeSpringSpec) } }
            launch {
                motion.moveOffsets {
                    launch { offsetX.animateTo(targetX, miniResizeSpringSpec) }
                    launch { offsetY.animateTo(targetY, miniResizeSpringSpec) }
                }
            }
        }
    }

    fun collapse() {
        scope.launch {
            isShrinkingToCorner = false
            val anim = playerExpandSpringSpec
            if (cachedTargetX == 0f && cachedTargetY == 0f) {
                launch { motion.snapFraction(1f) }
            } else {
                launch { motion.animateFraction { expandFraction.animateTo(1f, anim) } }
                launch {
                    motion.moveOffsets {
                        launch { offsetX.animateTo(cachedTargetX, anim) }
                        launch { offsetY.animateTo(cachedTargetY, anim) }
                    }
                }
                launch {
                    motion.animateDip {
                        // The lift starts on a fixed beat after the landing rather than when the
                        // spring reports done: its sub-pixel tail would hold the mini down for
                        // most of a second.
                        val landing = launch { settleDip.animateTo(settleDipPx, anim) }
                        delay(MINI_SETTLE_DIP_HOLD_MS)
                        landing.cancel()
                        settleDip.animateTo(0f, miniSnapSpringSpec)
                    }
                }
            }
            launch { motion.resize { miniSizeScale.animateTo(1f, anim) } }
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
                        launch { motion.resize { miniSizeScale.animateTo(1f, anim) } },
                        launch {
                            motion.moveOffsets {
                                launch { offsetX.animateTo(targetX, anim) }
                                launch { offsetY.animateTo(targetY, anim) }
                            }
                        },
                    )
                jobs.forEach { it.join() }
            } finally {
                isShrinkingToCorner = false
            }
        }
    }

    /**
     * Predictive back scrubs the collapse the way a finger does: the mini offsets snap to the
     * resting corner first, then the fraction follows the gesture. Commit with [collapse], cancel
     * with [expand].
     */
    suspend fun beginBackScrub() {
        motion.stopFraction()
        motion.snapOffsets(x = cachedTargetX, y = cachedTargetY)
    }

    suspend fun scrubBack(progress: Float) {
        motion.snapFraction(progress.coerceIn(0f, 1f))
    }

    fun snapTo(target: PlayerSheetValue) {
        scope.launch {
            val targetF = if (target == PlayerSheetValue.Collapsed) 1f else 0f
            motion.snapFraction(targetF)
            if (target == PlayerSheetValue.Expanded) {
                motion.snapOffsets(x = 0f, y = 0f)
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

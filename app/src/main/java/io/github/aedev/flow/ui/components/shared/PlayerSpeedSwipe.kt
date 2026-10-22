package io.github.aedev.flow.ui.components.shared

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.local.SpeedSwipeSide
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.truncate

/** Vertical travel that moves a speed swipe by one step. */
val SpeedSwipeStepTravel = 16.dp

/** Float slack when placing a speed on the step grid. */
private const val GRID_EPSILON = 0.001f

/**
 * The speed a vertical swipe on the speed side asks for. Steps land on the step grid, so a swipe from
 * 1.07x at 0.1 goes to 1.1x and not 1.17x, and the result stays inside the player's speed range.
 */
object PlayerSpeedSwipe {
    /**
     * The speed after a swipe that started at [startSpeed] and has travelled [travelUpPx] upwards
     * (negative is down), one [step] for every [stepTravelPx].
     */
    fun speedFor(
        startSpeed: Float,
        travelUpPx: Float,
        stepTravelPx: Float,
        step: Float,
        minSpeed: Float,
        maxSpeed: Float,
    ): Float {
        if (stepTravelPx <= 0f || step <= 0f) return startSpeed
        val steps = truncate(travelUpPx / stepTravelPx).toInt()
        if (steps == 0) return startSpeed
        val gridIndex = startSpeed / step
        val baseIndex = if (steps > 0) floor(gridIndex + GRID_EPSILON) else ceil(gridIndex - GRID_EPSILON)
        val target = (baseIndex + steps) * step
        return (round(target * 100f) / 100f).coerceIn(minSpeed, maxSpeed)
    }

    /** Whether a vertical drag that starts at [x] on a player [width] wide changes the speed. */
    fun ownsDrag(
        side: SpeedSwipeSide,
        x: Float,
        width: Float,
    ): Boolean =
        when (side) {
            SpeedSwipeSide.OFF -> false
            SpeedSwipeSide.LEFT -> x < width / 2f
            SpeedSwipeSide.RIGHT -> x >= width / 2f
        }
}

/**
 * One speed swipe in progress. It starts from the speed actually playing once the drag is claimed:
 * claiming it ends a held long-press boost, whose release restores the speed underneath first. Live
 * streams stay at their speed, so a swipe there changes nothing.
 */
class SpeedSwipeDrag(
    private val haptics: HapticFeedback,
    private val step: () -> Float,
    private val currentSpeed: () -> Float,
    private val isLiveStream: () -> Boolean,
    private val setSpeed: (Float) -> Unit,
    private val onSpeed: (Float) -> Unit,
    private val onEnd: (Float) -> Unit,
) {
    private var startSpeed = Float.NaN
    private var travelUpPx = 0f
    private var lastSpeed = Float.NaN
    private var isLive = false

    fun begin() {
        isLive = isLiveStream()
    }

    fun drag(
        dy: Float,
        stepTravelPx: Float,
    ) {
        if (isLive) return
        if (startSpeed.isNaN()) {
            startSpeed = currentSpeed()
            lastSpeed = startSpeed
            travelUpPx = 0f
        }
        travelUpPx -= dy
        val next =
            PlayerSpeedSwipe.speedFor(
                startSpeed = startSpeed,
                travelUpPx = travelUpPx,
                stepTravelPx = stepTravelPx,
                step = step(),
                minSpeed = MIN_PLAYBACK_SPEED,
                maxSpeed = MAX_SLIDER_PLAYBACK_SPEED,
            )
        // Travel past either end is not banked, so turning back moves the speed at once.
        if (next == MIN_PLAYBACK_SPEED || next == MAX_SLIDER_PLAYBACK_SPEED) {
            startSpeed = next
            travelUpPx = 0f
        }
        if (next != lastSpeed) {
            setSpeed(next)
            haptics.performHapticFeedback(
                if (next == NORMAL_PLAYBACK_SPEED) HapticFeedbackType.GestureThresholdActivate else HapticFeedbackType.SegmentFrequentTick,
            )
            lastSpeed = next
        }
        onSpeed(next)
    }

    /** Hands the swipe's speed over to be kept, once, and readies for the next swipe. */
    fun end() {
        if (!lastSpeed.isNaN() && !isLive) onEnd(lastSpeed)
        startSpeed = Float.NaN
        lastSpeed = Float.NaN
        isLive = false
    }
}

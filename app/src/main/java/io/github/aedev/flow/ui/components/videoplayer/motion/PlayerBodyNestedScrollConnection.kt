package io.github.aedev.flow.ui.components.videoplayer.motion

import androidx.compose.animation.core.animate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

private const val PORTRAIT_FS_COMMIT_FRACTION = 0.4f
private const val PORTRAIT_FS_COMMIT_VELOCITY = 1400f

/**
 * Lets the body list drive two things while the player is expanded: a tall video shrinking to
 * 16:9 as the list scrolls up, and an overscroll pull at the top that grows the player into
 * portrait fullscreen. Both fractions live in the layout as snapshot state and are only ever read
 * in the layout or draw phase.
 */
internal class PlayerBodyNestedScrollConnection(
    private val expandedVideoHeight: Float,
    private val baseVideoHeight: Float,
    private val playerHeightFraction: () -> Float,
    private val onPlayerHeightFractionChange: (Float) -> Unit,
    private val portraitFsFraction: () -> Float,
    private val onPortraitFsFractionChange: (Float) -> Unit,
    private val portraitFsTravel: () -> Float,
    private val portraitFsEnabled: () -> Boolean,
    private val portraitFsActivationPx: () -> Float,
    private val expandFraction: () -> Float,
    private val onEnterPortraitFullscreen: () -> (() -> Unit)?,
) : NestedScrollConnection {
    private var listScrolledThisGesture = false
    private var pullAccum = 0f

    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        val delta = available.y
        val portraitFraction = portraitFsFraction()
        if (source == NestedScrollSource.UserInput && delta < 0f && portraitFraction > 0f && portraitFsEnabled()) {
            val travel = portraitFsTravel()
            val maxConsumable = portraitFraction * travel
            val consumed = maxOf(delta, -maxConsumable)
            onPortraitFsFractionChange((portraitFraction + consumed / travel).coerceIn(0f, 1f))
            return Offset(0f, consumed)
        }
        val playerDelta = expandedVideoHeight - baseVideoHeight
        val heightFraction = playerHeightFraction()
        if (delta < 0 && heightFraction > 0f && playerDelta > 1f) {
            val maxConsumable = heightFraction * playerDelta
            val consumed = maxOf(delta, -maxConsumable)
            onPlayerHeightFractionChange((heightFraction + consumed / playerDelta).coerceIn(0f, 1f))
            return Offset(0f, consumed)
        }
        return Offset.Zero
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (consumed.y != 0f) listScrolledThisGesture = true
        val delta = available.y
        val playerDelta = expandedVideoHeight - baseVideoHeight
        val heightFraction = playerHeightFraction()
        if (delta > 0 && heightFraction < 1f && playerDelta > 1f) {
            val maxConsumable = (1f - heightFraction) * playerDelta
            val consumable = minOf(delta, maxConsumable)
            onPlayerHeightFractionChange((heightFraction + consumable / playerDelta).coerceIn(0f, 1f))
            return Offset(0f, consumable)
        }
        val canPull =
            source == NestedScrollSource.UserInput &&
                !listScrolledThisGesture &&
                portraitFsEnabled() &&
                expandFraction() < 0.05f
        val portraitFraction = portraitFsFraction()
        if (delta > 0f && portraitFraction < 1f && canPull) {
            pullAccum += delta
            val past = pullAccum - portraitFsActivationPx()
            if (past <= 0f) return Offset(0f, delta)
            val travel = portraitFsTravel()
            val effective = minOf(delta, past)
            val maxConsumable = (1f - portraitFraction) * travel
            val consumable = minOf(effective, maxConsumable)
            onPortraitFsFractionChange((portraitFraction + consumable / travel).coerceIn(0f, 1f))
            return Offset(0f, delta)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val frac = portraitFsFraction()
        listScrolledThisGesture = false
        pullAccum = 0f
        if (frac <= 0f || frac >= 1f) return Velocity.Zero
        val shouldEnter = frac > PORTRAIT_FS_COMMIT_FRACTION || available.y > PORTRAIT_FS_COMMIT_VELOCITY
        animate(
            initialValue = frac,
            targetValue = if (shouldEnter) 1f else 0f,
            initialVelocity = available.y,
            animationSpec = portraitFullscreenSettleSpec,
        ) { value, _ -> onPortraitFsFractionChange(value) }
        if (shouldEnter) onEnterPortraitFullscreen()?.invoke()
        return available
    }
}

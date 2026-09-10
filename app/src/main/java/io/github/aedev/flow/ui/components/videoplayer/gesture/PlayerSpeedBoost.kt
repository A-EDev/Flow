package io.github.aedev.flow.ui.components.videoplayer.gesture

/**
 * The speed a press-and-hold on the video applies.
 *
 * Holding again while already boosted steps up rather than re-applying the same target, so a second
 * hold on an already fast stream still does something.
 */
internal object PlayerSpeedBoost {
    const val MAX_BOOST_SPEED = 4.0f

    private const val BOOST_STEP = 0.5f

    fun boostedPlaybackSpeed(
        currentSpeed: Float,
        targetSpeed: Float,
    ): Float {
        val target = targetSpeed.coerceIn(0.1f, MAX_BOOST_SPEED)
        val current = currentSpeed.takeIf { it > 0f } ?: 1.0f
        return if (current < target) {
            target
        } else {
            (current + BOOST_STEP).coerceAtMost(MAX_BOOST_SPEED)
        }
    }
}

package io.github.aedev.flow.ui.components.videoplayer.motion

internal enum class MiniPlayerTap { SINGLE_PENDING, DOUBLE }

/**
 * Classifies mini player taps the way the platform's tap detector does, without owning a pointer
 * node: a second tap inside the double-tap timeout is a double tap, anything else starts a new
 * single-tap window. The caller commits a pending single tap after the same timeout.
 */
internal class MiniPlayerTapDecider {
    private var lastTapUptimeMillis = 0L

    fun onTap(
        uptimeMillis: Long,
        doubleTapTimeoutMillis: Long,
    ): MiniPlayerTap {
        val isDouble = lastTapUptimeMillis != 0L && uptimeMillis - lastTapUptimeMillis < doubleTapTimeoutMillis
        lastTapUptimeMillis = if (isDouble) 0L else uptimeMillis
        return if (isDouble) MiniPlayerTap.DOUBLE else MiniPlayerTap.SINGLE_PENDING
    }
}

package io.github.aedev.flow.player

/** Whether the queue behind a PiP window can step back or forward. */
data class PipQueueNavigation(
    val hasPrevious: Boolean,
    val hasNext: Boolean,
)

internal enum class PipControl { PREVIOUS, PLAY, PAUSE, NEXT }

internal data class PipActionSpec(
    val control: PipControl,
    val enabled: Boolean = true,
)

private const val SKIP_LAYOUT_ACTION_COUNT = 3

/** The window's buttons in display order. Skip buttons need a queue and room for all three. */
internal fun pipActionSpecs(
    isPlaying: Boolean,
    navigation: PipQueueNavigation?,
    maxActions: Int,
): List<PipActionSpec> {
    val playPause = PipActionSpec(if (isPlaying) PipControl.PAUSE else PipControl.PLAY)
    if (navigation == null || maxActions < SKIP_LAYOUT_ACTION_COUNT) return listOf(playPause)
    return listOf(
        PipActionSpec(PipControl.PREVIOUS, enabled = navigation.hasPrevious),
        playPause,
        PipActionSpec(PipControl.NEXT, enabled = navigation.hasNext),
    )
}

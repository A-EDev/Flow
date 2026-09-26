package io.github.aedev.flow.player

/**
 * Pure policy for the delayed PiP-exit shutdown in [io.github.aedev.flow.MainActivity].
 *
 * Tapping the system PiP close button backgrounds the activity without explicit background
 * playback, so playback must stop. A headphones-triggered exit sets explicit background
 * playback active before PiP exits, so the normal onStop handoff must retain the player.
 * A foreground PiP exit is a plain expand and never triggers dismissal cleanup.
 */
object PipDismissPolicy {
    fun shouldDismissAfterPipExit(
        stillBackgrounded: Boolean,
        isInPipMode: Boolean,
        explicitBackgroundActive: Boolean,
    ): Boolean = stillBackgrounded && !isInPipMode && !explicitBackgroundActive
}

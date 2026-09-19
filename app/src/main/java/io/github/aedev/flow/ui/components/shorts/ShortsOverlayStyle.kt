package io.github.aedev.flow.ui.components.shorts

import androidx.compose.runtime.Immutable
import io.github.aedev.flow.data.local.ShortsPlayerUiMode

/**
 * How a reel's chrome is drawn for one [ShortsPlayerUiMode]. The composables read these facts,
 * never the mode, so a fourth mode is a new row here and nothing else.
 */
@Immutable
internal data class ShortsOverlayStyle(
    /** Rail buttons carry a label, and a missing count falls back to the action's name. */
    val showRailLabels: Boolean,
    val subscribeControl: ShortsSubscribeControl,
    /** Chrome stays hidden until a centre tap and hides again on its own. */
    val controlsOnDemand: Boolean,
    /** Saving confirms with a toast, because the rail has no label to change. */
    val toastOnSave: Boolean,
) {
    companion object {
        fun from(mode: ShortsPlayerUiMode): ShortsOverlayStyle =
            when (mode) {
                ShortsPlayerUiMode.DEFAULT -> {
                    ShortsOverlayStyle(
                        showRailLabels = true,
                        subscribeControl = ShortsSubscribeControl.Button,
                        controlsOnDemand = false,
                        toastOnSave = false,
                    )
                }

                ShortsPlayerUiMode.SIMPLE -> {
                    ShortsOverlayStyle(
                        showRailLabels = false,
                        subscribeControl = ShortsSubscribeControl.IconToggle,
                        controlsOnDemand = false,
                        toastOnSave = true,
                    )
                }

                ShortsPlayerUiMode.IMPRESSIVE -> {
                    ShortsOverlayStyle(
                        showRailLabels = true,
                        subscribeControl = ShortsSubscribeControl.Button,
                        controlsOnDemand = true,
                        toastOnSave = false,
                    )
                }
            }
    }
}

internal enum class ShortsSubscribeControl {
    /** A square icon toggle beside the channel name: a plus to subscribe, a tick once subscribed. */
    IconToggle,

    /** The app-wide subscribe button in its compact size. */
    Button,
}

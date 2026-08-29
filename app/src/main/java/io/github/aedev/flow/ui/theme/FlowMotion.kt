package io.github.aedev.flow.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Shared motion values for the Flow shell and content surfaces. */
object FlowMotion {
    const val EnterDurationMillis: Int = 220
    const val ExitDurationMillis: Int = 160
    const val ContentDurationMillis: Int = 240
    const val PressedScale: Float = 0.98f

    /** Fast start with a soft landing for elements entering the screen. */
    val EnterEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** Quick departure that does not make the user wait for the old content. */
    val ExitEasing: Easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)

    fun durationFor(durationMillis: Int, reduceMotion: Boolean): Int =
        if (reduceMotion) 0 else durationMillis.coerceAtLeast(0)

    fun scaleFor(
        isPressed: Boolean,
        reduceMotion: Boolean,
        pressedScale: Float = PressedScale,
    ): Float =
        if (isPressed && !reduceMotion) pressedScale else 1f
}

/** Reads Android's animator scale so interactive feedback can respect reduced-motion settings. */
@Composable
fun rememberFlowReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}

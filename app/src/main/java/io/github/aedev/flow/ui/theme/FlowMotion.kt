package io.github.aedev.flow.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

object FlowMotion {
    const val FEEDBACK_DURATION_MILLIS: Int = 120
    const val EXIT_DURATION_MILLIS: Int = 160
    const val ENTER_DURATION_MILLIS: Int = 220
    const val CONTENT_DURATION_MILLIS: Int = 240
    const val EMPHASIZED_DURATION_MILLIS: Int = 320
    const val PRESSED_SCALE: Float = 0.98f

    val EnterEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val ExitEasing: Easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)

    fun durationFor(
        durationMillis: Int,
        reduceMotion: Boolean,
    ): Int = if (reduceMotion) 0 else durationMillis.coerceAtLeast(0)

    fun scaleFor(
        isPressed: Boolean,
        reduceMotion: Boolean,
        pressedScale: Float = PRESSED_SCALE,
    ): Float = if (isPressed && !reduceMotion) pressedScale else 1f
}

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

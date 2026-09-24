package io.github.aedev.flow.ui.startup

import android.app.Activity
import android.os.SystemClock
import android.view.animation.AnimationUtils
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider

private const val MAX_HOLD_MS = 800L
private const val ICON_EXIT_SCALE = 1.25f

/**
 * The platform splash, held only until the first frame can be drawn in the user's theme, then
 * handed off with one short exit. [MAX_HOLD_MS] caps the hold so a stalled preference read can
 * never keep the app hidden.
 */
class SplashController(
    private val activity: Activity,
) {
    @Volatile
    var themeReady: Boolean = false

    private val startedAt = SystemClock.uptimeMillis()

    fun install(splash: SplashScreen) {
        splash.setKeepOnScreenCondition { !themeReady && SystemClock.uptimeMillis() - startedAt < MAX_HOLD_MS }
        splash.setOnExitAnimationListener(::animateExit)
    }

    private fun animateExit(provider: SplashScreenViewProvider) {
        val interpolator = AnimationUtils.loadInterpolator(activity, android.R.interpolator.fast_out_slow_in)
        val duration = activity.resources.getInteger(android.R.integer.config_mediumAnimTime).toLong()
        provider.iconView
            .animate()
            .scaleX(ICON_EXIT_SCALE)
            .scaleY(ICON_EXIT_SCALE)
            .setInterpolator(interpolator)
            .setDuration(duration)
            .start()
        provider.view
            .animate()
            .alpha(0f)
            .setInterpolator(interpolator)
            .setDuration(duration)
            .withEndAction(provider::remove)
            .start()
    }
}

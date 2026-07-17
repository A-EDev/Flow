package io.github.aedev.flow.widget.core

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProviders
import androidx.glance.unit.ColorProvider

/**
 * Theme wrapper for all Flow widgets. [colors] comes from [widgetColorsFlow], so every
 * widget renders in the exact palette the user selected in the app.
 */
@Composable
fun FlowGlanceTheme(colors: ColorProviders, content: @Composable () -> Unit) {
    GlanceTheme(colors = colors, content = content)
}

/**
 * Standard root surface for every Flow widget: theme surface color + the launcher's
 * system corner radius on Android 12+ (squared below, matching platform convention).
 * Pass a container color to make the whole widget a tonal block (e.g. the recognizer tile).
 */
@Composable
fun GlanceModifier.widgetSurface(
    color: ColorProvider = GlanceTheme.colors.widgetBackground,
): GlanceModifier {
    val withBackground = this
        .appWidgetBackground()
        .background(color)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        withBackground.cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        withBackground
    }
}

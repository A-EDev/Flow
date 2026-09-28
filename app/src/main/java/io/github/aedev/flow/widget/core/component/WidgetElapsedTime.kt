package io.github.aedev.flow.widget.core.component

import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.aedev.flow.R
import io.github.aedev.flow.utils.formatDurationMillis

/**
 * Elapsed playback time. While playing it is a platform Chronometer the launcher advances each
 * second; the app publishes only on player events, so a Glance text would sit frozen.
 */
@Composable
internal fun WidgetElapsedTime(
    positionMs: Long,
    capturedAtElapsedMs: Long,
    isRunning: Boolean,
    style: TextStyle,
    modifier: GlanceModifier = GlanceModifier,
) {
    if (!isRunning || capturedAtElapsedMs <= 0L) {
        // Padded like the Chronometer's own "00:07", so pausing does not shift the digits.
        Text(text = formatDurationMillis(positionMs, padMinutes = true), style = style, maxLines = 1, modifier = modifier)
        return
    }
    val context = LocalContext.current
    val views =
        RemoteViews(context.packageName, R.layout.widget_chronometer).apply {
            setChronometer(R.id.widget_chronometer, capturedAtElapsedMs - positionMs, null, true)
            style.fontSize?.let { setTextViewTextSize(R.id.widget_chronometer, TypedValue.COMPLEX_UNIT_SP, it.value) }
            style.color?.let { color ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setColorProvider(R.id.widget_chronometer, "setTextColor", color, context)
                } else {
                    setTextColor(R.id.widget_chronometer, color.getColor(context).toArgb())
                }
            }
        }
    // Unsized, an embedded platform view fills its row and pushes its neighbours out of sight.
    AndroidRemoteViews(views, modifier)
}

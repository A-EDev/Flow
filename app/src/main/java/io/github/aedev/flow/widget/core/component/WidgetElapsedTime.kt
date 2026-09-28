package io.github.aedev.flow.widget.core.component

import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.glance.LocalContext
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.color.DayNightColorProvider
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.unit.ResourceColorProvider
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
) {
    if (!isRunning || capturedAtElapsedMs <= 0L) {
        Text(text = formatDurationMillis(positionMs), style = style, maxLines = 1)
        return
    }
    val context = LocalContext.current
    val views =
        RemoteViews(context.packageName, R.layout.widget_chronometer).apply {
            setChronometer(R.id.widget_chronometer, capturedAtElapsedMs - positionMs, null, true)
            style.fontSize?.let { setTextViewTextSize(R.id.widget_chronometer, TypedValue.COMPLEX_UNIT_SP, it.value) }
            style.color?.let { setTextColor(context, R.id.widget_chronometer, it) }
        }
    AndroidRemoteViews(views)
}

private fun RemoteViews.setTextColor(
    context: Context,
    viewId: Int,
    color: ColorProvider,
) {
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && color is DayNightColorProvider -> {
            setColorInt(viewId, "setTextColor", color.day.toArgb(), color.night.toArgb())
        }

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && color is ResourceColorProvider -> {
            setColorStateList(viewId, "setTextColor", color.resId)
        }

        else -> {
            setTextColor(viewId, color.getColor(context).toArgb())
        }
    }
}

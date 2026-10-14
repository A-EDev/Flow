package io.github.aedev.flow.widget.core.component

import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.toArgb
import androidx.glance.color.DayNightColorProvider
import androidx.glance.unit.ColorProvider
import androidx.glance.unit.ResourceColorProvider

/**
 * Hands a theme colour to a platform view inside the widget as a day/night pair, so the launcher
 * switches it with dark mode the way it does every Glance colour.
 */
@RequiresApi(Build.VERSION_CODES.S)
internal fun RemoteViews.setColorProvider(
    viewId: Int,
    method: String,
    color: ColorProvider,
    context: Context,
) {
    when (color) {
        is DayNightColorProvider -> {
            setColorStateList(viewId, method, ColorStateList.valueOf(color.day.toArgb()), ColorStateList.valueOf(color.night.toArgb()))
        }

        is ResourceColorProvider -> {
            setColorStateList(viewId, method, color.resId)
        }

        else -> {
            setColorStateList(viewId, method, ColorStateList.valueOf(color.getColor(context).toArgb()))
        }
    }
}

package io.github.aedev.flow.widget.downloads

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.musicPlayerRoute
import io.github.aedev.flow.widget.core.FlowGlanceTheme
import io.github.aedev.flow.widget.core.WIDGET_HERO_CORNER_DP
import io.github.aedev.flow.widget.core.WIDGET_HERO_HEIGHT_PX
import io.github.aedev.flow.widget.core.WIDGET_HERO_WIDTH_PX
import io.github.aedev.flow.widget.core.WIDGET_THUMB_CORNER_DP
import io.github.aedev.flow.widget.core.WIDGET_THUMB_HEIGHT
import io.github.aedev.flow.widget.core.WIDGET_THUMB_WIDTH
import io.github.aedev.flow.widget.core.WidgetDeepLink
import io.github.aedev.flow.widget.core.WidgetImageLoader
import io.github.aedev.flow.widget.core.WidgetVideoItem
import io.github.aedev.flow.widget.core.WidgetVideoPanel
import io.github.aedev.flow.widget.core.widgetColorsFlow
import io.github.aedev.flow.widget.core.widgetEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Offline downloads panel: newest download as a hero card, then compact rows. */
class DownloadsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val density = context.resources.displayMetrics.density
        val thumbWidthPx = (WIDGET_THUMB_WIDTH.value * density).toInt()
        val thumbHeightPx = (WIDGET_THUMB_HEIGHT.value * density).toInt()

        val items = withContext(Dispatchers.IO) {
            widgetEntryPoint(context).downloadsSource().load().items
                .mapIndexed { index, download ->
                    WidgetVideoItem(
                        videoId = download.id,
                        title = download.title,
                        subtitle = download.subtitle,
                        thumbnail = if (index == 0) null else WidgetImageLoader.load(
                            context, download.thumbnailUrl,
                            thumbWidthPx, thumbHeightPx, WIDGET_THUMB_CORNER_DP * density,
                        ),
                        hero = if (index == 0) WidgetImageLoader.load(
                            context, download.thumbnailUrl,
                            WIDGET_HERO_WIDTH_PX, WIDGET_HERO_HEIGHT_PX, WIDGET_HERO_CORNER_DP * density,
                        ) else null,
                        openIntent = if (download.isMusic) {
                            WidgetDeepLink.openRoute(context, musicPlayerRoute(download.id))
                        } else {
                            null
                        },
                    )
                }
        }

        val colorsFlow = widgetColorsFlow(context)
        val initialColors = colorsFlow.first()

        provideContent {
            val colors by colorsFlow.collectAsState(initialColors)
            FlowGlanceTheme(colors) {
                WidgetVideoPanel(
                    title = context.getString(R.string.widget_downloads),
                    headerIconRes = R.drawable.ic_widget_download,
                    chipBackground = GlanceTheme.colors.secondaryContainer,
                    chipContent = GlanceTheme.colors.onSecondaryContainer,
                    headerAction = actionStartActivity(
                        WidgetDeepLink.openRoute(context, WidgetDeepLink.ROUTE_DOWNLOADS),
                    ),
                    emptyMessage = context.getString(R.string.widget_no_downloads),
                    emptyAction = actionStartActivity(
                        WidgetDeepLink.openRoute(context, WidgetDeepLink.ROUTE_DOWNLOADS),
                    ),
                    items = items,
                )
            }
        }
    }
}

class DownloadsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DownloadsWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        widgetEntryPoint(context).widgetContentSync().onPlacementChanged()
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        widgetEntryPoint(context).widgetContentSync().onPlacementChanged()
    }
}

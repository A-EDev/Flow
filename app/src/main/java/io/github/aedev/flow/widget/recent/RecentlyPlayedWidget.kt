package io.github.aedev.flow.widget.recent

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
import io.github.aedev.flow.data.local.ViewHistory
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Continue-watching panel: newest video as a hero card, then compact rows. */
class RecentlyPlayedWidget : GlanceAppWidget() {

    companion object {
        private const val MAX_ITEMS = 8
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val density = context.resources.displayMetrics.density
        val thumbWidthPx = (WIDGET_THUMB_WIDTH.value * density).toInt()
        val thumbHeightPx = (WIDGET_THUMB_HEIGHT.value * density).toInt()

        val items = withContext(Dispatchers.IO) {
            ViewHistory.getInstance(context).getVideoHistoryFlow().first()
                .take(MAX_ITEMS)
                .mapIndexed { index, entry ->
                    WidgetVideoItem(
                        videoId = entry.videoId,
                        title = entry.title,
                        subtitle = entry.channelName,
                        thumbnail = if (index == 0) null else WidgetImageLoader.load(
                            context, entry.thumbnailUrl,
                            thumbWidthPx, thumbHeightPx, WIDGET_THUMB_CORNER_DP * density,
                        ),
                        hero = if (index == 0) WidgetImageLoader.load(
                            context, entry.thumbnailUrl,
                            WIDGET_HERO_WIDTH_PX, WIDGET_HERO_HEIGHT_PX, WIDGET_HERO_CORNER_DP * density,
                        ) else null,
                    )
                }
        }

        val colorsFlow = widgetColorsFlow(context)
        val initialColors = colorsFlow.first()

        provideContent {
            val colors by colorsFlow.collectAsState(initialColors)
            FlowGlanceTheme(colors) {
                WidgetVideoPanel(
                    title = context.getString(R.string.widget_recently_played),
                    headerIconRes = R.drawable.ic_widget_history,
                    chipBackground = GlanceTheme.colors.tertiaryContainer,
                    chipContent = GlanceTheme.colors.onTertiaryContainer,
                    headerAction = actionStartActivity(
                        WidgetDeepLink.openRoute(context, WidgetDeepLink.ROUTE_HISTORY),
                    ),
                    emptyMessage = context.getString(R.string.widget_no_recent),
                    emptyAction = actionStartActivity(WidgetDeepLink.openApp(context)),
                    items = items,
                )
            }
        }
    }
}

class RecentlyPlayedWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecentlyPlayedWidget()
}

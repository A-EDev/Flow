package io.github.aedev.flow.widget.nowplaying

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import io.github.aedev.flow.widget.core.image.WidgetImageLoader
import io.github.aedev.flow.widget.core.state.nowPlayingSnapshotFlow
import io.github.aedev.flow.widget.core.theme.FlowGlanceTheme
import io.github.aedev.flow.widget.core.theme.bakedCornerRadiusPx
import io.github.aedev.flow.widget.core.theme.dpToPx
import io.github.aedev.flow.widget.core.theme.widgetColorsFlow
import kotlinx.coroutines.flow.first

class NowPlayingWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(NowPlayingLayout.sizes)

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        // One bitmap at the largest size this instance draws, shared by every layout in the RemoteViews.
        val artworkPx = context.dpToPx(NowPlayingLayout.artworkDpFor(GlanceAppWidgetManager(context).getAppWidgetSizes(id)))
        val cornerPx = bakedCornerRadiusPx(context)
        val snapshotFlow = context.nowPlayingSnapshotFlow()
        val initialSnapshot = snapshotFlow.first()
        val initialArtwork = WidgetImageLoader.load(context, initialSnapshot?.artworkUrl, artworkPx, cornerRadiusPx = cornerPx)
        val colorsFlow = widgetColorsFlow(context)
        val initialColors = colorsFlow.first()

        provideContent {
            val snapshot by snapshotFlow.collectAsState(initialSnapshot)
            val artwork by produceState(initialArtwork, snapshot?.artworkUrl) {
                value = WidgetImageLoader.load(context, snapshot?.artworkUrl, artworkPx, cornerRadiusPx = cornerPx)
            }
            val colors by colorsFlow.collectAsState(initialColors)
            FlowGlanceTheme(colors) {
                NowPlayingContent(snapshot = snapshot, artwork = artwork)
            }
        }
    }
}

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}

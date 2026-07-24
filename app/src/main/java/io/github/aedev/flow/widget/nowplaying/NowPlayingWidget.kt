package io.github.aedev.flow.widget.nowplaying

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import io.github.aedev.flow.widget.core.FlowGlanceTheme
import io.github.aedev.flow.widget.core.WidgetImageLoader
import io.github.aedev.flow.widget.core.WidgetShape
import io.github.aedev.flow.widget.core.nowPlayingSnapshotFlow
import io.github.aedev.flow.widget.core.widgetColorsFlow
import kotlinx.coroutines.flow.first

class NowPlayingWidget : GlanceAppWidget() {

    companion object {
        val COMPACT = DpSize(110.dp, 48.dp)
        val WIDE = DpSize(180.dp, 48.dp)
        val LARGE = DpSize(220.dp, 140.dp)

        // Largest rendered artwork is the 88dp vinyl circle; load once and reuse everywhere.
        private const val ARTWORK_DP = 88f
    }

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, WIDE, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val density = context.resources.displayMetrics.density
        val artworkPx = (ARTWORK_DP * density).toInt()

        val snapshotFlow = context.nowPlayingSnapshotFlow()
        val initialSnapshot = snapshotFlow.first()
        val initialArtwork =
            WidgetImageLoader.load(context, initialSnapshot?.artworkUrl, artworkPx, shape = WidgetShape.SUNNY)
        val colorsFlow = widgetColorsFlow(context)
        val initialColors = colorsFlow.first()

        provideContent {
            val snapshot by snapshotFlow.collectAsState(initialSnapshot)
            val artwork by produceState(initialArtwork, snapshot?.artworkUrl) {
                value = WidgetImageLoader.load(context, snapshot?.artworkUrl, artworkPx, shape = WidgetShape.SUNNY)
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

package io.github.aedev.flow.widget.nowplaying

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.color.ColorProviders
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.widget.core.image.WidgetImageLoader
import io.github.aedev.flow.widget.core.state.nowPlayingSnapshotFlow
import io.github.aedev.flow.widget.core.theme.FlowGlanceTheme
import io.github.aedev.flow.widget.core.theme.artworkColorProviders
import io.github.aedev.flow.widget.core.theme.bakedCornerRadiusPx
import io.github.aedev.flow.widget.core.theme.dpToPx
import io.github.aedev.flow.widget.core.theme.widgetColorsFlow
import kotlinx.coroutines.flow.first

class NowPlayingWidget : GlanceAppWidget() {
    // Exact, so the card's artwork can match the widget's real height.
    override val sizeMode = SizeMode.Exact

    override val previewSizeMode = SizeMode.Responsive(setOf(NowPlayingLayout.STRIP.size, NowPlayingLayout.CARD.size))

    // Picker previews are one static render without artwork, which would only weigh down the preview.
    override suspend fun providePreview(
        context: Context,
        widgetCategory: Int,
    ) {
        val snapshot = context.nowPlayingSnapshotFlow { NowPlayingWidgetPublisher.hasPublished }.first()?.copy(isPlaying = false)
        val colors = widgetColorsFlow(context).first()
        provideContent { FlowGlanceTheme(colors) { NowPlayingContent(snapshot = snapshot, artwork = null) } }
    }

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        // One bitmap at the largest size this instance draws, shared by every layout in the RemoteViews.
        val artworkPx = context.dpToPx(NowPlayingLayout.artworkDpFor(GlanceAppWidgetManager(context).getAppWidgetSizes(id)))
        val cornerPx = bakedCornerRadiusPx(context)
        val snapshotFlow = context.nowPlayingSnapshotFlow { NowPlayingWidgetPublisher.hasPublished }
        val initialSnapshot = snapshotFlow.first()
        val initialArtwork = WidgetImageLoader.load(context, initialSnapshot?.artworkUrl, artworkPx, cornerRadiusPx = cornerPx)
        val colorsFlow = widgetColorsFlow(context)
        val initialColors = colorsFlow.first()
        val styleFlow = PlayerPreferences(context).musicPlayerBackgroundStyle
        val initialStyle = styleFlow.first()
        val initialArtworkColors = artworkColorProviders(context, initialSnapshot?.artworkUrl, initialStyle, initialColors)

        provideContent {
            val snapshot by snapshotFlow.collectAsState(initialSnapshot)
            val artwork by produceState(initialArtwork, snapshot?.artworkUrl) {
                value = WidgetImageLoader.load(context, snapshot?.artworkUrl, artworkPx, cornerRadiusPx = cornerPx)
            }
            val colors by colorsFlow.collectAsState(initialColors)
            val style by styleFlow.collectAsState(initialStyle)
            val artworkColors by produceState<ColorProviders?>(initialArtworkColors, snapshot?.artworkUrl, style, colors) {
                value = artworkColorProviders(context, snapshot?.artworkUrl, style, colors)
            }
            // Read so each progress tick recomposes; the bar itself is placed from the clock right now.
            NowPlayingProgressTicker.clock.collectAsState().value
            FlowGlanceTheme(artworkColors ?: colors) {
                NowPlayingContent(snapshot = snapshot, artwork = artwork, nowElapsedMs = SystemClock.elapsedRealtime())
            }
        }
    }
}

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}

package io.github.aedev.flow.widget.turntable

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import io.github.aedev.flow.widget.core.FlowGlanceTheme
import io.github.aedev.flow.widget.core.NowPlayingSnapshot
import io.github.aedev.flow.widget.core.WidgetImageLoader
import io.github.aedev.flow.widget.core.WidgetShape
import io.github.aedev.flow.widget.core.nowPlayingSnapshotFlow
import io.github.aedev.flow.widget.core.widgetColorsFlow
import io.github.aedev.flow.widget.core.widgetSurface
import io.github.aedev.flow.widget.nowplaying.ShapedArtwork
import io.github.aedev.flow.widget.nowplaying.WidePlayPauseButton
import kotlinx.coroutines.flow.first

/**
 * The turntable: a 2x2 tile that is almost entirely the spinning record — full-size
 * circular artwork with a center hole and a single morphing play control docked at
 * the bottom edge. Art tap opens the music player.
 */
class TurntableWidget : GlanceAppWidget() {

    companion object {
        // Load at the largest disc we can render on a resized turntable.
        private const val DISC_LOAD_DP = 220f
    }

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val density = context.resources.displayMetrics.density
        val discPx = (DISC_LOAD_DP * density).toInt()

        val snapshotFlow = context.nowPlayingSnapshotFlow()
        val initialSnapshot = snapshotFlow.first()
        val initialArtwork =
            WidgetImageLoader.load(context, initialSnapshot?.artworkUrl, discPx, shape = WidgetShape.COOKIE)
        val colorsFlow = widgetColorsFlow(context)
        val initialColors = colorsFlow.first()

        provideContent {
            val snapshot by snapshotFlow.collectAsState(initialSnapshot)
            val artwork by produceState(initialArtwork, snapshot?.artworkUrl) {
                value = WidgetImageLoader.load(context, snapshot?.artworkUrl, discPx, shape = WidgetShape.COOKIE)
            }
            val colors by colorsFlow.collectAsState(initialColors)
            FlowGlanceTheme(colors) {
                TurntableContent(snapshot = snapshot, artwork = artwork)
            }
        }
    }
}

@Composable
private fun TurntableContent(snapshot: NowPlayingSnapshot?, artwork: Bitmap?) {
    val size = LocalSize.current
    val discSize = minOf(size.width, size.height) - 20.dp
    Box(modifier = GlanceModifier.fillMaxSize().widgetSurface()) {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            ShapedArtwork(artwork, discSize, centerHole = artwork != null)
        }
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(8.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            WidePlayPauseButton(snapshot?.isPlaying == true, 36.dp)
        }
    }
}

class TurntableWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TurntableWidget()
}

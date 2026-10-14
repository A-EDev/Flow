package io.github.aedev.flow.widget.turntable

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.color.ColorProviders
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.widget.core.action.WidgetDeepLink
import io.github.aedev.flow.widget.core.component.WidePlayPauseButton
import io.github.aedev.flow.widget.core.component.WidgetEmptyState
import io.github.aedev.flow.widget.core.image.ShapeDecor
import io.github.aedev.flow.widget.core.image.WidgetImageLoader
import io.github.aedev.flow.widget.core.image.WidgetShape
import io.github.aedev.flow.widget.core.state.NowPlayingSnapshot
import io.github.aedev.flow.widget.core.state.nowPlayingSnapshotFlow
import io.github.aedev.flow.widget.core.theme.FlowGlanceTheme
import io.github.aedev.flow.widget.core.theme.artworkColorProviders
import io.github.aedev.flow.widget.core.theme.dpToPx
import io.github.aedev.flow.widget.core.theme.widgetColorsFlow
import io.github.aedev.flow.widget.core.theme.widgetSurface
import io.github.aedev.flow.widget.nowplaying.NowPlayingWidgetPublisher
import kotlinx.coroutines.flow.first
import kotlin.math.min

/**
 * The record: artwork on a Cookie12 disc with a spindle hole and one play control. Paused, the disc
 * steps down in size; RemoteViews cannot spin it, and nothing here animates.
 */
class TurntableWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    // Exact sizing has no size of its own in the picker, so the preview names the one it draws.
    override val previewSizeMode = SizeMode.Responsive(setOf(DpSize(170.dp, 170.dp)))

    override suspend fun providePreview(
        context: Context,
        widgetCategory: Int,
    ) {
        val snapshot = context.nowPlayingSnapshotFlow { NowPlayingWidgetPublisher.hasPublished }.first()?.copy(isPlaying = true)
        val colors = widgetColorsFlow(context).first()
        provideContent { FlowGlanceTheme(colors) { TurntableContent(snapshot = snapshot, disc = null) } }
    }

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val discDp =
            GlanceAppWidgetManager(context)
                .getAppWidgetSizes(id)
                .maxOfOrNull { min(it.width.value, it.height.value) - DISC_INSET_DP }
                ?.coerceIn(MIN_DISC_DP, MAX_DISC_DP)
                ?: MIN_DISC_DP
        val discPx = context.dpToPx(discDp)
        val snapshotFlow = context.nowPlayingSnapshotFlow { NowPlayingWidgetPublisher.hasPublished }
        val initialSnapshot = snapshotFlow.first()
        val initialDisc = loadDisc(context, initialSnapshot?.artworkUrl, discPx)
        val colorsFlow = widgetColorsFlow(context)
        val initialColors = colorsFlow.first()
        val styleFlow = PlayerPreferences(context).musicPlayerBackgroundStyle
        val initialStyle = styleFlow.first()
        val initialArtworkColors = artworkColorProviders(context, initialSnapshot?.artworkUrl, initialStyle, initialColors)

        provideContent {
            val snapshot by snapshotFlow.collectAsState(initialSnapshot)
            val disc by produceState(initialDisc, snapshot?.artworkUrl) { value = loadDisc(context, snapshot?.artworkUrl, discPx) }
            val colors by colorsFlow.collectAsState(initialColors)
            val style by styleFlow.collectAsState(initialStyle)
            val artworkColors by produceState<ColorProviders?>(initialArtworkColors, snapshot?.artworkUrl, style, colors) {
                value = artworkColorProviders(context, snapshot?.artworkUrl, style, colors)
            }
            FlowGlanceTheme(artworkColors ?: colors) {
                TurntableContent(snapshot = snapshot, disc = disc)
            }
        }
    }

    private suspend fun loadDisc(
        context: Context,
        url: String?,
        px: Int,
    ): Bitmap? = WidgetImageLoader.load(context, url, px, shape = WidgetShape.COOKIE_12, holeFraction = HOLE_FRACTION)

    private companion object {
        const val DISC_INSET_DP = 20f
        const val MIN_DISC_DP = 90f
        const val MAX_DISC_DP = 256f
        const val HOLE_FRACTION = 0.09f
    }
}

@Composable
private fun TurntableContent(
    snapshot: NowPlayingSnapshot?,
    disc: Bitmap?,
) {
    val context = LocalContext.current
    Box(modifier = GlanceModifier.fillMaxSize().widgetSurface()) {
        if (snapshot == null) {
            WidgetEmptyState(
                icon = R.drawable.ic_music_note,
                message = context.getString(R.string.widget_nothing_playing),
                action = actionStartActivity(WidgetDeepLink.openRoute(context, WidgetDeepLink.ROUTE_MUSIC)),
            )
            return@Box
        }
        val size = LocalSize.current
        val fullDisc = minOf(size.width, size.height) - 20.dp
        val discSize = if (snapshot.isPlaying) fullDisc else fullDisc * PAUSED_SCALE
        Box(
            modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(WidgetDeepLink.openMusicPlayer(context))),
            contentAlignment = Alignment.Center,
        ) {
            if (disc != null) {
                Image(
                    provider = ImageProvider(disc),
                    contentDescription = context.getString(R.string.widget_open_player),
                    modifier = GlanceModifier.size(discSize),
                    contentScale = ContentScale.Fit,
                )
            } else {
                ShapeDecor(WidgetShape.COOKIE_12, GlanceTheme.colors.secondaryContainer, discSize)
            }
        }
        Box(modifier = GlanceModifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.BottomEnd) {
            WidePlayPauseButton(snapshot.isPlaying, GlanceModifier.width(72.dp))
        }
    }
}

private const val PAUSED_SCALE = 0.84f

class TurntableWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TurntableWidget()
}

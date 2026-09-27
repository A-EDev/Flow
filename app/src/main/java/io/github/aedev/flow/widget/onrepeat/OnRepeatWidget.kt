package io.github.aedev.flow.widget.onrepeat

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.widget.core.action.WidgetDeepLink
import io.github.aedev.flow.widget.core.component.WidgetCoverGrid
import io.github.aedev.flow.widget.core.component.WidgetEmptyState
import io.github.aedev.flow.widget.core.component.WidgetMediaItem
import io.github.aedev.flow.widget.core.component.WidgetPanel
import io.github.aedev.flow.widget.core.component.WidgetShuffleButton
import io.github.aedev.flow.widget.core.component.coverSizeFor
import io.github.aedev.flow.widget.core.image.WidgetImageSpec
import io.github.aedev.flow.widget.core.image.preloadWidgetImages
import io.github.aedev.flow.widget.core.image.rememberWidgetImages
import io.github.aedev.flow.widget.core.theme.FlowGlanceTheme
import io.github.aedev.flow.widget.core.theme.WidgetDimens
import io.github.aedev.flow.widget.core.theme.bakedCornerRadiusPx
import io.github.aedev.flow.widget.core.theme.dpToPx
import io.github.aedev.flow.widget.core.theme.widgetColorsFlow
import io.github.aedev.flow.widget.core.theme.widgetSurface
import io.github.aedev.flow.widget.core.widgetEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** The Music page's On Repeat shelf as square covers; each plays its song, Shuffle plays them all. */
class OnRepeatWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    // Exact sizing has no size of its own in the picker, so the preview names the one it draws.
    override val previewSizeMode = SizeMode.Responsive(setOf(DpSize(320.dp, 260.dp)))

    override suspend fun providePreview(
        context: Context,
        widgetCategory: Int,
    ) {
        val tracks = withContext(Dispatchers.IO) { widgetEntryPoint(context).onRepeatSource().tracks() }
        val colors = widgetColorsFlow(context).first()
        provideContent { FlowGlanceTheme(colors) { OnRepeatContent(tracks.map { it.toItem(context, emptyMap()) }) } }
    }

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val tracks = withContext(Dispatchers.IO) { widgetEntryPoint(context).onRepeatSource().tracks() }
        val coverDp =
            GlanceAppWidgetManager(context)
                .getAppWidgetSizes(id)
                .maxOfOrNull { onRepeatGrid(it).coverSize.value }
                ?: WidgetDimens.TouchTarget.value * 2
        val corner = bakedCornerRadiusPx(context)
        val specs = tracks.map { WidgetImageSpec(it.videoId, it.listThumbnailUrl, context.dpToPx(coverDp), cornerRadiusPx = corner) }
        val preloaded = preloadWidgetImages(context, specs)
        val colorsFlow = widgetColorsFlow(context)
        val initialColors = colorsFlow.first()

        provideContent {
            val colors by colorsFlow.collectAsState(initialColors)
            val images by rememberWidgetImages(context, specs, preloaded)
            FlowGlanceTheme(colors) {
                OnRepeatContent(tracks.map { it.toItem(context, images) })
            }
        }
    }
}

/** How the covers fill a widget of [size]: without a header when it is too short for one. */
internal data class OnRepeatGrid(
    val columns: Int,
    val coverSize: Dp,
    val withHeader: Boolean,
)

internal fun onRepeatGrid(size: DpSize): OnRepeatGrid {
    val withHeader = size.height >= HeaderMinHeight
    val columns =
        if (withHeader) {
            ((size.width - WidgetDimens.ContentPadding * 2) / CoverTarget).toInt().coerceIn(2, 5)
        } else if (size.width >= WideMinWidth) {
            4
        } else {
            2
        }
    val rows = if (withHeader || columns == 4) 1 else 2
    val byWidth = coverSizeFor(size.width, columns)
    val byHeight = (size.height - WidgetDimens.ContentPadding * 2) / rows - WidgetDimens.SmallGap * 2
    return OnRepeatGrid(columns, if (withHeader) byWidth else minOf(byWidth, byHeight), withHeader)
}

private fun MusicTrack.toItem(
    context: Context,
    images: Map<String, Bitmap>,
) = WidgetMediaItem(
    id = videoId,
    title = title,
    subtitle = artist,
    open = actionStartActivity(WidgetDeepLink.playSong(context, videoId)),
    thumbnail = images[videoId],
    isSquare = true,
    placeholderIcon = R.drawable.ic_music_note,
)

@Composable
private fun OnRepeatContent(items: List<WidgetMediaItem>) {
    val context = LocalContext.current
    val grid = onRepeatGrid(LocalSize.current)
    val openMusic: Action = actionStartActivity(WidgetDeepLink.openRoute(context, WidgetDeepLink.ROUTE_MUSIC))
    if (!grid.withHeader) {
        Box(modifier = GlanceModifier.fillMaxSize().widgetSurface().padding(WidgetDimens.ContentPadding - WidgetDimens.SmallGap)) {
            if (items.isEmpty()) {
                Empty(openMusic)
            } else {
                WidgetCoverGrid(items.take(4), grid.columns, grid.coverSize, showLabels = false)
            }
        }
        return
    }
    WidgetPanel(
        title = context.getString(R.string.widget_on_repeat),
        icon = R.drawable.ic_repeat,
        onTitleClick = openMusic,
        actions = { if (items.size > 1) WidgetShuffleButton(actionStartActivity(WidgetDeepLink.shuffleOnRepeat(context))) },
    ) {
        if (items.isEmpty()) {
            Empty(openMusic)
        } else {
            WidgetCoverGrid(items, grid.columns, grid.coverSize, showLabels = true)
        }
    }
}

@Composable
private fun Empty(openMusic: Action) {
    WidgetEmptyState(
        icon = R.drawable.ic_music_note,
        message = LocalContext.current.getString(R.string.widget_no_on_repeat),
        action = openMusic,
    )
}

private val HeaderMinHeight = 160.dp
private val WideMinWidth = 250.dp
private val CoverTarget = 84.dp

class OnRepeatWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OnRepeatWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        widgetEntryPoint(context).widgetContentSync().onPlacementChanged()
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        widgetEntryPoint(context).widgetContentSync().onPlacementChanged()
    }
}

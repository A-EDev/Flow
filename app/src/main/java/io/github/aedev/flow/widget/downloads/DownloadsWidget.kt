package io.github.aedev.flow.widget.downloads

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.musicPlayerRoute
import io.github.aedev.flow.utils.formatDuration
import io.github.aedev.flow.widget.core.action.WidgetDeepLink
import io.github.aedev.flow.widget.core.component.WidgetEmptyState
import io.github.aedev.flow.widget.core.component.WidgetHeroCard
import io.github.aedev.flow.widget.core.component.WidgetMediaItem
import io.github.aedev.flow.widget.core.component.WidgetMediaRow
import io.github.aedev.flow.widget.core.component.WidgetPanel
import io.github.aedev.flow.widget.core.component.WidgetRowSquareThumb
import io.github.aedev.flow.widget.core.component.WidgetRowThumb
import io.github.aedev.flow.widget.core.image.WidgetImageSpec
import io.github.aedev.flow.widget.core.image.preloadWidgetImages
import io.github.aedev.flow.widget.core.image.rememberWidgetImages
import io.github.aedev.flow.widget.core.theme.FlowGlanceTheme
import io.github.aedev.flow.widget.core.theme.WidgetDimens
import io.github.aedev.flow.widget.core.theme.WidgetText
import io.github.aedev.flow.widget.core.theme.bakedCornerRadiusPx
import io.github.aedev.flow.widget.core.theme.dpToPx
import io.github.aedev.flow.widget.core.theme.widgetColorsFlow
import io.github.aedev.flow.widget.core.theme.widgetSurface
import io.github.aedev.flow.widget.core.widgetEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Downloaded videos and songs, newest first, with how many are still downloading. */
class DownloadsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    // Exact sizing has no size of its own in the picker, so the preview names the one it draws.
    override val previewSizeMode = SizeMode.Responsive(setOf(DpSize(320.dp, 260.dp)))

    override suspend fun providePreview(
        context: Context,
        widgetCategory: Int,
    ) {
        val downloads = withContext(Dispatchers.IO) { widgetEntryPoint(context).downloadsSource().load() }
        val colors = widgetColorsFlow(context).first()
        provideContent {
            FlowGlanceTheme(colors) { DownloadsContent(downloads.items.map { it.toItem(context, emptyMap()) }, downloads.inProgress) }
        }
    }

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val downloads = withContext(Dispatchers.IO) { widgetEntryPoint(context).downloadsSource().load() }
        val corner = bakedCornerRadiusPx(context)
        val specs =
            downloads.items.mapIndexed { index, download ->
                // The newest also fills the 2x2 layout, so it loads at hero size.
                val thumb = if (download.isMusic) WidgetRowSquareThumb else WidgetRowThumb
                val scale = if (index == 0) HERO_SCALE else 1f
                WidgetImageSpec(
                    download.id,
                    download.thumbnailUrl,
                    context.dpToPx(thumb.width.value * scale),
                    context.dpToPx(thumb.height.value * scale),
                    corner,
                )
            }
        val preloaded = preloadWidgetImages(context, specs)
        val colorsFlow = widgetColorsFlow(context)
        val initialColors = colorsFlow.first()

        provideContent {
            val colors by colorsFlow.collectAsState(initialColors)
            val images by rememberWidgetImages(context, specs, preloaded)
            FlowGlanceTheme(colors) {
                DownloadsContent(downloads.items.map { it.toItem(context, images) }, downloads.inProgress)
            }
        }
    }
}

private fun WidgetDownload.toItem(
    context: Context,
    images: Map<String, Bitmap>,
) = WidgetMediaItem(
    id = id,
    title = title,
    subtitle = subtitle,
    open =
        actionStartActivity(
            if (isMusic) WidgetDeepLink.openRoute(context, musicPlayerRoute(id)) else WidgetDeepLink.playVideo(context, id),
        ),
    thumbnail = images[id],
    badge = durationSeconds.takeIf { it > 0 && !isMusic }?.let { formatDuration(it.toInt()) },
    isSquare = isMusic,
    placeholderIcon = if (isMusic) R.drawable.ic_music_note else R.drawable.ic_widget_download,
)

@Composable
private fun DownloadsContent(
    items: List<WidgetMediaItem>,
    inProgress: Int,
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val openDownloads: Action = actionStartActivity(WidgetDeepLink.openRoute(context, WidgetDeepLink.ROUTE_DOWNLOADS))
    if (size.width < PanelMinWidth || size.height < PanelMinHeight) {
        Box(modifier = GlanceModifier.fillMaxSize().widgetSurface().padding(WidgetDimens.ContentPadding - 2.dp)) {
            val first = items.firstOrNull()
            if (first == null) {
                Empty(openDownloads, withButton = false)
            } else {
                WidgetHeroCard(first.copy(isSquare = false), imageHeight = size.height - 64.dp, titleLines = 1)
            }
        }
        return
    }
    WidgetPanel(
        title = context.getString(R.string.widget_downloads),
        icon = R.drawable.ic_widget_download,
        onTitleClick = openDownloads,
        actions = { if (inProgress > 0) InProgressChip(inProgress) },
    ) {
        if (items.isEmpty()) {
            Empty(openDownloads, withButton = true)
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(items, itemId = { it.id.hashCode().toLong() }) { WidgetMediaRow(it) }
            }
        }
    }
}

@Composable
private fun InProgressChip(count: Int) {
    Box(
        modifier =
            GlanceModifier
                .padding(end = WidgetDimens.SmallGap)
                .background(GlanceTheme.colors.secondaryContainer)
                .cornerRadius(WidgetDimens.ContentPadding)
                .padding(horizontal = 10.dp, vertical = WidgetDimens.SmallGap),
    ) {
        Text(
            text = LocalContext.current.resources.getQuantityString(R.plurals.widget_downloads_in_progress, count, count),
            style = WidgetText.labelMedium(GlanceTheme.colors.onSecondaryContainer),
            maxLines = 1,
        )
    }
}

@Composable
private fun Empty(
    openDownloads: Action,
    withButton: Boolean,
) {
    val context = LocalContext.current
    WidgetEmptyState(
        icon = R.drawable.ic_widget_download,
        message = context.getString(R.string.widget_no_downloads),
        action = openDownloads,
        actionLabel = if (withButton) context.getString(R.string.widget_browse_downloads) else null,
    )
}

private const val HERO_SCALE = 2.5f
private val PanelMinWidth = 200.dp
private val PanelMinHeight = 150.dp

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

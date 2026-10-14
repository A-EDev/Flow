package io.github.aedev.flow.widget.recent

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.VideoHistoryEntry
import io.github.aedev.flow.utils.formatDurationMillis
import io.github.aedev.flow.widget.core.action.WidgetDeepLink
import io.github.aedev.flow.widget.core.component.WidgetEmptyState
import io.github.aedev.flow.widget.core.component.WidgetHeroCard
import io.github.aedev.flow.widget.core.component.WidgetMediaItem
import io.github.aedev.flow.widget.core.component.WidgetMediaRow
import io.github.aedev.flow.widget.core.component.WidgetPanel
import io.github.aedev.flow.widget.core.component.WidgetRowThumb
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

/** Continue watching: where each recent video stopped, the newest as a hero. The class name predates the rename. */
class RecentlyPlayedWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    // Exact sizing has no size of its own in the picker, so the preview names the one it draws.
    override val previewSizeMode = SizeMode.Responsive(setOf(DpSize(320.dp, 260.dp)))

    override suspend fun providePreview(
        context: Context,
        widgetCategory: Int,
    ) {
        val entries = withContext(Dispatchers.IO) { widgetEntryPoint(context).recentlyPlayedSource().entries() }
        val colors = widgetColorsFlow(context).first()
        provideContent { FlowGlanceTheme(colors) { ContinueWatchingContent(entries.map { it.toItem(context, emptyMap()) }) } }
    }

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val entries = withContext(Dispatchers.IO) { widgetEntryPoint(context).recentlyPlayedSource().entries() }
        val maxWidthDp = GlanceAppWidgetManager(context).getAppWidgetSizes(id).maxOfOrNull { it.width.value } ?: HERO_FALLBACK_DP
        val specs = imageSpecs(context, entries, maxWidthDp)
        val preloaded = preloadWidgetImages(context, specs)
        val colorsFlow = widgetColorsFlow(context)
        val initialColors = colorsFlow.first()

        provideContent {
            val colors by colorsFlow.collectAsState(initialColors)
            val images by rememberWidgetImages(context, specs, preloaded)
            FlowGlanceTheme(colors) {
                ContinueWatchingContent(entries.map { it.toItem(context, images) })
            }
        }
    }

    private fun imageSpecs(
        context: Context,
        entries: List<VideoHistoryEntry>,
        maxWidthDp: Float,
    ): List<WidgetImageSpec> {
        val corner = bakedCornerRadiusPx(context)
        val heroWidth = context.dpToPx((maxWidthDp - 24f).coerceIn(96f, HERO_MAX_DP))
        return entries.mapIndexed { index, entry ->
            if (index == 0) {
                WidgetImageSpec(entry.videoId, entry.thumbnailUrl, heroWidth, heroWidth * 9 / 16, corner)
            } else {
                WidgetImageSpec(
                    entry.videoId,
                    entry.thumbnailUrl,
                    context.dpToPx(WidgetRowThumb.width.value),
                    context.dpToPx(WidgetRowThumb.height.value),
                    corner,
                )
            }
        }
    }

    private companion object {
        const val HERO_FALLBACK_DP = 250f
        const val HERO_MAX_DP = 400f
    }
}

private fun VideoHistoryEntry.toItem(
    context: Context,
    images: Map<String, Bitmap>,
): WidgetMediaItem {
    val left = minutesLeft(position, duration)
    return WidgetMediaItem(
        id = videoId,
        title = title,
        subtitle = channelName,
        open = actionStartActivity(if (isShort) WidgetDeepLink.playShort(context, videoId) else WidgetDeepLink.playVideo(context, videoId)),
        thumbnail = images[videoId],
        badge =
            when {
                isShort -> context.getString(R.string.widget_short_badge)
                left != null -> context.resources.getQuantityString(R.plurals.widget_minutes_left, left, left)
                duration > 0L -> formatDurationMillis(duration)
                else -> null
            },
        emphasizedBadge = isShort,
        progress = watchedFraction(position, duration),
        placeholderIcon = R.drawable.ic_widget_history,
    )
}

@Composable
private fun ContinueWatchingContent(items: List<WidgetMediaItem>) {
    val context = LocalContext.current
    val size = LocalSize.current
    val openHistory: Action = actionStartActivity(WidgetDeepLink.openRoute(context, WidgetDeepLink.ROUTE_HISTORY))
    when {
        size.height < PanelMinHeight && size.width < WideMinWidth -> {
            SingleHero(items.firstOrNull(), openHistory)
        }

        size.height < PanelMinHeight -> {
            WideRow(items, openHistory)
        }

        else -> {
            WidgetPanel(
                title = context.getString(R.string.widget_recently_played),
                icon = R.drawable.ic_widget_history,
                onTitleClick = openHistory,
            ) {
                if (items.isEmpty()) {
                    Empty(openHistory)
                } else {
                    val heroHeight = minOf((size.width - WidgetDimens.ContentPadding * 2) * 9f / 16f, size.height * 0.45f)
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        item(itemId = HERO_ITEM_ID) {
                            WidgetHeroCard(items.first(), heroHeight, GlanceModifier.padding(bottom = WidgetDimens.ItemGap))
                        }
                        items(items.drop(1), itemId = { it.id.hashCode().toLong() }) { WidgetMediaRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleHero(
    item: WidgetMediaItem?,
    openHistory: Action,
) {
    Box(modifier = GlanceModifier.fillMaxSize().widgetSurface().padding(WidgetDimens.ContentPadding - 2.dp)) {
        if (item == null) {
            Empty(openHistory)
        } else {
            val size = LocalSize.current
            WidgetHeroCard(item, imageHeight = size.height - 64.dp, titleLines = 1)
        }
    }
}

@Composable
private fun WideRow(
    items: List<WidgetMediaItem>,
    openHistory: Action,
) {
    Box(modifier = GlanceModifier.fillMaxSize().widgetSurface().padding(WidgetDimens.ContentPadding)) {
        if (items.isEmpty()) {
            Empty(openHistory)
            return@Box
        }
        val size = LocalSize.current
        Row(modifier = GlanceModifier.fillMaxSize()) {
            WidgetHeroCard(items.first(), size.height - 64.dp, GlanceModifier.defaultWeight(), titleLines = 1)
            if (items.size > 1) {
                Spacer(GlanceModifier.width(WidgetDimens.ContentPadding))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    items.drop(1).take(2).forEachIndexed { index, item ->
                        if (index > 0) Spacer(GlanceModifier.height(WidgetDimens.ItemGap))
                        WidgetHeroCard(item, (size.height - 24.dp) / 2 - 22.dp, GlanceModifier.fillMaxWidth(), titleLines = 0)
                    }
                }
            }
        }
    }
}

@Composable
private fun Empty(openHistory: Action) {
    WidgetEmptyState(
        icon = R.drawable.ic_widget_history,
        message = LocalContext.current.getString(R.string.widget_no_recent),
        action = openHistory,
    )
}

private const val HERO_ITEM_ID = Long.MIN_VALUE
private val PanelMinHeight = 150.dp
private val WideMinWidth = 230.dp

class RecentlyPlayedWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecentlyPlayedWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        widgetEntryPoint(context).widgetContentSync().onPlacementChanged()
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        widgetEntryPoint(context).widgetContentSync().onPlacementChanged()
    }
}

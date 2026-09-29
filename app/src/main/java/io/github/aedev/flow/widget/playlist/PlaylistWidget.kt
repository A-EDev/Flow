package io.github.aedev.flow.widget.playlist

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.widget.core.action.WidgetDeepLink
import io.github.aedev.flow.widget.core.component.WidgetRowSquareThumb
import io.github.aedev.flow.widget.core.component.WidgetRowThumb
import io.github.aedev.flow.widget.core.image.WidgetImageLoader
import io.github.aedev.flow.widget.core.image.WidgetImageSpec
import io.github.aedev.flow.widget.core.image.preloadWidgetImages
import io.github.aedev.flow.widget.core.image.rememberWidgetImages
import io.github.aedev.flow.widget.core.theme.FlowGlanceTheme
import io.github.aedev.flow.widget.core.theme.bakedCornerRadiusPx
import io.github.aedev.flow.widget.core.theme.dpToPx
import io.github.aedev.flow.widget.core.theme.widgetColorsFlow
import io.github.aedev.flow.widget.core.widgetEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.min

/** One playlist, Watch later unless the user picked another: a list of its videos, or its cover when small. */
class PlaylistWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override val previewSizeMode = SizeMode.Responsive(setOf(PlaylistLayout.PreviewSize))

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val manager = GlanceAppWidgetManager(context)
        val appWidgetId = manager.getAppWidgetId(id)
        val source = widgetEntryPoint(context).playlistWidgetSource()
        val sizes = manager.getAppWidgetSizes(id)
        val coverDp = sizes.maxOfOrNull { min(it.width.value, it.height.value) } ?: COVER_FALLBACK_DP
        val coverPx = context.dpToPx(coverDp.coerceAtMost(COVER_MAX_DP))
        val cornerPx = bakedCornerRadiusPx(context)
        // Row thumbnails are only worth loading when some orientation draws the list.
        val drawsList = sizes.any { PlaylistLayout.forSize(it) == PlaylistLayout.LIST }
        val initialId = PlaylistWidgetConfig.load(context, id) ?: PlaylistWidgetSource.DEFAULT_PLAYLIST_ID
        val initial = withContext(Dispatchers.IO) { source.content(initialId).first() }
        val initialCover = WidgetImageLoader.load(context, initial.playlist?.coverUrl, coverPx, cornerRadiusPx = cornerPx)
        val initialImages = preloadWidgetImages(context, rowImageSpecs(context, initial.items, drawsList, cornerPx))
        val colorsFlow = widgetColorsFlow(context)
        val initialColors = colorsFlow.first()
        val choose = actionStartActivity(WidgetDeepLink.configure(context, appWidgetId))

        // A running session only recomposes on update, so the chosen playlist and its rows are read here.
        provideContent {
            val playlistId = currentState(PLAYLIST_ID) ?: PlaylistWidgetSource.DEFAULT_PLAYLIST_ID
            val refreshing = currentState(REFRESHING) == true
            val content by remember(playlistId) { source.content(playlistId) }
                .collectAsState(initial.takeIf { playlistId == initialId })
            val coverUrl = content?.playlist?.coverUrl
            val cover by produceState(initialCover.takeIf { coverUrl == initial.playlist?.coverUrl }, coverUrl) {
                value = WidgetImageLoader.load(context, coverUrl, coverPx, cornerRadiusPx = cornerPx)
            }
            val specs = remember(content) { rowImageSpecs(context, content?.items.orEmpty(), drawsList, cornerPx) }
            val images by rememberWidgetImages(context, specs, initialImages)
            val colors by colorsFlow.collectAsState(initialColors)
            FlowGlanceTheme(colors) {
                PlaylistContent(content, cover, images, choose, refreshing)
            }
        }
    }

    override suspend fun providePreview(
        context: Context,
        widgetCategory: Int,
    ) {
        val sample =
            withContext(Dispatchers.IO) {
                val source = widgetEntryPoint(context).playlistWidgetSource()
                val id =
                    source
                        .options()
                        .first()
                        .firstOrNull { it.count > 0 }
                        ?.id ?: PlaylistWidgetSource.DEFAULT_PLAYLIST_ID
                source.content(id).first()
            }
        val colors = widgetColorsFlow(context).first()
        provideContent { FlowGlanceTheme(colors) { PlaylistContent(sample, cover = null, images = emptyMap(), choose = null) } }
    }

    private fun rowImageSpecs(
        context: Context,
        items: List<Video>,
        drawsList: Boolean,
        cornerPx: Float,
    ): List<WidgetImageSpec> {
        if (!drawsList) return emptyList()
        return items.map { video ->
            val thumb = if (video.isMusic) WidgetRowSquareThumb else WidgetRowThumb
            WidgetImageSpec(
                video.id,
                video.thumbnailUrl.takeIf { it.isNotBlank() },
                context.dpToPx(thumb.width.value),
                context.dpToPx(thumb.height.value),
                cornerPx,
            )
        }
    }

    companion object {
        val PLAYLIST_ID = stringPreferencesKey("playlist_id")
        val REFRESHING = booleanPreferencesKey("refreshing")
        private const val COVER_FALLBACK_DP = 110f
        private const val COVER_MAX_DP = 200f
    }
}

class PlaylistWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlaylistWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        widgetEntryPoint(context).widgetContentSync().onPlacementChanged()
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        widgetEntryPoint(context).widgetContentSync().onPlacementChanged()
    }
}

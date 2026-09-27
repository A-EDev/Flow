package io.github.aedev.flow.widget.playlist

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import io.github.aedev.flow.R
import io.github.aedev.flow.widget.core.action.WidgetDeepLink
import io.github.aedev.flow.widget.core.component.PlaybackSegment
import io.github.aedev.flow.widget.core.component.WidgetArtwork
import io.github.aedev.flow.widget.core.component.WidgetEmptyState
import io.github.aedev.flow.widget.core.component.WidgetShuffleButton
import io.github.aedev.flow.widget.core.image.WidgetImageLoader
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
import kotlin.math.min

/** One playlist the user picked: its cover and name, with Play and Shuffle. */
class PlaylistWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override val previewSizeMode = SizeMode.Responsive(setOf(DpSize(320.dp, 150.dp)))

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val manager = GlanceAppWidgetManager(context)
        val appWidgetId = manager.getAppWidgetId(id)
        val playlistId = getAppWidgetState<Preferences>(context, PreferencesGlanceStateDefinition, id)[PLAYLIST_ID]
        val playlist = playlistId?.let { withContext(Dispatchers.IO) { widgetEntryPoint(context).playlistWidgetSource().playlist(it) } }
        val coverDp = manager.getAppWidgetSizes(id).maxOfOrNull { min(it.width.value, it.height.value) } ?: 110f
        val cover =
            WidgetImageLoader.load(
                context,
                playlist?.coverUrl,
                context.dpToPx(coverDp.coerceAtMost(COVER_MAX_DP)),
                cornerRadiusPx = bakedCornerRadiusPx(context),
            )
        val colorsFlow = widgetColorsFlow(context)
        val initialColors = colorsFlow.first()

        provideContent {
            val colors by colorsFlow.collectAsState(initialColors)
            FlowGlanceTheme(colors) {
                PlaylistContent(playlist, cover, actionStartActivity(WidgetDeepLink.configure(context, appWidgetId)))
            }
        }
    }

    override suspend fun providePreview(
        context: Context,
        widgetCategory: Int,
    ) {
        val sample =
            withContext(Dispatchers.IO) {
                widgetEntryPoint(context)
                    .playlistWidgetSource()
                    .options()
                    .first()
                    .firstOrNull()
            }
        val colors = widgetColorsFlow(context).first()
        provideContent { FlowGlanceTheme(colors) { PlaylistContent(sample, cover = null, choose = null) } }
    }

    companion object {
        val PLAYLIST_ID = stringPreferencesKey("playlist_id")
        private const val COVER_MAX_DP = 200f
    }
}

@Composable
private fun PlaylistContent(
    playlist: WidgetPlaylist?,
    cover: Bitmap?,
    choose: Action?,
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val modifier = GlanceModifier.fillMaxSize().widgetSurface()
    if (playlist == null) {
        Column(modifier = modifier) {
            WidgetEmptyState(
                icon = R.drawable.ic_widget_library,
                message = context.getString(R.string.widget_choose_playlist),
                action = choose ?: actionStartActivity(WidgetDeepLink.openRoute(context, WidgetDeepLink.ROUTE_LIBRARY)),
            )
        }
        return
    }
    val open = actionStartActivity(WidgetDeepLink.openPlaylist(context, playlist.id, playlist.isMusic))
    if (size.width >= WideMinWidth) {
        val coverSize = size.height - WidgetDimens.ContentPadding * 2
        Row(modifier = modifier.padding(WidgetDimens.ContentPadding), verticalAlignment = Alignment.CenterVertically) {
            WidgetArtwork(cover, R.drawable.ic_widget_library, GlanceModifier.size(coverSize).clickable(open), playlist.name)
            Spacer(GlanceModifier.width(14.dp))
            Column(modifier = GlanceModifier.defaultWeight().fillMaxSize()) {
                PlaylistTitle(playlist, open, titleLines = 2)
                Spacer(GlanceModifier.defaultWeight())
                PlaylistActions(playlist)
            }
        }
    } else {
        Column(modifier = modifier.padding(WidgetDimens.ContentPadding)) {
            WidgetArtwork(cover, R.drawable.ic_widget_library, GlanceModifier.fillMaxWidth().defaultWeight().clickable(open), playlist.name)
            Spacer(GlanceModifier.height(WidgetDimens.ItemGap))
            PlaylistTitle(playlist, open, titleLines = 1)
            Spacer(GlanceModifier.height(WidgetDimens.ItemGap))
            PlaylistActions(playlist)
        }
    }
}

@Composable
private fun PlaylistTitle(
    playlist: WidgetPlaylist,
    open: Action,
    titleLines: Int,
) {
    val context = LocalContext.current
    val count =
        context.resources.getQuantityString(
            if (playlist.isMusic) R.plurals.widget_playlist_songs else R.plurals.widget_playlist_videos,
            playlist.count,
            playlist.count,
        )
    Column(modifier = GlanceModifier.fillMaxWidth().clickable(open)) {
        Text(text = playlist.name, style = WidgetText.titleMedium(), maxLines = titleLines)
        Text(text = count, style = WidgetText.bodySmall(), maxLines = 1)
    }
}

@Composable
private fun PlaylistActions(playlist: WidgetPlaylist) {
    val context = LocalContext.current
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PlaybackSegment(
            iconRes = R.drawable.ic_play,
            contentDescription = context.getString(R.string.widget_play),
            onClick = actionStartActivity(WidgetDeepLink.playPlaylist(context, playlist.id, shuffle = false)),
            modifier = GlanceModifier.defaultWeight(),
            filled = true,
        )
        Spacer(GlanceModifier.width(WidgetDimens.ItemGap))
        WidgetShuffleButton(actionStartActivity(WidgetDeepLink.playPlaylist(context, playlist.id, shuffle = true)))
    }
}

private val WideMinWidth = 220.dp

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

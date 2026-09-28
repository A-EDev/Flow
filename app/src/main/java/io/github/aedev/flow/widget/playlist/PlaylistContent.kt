package io.github.aedev.flow.widget.playlist

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.utils.formatDuration
import io.github.aedev.flow.widget.core.action.WidgetDeepLink
import io.github.aedev.flow.widget.core.component.WidgetArtwork
import io.github.aedev.flow.widget.core.component.WidgetEmptyState
import io.github.aedev.flow.widget.core.component.WidgetMediaItem
import io.github.aedev.flow.widget.core.component.WidgetMediaRow
import io.github.aedev.flow.widget.core.component.WidgetPanel
import io.github.aedev.flow.widget.core.component.WidgetPressButton
import io.github.aedev.flow.widget.core.component.WidgetShuffleButton
import io.github.aedev.flow.widget.core.theme.WidgetDimens
import io.github.aedev.flow.widget.core.theme.WidgetText
import io.github.aedev.flow.widget.core.theme.widgetSurface

/** How a Playlist widget of a given size is drawn: its videos as a list, or its cover beside or above its name. */
internal enum class PlaylistLayout {
    LIST,
    WIDE,
    TALL,
    ;

    companion object {
        val PreviewSize = DpSize(320.dp, 200.dp)
        private val ListMinWidth = 240.dp
        private val ListMinHeight = 150.dp
        private val WideMinWidth = 220.dp

        fun forSize(size: DpSize): PlaylistLayout =
            when {
                size.width >= ListMinWidth && size.height >= ListMinHeight -> LIST
                size.width >= WideMinWidth -> WIDE
                else -> TALL
            }
    }
}

@Composable
internal fun PlaylistContent(
    content: WidgetPlaylistContent?,
    cover: Bitmap?,
    images: Map<String, Bitmap>,
    choose: Action?,
    refreshing: Boolean = false,
) {
    val context = LocalContext.current
    val playlist = content?.playlist
    if (playlist == null) {
        Column(modifier = GlanceModifier.fillMaxSize().widgetSurface()) {
            // Null content is a playlist switch still loading; a missing playlist was deleted.
            if (content != null) {
                WidgetEmptyState(
                    icon = R.drawable.ic_widget_library,
                    message = context.getString(R.string.widget_choose_playlist),
                    action = choose ?: actionStartActivity(WidgetDeepLink.openRoute(context, WidgetDeepLink.ROUTE_LIBRARY)),
                )
            }
        }
        return
    }
    val open = actionStartActivity(WidgetDeepLink.openPlaylist(context, playlist.id, playlist.isMusic))
    when (PlaylistLayout.forSize(LocalSize.current)) {
        PlaylistLayout.LIST -> PlaylistList(playlist, content.items, images, open, refreshing)
        PlaylistLayout.WIDE -> WideCover(playlist, cover, open)
        PlaylistLayout.TALL -> TallCover(playlist, cover, open)
    }
}

@Composable
private fun PlaylistList(
    playlist: WidgetPlaylist,
    items: List<Video>,
    images: Map<String, Bitmap>,
    open: Action,
    refreshing: Boolean,
) {
    val context = LocalContext.current
    val icon = if (playlist.id == PlaylistRepository.WATCH_LATER_ID) R.drawable.ic_widget_watch_later else R.drawable.ic_widget_library
    WidgetPanel(
        title = playlist.name,
        icon = icon,
        onTitleClick = open,
        actions = {
            RefreshButton(refreshing)
            if (items.isNotEmpty()) {
                if (LocalSize.current.width >= ShuffleInHeaderMinWidth) {
                    WidgetShuffleButton(actionStartActivity(WidgetDeepLink.playPlaylist(context, playlist.id, shuffle = true)))
                }
                PlayButton(playlist, GlanceModifier.size(HeaderButtonSize))
                Spacer(GlanceModifier.width(WidgetDimens.SmallGap))
            }
        },
    ) {
        if (items.isEmpty()) {
            WidgetEmptyState(icon = icon, message = context.getString(R.string.widget_playlist_empty), action = open)
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(items, itemId = { it.id.hashCode().toLong() }) { video -> WidgetMediaRow(video.toItem(context, playlist.id, images)) }
            }
        }
    }
}

@Composable
private fun RefreshButton(refreshing: Boolean) {
    if (refreshing) {
        Box(modifier = GlanceModifier.size(WidgetDimens.TouchTarget), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = GlanceModifier.size(22.dp), color = GlanceTheme.colors.primary)
        }
    } else {
        CircleIconButton(
            imageProvider = ImageProvider(R.drawable.ic_widget_refresh),
            contentDescription = LocalContext.current.getString(R.string.widget_refresh),
            onClick = actionRunCallback<RefreshPlaylistAction>(),
            backgroundColor = null,
            contentColor = GlanceTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun WideCover(
    playlist: WidgetPlaylist,
    cover: Bitmap?,
    open: Action,
) {
    val coverSize = LocalSize.current.height - WidgetDimens.ContentPadding * 2
    Row(
        modifier = GlanceModifier.fillMaxSize().widgetSurface().padding(WidgetDimens.ContentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetArtwork(cover, R.drawable.ic_widget_library, GlanceModifier.size(coverSize).clickable(open), playlist.name)
        Spacer(GlanceModifier.width(14.dp))
        Column(modifier = GlanceModifier.defaultWeight().fillMaxSize()) {
            PlaylistTitle(playlist, open, titleLines = 2)
            Spacer(GlanceModifier.defaultWeight())
            PlaylistActions(playlist)
        }
    }
}

@Composable
private fun TallCover(
    playlist: WidgetPlaylist,
    cover: Bitmap?,
    open: Action,
) {
    Column(modifier = GlanceModifier.fillMaxSize().widgetSurface().padding(WidgetDimens.ContentPadding)) {
        WidgetArtwork(cover, R.drawable.ic_widget_library, GlanceModifier.fillMaxWidth().defaultWeight().clickable(open), playlist.name)
        Spacer(GlanceModifier.height(WidgetDimens.ItemGap))
        PlaylistTitle(playlist, open, titleLines = 1)
        Spacer(GlanceModifier.height(WidgetDimens.ItemGap))
        PlaylistActions(playlist)
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
        PlayButton(playlist, GlanceModifier.defaultWeight().height(WidgetDimens.TouchTarget))
        Spacer(GlanceModifier.width(WidgetDimens.ItemGap))
        WidgetShuffleButton(actionStartActivity(WidgetDeepLink.playPlaylist(context, playlist.id, shuffle = true)))
    }
}

@Composable
private fun PlayButton(
    playlist: WidgetPlaylist,
    modifier: GlanceModifier,
) {
    val context = LocalContext.current
    val play = WidgetDeepLink.playPlaylist(context, playlist.id, shuffle = false)
    WidgetPressButton(
        icon = R.drawable.ic_play,
        contentDescription = context.getString(R.string.widget_play),
        pendingIntent =
            PendingIntent.getActivity(
                context,
                playlist.id.hashCode(),
                play,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        fallback = actionStartActivity(play),
        modifier = modifier,
        filled = true,
    )
}

// Each row starts the playlist from itself, so the rest of the list plays on after it.
private fun Video.toItem(
    context: Context,
    playlistId: String,
    images: Map<String, Bitmap>,
) = WidgetMediaItem(
    id = id,
    title = title,
    subtitle = channelName,
    open = actionStartActivity(WidgetDeepLink.playPlaylist(context, playlistId, shuffle = false, startVideoId = id)),
    thumbnail = images[id],
    badge =
        when {
            isShort -> context.getString(R.string.widget_short_badge)
            duration > 0 && !isMusic -> formatDuration(duration)
            else -> null
        },
    emphasizedBadge = isShort,
    isSquare = isMusic,
    placeholderIcon = if (isMusic) R.drawable.ic_music_note else R.drawable.ic_widget_library,
)

private val ShuffleInHeaderMinWidth = 280.dp
private val HeaderButtonSize = 44.dp

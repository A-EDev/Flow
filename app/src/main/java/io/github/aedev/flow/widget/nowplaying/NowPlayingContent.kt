package io.github.aedev.flow.widget.nowplaying

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
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
import androidx.glance.text.TextStyle
import io.github.aedev.flow.R
import io.github.aedev.flow.utils.formatDurationMillis
import io.github.aedev.flow.widget.core.action.WidgetDeepLink
import io.github.aedev.flow.widget.core.component.ConnectedPlaybackControls
import io.github.aedev.flow.widget.core.component.LikeButton
import io.github.aedev.flow.widget.core.component.NextSegment
import io.github.aedev.flow.widget.core.component.WidePlayPauseButton
import io.github.aedev.flow.widget.core.component.WidgetArtwork
import io.github.aedev.flow.widget.core.component.WidgetElapsedTime
import io.github.aedev.flow.widget.core.component.WidgetEmptyState
import io.github.aedev.flow.widget.core.state.NowPlayingSnapshot
import io.github.aedev.flow.widget.core.theme.WidgetDimens
import io.github.aedev.flow.widget.core.theme.WidgetText
import io.github.aedev.flow.widget.core.theme.widgetSurface

/** The music player on the home screen, laid out for whichever [NowPlayingLayout] fits. */
@Composable
internal fun NowPlayingContent(
    snapshot: NowPlayingSnapshot?,
    artwork: Bitmap?,
) {
    val layout = NowPlayingLayout.forSize(LocalSize.current)
    Box(modifier = GlanceModifier.fillMaxSize().widgetSurface()) {
        when {
            snapshot == null && (layout == NowPlayingLayout.SMALL || layout == NowPlayingLayout.STRIP) -> CompactEmpty()
            snapshot == null -> NothingPlaying()
            layout == NowPlayingLayout.SMALL -> SmallLayout(snapshot)
            layout == NowPlayingLayout.STRIP -> StripLayout(snapshot, artwork)
            layout == NowPlayingLayout.SQUARE -> SquareLayout(snapshot, artwork)
            layout == NowPlayingLayout.CARD -> CardLayout(snapshot, artwork)
            else -> PosterLayout(snapshot, artwork)
        }
    }
}

@Composable
private fun NothingPlaying() {
    val context = LocalContext.current
    WidgetEmptyState(
        icon = R.drawable.ic_music_note,
        message = context.getString(R.string.widget_nothing_playing),
        action = actionStartActivity(WidgetDeepLink.openRoute(context, WidgetDeepLink.ROUTE_MUSIC)),
    )
}

@Composable
private fun CompactEmpty() {
    val context = LocalContext.current
    Row(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .padding(horizontal = WidgetDimens.ContentPadding)
                .clickable(actionStartActivity(WidgetDeepLink.openRoute(context, WidgetDeepLink.ROUTE_MUSIC))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_music_note),
            contentDescription = null,
            modifier = GlanceModifier.size(24.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
        )
        Spacer(GlanceModifier.width(WidgetDimens.ItemGap))
        Text(text = context.getString(R.string.widget_nothing_playing), style = WidgetText.bodyMedium(GlanceTheme.colors.onSurfaceVariant))
    }
}

@Composable
private fun SmallLayout(snapshot: NowPlayingSnapshot) {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(start = WidgetDimens.ContentPadding + 2.dp, end = WidgetDimens.ItemGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackText(snapshot, GlanceModifier.defaultWeight(), WidgetText.titleSmall())
        Spacer(GlanceModifier.width(WidgetDimens.ItemGap))
        WidePlayPauseButton(snapshot.isPlaying, GlanceModifier.width(64.dp))
    }
}

@Composable
private fun StripLayout(
    snapshot: NowPlayingSnapshot,
    artwork: Bitmap?,
) {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(start = WidgetDimens.ContentPadding, end = WidgetDimens.ItemGap + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerArtwork(artwork, NowPlayingLayout.STRIP_ART_DP.dp)
        Spacer(GlanceModifier.width(WidgetDimens.ContentPadding))
        TrackText(snapshot, GlanceModifier.defaultWeight(), WidgetText.titleSmall())
        Spacer(GlanceModifier.width(WidgetDimens.ItemGap))
        WidePlayPauseButton(snapshot.isPlaying, GlanceModifier.width(76.dp))
        Spacer(GlanceModifier.width(6.dp))
        NextSegment(GlanceModifier.width(WidgetDimens.TouchTarget))
    }
}

@Composable
private fun SquareLayout(
    snapshot: NowPlayingSnapshot,
    artwork: Bitmap?,
) {
    Column(modifier = GlanceModifier.fillMaxSize().padding(WidgetDimens.ContentPadding)) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            PlayerArtwork(artwork, NowPlayingLayout.SQUARE_ART_DP.dp)
            Spacer(GlanceModifier.defaultWeight())
            LikeButton(snapshot.isLiked)
        }
        Spacer(GlanceModifier.height(WidgetDimens.ItemGap))
        TrackText(snapshot, GlanceModifier.fillMaxWidth(), WidgetText.titleSmall())
        Spacer(GlanceModifier.defaultWeight())
        WidePlayPauseButton(snapshot.isPlaying, GlanceModifier.fillMaxWidth())
    }
}

@Composable
private fun CardLayout(
    snapshot: NowPlayingSnapshot,
    artwork: Bitmap?,
) {
    Column(modifier = GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = WidgetDimens.ContentPadding)) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PlayerArtwork(artwork, NowPlayingLayout.CARD_ART_DP.dp)
            Spacer(GlanceModifier.width(14.dp))
            TrackText(snapshot, GlanceModifier.defaultWeight(), WidgetText.titleMedium())
            Spacer(GlanceModifier.width(WidgetDimens.SmallGap))
            LikeButton(snapshot.isLiked)
        }
        Spacer(GlanceModifier.defaultWeight())
        PlaybackTime(snapshot, GlanceModifier.fillMaxWidth().padding(horizontal = WidgetDimens.SmallGap))
        Spacer(GlanceModifier.height(WidgetDimens.ItemGap))
        ConnectedPlaybackControls(snapshot.isPlaying, GlanceModifier.fillMaxWidth(), sideWidth = 64.dp)
    }
}

@Composable
private fun PosterLayout(
    snapshot: NowPlayingSnapshot,
    artwork: Bitmap?,
) {
    val context = LocalContext.current
    Column(modifier = GlanceModifier.fillMaxSize().padding(WidgetDimens.ContentPadding)) {
        WidgetArtwork(
            bitmap = artwork,
            placeholderIcon = R.drawable.ic_music_note,
            modifier =
                GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .clickable(actionStartActivity(WidgetDeepLink.openMusicPlayer(context))),
            contentDescription = context.getString(R.string.widget_open_player),
        )
        Spacer(GlanceModifier.height(WidgetDimens.ItemGap))
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(start = WidgetDimens.SmallGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackText(snapshot, GlanceModifier.defaultWeight(), WidgetText.titleMedium())
            LikeButton(snapshot.isLiked)
        }
        PlaybackTime(snapshot, GlanceModifier.fillMaxWidth().padding(horizontal = WidgetDimens.SmallGap, vertical = WidgetDimens.SmallGap))
        ConnectedPlaybackControls(snapshot.isPlaying, GlanceModifier.fillMaxWidth(), sideWidth = 56.dp)
    }
}

@Composable
private fun PlayerArtwork(
    artwork: Bitmap?,
    size: Dp,
) {
    val context = LocalContext.current
    WidgetArtwork(
        bitmap = artwork,
        placeholderIcon = R.drawable.ic_music_note,
        modifier = GlanceModifier.size(size).clickable(actionStartActivity(WidgetDeepLink.openMusicPlayer(context))),
        contentDescription = context.getString(R.string.widget_open_player),
    )
}

@Composable
private fun TrackText(
    snapshot: NowPlayingSnapshot,
    modifier: GlanceModifier,
    titleStyle: TextStyle,
) {
    val context = LocalContext.current
    Column(modifier = modifier.clickable(actionStartActivity(WidgetDeepLink.openMusicPlayer(context)))) {
        Text(text = snapshot.title, style = titleStyle, maxLines = 1)
        if (snapshot.artist.isNotBlank()) {
            Text(text = snapshot.artist, style = WidgetText.bodySmall(), maxLines = 1)
        }
    }
}

@Composable
private fun PlaybackTime(
    snapshot: NowPlayingSnapshot,
    modifier: GlanceModifier,
) {
    if (snapshot.durationMs <= 0L) return
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        WidgetElapsedTime(
            positionMs = snapshot.positionMs,
            capturedAtElapsedMs = snapshot.capturedAtElapsedMs,
            isRunning = snapshot.isPlaying,
            style = WidgetText.labelMedium(GlanceTheme.colors.onSurface),
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(text = formatDurationMillis(snapshot.durationMs), style = WidgetText.labelMedium(), maxLines = 1)
    }
}

package io.github.aedev.flow.widget.nowplaying

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.aedev.flow.R
import io.github.aedev.flow.widget.core.NextTrackAction
import io.github.aedev.flow.widget.core.NowPlayingSnapshot
import io.github.aedev.flow.widget.core.ShapeDecor
import io.github.aedev.flow.widget.core.WidgetDeepLink
import io.github.aedev.flow.widget.core.WidgetShape
import io.github.aedev.flow.widget.core.widgetSurface

/**
 * Material 3 Expressive player: sunny-shaped artwork, emphasized type, and the same
 * connected wide-segment playback group as the in-app player (PlayerControls.kt).
 */
@Composable
fun NowPlayingContent(snapshot: NowPlayingSnapshot?, artwork: Bitmap?) {
    Box(modifier = GlanceModifier.fillMaxSize().widgetSurface()) {
        if (snapshot == null) {
            EmptyState()
        } else {
            val size = LocalSize.current
            when {
                size.height >= NowPlayingWidget.LARGE.height -> LargeLayout(snapshot, artwork)
                size.width >= NowPlayingWidget.WIDE.width -> WideLayout(snapshot, artwork)
                else -> CompactLayout(snapshot, artwork)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity(WidgetDeepLink.openApp(context))),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            ShapeDecor(WidgetShape.CLOVER, GlanceTheme.colors.secondaryContainer, 52.dp)
            Image(
                provider = ImageProvider(R.drawable.ic_music_note),
                contentDescription = null,
                modifier = GlanceModifier.size(22.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
            )
        }
        Spacer(modifier = GlanceModifier.height(6.dp))
        Text(
            text = context.getString(R.string.widget_nothing_playing),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun CompactLayout(snapshot: NowPlayingSnapshot, artwork: Bitmap?) {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShapedArtwork(artwork, 40.dp)
        Spacer(modifier = GlanceModifier.defaultWeight())
        WidePlayPauseButton(snapshot.isPlaying, 40.dp)
    }
}

@Composable
private fun WideLayout(snapshot: NowPlayingSnapshot, artwork: Bitmap?) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShapedArtwork(artwork, 44.dp)
        Spacer(modifier = GlanceModifier.width(12.dp))
        TrackText(snapshot, modifier = GlanceModifier.defaultWeight())
        Spacer(modifier = GlanceModifier.width(8.dp))
        WidePlayPauseButton(snapshot.isPlaying, 40.dp)
        Spacer(modifier = GlanceModifier.width(6.dp))
        PlaybackSegment(
            iconRes = R.drawable.ic_next,
            contentDescription = context.getString(R.string.widget_next),
            onClick = actionRunCallback<NextTrackAction>(),
            modifier = GlanceModifier.width(44.dp),
            heightDp = 40.dp,
        )
    }
}

@Composable
private fun LargeLayout(snapshot: NowPlayingSnapshot, artwork: Bitmap?) {
    Column(modifier = GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShapedArtwork(artwork, 56.dp)
            Spacer(modifier = GlanceModifier.width(12.dp))
            TrackText(snapshot, modifier = GlanceModifier.defaultWeight(), titleSize = 16)
            Spacer(modifier = GlanceModifier.width(4.dp))
            LikeButton(snapshot.isLiked)
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
        if (snapshot.durationMs > 0L) {
            LinearProgressIndicator(
                progress = (snapshot.positionMs.toFloat() / snapshot.durationMs).coerceIn(0f, 1f),
                modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.surfaceVariant,
            )
        }
        Spacer(modifier = GlanceModifier.height(8.dp))
        ConnectedPlaybackControls(
            isPlaying = snapshot.isPlaying,
            heightDp = 44.dp,
            modifier = GlanceModifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TrackText(
    snapshot: NowPlayingSnapshot,
    modifier: GlanceModifier,
    titleSize: Int = 14,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.clickable(actionStartActivity(WidgetDeepLink.openMusicPlayer(context))),
    ) {
        Text(
            text = snapshot.title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = titleSize.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = snapshot.artist,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            maxLines = 1,
        )
    }
}

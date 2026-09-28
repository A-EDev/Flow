package io.github.aedev.flow.widget.nowplaying

import android.graphics.Bitmap
import android.os.SystemClock
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
import androidx.glance.layout.fillMaxHeight
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
import io.github.aedev.flow.widget.core.component.NextButton
import io.github.aedev.flow.widget.core.component.PreviousButton
import io.github.aedev.flow.widget.core.component.SquarePlayPauseButton
import io.github.aedev.flow.widget.core.component.WidePlayPauseButton
import io.github.aedev.flow.widget.core.component.WidgetArtwork
import io.github.aedev.flow.widget.core.component.WidgetElapsedTime
import io.github.aedev.flow.widget.core.component.WidgetEmptyState
import io.github.aedev.flow.widget.core.component.WidgetWavyProgress
import io.github.aedev.flow.widget.core.component.widgetProgressAt
import io.github.aedev.flow.widget.core.state.NowPlayingSnapshot
import io.github.aedev.flow.widget.core.theme.WidgetDimens
import io.github.aedev.flow.widget.core.theme.WidgetText
import io.github.aedev.flow.widget.core.theme.widgetSurface

/**
 * The music player on the home screen, laid out for whichever [NowPlayingLayout] fits. [nowElapsedMs]
 * places the progress bar; the widget passes the ticker's clock so each tick re-renders it.
 */
@Composable
internal fun NowPlayingContent(
    snapshot: NowPlayingSnapshot?,
    artwork: Bitmap?,
    nowElapsedMs: Long = SystemClock.elapsedRealtime(),
) {
    val layout = NowPlayingLayout.forSize(LocalSize.current)
    Box(modifier = GlanceModifier.fillMaxSize().widgetSurface()) {
        when {
            snapshot == null && (layout == NowPlayingLayout.SMALL || layout == NowPlayingLayout.STRIP) -> CompactEmpty()
            snapshot == null -> NothingPlaying()
            layout == NowPlayingLayout.SMALL -> SmallLayout(snapshot)
            layout == NowPlayingLayout.STRIP -> StripLayout(snapshot, artwork)
            layout == NowPlayingLayout.SQUARE -> SquareLayout(snapshot, artwork)
            layout == NowPlayingLayout.CARD -> CardLayout(snapshot, artwork, nowElapsedMs)
            else -> PosterLayout(snapshot, artwork, nowElapsedMs)
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
        // At the narrowest strip the artwork would push Next off the edge, so it goes first.
        if (LocalSize.current.width >= StripArtMinWidth) {
            PlayerArtwork(artwork, NowPlayingLayout.STRIP_ART_DP.dp)
            Spacer(GlanceModifier.width(WidgetDimens.ContentPadding))
        }
        TrackText(snapshot, GlanceModifier.defaultWeight(), WidgetText.titleSmall())
        Spacer(GlanceModifier.width(WidgetDimens.ItemGap))
        WidePlayPauseButton(snapshot.isPlaying, GlanceModifier.width(76.dp))
        Spacer(GlanceModifier.width(6.dp))
        NextButton(GlanceModifier.size(WidgetDimens.TouchTarget))
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

/** Artwork on the left filling the card, the track centred beside it over the wave and the controls. */
@Composable
private fun CardLayout(
    snapshot: NowPlayingSnapshot,
    artwork: Bitmap?,
    nowElapsedMs: Long,
) {
    val size = LocalSize.current
    val card = cardMetrics(size)
    val column = size.width - card.inset * 2 - if (card.art > 0.dp) card.art + ArtGap else 0.dp
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(card.inset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (card.art > 0.dp) {
            PlayerArtwork(artwork, card.art)
            Spacer(GlanceModifier.width(ArtGap))
        }
        Column(
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TrackText(snapshot, GlanceModifier.fillMaxWidth(), WidgetText.centered(WidgetText.titleMedium()), centered = true)
            if (card.showProgress) {
                Spacer(GlanceModifier.height(WidgetDimens.ItemGap))
                ProgressRow(snapshot, nowElapsedMs, column)
            }
            Spacer(GlanceModifier.height(WidgetDimens.ItemGap))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PreviousButton(GlanceModifier.size(card.controls.side))
                Spacer(GlanceModifier.width(card.controls.gap))
                SquarePlayPauseButton(snapshot.isPlaying, card.controls.play)
                Spacer(GlanceModifier.width(card.controls.gap))
                NextButton(GlanceModifier.size(card.controls.side))
            }
        }
    }
}

/** The wavy progress bar between the live elapsed time and the track length, [width] across in all. */
@Composable
private fun ProgressRow(
    snapshot: NowPlayingSnapshot,
    nowElapsedMs: Long,
    width: Dp,
    modifier: GlanceModifier = GlanceModifier.fillMaxWidth(),
) {
    val progress =
        widgetProgressAt(
            positionMs = snapshot.positionMs,
            durationMs = snapshot.durationMs,
            capturedAtElapsedMs = snapshot.capturedAtElapsedMs,
            isPlaying = snapshot.isPlaying,
            speed = snapshot.speed,
            nowElapsedMs = nowElapsedMs,
        )
    val timed = snapshot.durationMs > 0L
    val barWidth = if (timed) width - (TimeLabelWidth + WidgetDimens.ItemGap) * 2 else width
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (timed) {
            WidgetElapsedTime(
                positionMs = snapshot.positionMs,
                capturedAtElapsedMs = snapshot.capturedAtElapsedMs,
                isRunning = snapshot.isPlaying,
                style = WidgetText.labelSmall(GlanceTheme.colors.onSurfaceVariant),
                modifier = GlanceModifier.width(TimeLabelWidth),
            )
            Spacer(GlanceModifier.width(WidgetDimens.ItemGap))
        }
        WidgetWavyProgress(progress = progress, playing = snapshot.isPlaying, width = barWidth.coerceAtLeast(0.dp))
        if (timed) {
            Spacer(GlanceModifier.width(WidgetDimens.ItemGap))
            Text(
                text = formatDurationMillis(snapshot.durationMs, padMinutes = true),
                style = WidgetText.labelSmall(GlanceTheme.colors.onSurfaceVariant),
                maxLines = 1,
                modifier = GlanceModifier.width(TimeLabelWidth),
            )
        }
    }
}

@Composable
private fun PosterLayout(
    snapshot: NowPlayingSnapshot,
    artwork: Bitmap?,
    nowElapsedMs: Long,
) {
    val context = LocalContext.current
    val row = LocalSize.current.width - WidgetDimens.ContentPadding * 2 - WidgetDimens.SmallGap * 2
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
        ProgressRow(
            snapshot,
            nowElapsedMs,
            row,
            GlanceModifier.fillMaxWidth().padding(horizontal = WidgetDimens.SmallGap, vertical = WidgetDimens.SmallGap),
        )
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
    centered: Boolean = false,
) {
    val context = LocalContext.current
    val artistStyle =
        if (centered) {
            WidgetText.centered(
                WidgetText.bodyMedium(GlanceTheme.colors.onSurfaceVariant),
            )
        } else {
            WidgetText.bodySmall()
        }
    Column(
        modifier = modifier.clickable(actionStartActivity(WidgetDeepLink.openMusicPlayer(context))),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(text = snapshot.title, style = titleStyle, maxLines = 1)
        if (snapshot.artist.isNotBlank()) {
            Text(text = snapshot.artist, style = artistStyle, maxLines = 1)
        }
    }
}

private val StripArtMinWidth = 260.dp

// Fits "1:05:03" at the label size; fixed, so the bar keeps its width as the digits change.
private val TimeLabelWidth = 44.dp

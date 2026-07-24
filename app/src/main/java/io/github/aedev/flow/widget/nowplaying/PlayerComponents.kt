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
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import io.github.aedev.flow.R
import io.github.aedev.flow.widget.core.NextTrackAction
import io.github.aedev.flow.widget.core.PlayPauseAction
import io.github.aedev.flow.widget.core.PreviousTrackAction
import io.github.aedev.flow.widget.core.ShapeDecor
import io.github.aedev.flow.widget.core.ToggleLikeAction
import io.github.aedev.flow.widget.core.WidgetDeepLink
import io.github.aedev.flow.widget.core.WidgetShape

/**
 * Artwork clipped to an expressive shape at load time (see WidgetShapeTransformation).
 * Falls back to a tonal clover with a note glyph; optional record center hole.
 */
@Composable
internal fun ShapedArtwork(
    artwork: Bitmap?,
    sizeDp: Dp,
    centerHole: Boolean = false,
) {
    val context = LocalContext.current
    val clickModifier = GlanceModifier
        .size(sizeDp)
        .clickable(actionStartActivity(WidgetDeepLink.openMusicPlayer(context)))
    Box(modifier = clickModifier, contentAlignment = Alignment.Center) {
        if (artwork != null) {
            Image(
                provider = ImageProvider(artwork),
                contentDescription = context.getString(R.string.widget_artwork),
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            ShapeDecor(WidgetShape.CLOVER, GlanceTheme.colors.secondaryContainer, sizeDp)
            Image(
                provider = ImageProvider(R.drawable.ic_music_note),
                contentDescription = context.getString(R.string.widget_artwork),
                modifier = GlanceModifier.size(sizeDp * 0.4f),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
            )
        }
        if (centerHole && artwork != null) {
            Box(
                modifier = GlanceModifier
                    .size(sizeDp * 0.14f)
                    .background(GlanceTheme.colors.widgetBackground)
                    .cornerRadius(sizeDp * 0.07f),
            ) {}
        }
    }
}

/**
 * Wide stadium playback segment mirroring the in-app player's connected button group
 * (PlayerControls.kt): every control is a wide fully-rounded segment; play/pause is
 * the dominant filled one and only its glyph swaps with state.
 */
@Composable
internal fun PlaybackSegment(
    iconRes: Int,
    contentDescription: String,
    onClick: Action,
    modifier: GlanceModifier,
    filled: Boolean = false,
    heightDp: Dp,
    iconSize: Dp = heightDp * 0.5f,
) {
    Box(
        modifier = modifier
            .height(heightDp)
            .background(
                if (filled) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer,
            )
            .cornerRadius(heightDp / 2)
            .clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = contentDescription,
            modifier = GlanceModifier.size(iconSize),
            colorFilter = ColorFilter.tint(
                if (filled) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer,
            ),
        )
    }
}

/** The full prev / play-pause / next connected group, play dominant like the app player. */
@Composable
internal fun ConnectedPlaybackControls(
    isPlaying: Boolean,
    heightDp: Dp,
    modifier: GlanceModifier = GlanceModifier,
    sideWidth: Dp = 56.dp,
) {
    val context = LocalContext.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        PlaybackSegment(
            iconRes = R.drawable.ic_previous,
            contentDescription = context.getString(R.string.widget_previous),
            onClick = actionRunCallback<PreviousTrackAction>(),
            modifier = GlanceModifier.width(sideWidth),
            heightDp = heightDp,
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        WidePlayPauseButton(
            isPlaying = isPlaying,
            heightDp = heightDp,
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        PlaybackSegment(
            iconRes = R.drawable.ic_next,
            contentDescription = context.getString(R.string.widget_next),
            onClick = actionRunCallback<NextTrackAction>(),
            modifier = GlanceModifier.width(sideWidth),
            heightDp = heightDp,
        )
    }
}

/** Wide filled play/pause — container stays wide in both states, only the glyph swaps. */
@Composable
internal fun WidePlayPauseButton(
    isPlaying: Boolean,
    heightDp: Dp,
    modifier: GlanceModifier = GlanceModifier.width(heightDp * 1.7f),
) {
    val context = LocalContext.current
    PlaybackSegment(
        iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
        contentDescription = context.getString(
            if (isPlaying) R.string.widget_pause else R.string.widget_play,
        ),
        onClick = actionRunCallback<PlayPauseAction>(),
        modifier = modifier,
        filled = true,
        heightDp = heightDp,
        iconSize = heightDp * 0.55f,
    )
}

/** Like state is a color shift: quiet outline normally, filled tonal chip when liked. */
@Composable
internal fun LikeButton(isLiked: Boolean) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .size(36.dp)
            .background(
                if (isLiked) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.widgetBackground,
            )
            .cornerRadius(18.dp)
            .clickable(actionRunCallback<ToggleLikeAction>()),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(if (isLiked) R.drawable.ic_like_filled else R.drawable.ic_like),
            contentDescription = context.getString(R.string.widget_like),
            modifier = GlanceModifier.size(20.dp),
            colorFilter = ColorFilter.tint(
                if (isLiked) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurfaceVariant,
            ),
        )
    }
}

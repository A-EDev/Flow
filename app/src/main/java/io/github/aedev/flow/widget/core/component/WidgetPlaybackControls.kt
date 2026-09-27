package io.github.aedev.flow.widget.core.component

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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import io.github.aedev.flow.R
import io.github.aedev.flow.widget.core.action.NextTrackAction
import io.github.aedev.flow.widget.core.action.PlayPauseAction
import io.github.aedev.flow.widget.core.action.PreviousTrackAction
import io.github.aedev.flow.widget.core.action.ToggleLikeAction
import io.github.aedev.flow.widget.core.theme.WidgetDimens

/**
 * One stadium segment of the in-app player's connected button group (PlayerControls.kt): play is
 * the filled dominant one and only its glyph changes with state.
 */
@Composable
internal fun PlaybackSegment(
    iconRes: Int,
    contentDescription: String,
    onClick: Action,
    modifier: GlanceModifier,
    filled: Boolean = false,
    height: Dp = WidgetDimens.TouchTarget,
) {
    Box(
        modifier =
            modifier
                .height(height)
                .background(if (filled) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer)
                .cornerRadius(height / 2)
                .clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = contentDescription,
            modifier = GlanceModifier.size(height * if (filled) 0.55f else 0.5f),
            colorFilter = ColorFilter.tint(if (filled) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer),
        )
    }
}

/** Wide filled play/pause; it keeps its width in both states, as the owner chose for the app player. */
@Composable
internal fun WidePlayPauseButton(
    isPlaying: Boolean,
    modifier: GlanceModifier = GlanceModifier.width(WidgetDimens.TouchTarget * 1.6f),
    height: Dp = WidgetDimens.TouchTarget,
) {
    val context = LocalContext.current
    PlaybackSegment(
        iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
        contentDescription = context.getString(if (isPlaying) R.string.widget_pause else R.string.widget_play),
        onClick = actionRunCallback<PlayPauseAction>(),
        modifier = modifier,
        filled = true,
        height = height,
    )
}

@Composable
internal fun NextSegment(modifier: GlanceModifier) {
    PlaybackSegment(
        iconRes = R.drawable.ic_next,
        contentDescription = LocalContext.current.getString(R.string.widget_next),
        onClick = actionRunCallback<NextTrackAction>(),
        modifier = modifier,
    )
}

/** Previous, play/pause and next as one connected group, play taking the spare width. */
@Composable
internal fun ConnectedPlaybackControls(
    isPlaying: Boolean,
    modifier: GlanceModifier,
    sideWidth: Dp,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        PlaybackSegment(
            iconRes = R.drawable.ic_previous,
            contentDescription = LocalContext.current.getString(R.string.widget_previous),
            onClick = actionRunCallback<PreviousTrackAction>(),
            modifier = GlanceModifier.width(sideWidth),
        )
        Spacer(GlanceModifier.width(SegmentGap))
        WidePlayPauseButton(isPlaying = isPlaying, modifier = GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(SegmentGap))
        NextSegment(GlanceModifier.width(sideWidth))
    }
}

/** Liked is a tonal fill; TalkBack hears what a tap will do. */
@Composable
internal fun LikeButton(isLiked: Boolean) {
    val context = LocalContext.current
    Box(
        modifier =
            GlanceModifier
                .size(WidgetDimens.TouchTarget)
                .background(if (isLiked) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.widgetBackground)
                .cornerRadius(WidgetDimens.TouchTarget / 2)
                .clickable(actionRunCallback<ToggleLikeAction>()),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(if (isLiked) R.drawable.ic_like_filled else R.drawable.ic_like),
            contentDescription = context.getString(if (isLiked) R.string.widget_unlike else R.string.widget_like),
            modifier = GlanceModifier.size(22.dp),
            colorFilter =
                ColorFilter.tint(if (isLiked) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurfaceVariant),
        )
    }
}

private val SegmentGap = 6.dp

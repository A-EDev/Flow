package io.github.aedev.flow.widget.core.component

import android.app.PendingIntent
import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
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
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionSendBroadcast
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
import io.github.aedev.flow.widget.core.action.ToggleLikeAction
import io.github.aedev.flow.widget.core.action.WidgetPlaybackCommand
import io.github.aedev.flow.widget.core.theme.WidgetDimens

/** The resting shape of a pressable button; each squares off further while it is pressed. */
enum class WidgetButtonShape(
    @DrawableRes val background: Int,
) {
    ROUND(R.drawable.widget_button_round),
    SQUARE(R.drawable.widget_button_square),
}

/**
 * A button the launcher draws as a platform view, so its shape tightens under the finger the way
 * M3 Expressive buttons do. Before Android 12 a platform view cannot take a theme tint, so it falls
 * back to a plain Glance button.
 */
@Composable
internal fun WidgetPressButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    pendingIntent: PendingIntent,
    fallback: Action,
    modifier: GlanceModifier,
    filled: Boolean = false,
    shape: WidgetButtonShape = WidgetButtonShape.ROUND,
    iconSize: Dp = 24.dp,
) {
    val context = LocalContext.current
    val container = if (filled) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer
    val content = if (filled) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val views =
            RemoteViews(context.packageName, R.layout.widget_playback_button).apply {
                setInt(R.id.widget_button, "setBackgroundResource", shape.background)
                setColorProvider(R.id.widget_button, "setBackgroundTintList", container, context)
                setImageViewResource(R.id.widget_button_icon, icon)
                setColorProvider(R.id.widget_button_icon, "setImageTintList", content, context)
                setViewLayoutWidth(R.id.widget_button_icon, iconSize.value, TypedValue.COMPLEX_UNIT_DIP)
                setViewLayoutHeight(R.id.widget_button_icon, iconSize.value, TypedValue.COMPLEX_UNIT_DIP)
                setContentDescription(R.id.widget_button, contentDescription)
                setOnClickPendingIntent(R.id.widget_button, pendingIntent)
            }
        AndroidRemoteViews(views, modifier)
    } else {
        Box(modifier = modifier.background(container).clickable(fallback), contentAlignment = Alignment.Center) {
            Image(
                provider = ImageProvider(icon),
                contentDescription = contentDescription,
                modifier = GlanceModifier.size(iconSize),
                colorFilter = ColorFilter.tint(content),
            )
        }
    }
}

@Composable
internal fun PlaybackCommandButton(
    command: WidgetPlaybackCommand,
    @DrawableRes icon: Int,
    contentDescription: String,
    modifier: GlanceModifier,
    filled: Boolean = false,
    shape: WidgetButtonShape = WidgetButtonShape.ROUND,
    iconSize: Dp = 24.dp,
) {
    val context = LocalContext.current
    WidgetPressButton(
        icon = icon,
        contentDescription = contentDescription,
        pendingIntent = command.pendingIntent(context),
        fallback = actionSendBroadcast(command.intent(context)),
        modifier = modifier,
        filled = filled,
        shape = shape,
        iconSize = iconSize,
    )
}

/** Wide filled play/pause; it keeps its width in both states, as the owner chose for the app player. */
@Composable
internal fun WidePlayPauseButton(
    isPlaying: Boolean,
    modifier: GlanceModifier = GlanceModifier.width(WidgetDimens.TouchTarget * 1.6f),
    height: Dp = WidgetDimens.TouchTarget,
) {
    val context = LocalContext.current
    PlaybackCommandButton(
        command = WidgetPlaybackCommand.PLAY_PAUSE,
        icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
        contentDescription = context.getString(if (isPlaying) R.string.widget_pause else R.string.widget_play),
        modifier = modifier.height(height),
        filled = true,
        iconSize = height * 0.55f,
    )
}

/** The dominant rounded-square play/pause of the card layout. */
@Composable
internal fun SquarePlayPauseButton(
    isPlaying: Boolean,
    size: Dp,
) {
    val context = LocalContext.current
    PlaybackCommandButton(
        command = WidgetPlaybackCommand.PLAY_PAUSE,
        icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
        contentDescription = context.getString(if (isPlaying) R.string.widget_pause else R.string.widget_play),
        modifier = GlanceModifier.size(size),
        filled = true,
        shape = WidgetButtonShape.SQUARE,
        iconSize = size * 0.5f,
    )
}

@Composable
internal fun NextButton(modifier: GlanceModifier) {
    PlaybackCommandButton(
        command = WidgetPlaybackCommand.NEXT,
        icon = R.drawable.ic_next,
        contentDescription = LocalContext.current.getString(R.string.widget_next),
        modifier = modifier,
    )
}

@Composable
internal fun PreviousButton(modifier: GlanceModifier) {
    PlaybackCommandButton(
        command = WidgetPlaybackCommand.PREVIOUS,
        icon = R.drawable.ic_previous,
        contentDescription = LocalContext.current.getString(R.string.widget_previous),
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
        PreviousButton(GlanceModifier.width(sideWidth).height(WidgetDimens.TouchTarget))
        Spacer(GlanceModifier.width(SegmentGap))
        WidePlayPauseButton(isPlaying = isPlaying, modifier = GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(SegmentGap))
        NextButton(GlanceModifier.width(sideWidth).height(WidgetDimens.TouchTarget))
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

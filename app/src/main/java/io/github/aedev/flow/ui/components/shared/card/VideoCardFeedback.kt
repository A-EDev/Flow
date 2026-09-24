package io.github.aedev.flow.ui.components.shared.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedToggleButton
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.aedev.flow.R

private val FeedbackHeight = ButtonDefaults.ExtraSmallContainerHeight

/**
 * The card's feedback actions as one connected M3 Expressive group. I like this and Not interested
 * each fire once; Watched is a toggle that stays on, because a watched mark can't be taken back here.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun VideoCardFeedback(
    state: VideoCardState,
    showRating: Boolean,
    showWatched: Boolean,
    actions: VideoCardActions,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val count = (if (showRating) 2 else 0) + (if (showWatched) 1 else 0)
    if (count == 0) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        if (showRating) {
            FeedbackButton(
                icon = Icons.Outlined.ThumbUp,
                label = stringResource(R.string.i_like_this),
                shapes = connectedShapes(index = 0, count = count).asButtonShapes(),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    actions.onInterested(state.video)
                },
            )
            FeedbackButton(
                icon = Icons.Outlined.ThumbDown,
                label = stringResource(R.string.not_interested),
                shapes = connectedShapes(index = 1, count = count).asButtonShapes(),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    actions.onNotInterested(state.video)
                },
            )
        }
        if (showWatched) {
            val watched = state.isWatched
            OutlinedToggleButton(
                checked = watched,
                onCheckedChange = {
                    if (!watched) {
                        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        actions.onWatched(state.video)
                    }
                },
                shapes = connectedShapes(index = count - 1, count = count),
                contentPadding = ButtonDefaults.ExtraSmallContentPadding,
                modifier = Modifier.heightIn(min = FeedbackHeight),
            ) {
                Icon(
                    imageVector = if (watched) Icons.Filled.Visibility else Icons.Outlined.Visibility,
                    contentDescription = stringResource(R.string.mark_as_watched),
                    modifier = Modifier.size(ButtonDefaults.ExtraSmallIconSize),
                )
                if (!showRating) {
                    Spacer(Modifier.width(ButtonDefaults.ExtraSmallIconSpacing))
                    FeedbackLabel(stringResource(R.string.mark_as_watched))
                }
            }
        }
    }
}

@Composable
private fun RowScope.FeedbackButton(
    icon: ImageVector,
    label: String,
    shapes: ButtonShapes,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shapes = shapes,
        contentPadding = ButtonDefaults.ExtraSmallContentPadding,
        modifier = Modifier.weight(1f).heightIn(min = FeedbackHeight),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.ExtraSmallIconSize))
        Spacer(Modifier.width(ButtonDefaults.ExtraSmallIconSpacing))
        FeedbackLabel(label)
    }
}

@Composable
private fun FeedbackLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun connectedShapes(
    index: Int,
    count: Int,
): ToggleButtonShapes =
    when {
        count == 1 -> ToggleButtonDefaults.shapesFor(FeedbackHeight)
        index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
        index == count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
    }

private fun ToggleButtonShapes.asButtonShapes() = ButtonShapes(shape = shape, pressedShape = pressedShape)

package io.github.aedev.flow.ui.components.shared.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.aedev.flow.R

/**
 * I want more like this and Not interested as one connected M3 Expressive pair. Each fires once and
 * holds no state, so they are plain buttons shaped as a group rather than toggles.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun VideoCardFeedback(
    state: VideoCardState,
    actions: VideoCardActions,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        FeedbackButton(
            icon = Icons.Outlined.ThumbUp,
            label = stringResource(R.string.i_like_this),
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes().asButtonShapes(),
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                actions.onInterested(state.video)
            },
        )
        FeedbackButton(
            icon = Icons.Outlined.ThumbDown,
            label = stringResource(R.string.not_interested),
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes().asButtonShapes(),
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                actions.onNotInterested(state.video)
            },
        )
    }
}

/** Longer translations shrink to the small label size before they are cut. */
@Composable
private fun RowScope.FeedbackButton(
    icon: ImageVector,
    label: String,
    shapes: ButtonShapes,
    onClick: () -> Unit,
) {
    val typography = MaterialTheme.typography
    OutlinedButton(
        onClick = onClick,
        shapes = shapes,
        contentPadding = ButtonDefaults.ExtraSmallContentPadding,
        modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.ExtraSmallContainerHeight),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.ExtraSmallIconSize))
        Spacer(Modifier.width(ButtonDefaults.ExtraSmallIconSpacing))
        Text(
            text = label,
            style = typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize =
                TextAutoSize.StepBased(
                    minFontSize = typography.labelSmall.fontSize,
                    maxFontSize = typography.labelMedium.fontSize,
                ),
        )
    }
}

private fun ToggleButtonShapes.asButtonShapes() = ButtonShapes(shape = shape, pressedShape = pressedShape)

package io.github.aedev.flow.ui.components.shared.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R

private val FeedbackHeight = ButtonDefaults.ExtraSmallContainerHeight
private val WatchedToggleWidth = 48.dp

/**
 * The card's feedback as one connected M3 Expressive group: I want more like this and Not interested
 * fire once, and the trailing eye is a toggle that stays on, since a watched mark can't be undone here.
 *
 * Each button is as wide as its label needs. On a narrow phone or with a long translation the icons
 * drop first so the words stay whole, and only then do the labels shrink to the small label size.
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
    if (!showRating && !showWatched) return
    val haptics = LocalHapticFeedback.current
    val count = (if (showRating) 2 else 0) + (if (showWatched) 1 else 0)
    val likeLabel = stringResource(R.string.i_like_this)
    val notInterestedLabel = stringResource(R.string.not_interested)

    BoxWithConstraints(modifier = modifier) {
        val layout = rememberFeedbackLayout(listOf(likeLabel, notInterestedLabel), rowWidth = maxWidth, withWatched = showWatched)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            if (showRating) {
                FeedbackButton(
                    icon = Icons.Outlined.ThumbUp.takeIf { layout.showIcons },
                    label = likeLabel,
                    weight = layout.weights[0],
                    shapes = connectedShapes(index = 0, count = count).asButtonShapes(),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        actions.onInterested(state.video)
                    },
                )
                FeedbackButton(
                    icon = Icons.Outlined.ThumbDown.takeIf { layout.showIcons },
                    label = notInterestedLabel,
                    weight = layout.weights[1],
                    shapes = connectedShapes(index = 1, count = count).asButtonShapes(),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Reject)
                        actions.onNotInterested(state.video)
                    },
                )
            }
            if (showWatched) {
                WatchedToggle(
                    state = state,
                    labelled = !showRating,
                    shapes = connectedShapes(index = count - 1, count = count),
                    onWatched = {
                        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        actions.onWatched(state.video)
                    },
                )
            }
        }
    }
}

/** How the two rated buttons share their row: whether they carry icons, and each one's share of the width. */
internal data class FeedbackLayout(
    val showIcons: Boolean,
    val weights: List<Float>,
)

/**
 * Sizes each button to its own label instead of splitting the row evenly, so a long label borrows
 * room from a short one. Icons stay while every label fits beside one at full size; otherwise they
 * go first, and only then does the text shrink.
 */
internal fun planFeedbackLayout(
    labelWidths: List<Float>,
    available: Float,
    iconRoom: Float,
    padding: Float,
): FeedbackLayout {
    val withIcons = labelWidths.map { it + padding + iconRoom }
    val showIcons = withIcons.sum() <= available
    val needed = if (showIcons) withIcons else labelWidths.map { it + padding }
    return FeedbackLayout(showIcons = showIcons, weights = needed)
}

@Composable
private fun rememberFeedbackLayout(
    labels: List<String>,
    rowWidth: Dp,
    withWatched: Boolean,
): FeedbackLayout {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelMedium
    val padding = ButtonDefaults.ExtraSmallContentPadding
    return remember(labels, rowWidth, withWatched, style, density) {
        with(density) {
            val gaps = ButtonGroupDefaults.ConnectedSpaceBetween * (if (withWatched) labels.size else labels.size - 1)
            val toggle = if (withWatched) WatchedToggleWidth else 0.dp
            planFeedbackLayout(
                labelWidths =
                    labels.map {
                        measurer
                            .measure(it, style, maxLines = 1)
                            .size.width
                            .toFloat()
                    },
                available = (rowWidth - gaps - toggle).toPx(),
                iconRoom = (ButtonDefaults.ExtraSmallIconSize + ButtonDefaults.ExtraSmallIconSpacing).toPx(),
                padding =
                    (padding.calculateLeftPadding(LayoutDirection.Ltr) + padding.calculateRightPadding(LayoutDirection.Ltr)).toPx(),
            )
        }
    }
}

@Composable
private fun RowScope.FeedbackButton(
    icon: ImageVector?,
    label: String,
    weight: Float,
    shapes: ButtonShapes,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shapes = shapes,
        contentPadding = ButtonDefaults.ExtraSmallContentPadding,
        modifier = Modifier.weight(weight).heightIn(min = FeedbackHeight),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.ExtraSmallIconSize))
            Spacer(Modifier.width(ButtonDefaults.ExtraSmallIconSpacing))
        }
        FeedbackLabel(label)
    }
}

@Composable
private fun WatchedToggle(
    state: VideoCardState,
    labelled: Boolean,
    shapes: ToggleButtonShapes,
    onWatched: () -> Unit,
) {
    val watched = state.isWatched
    OutlinedToggleButton(
        checked = watched,
        onCheckedChange = { if (!watched) onWatched() },
        shapes = shapes,
        contentPadding = ButtonDefaults.ExtraSmallContentPadding,
        modifier = Modifier.heightIn(min = FeedbackHeight).widthIn(min = WatchedToggleWidth),
    ) {
        Icon(
            imageVector = if (watched) Icons.Filled.Visibility else Icons.Outlined.Visibility,
            contentDescription = stringResource(R.string.mark_as_watched),
            modifier = Modifier.size(ButtonDefaults.ExtraSmallIconSize),
        )
        if (labelled) {
            Spacer(Modifier.width(ButtonDefaults.ExtraSmallIconSpacing))
            FeedbackLabel(stringResource(R.string.mark_as_watched))
        }
    }
}

@Composable
private fun FeedbackLabel(text: String) {
    val typography = MaterialTheme.typography
    Text(
        text = text,
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

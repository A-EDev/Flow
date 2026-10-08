package io.github.aedev.flow.ui.components.videoplayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import kotlin.math.roundToInt

private val HandleWidth = 28.dp
private val HandleHeight = 64.dp

/**
 * The tab left at the screen edge while the mini player is tucked past it. It rides at the mini
 * player's height, read in the layout phase, and a tap or a drag on it brings the player back.
 */
@Composable
internal fun BoxScope.MiniPlayerTuckHandle(
    state: PlayerDraggableState,
    miniY: () -> Float,
    miniHeight: Float,
    onUntuck: () -> Unit,
) {
    val side = state.tuckedSide
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = side != null,
        enter = fadeIn(motion.defaultEffectsSpec()),
        exit = ExitTransition.None,
        modifier =
            Modifier
                .align(if (side == MiniPlayerTuckSide.Left) Alignment.TopStart else Alignment.TopEnd)
                .offset {
                    val handleHeight = HandleHeight.toPx()
                    IntOffset(0, (miniY() + (miniHeight - handleHeight) / 2f).roundToInt())
                },
    ) {
        val onLeft = side == MiniPlayerTuckSide.Left
        Surface(
            shape =
                if (onLeft) {
                    RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                } else {
                    RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                },
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 3.dp,
            modifier =
                Modifier
                    .size(HandleWidth, HandleHeight)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.mini_player_show),
                        onClick = onUntuck,
                    ).draggable(
                        state = rememberDraggableState { },
                        orientation = Orientation.Horizontal,
                        onDragStarted = { onUntuck() },
                    ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (onLeft) Icons.Rounded.ChevronRight else Icons.Rounded.ChevronLeft,
                    contentDescription = stringResource(R.string.mini_player_show),
                )
            }
        }
    }
}

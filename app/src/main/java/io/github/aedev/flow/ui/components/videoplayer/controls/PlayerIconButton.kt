package io.github.aedev.flow.ui.components.videoplayer.controls

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import io.github.aedev.flow.ui.theme.PlayerScrimAffordance
import io.github.aedev.flow.ui.theme.PlayerScrimContent

/**
 * A round icon button resting on video.
 *
 * [FilledIconButton] rather than [androidx.compose.material3.IconButton] because the player's
 * buttons are laid out against fixed pill and row heights: `IconButton` reserves a 48dp minimum
 * interactive size, which would grow a 28dp pill's slot and move everything beside it.
 */
@Composable
internal fun PlayerPillIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    buttonSize: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    containerColor: Color = PlayerScrimAffordance,
    contentColor: Color = PlayerScrimContent,
) {
    FilledIconButton(
        onClick = onClick,
        shape = CircleShape,
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
        modifier = modifier.size(buttonSize),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
        )
    }
}

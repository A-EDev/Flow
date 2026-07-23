package io.github.aedev.flow.ui.tv.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.tv.focus.rememberTvFocusState
import io.github.aedev.flow.ui.tv.focus.tvFocusScale

/**
 * Focusable pill button for the ten-foot UI. Focus flips to the inverse surface
 * pair — the strongest tokens-only affordance at TV distance.
 */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val focusState = rememberTvFocusState()
    val focused = focusState.isFocused

    Surface(
        onClick = onClick,
        modifier = modifier.tvFocusScale(focusState),
        shape = CircleShape,
        color = if (focused) {
            MaterialTheme.colorScheme.inverseSurface
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (focused) {
            MaterialTheme.colorScheme.inverseOnSurface
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(22.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

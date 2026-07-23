package io.github.aedev.flow.ui.tv.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.tv.navigation.TvDestination
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens

/**
 * Collapsible navigation rail: 72dp icon-only strip that expands with labels
 * while any rail item holds focus. It overlays the content (which is laid out
 * against the collapsed width) so expansion never reflows the screen.
 */
@Composable
fun TvNavRail(
    selected: TvDestination,
    onSelected: (TvDestination) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    selectedFocusRequester: FocusRequester? = null,
) {
    val dimens = LocalTvDimens.current
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        targetValue = if (expanded) dimens.railExpandedWidth else dimens.railCollapsedWidth,
        label = "tvRailWidth",
    )

    Surface(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .onFocusChanged {
                expanded = it.hasFocus
                onFocusChanged(it.hasFocus)
            },
        color = if (expanded) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        tonalElevation = if (expanded) 2.dp else 0.dp,
    ) {
        Column(
            modifier = Modifier
                .focusGroup()
                .padding(horizontal = 12.dp, vertical = dimens.overscanVertical),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Two-layer brand badge (tight crop) tinted from the active
                // color scheme, so the logo follows all 28 theme modes.
                Box {
                    Icon(
                        painter = painterResource(R.drawable.ic_flow_badge_shape),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(width = 48.dp, height = 36.dp),
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_flow_badge_glyph),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(width = 48.dp, height = 36.dp),
                    )
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            TvDestination.primary.forEach { destination ->
                TvRailItem(
                    destination = destination,
                    selected = destination == selected,
                    expanded = expanded,
                    onClick = { onSelected(destination) },
                    modifier = if (destination == selected && selectedFocusRequester != null) {
                        Modifier.focusRequester(selectedFocusRequester)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

@Composable
private fun TvRailItem(
    destination: TvDestination,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        shape = CircleShape,
        color = when {
            focused -> MaterialTheme.colorScheme.inverseSurface
            selected -> MaterialTheme.colorScheme.secondaryContainer
            else -> Color.Transparent
        },
        contentColor = when {
            focused -> MaterialTheme.colorScheme.inverseOnSurface
            selected -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = stringResource(destination.labelRes),
                modifier = Modifier.size(26.dp),
            )
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                Text(
                    text = stringResource(destination.labelRes),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

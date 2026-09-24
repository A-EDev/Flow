package io.github.aedev.flow.ui.components.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** One button in a select mode's toolbar. */
internal class SelectionAction(
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * The actions for what is selected in a library list, floating over its bottom edge so they stay
 * under the thumb and don't depend on how wide the top bar is.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LibrarySelectionToolbar(
    visible: Boolean,
    summary: String,
    actions: List<SelectionAction>,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = FloatingToolbarDefaults.verticalEnterTransition(Alignment.Bottom),
        exit = FloatingToolbarDefaults.verticalExitTransition(Alignment.Bottom),
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier.padding(bottom = FloatingToolbarDefaults.ScreenOffset),
            leadingContent = {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            },
        ) {
            actions.forEach { action ->
                IconButton(onClick = action.onClick) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = action.label,
                        tint = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

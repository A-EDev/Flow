package io.github.aedev.flow.ui.tv.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Color set for [TvIconButton]; defaults come from the active theme. */
@Immutable
data class TvIconButtonColors(
    val container: Color,
    val content: Color,
    val focusedContainer: Color,
    val focusedContent: Color,
    val activeContainer: Color,
    val activeContent: Color,
)

/**
 * Round focusable icon button for TV transport rows and toolbars.
 * [active] marks a latched toggle (captions on, autoplay on, …).
 * [colors] overrides the theme-token defaults for surfaces with their own
 * color system (e.g. the palette-tinted music player).
 */
@Composable
fun TvIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    focusRequester: FocusRequester? = null,
    colors: TvIconButtonColors? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val resolved = colors ?: TvIconButtonColors(
        container = MaterialTheme.colorScheme.surfaceContainerHigh,
        content = MaterialTheme.colorScheme.onSurface,
        focusedContainer = MaterialTheme.colorScheme.inverseSurface,
        focusedContent = MaterialTheme.colorScheme.inverseOnSurface,
        activeContainer = MaterialTheme.colorScheme.primary,
        activeContent = MaterialTheme.colorScheme.onPrimary,
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged { focused = it.isFocused },
        shape = CircleShape,
        color = when {
            focused -> resolved.focusedContainer
            active -> resolved.activeContainer
            else -> resolved.container
        },
        contentColor = when {
            !enabled -> resolved.content.copy(alpha = 0.38f)
            focused -> resolved.focusedContent
            active -> resolved.activeContent
            else -> resolved.content
        },
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

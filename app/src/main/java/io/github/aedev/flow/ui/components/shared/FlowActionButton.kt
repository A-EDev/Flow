@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

/**
 * The medium, full-width call to action of a page's bottom bar. With [progress] it becomes a tonal
 * button whose container fills from the start as the lambda moves from 0 to 1; the lambda is read
 * while drawing, so a moving fill never recomposes the button.
 */
@Composable
fun FlowActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
    progress: (() -> Float)? = null,
) {
    val height = ButtonDefaults.MediumContainerHeight
    val shapes = ButtonDefaults.shapesFor(height)
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        enabled = enabled,
        shapes = shapes,
        colors =
            if (progress != null) {
                ButtonDefaults.filledTonalButtonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent)
            } else {
                ButtonDefaults.buttonColors()
            },
        contentPadding = ButtonDefaults.contentPaddingFor(height, leading != null, trailing != null),
        modifier =
            modifier.heightIn(min = height).then(
                if (progress != null) {
                    Modifier.clip(shapes.shape).drawBehind {
                        drawRect(colors.secondaryContainer)
                        drawRect(colors.primaryContainer, size = Size(size.width * progress().coerceIn(0f, 1f), size.height))
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        leading?.let { ActionIcon(it, height, trailing = false) }
        Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        trailing?.let { ActionIcon(it, height, trailing = true) }
    }
}

@Composable
private fun ActionIcon(
    icon: ImageVector,
    height: Dp,
    trailing: Boolean,
) {
    if (trailing) Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
    Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)))
    if (!trailing) Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
}

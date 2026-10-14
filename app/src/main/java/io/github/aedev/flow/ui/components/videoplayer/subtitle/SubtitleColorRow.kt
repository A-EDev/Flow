package io.github.aedev.flow.ui.components.videoplayer.subtitle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R

/**
 * A labelled row of colour swatches ending in a custom one. The custom swatch wears the chosen
 * colour when it matches none of [swatches], and a palette icon otherwise. Swatches compare by RGB
 * and are drawn at the selected colour's opacity, which has its own slider, never so faint they
 * vanish. A fully transparent layer is off, so nothing shows as selected and the swatches are solid.
 */
@Composable
internal fun SubtitleColorRow(
    label: String,
    swatches: List<Color>,
    selected: Color,
    onSelect: (Color) -> Unit,
    onCustom: () -> Unit,
) {
    val isOff = selected.alpha == 0f
    val alpha = if (isOff) 1f else selected.alpha.coerceAtLeast(MIN_SWATCH_ALPHA)
    val isCustom = !isOff && swatches.none { sameRgb(it, selected) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(swatches) { color ->
                ColorSwatch(
                    color = color.copy(alpha = alpha),
                    selected = !isOff && sameRgb(selected, color),
                    onClick = { onSelect(color) },
                )
            }
            item {
                if (isCustom) {
                    ColorSwatch(color = selected.copy(alpha = alpha), selected = true, onClick = onCustom)
                } else {
                    CustomSwatch(onClick = onCustom)
                }
            }
        }
    }
}

/**
 * A swatch marks its selection with a check inside the colour rather than a ring around it: an
 * accent-coloured outline reads as decoration next to a row of colours.
 */
@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(SwatchSize)
                .clip(CircleShape)
                .background(color)
                .border(SwatchBorderWidth, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = if (color.luminance() > SWATCH_LUMINANCE_SPLIT) Color.Black else Color.White,
                modifier = Modifier.size(SwatchIconSize),
            )
        }
    }
}

@Composable
private fun CustomSwatch(onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .size(SwatchSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(SwatchBorderWidth, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .selectable(selected = false, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Palette,
            contentDescription = stringResource(R.string.subtitle_custom_color),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(SwatchIconSize),
        )
    }
}

internal fun sameRgb(
    first: Color,
    second: Color,
): Boolean = (first.toArgb() and RGB_MASK) == (second.toArgb() and RGB_MASK)

private val SwatchSize = 42.dp
private val SwatchBorderWidth = 1.dp
private val SwatchIconSize = 20.dp
private const val SWATCH_LUMINANCE_SPLIT = 0.5f
private const val RGB_MASK = 0x00FFFFFF
private const val MIN_SWATCH_ALPHA = 0.35f

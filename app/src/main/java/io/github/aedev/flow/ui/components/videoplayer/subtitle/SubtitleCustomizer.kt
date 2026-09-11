package io.github.aedev.flow.ui.components.videoplayer.subtitle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.theme.SubtitleBackgroundSwatches
import io.github.aedev.flow.ui.theme.SubtitleTextSwatches

@Composable
fun SubtitleCustomizer(
    currentStyle: SubtitleStyle,
    onStyleChange: (SubtitleStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColors = SubtitleTextSwatches
    val backgroundColors = SubtitleBackgroundSwatches

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = stringResource(R.string.subtitle_customization_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        // Preview Section
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(PreviewHeight)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = MaterialTheme.shapes.large,
                    ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                color = currentStyle.backgroundColor,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.offset(y = -(currentStyle.bottomPadding * 0.35f).dp),
            ) {
                Text(
                    text = stringResource(R.string.subtitle_preview_text),
                    color = currentStyle.textColor,
                    fontSize = currentStyle.fontSize.sp,
                    fontWeight = if (currentStyle.isBold) FontWeight.Bold else FontWeight.Normal,
                    lineHeight = (currentStyle.fontSize * 1.25f).sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        // Font Size
        Column {
            Text(
                stringResource(R.string.subtitle_font_size_template, currentStyle.fontSize.toInt()),
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = currentStyle.fontSize,
                onValueChange = { onStyleChange(currentStyle.copy(fontSize = it)) },
                valueRange = 12f..32f,
                steps = 10,
            )
        }

        // Position
        Column {
            Text(
                stringResource(R.string.subtitle_position_template, currentStyle.bottomPadding.toInt()),
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = currentStyle.bottomPadding,
                onValueChange = { onStyleChange(currentStyle.copy(bottomPadding = it)) },
                valueRange = 24f..180f,
                steps = 11,
            )
        }

        // Text Color
        Text(stringResource(R.string.subtitle_text_color), style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(textColors) { color ->
                ColorSwatch(
                    color = color,
                    selected = sameRgb(currentStyle.textColor, color),
                    onClick = { onStyleChange(currentStyle.copy(textColor = color)) },
                )
            }
        }

        // Background Color
        Text(
            stringResource(R.string.subtitle_background_color),
            style = MaterialTheme.typography.labelLarge,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(backgroundColors) { color ->
                ColorSwatch(
                    color = color.copy(alpha = currentStyle.backgroundColor.alpha),
                    selected = sameRgb(currentStyle.backgroundColor, color),
                    onClick = {
                        onStyleChange(
                            currentStyle.copy(
                                backgroundColor = color.copy(alpha = currentStyle.backgroundColor.alpha),
                            ),
                        )
                    },
                )
            }
        }

        // Background Opacity
        Column {
            Text(
                stringResource(
                    R.string.subtitle_background_opacity_template,
                    (currentStyle.backgroundColor.alpha * 100).toInt(),
                ),
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = currentStyle.backgroundColor.alpha,
                onValueChange = { onStyleChange(currentStyle.copy(backgroundColor = currentStyle.backgroundColor.copy(alpha = it))) },
                valueRange = 0f..1f,
            )
        }

        // Bold toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.subtitle_bold_text), style = MaterialTheme.typography.labelLarge)
            Switch(
                checked = currentStyle.isBold,
                onCheckedChange = { onStyleChange(currentStyle.copy(isBold = it)) },
            )
        }

        OutlinedButton(
            onClick = { onStyleChange(SubtitleStyle()) },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.subtitle_reset_default))
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
                .border(
                    width = SwatchBorderWidth,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                ).selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = contentColorForSwatch(color),
                modifier = Modifier.size(SwatchCheckSize),
            )
        }
    }
}

/** Black or white, whichever the swatch it sits on can actually show. */
private fun contentColorForSwatch(color: Color): Color = if (color.luminance() > SWATCH_LUMINANCE_SPLIT) Color.Black else Color.White

private val PreviewHeight = 150.dp
private val SwatchSize = 42.dp
private val SwatchBorderWidth = 1.dp
private val SwatchCheckSize = 20.dp
private const val SWATCH_LUMINANCE_SPLIT = 0.5f

private fun sameRgb(
    first: Color,
    second: Color,
): Boolean = (first.toArgb() and 0x00FFFFFF) == (second.toArgb() and 0x00FFFFFF)

package io.github.aedev.flow.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R

@Composable
fun SubtitleCustomizer(
    currentStyle: SubtitleStyle,
    onStyleChange: (SubtitleStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColors =
        remember {
            listOf(
                Color.White,
                Color(0xFFFFF59D),
                Color(0xFF80DEEA),
                Color(0xFFA5D6A7),
                Color(0xFFFFCC80),
                Color(0xFFF8BBD0),
            )
        }
    val backgroundColors =
        remember {
            listOf(
                Color.Black,
                Color(0xFF1F2937),
                Color(0xFF263238),
                Color(0xFF4E342E),
                Color(0xFF102A43),
                Color(0xFF37474F),
            )
        }

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
            color = MaterialTheme.colorScheme.onSurface,
        )

        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(150.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    color = currentStyle.backgroundColor,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.padding(bottom = (currentStyle.bottomPadding * 0.35f).dp),
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
        }

        Column {
            Text(
                text = stringResource(R.string.subtitle_font_size_template, currentStyle.fontSize.toInt()),
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = currentStyle.fontSize,
                onValueChange = { onStyleChange(currentStyle.copy(fontSize = it)) },
                valueRange = 12f..32f,
                steps = 10,
            )
        }

        Column {
            Text(
                text = stringResource(R.string.subtitle_position_template, currentStyle.bottomPadding.toInt()),
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = currentStyle.bottomPadding,
                onValueChange = { onStyleChange(currentStyle.copy(bottomPadding = it)) },
                valueRange = 24f..180f,
                steps = 11,
            )
        }

        Text(
            text = stringResource(R.string.subtitle_text_color),
            style = MaterialTheme.typography.labelLarge,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(
                items = textColors,
                key = { it.toArgb() },
            ) { color ->
                ColorSwatch(
                    color = color,
                    selected = sameRgb(currentStyle.textColor, color),
                    onClick = { onStyleChange(currentStyle.copy(textColor = color)) },
                )
            }
        }

        Text(
            text = stringResource(R.string.subtitle_background_color),
            style = MaterialTheme.typography.labelLarge,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(
                items = backgroundColors,
                key = { it.toArgb() },
            ) { color ->
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

        Column {
            Text(
                text =
                    stringResource(
                        R.string.subtitle_background_opacity_template,
                        (currentStyle.backgroundColor.alpha * 100).toInt(),
                    ),
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = currentStyle.backgroundColor.alpha,
                onValueChange = {
                    onStyleChange(currentStyle.copy(backgroundColor = currentStyle.backgroundColor.copy(alpha = it)))
                },
                valueRange = 0f..1f,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.subtitle_bold_text),
                style = MaterialTheme.typography.labelLarge,
            )
            Switch(
                checked = currentStyle.isBold,
                onCheckedChange = { onStyleChange(currentStyle.copy(isBold = it)) },
            )
        }

        OutlinedButton(
            onClick = { onStyleChange(SubtitleStyle()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.subtitle_reset_default))
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier =
            Modifier
                .size(48.dp)
                .semantics { this.selected = selected },
        shape = CircleShape,
        color = color,
        border =
            BorderStroke(
                width = if (selected) 3.dp else 1.dp,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
            ),
    ) {}
}

private fun sameRgb(
    first: Color,
    second: Color,
): Boolean = (first.toArgb() and 0x00FFFFFF) == (second.toArgb() and 0x00FFFFFF)

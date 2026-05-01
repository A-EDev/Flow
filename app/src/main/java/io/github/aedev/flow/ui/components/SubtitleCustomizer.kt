package io.github.aedev.flow.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SubtitleCustomizer(
    currentStyle: SubtitleStyle,
    onStyleChange: (SubtitleStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    val textColors = remember {
        listOf(
            Color.White,
            Color(0xFFFFF59D),
            Color(0xFF80DEEA),
            Color(0xFFA5D6A7),
            Color(0xFFFFCC80),
            Color(0xFFF8BBD0)
        )
    }
    val backgroundColors = remember {
        listOf(
            Color.Black,
            Color(0xFF1F2937),
            Color(0xFF263238),
            Color(0xFF4E342E),
            Color(0xFF102A43),
            Color(0xFF37474F)
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Subtitle Customization",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )

        // Preview Section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Color.DarkGray.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                color = currentStyle.backgroundColor,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.offset(y = -(currentStyle.bottomPadding * 0.35f).dp)
            ) {
                Text(
                    text = "Preview Subtitle Text",
                    color = currentStyle.textColor,
                    fontSize = currentStyle.fontSize.sp,
                    fontWeight = if (currentStyle.isBold) FontWeight.Bold else FontWeight.Normal,
                    lineHeight = (currentStyle.fontSize * 1.25f).sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Font Size
        Column {
            Text("Font Size: ${currentStyle.fontSize.toInt()}", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = currentStyle.fontSize,
                onValueChange = { onStyleChange(currentStyle.copy(fontSize = it)) },
                valueRange = 12f..32f,
                steps = 10
            )
        }

        // Position
        Column {
            Text("Position: ${currentStyle.bottomPadding.toInt()}", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = currentStyle.bottomPadding,
                onValueChange = { onStyleChange(currentStyle.copy(bottomPadding = it)) },
                valueRange = 24f..180f,
                steps = 11
            )
        }

        // Text Color
        Text("Text Color", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(textColors) { color ->
                ColorSwatch(
                    color = color,
                    selected = sameRgb(currentStyle.textColor, color),
                    onClick = { onStyleChange(currentStyle.copy(textColor = color)) }
                )
            }
        }

        // Background Color
        Text("Background Color", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(backgroundColors) { color ->
                ColorSwatch(
                    color = color.copy(alpha = currentStyle.backgroundColor.alpha),
                    selected = sameRgb(currentStyle.backgroundColor, color),
                    onClick = {
                        onStyleChange(
                            currentStyle.copy(
                                backgroundColor = color.copy(alpha = currentStyle.backgroundColor.alpha)
                            )
                        )
                    }
                )
            }
        }

        // Background Opacity
        Column {
            Text(
                "Background Opacity: ${(currentStyle.backgroundColor.alpha * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = currentStyle.backgroundColor.alpha,
                onValueChange = { onStyleChange(currentStyle.copy(backgroundColor = currentStyle.backgroundColor.copy(alpha = it))) },
                valueRange = 0f..1f
            )
        }

        // Bold toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Bold Text", style = MaterialTheme.typography.labelLarge)
            Switch(
                checked = currentStyle.isBold,
                onCheckedChange = { onStyleChange(currentStyle.copy(isBold = it)) }
            )
        }

        OutlinedButton(
            onClick = { onStyleChange(SubtitleStyle()) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset to Default")
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

private fun sameRgb(first: Color, second: Color): Boolean {
    return (first.toArgb() and 0x00FFFFFF) == (second.toArgb() and 0x00FFFFFF)
}

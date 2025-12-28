package com.flow.youtube.ui.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SubtitleCustomizer(
    currentStyle: SubtitleStyle,
    onStyleChange: (SubtitleStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
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
                .height(100.dp)
                .background(Color.DarkGray.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = currentStyle.backgroundColor,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Preview Subtitle Text",
                    color = currentStyle.textColor,
                    fontSize = currentStyle.fontSize.sp,
                    fontWeight = if (currentStyle.isBold) FontWeight.Bold else FontWeight.Normal,
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

        // Text Color
        val textColors = listOf(Color.White, Color.Yellow, Color.Cyan, Color.Green, Color.Red, Color.LightGray)
        Text("Text Color", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(textColors) { color ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color)
                        .clickable { onStyleChange(currentStyle.copy(textColor = color)) }
                        .padding(4.dp)
                ) {
                    if (currentStyle.textColor == color) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✓", color = Color.White)
                        }
                    }
                }
            }
        }

        // Background Opacity
        Column {
            Text("Background Opacity", style = MaterialTheme.typography.labelLarge)
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
    }
}

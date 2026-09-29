package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class MediaQualitySelectorOption<T>(
    val item: T,
    val height: Int,
    val label: String,
    val selected: Boolean,
    val supportingText: String? = null,
    val codecKey: String = "",
    val codecLabel: String = "",
)

@Composable
fun <T> MediaQualitySelectorContent(
    options: List<MediaQualitySelectorOption<T>>,
    groupedByResolution: Boolean,
    onOptionSelected: (T) -> Unit,
) {
    if (!groupedByResolution) {
        val sorted = options.sortedByDescending { it.height }
        FlowRowGroup {
            sorted.forEachIndexed { index, option ->
                FlowSelectionRow(
                    title = option.label,
                    supportingText = option.supportingText,
                    selected = option.selected,
                    shape = flowRowGroupShape(index, sorted.size),
                    onClick = { onOptionSelected(option.item) },
                )
            }
        }
        return
    }

    val auto = options.firstOrNull { it.height == 0 }
    val resolutions =
        options
            .filter { it.height != 0 }
            .groupBy { it.height }
            .entries
            .sortedByDescending { it.key }
            .map { it.value }
    val rowCount = resolutions.size + if (auto == null) 0 else 1
    FlowRowGroup {
        if (auto != null) {
            FlowSelectionRow(
                title = auto.label,
                selected = auto.selected,
                shape = flowRowGroupShape(0, rowCount),
                onClick = { onOptionSelected(auto.item) },
            )
        }
        resolutions.forEachIndexed { index, group ->
            val rowIndex = index + if (auto == null) 0 else 1
            val codecOptions = group.filter { it.codecKey.isNotBlank() || it.codecLabel.isNotBlank() }
            if (codecOptions.isEmpty()) {
                val option = group.first()
                FlowSelectionRow(
                    title = option.label,
                    supportingText = option.supportingText,
                    selected = option.selected,
                    shape = flowRowGroupShape(rowIndex, rowCount),
                    onClick = { onOptionSelected(option.item) },
                )
            } else {
                MediaQualitySelectorCodecRow(
                    qualityLabel = group.first().resolutionLabel(),
                    codecOptions = codecOptions,
                    shape = flowRowGroupShape(rowIndex, rowCount),
                    onOptionSelected = onOptionSelected,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> MediaQualitySelectorCodecRow(
    qualityLabel: String,
    codecOptions: List<MediaQualitySelectorOption<T>>,
    shape: Shape,
    onOptionSelected: (T) -> Unit,
) {
    val rowSelected = codecOptions.any { it.selected }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = qualityLabel,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (rowSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (rowSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.width(84.dp),
        )
        Spacer(Modifier.width(12.dp))
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            codecOptions.forEach { option ->
                FlowFilterChip(
                    label = option.codecLabel.ifBlank { option.label },
                    selected = option.selected,
                    onClick = { onOptionSelected(option.item) },
                )
            }
        }
    }
}

private fun MediaQualitySelectorOption<*>.resolutionLabel(): String =
    if (codecLabel.isNotBlank() && label.endsWith(" $codecLabel")) {
        label.removeSuffix(" $codecLabel")
    } else {
        label
    }

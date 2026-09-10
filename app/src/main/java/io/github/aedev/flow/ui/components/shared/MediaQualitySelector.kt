package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        options
            .sortedByDescending { it.height }
            .forEach { option ->
                FlowSelectionRow(
                    title = option.label,
                    supportingText = option.supportingText,
                    selected = option.selected,
                    onClick = { onOptionSelected(option.item) },
                )
            }
        return
    }

    options.firstOrNull { it.height == 0 }?.let { auto ->
        FlowSelectionRow(
            title = auto.label,
            selected = auto.selected,
            onClick = { onOptionSelected(auto.item) },
        )
    }
    options
        .filter { it.height != 0 }
        .groupBy { it.height }
        .entries
        .sortedByDescending { it.key }
        .forEach { (_, options) ->
            val codecOptions = options.filter { it.codecKey.isNotBlank() || it.codecLabel.isNotBlank() }
            if (codecOptions.isEmpty()) {
                val option = options.first()
                FlowSelectionRow(
                    title = option.label,
                    supportingText = option.supportingText,
                    selected = option.selected,
                    onClick = { onOptionSelected(option.item) },
                )
            } else {
                MediaQualitySelectorCodecRow(
                    qualityLabel = options.first().resolutionLabel(),
                    codecOptions = codecOptions,
                    onOptionSelected = onOptionSelected,
                )
            }
        }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> MediaQualitySelectorCodecRow(
    qualityLabel: String,
    codecOptions: List<MediaQualitySelectorOption<T>>,
    onOptionSelected: (T) -> Unit,
) {
    val rowSelected = codecOptions.any { it.selected }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
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
                FilterChip(
                    selected = option.selected,
                    onClick = { onOptionSelected(option.item) },
                    label = { Text(option.codecLabel.ifBlank { option.label }) },
                    leadingIcon =
                        if (option.selected) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            }
                        } else {
                            null
                        },
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

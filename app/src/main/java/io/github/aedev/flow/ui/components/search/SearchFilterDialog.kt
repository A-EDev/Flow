package io.github.aedev.flow.ui.components.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.ContentType
import io.github.aedev.flow.data.local.Duration
import io.github.aedev.flow.data.local.SearchFeature
import io.github.aedev.flow.data.local.SearchFilter
import io.github.aedev.flow.data.local.SortType
import io.github.aedev.flow.data.local.UploadDate
import io.github.aedev.flow.ui.components.shared.FlowFilterChip

/**
 * Search filters, laid out the way YouTube's own dialog is: one row per single-choice group with
 * the current value on the right, then the multi-choice features as chips, then Cancel and Apply.
 *
 * Choices are held locally until Apply, so a half-made selection never restarts the pager.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchFilterDialog(
    filter: SearchFilter,
    shortsEnabled: Boolean,
    onApply: (SearchFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(filter) { mutableStateOf(filter) }
    val videoFilters = draft.contentType != ContentType.CHANNELS && draft.contentType != ContentType.PLAYLISTS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_filters_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(RowSpacing),
            ) {
                FilterRow(
                    label = stringResource(R.string.search_filter_group_type),
                    value = stringResource(draft.contentType.labelRes()),
                    options = CHIP_TYPES.filterNot { it == ContentType.SHORTS && !shortsEnabled },
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelect = { draft = draft.copy(contentType = it) },
                )

                if (videoFilters) {
                    FilterRow(
                        label = stringResource(R.string.search_filter_group_duration),
                        value = stringResource(draft.duration.labelRes()),
                        options = Duration.entries,
                        optionLabel = { stringResource(it.labelRes()) },
                        onSelect = { draft = draft.copy(duration = it) },
                    )
                    FilterRow(
                        label = stringResource(R.string.search_filter_group_upload_date),
                        value = stringResource(draft.uploadDate.labelRes()),
                        options = UploadDate.entries,
                        optionLabel = { stringResource(it.labelRes()) },
                        onSelect = { draft = draft.copy(uploadDate = it) },
                    )
                }

                FilterRow(
                    label = stringResource(R.string.search_filter_group_sort),
                    value = stringResource(draft.sortType.labelRes()),
                    options = SortType.entries,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelect = { draft = draft.copy(sortType = it) },
                )

                if (videoFilters) {
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.search_filter_group_features),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
                        verticalArrangement = Arrangement.spacedBy(ChipSpacing),
                    ) {
                        SearchFeature.entries.forEach { feature ->
                            val selected = feature in draft.features
                            FlowFilterChip(
                                label = stringResource(feature.labelRes()),
                                selected = selected,
                                onClick = {
                                    draft =
                                        draft.copy(
                                            features =
                                                if (selected) {
                                                    draft.features - feature
                                                } else {
                                                    draft.features + feature
                                                },
                                        )
                                },
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        confirmButton = {
            TextButton(onClick = { onApply(draft) }) { Text(stringResource(R.string.search_filters_apply)) }
        },
    )
}

@Composable
private fun <T> FilterRow(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        androidx.compose.foundation.layout.Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(vertical = RowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(DropdownIconSize),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                val text = optionLabel(option)
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

internal fun Duration.labelRes(): Int =
    when (this) {
        Duration.ANY -> R.string.duration_any
        Duration.UNDER_3_MINUTES -> R.string.duration_under_3
        Duration.THREE_TO_20_MINUTES -> R.string.duration_3_20
        Duration.OVER_20_MINUTES -> R.string.duration_over_20
    }

internal fun UploadDate.labelRes(): Int =
    when (this) {
        UploadDate.ANY -> R.string.date_any
        UploadDate.LAST_HOUR -> R.string.date_last_hour
        UploadDate.TODAY -> R.string.date_today
        UploadDate.THIS_WEEK -> R.string.date_this_week
        UploadDate.THIS_MONTH -> R.string.date_this_month
        UploadDate.THIS_YEAR -> R.string.date_this_year
    }

internal fun SortType.labelRes(): Int =
    when (this) {
        SortType.RELEVANCE -> R.string.sort_relevance
        SortType.VIEW_COUNT -> R.string.sort_view_count
    }

internal fun SearchFeature.labelRes(): Int =
    when (this) {
        SearchFeature.HD -> R.string.search_feature_hd
        SearchFeature.FOUR_K -> R.string.search_feature_4k
        SearchFeature.HDR -> R.string.search_feature_hdr
        SearchFeature.SUBTITLES -> R.string.search_feature_subtitles
        SearchFeature.CREATIVE_COMMONS -> R.string.search_feature_creative_commons
        SearchFeature.THREE_SIXTY -> R.string.search_feature_360
        SearchFeature.VR180 -> R.string.search_feature_vr180
        SearchFeature.THREE_D -> R.string.search_feature_3d
        SearchFeature.LOCATION -> R.string.search_feature_location
        SearchFeature.PURCHASED -> R.string.search_feature_purchased
    }

private val CHIP_TYPES = ContentType.entries

private val RowSpacing = 4.dp
private val RowVerticalPadding = 10.dp
private val ChipSpacing = 8.dp
private val DropdownIconSize = 24.dp

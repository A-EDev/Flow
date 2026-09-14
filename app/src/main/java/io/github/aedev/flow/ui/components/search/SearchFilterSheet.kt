package io.github.aedev.flow.ui.components.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import io.github.aedev.flow.ui.components.shared.FlowBottomSheet
import io.github.aedev.flow.ui.components.shared.FlowFilterChip
import io.github.aedev.flow.ui.components.shared.FlowSheetHeader

/**
 * The five groups YouTube's own filter dialog offers — type, duration, upload date, features and
 * sort — applied together as one `params` token.
 *
 * Choices are held locally until Apply, so a half-made selection never restarts the pager.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchFilterSheet(
    filter: SearchFilter,
    shortsEnabled: Boolean,
    onApply: (SearchFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(filter) { mutableStateOf(filter) }
    val videoFilters = draft.contentType != ContentType.CHANNELS && draft.contentType != ContentType.PLAYLISTS

    FlowBottomSheet(
        onDismiss = onDismiss,
        header = { FlowSheetHeader(title = stringResource(R.string.search_filters_title), onClose = onDismiss) },
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = SheetBottomPadding),
            verticalArrangement = Arrangement.spacedBy(GroupSpacing),
        ) {
            FilterGroup(stringResource(R.string.search_filter_group_type)) {
                ContentType.entries
                    .filterNot { it == ContentType.SHORTS && !shortsEnabled }
                    .forEach { type ->
                        FlowFilterChip(
                            label = stringResource(type.labelRes()),
                            selected = draft.contentType == type,
                            onClick = { draft = draft.copy(contentType = type) },
                        )
                    }
            }

            if (videoFilters) {
                FilterGroup(stringResource(R.string.search_filter_group_duration)) {
                    Duration.entries.forEach { duration ->
                        FlowFilterChip(
                            label = stringResource(duration.labelRes()),
                            selected = draft.duration == duration,
                            onClick = { draft = draft.copy(duration = duration) },
                        )
                    }
                }

                FilterGroup(stringResource(R.string.search_filter_group_upload_date)) {
                    UploadDate.entries.forEach { date ->
                        FlowFilterChip(
                            label = stringResource(date.labelRes()),
                            selected = draft.uploadDate == date,
                            onClick = { draft = draft.copy(uploadDate = date) },
                        )
                    }
                }

                FilterGroup(stringResource(R.string.search_filter_group_features)) {
                    SearchFeature.entries.forEach { feature ->
                        val selected = feature in draft.features
                        FlowFilterChip(
                            label = stringResource(feature.labelRes()),
                            selected = selected,
                            onClick = {
                                draft =
                                    draft.copy(
                                        features =
                                            if (selected) draft.features - feature else draft.features + feature,
                                    )
                            },
                        )
                    }
                }
            }

            FilterGroup(stringResource(R.string.search_filter_group_sort)) {
                SortType.entries.forEach { sort ->
                    FlowFilterChip(
                        label = stringResource(sort.labelRes()),
                        selected = draft.sortType == sort,
                        onClick = { draft = draft.copy(sortType = sort) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = GroupHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(ActionSpacing, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { draft = SearchFilter.DEFAULT }, enabled = !draft.isDefault) {
                    Text(stringResource(R.string.search_filters_reset))
                }
                Button(onClick = { onApply(draft) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.search_filters_apply))
                }
            }
        }
    }
}

@Composable
private fun FilterGroup(
    title: String,
    options: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(GroupTitleSpacing)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = GroupHorizontalPadding),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = GroupHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
            ) {
                options()
            }
        }
    }
}

private fun Duration.labelRes(): Int =
    when (this) {
        Duration.ANY -> R.string.duration_any
        Duration.UNDER_3_MINUTES -> R.string.duration_under_3
        Duration.THREE_TO_20_MINUTES -> R.string.duration_3_20
        Duration.OVER_20_MINUTES -> R.string.duration_over_20
    }

private fun UploadDate.labelRes(): Int =
    when (this) {
        UploadDate.ANY -> R.string.date_any
        UploadDate.LAST_HOUR -> R.string.date_last_hour
        UploadDate.TODAY -> R.string.date_today
        UploadDate.THIS_WEEK -> R.string.date_this_week
        UploadDate.THIS_MONTH -> R.string.date_this_month
        UploadDate.THIS_YEAR -> R.string.date_this_year
    }

private fun SortType.labelRes(): Int =
    when (this) {
        SortType.RELEVANCE -> R.string.sort_relevance
        SortType.VIEW_COUNT -> R.string.sort_view_count
    }

private fun SearchFeature.labelRes(): Int =
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

private val GroupSpacing = 20.dp
private val GroupTitleSpacing = 8.dp
private val GroupHorizontalPadding = 20.dp
private val ChipSpacing = 8.dp
private val ActionSpacing = 8.dp
private val SheetBottomPadding = 24.dp

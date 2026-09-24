package io.github.aedev.flow.ui.components.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar

/**
 * The playlist's bar: its name once the header scrolls away, and Select. In select mode the bar
 * counts the selection and back leaves it; the actions float at the bottom.
 */
@Composable
internal fun PlaylistDetailTopBar(
    title: String,
    showTitle: Boolean,
    inSelectionMode: Boolean,
    selectedCount: Int,
    allSelected: Boolean,
    canSelect: Boolean,
    onNavigateBack: () -> Unit,
    onEnterSelection: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
) {
    FlowTopBar(
        title =
            when {
                inSelectionMode -> pluralStringResource(R.plurals.selected_count_template, selectedCount, selectedCount)
                showTitle -> title
                else -> ""
            },
        onBack = if (inSelectionMode) onClearSelection else onNavigateBack,
        actions = {
            if (inSelectionMode) {
                IconButton(onClick = onSelectAll) {
                    Icon(
                        imageVector = if (allSelected) Icons.Outlined.CheckBox else Icons.Default.SelectAll,
                        contentDescription = stringResource(if (allSelected) R.string.deselect_all else R.string.select_all),
                    )
                }
            } else if (canSelect) {
                IconButton(onClick = onEnterSelection) {
                    Icon(imageVector = Icons.Default.Checklist, contentDescription = stringResource(R.string.select_videos))
                }
            }
        },
    )
}

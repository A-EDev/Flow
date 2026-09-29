package io.github.aedev.flow.ui.components.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.layout.topbar.FlowSearchTopBar
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar

/** The playlist's search: the query while the field is open, or null while it is closed. */
internal class PlaylistSearchBar(
    val query: String?,
    val onOpen: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onClose: () -> Unit,
)

/**
 * The playlist's bar: its name once the header scrolls away, Search and Select. In select mode the
 * bar counts the selection and back leaves it; the actions float at the bottom. While searching the
 * bar is the search field, and back closes it.
 */
@Composable
internal fun PlaylistDetailTopBar(
    title: String,
    showTitle: Boolean,
    inSelectionMode: Boolean,
    selectedCount: Int,
    allSelected: Boolean,
    canSelect: Boolean,
    search: PlaylistSearchBar,
    onNavigateBack: () -> Unit,
    onEnterSelection: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
) {
    if (search.query != null && !inSelectionMode) {
        FlowSearchTopBar(
            query = search.query,
            onQueryChange = search.onQueryChange,
            onClose = search.onClose,
            placeholder = stringResource(R.string.search_in_playlist),
            releaseFocusWithKeyboard = true,
        )
        return
    }
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
                return@FlowTopBar
            }
            IconButton(onClick = search.onOpen) {
                Icon(imageVector = Icons.Outlined.Search, contentDescription = stringResource(R.string.search_in_playlist))
            }
            if (canSelect) {
                IconButton(onClick = onEnterSelection) {
                    Icon(imageVector = Icons.Default.Checklist, contentDescription = stringResource(R.string.select_videos))
                }
            }
        },
    )
}

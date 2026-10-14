package io.github.aedev.flow.ui.screens.music.collection

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.layout.topbar.FlowSearchTopBar
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar

/** What the top bar is doing: showing the page, searching it, or counting a selection. */
internal class CollectionTopBarState(
    val title: String,
    val searchQuery: String?,
    val selectedCount: Int?,
    val allSelected: Boolean,
    val hasSongs: Boolean,
)

internal class CollectionTopBarActions(
    val onBack: () -> Unit,
    val onOpenSearch: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onCloseSearch: () -> Unit,
    val onSelect: () -> Unit,
    val onClearSelection: () -> Unit,
    val onSelectAll: () -> Unit,
)

/**
 * The page's bar: its name once the header scrolls away, Search and Select. While searching the
 * bar is the field and back closes it; in select mode it counts the selection and back leaves it.
 */
@Composable
internal fun MusicCollectionTopBar(
    state: CollectionTopBarState,
    actions: CollectionTopBarActions,
) {
    val selected = state.selectedCount
    if (state.searchQuery != null && selected == null) {
        FlowSearchTopBar(
            query = state.searchQuery,
            onQueryChange = actions.onQueryChange,
            onClose = actions.onCloseSearch,
            placeholder = stringResource(R.string.search_in_playlist),
            releaseFocusWithKeyboard = true,
        )
        return
    }
    FlowTopBar(
        title = if (selected != null) pluralStringResource(R.plurals.selected_count_template, selected, selected) else state.title,
        onBack = if (selected != null) actions.onClearSelection else actions.onBack,
        actions = {
            if (selected != null) {
                IconButton(onClick = actions.onSelectAll) {
                    Icon(
                        imageVector = if (state.allSelected) Icons.Rounded.CheckBox else Icons.Rounded.SelectAll,
                        contentDescription = stringResource(if (state.allSelected) R.string.deselect_all else R.string.select_all),
                    )
                }
                return@FlowTopBar
            }
            if (state.hasSongs) {
                IconButton(onClick = actions.onOpenSearch) {
                    Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.search_in_playlist))
                }
                IconButton(onClick = actions.onSelect) {
                    Icon(Icons.Rounded.Checklist, contentDescription = stringResource(R.string.select_songs))
                }
            }
        },
    )
}

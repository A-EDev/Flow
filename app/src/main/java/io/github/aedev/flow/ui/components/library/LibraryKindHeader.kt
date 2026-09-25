package io.github.aedev.flow.ui.components.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.components.shared.FlowConnectedToggleGroup
import io.github.aedev.flow.ui.components.shared.FlowSearchField
import io.github.aedev.flow.ui.components.shared.FlowToggleOption
import io.github.aedev.flow.ui.components.shared.MediaKind
import io.github.aedev.flow.ui.utils.LocalWindowSizeClass
import io.github.aedev.flow.ui.utils.isMediumWidth

private val KindSwitchWidth = 320.dp

/** Whether [LibraryKindHeader] lays out as one row, so the screen can move its sort chip into it. */
@Composable
internal fun libraryHeaderIsOneRow(): Boolean = LocalWindowSizeClass.current.isMediumWidth

/**
 * Search and the Videos/Music switch at the top of Downloads and Local media. A phone stacks them;
 * from medium width up they share one row with [trailing] at its end, on the same edges as the list.
 */
@Composable
internal fun LibraryKindHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    selectedKind: MediaKind,
    onKindSelected: (MediaKind) -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val options = MediaKind.entries.map { FlowToggleOption(it, stringResource(it.labelRes), it.icon) }
    val search: @Composable (Modifier) -> Unit = { fieldModifier ->
        FlowSearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = placeholder,
            onClear = { onQueryChange("") },
            modifier = fieldModifier,
            onSearch = { focusManager.clearFocus() },
            releaseFocusWithKeyboard = true,
        )
    }
    if (libraryHeaderIsOneRow()) {
        Row(
            modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            search(Modifier.weight(1f))
            FlowConnectedToggleGroup(options, selectedKind, onKindSelected, Modifier.width(KindSwitchWidth))
            trailing?.invoke()
        }
    } else {
        Column(modifier) {
            search(Modifier.padding(horizontal = 16.dp).fillMaxWidth())
            FlowConnectedToggleGroup(options, selectedKind, onKindSelected, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}

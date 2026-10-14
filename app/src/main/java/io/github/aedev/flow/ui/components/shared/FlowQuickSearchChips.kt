package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val ChipSpacing = 8.dp

/** Ready-made searches as chips; tapping one fills the search field, and the one in it is shown selected. */
@Composable
fun FlowQuickSearchChips(
    searches: List<String>,
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (searches.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
    ) {
        searches.forEach { search ->
            SuggestionChip(
                onClick = { onQueryChange(search) },
                label = { Text(search) },
                icon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(SuggestionChipDefaults.IconSize),
                    )
                },
                colors =
                    if (query == search) {
                        SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    } else {
                        SuggestionChipDefaults.suggestionChipColors()
                    },
            )
        }
    }
}

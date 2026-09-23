package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.utils.foldForSearch

/** One option of a [FlowChoiceDialog]. */
@Immutable
data class FlowChoice<T>(
    val value: T,
    val label: String,
    val supportingText: String? = null,
)

private const val SEARCHABLE_THRESHOLD = 12
private val DefaultListMaxHeight = 400.dp
private val SearchSpacing = 8.dp
private val DescriptionSpacing = 12.dp

/**
 * The single-choice dialog for every setting that picks one value out of a list. Picking an option
 * applies it and closes the dialog, so there is no confirm button — only a way out.
 *
 * Long lists (languages, countries) get a search field; [footer] carries anything that belongs to
 * the choice itself, such as the interval slider under an "every N seconds" option.
 */
@Composable
fun <T> FlowChoiceDialog(
    title: String,
    options: List<FlowChoice<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    searchable: Boolean = options.size > SEARCHABLE_THRESHOLD,
    dismissOnSelect: Boolean = true,
    listMaxHeight: Dp = DefaultListMaxHeight,
    footer: (@Composable () -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visible =
        remember(query, options) {
            if (query.isBlank()) options else options.filter { it.matches(query) }
        }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val index = options.indexOfFirst { it.value == selected }
        if (index > 0) listState.scrollToItem(index)
    }

    FlowAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = icon?.let { { Icon(imageVector = it, contentDescription = null) } },
        title = { Text(text = title) },
        text = {
            Column {
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(DescriptionSpacing))
                }
                if (searchable) {
                    FlowSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = stringResource(R.string.search_hint),
                        onClear = { query = "" },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    )
                    Spacer(Modifier.height(SearchSpacing))
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = listMaxHeight),
                ) {
                    items(visible, key = { it.value.toString() }) { option ->
                        FlowSelectionRow(
                            title = option.label,
                            supportingText = option.supportingText,
                            selected = option.value == selected,
                            showSelectedContainer = false,
                            onClick = {
                                onSelect(option.value)
                                if (dismissOnSelect) onDismiss()
                            },
                        )
                    }
                }
                footer?.invoke()
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(if (dismissOnSelect) R.string.cancel else R.string.close))
            }
        },
    )
}

private fun FlowChoice<*>.matches(query: String): Boolean {
    val needle = query.foldForSearch()
    return label.foldForSearch().contains(needle) ||
        supportingText?.foldForSearch()?.contains(needle) == true ||
        value.toString().foldForSearch().contains(needle)
}

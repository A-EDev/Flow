package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.aedev.flow.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
    onClear: (() -> Unit)? = null,
    expanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
    focusRequester: FocusRequester? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val showClear = onClear != null && query.isNotEmpty()
    SearchBarDefaults.InputField(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = { onSearch() },
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        placeholder = {
            Text(
                text = placeholder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = leadingIcon,
        trailingIcon =
            if (showClear || trailingContent != null) {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showClear) {
                            IconButton(onClick = { onClear?.invoke() }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.clear),
                                )
                            }
                        }
                        trailingContent?.invoke(this)
                    }
                }
            } else {
                null
            },
        modifier = focusRequester?.let { modifier.focusRequester(it) } ?: modifier,
    )
}

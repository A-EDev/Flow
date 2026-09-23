package io.github.aedev.flow.ui.screens.settings.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowAlertDialog

private const val ICON_COLUMNS = 3
private val IconGridMaxHeight = 320.dp
private val IconGridSpacing = 8.dp
private val IconSize = 56.dp
private val IconCellPadding = 8.dp
private val IconLabelSpacing = 6.dp
private val WarningSpacing = 16.dp

/**
 * Picks the launcher icon. The grid scrolls inside a dialog of fixed height rather than growing to
 * the screen, and the choice is only applied on Apply: switching aliases can remove the app's
 * home-screen shortcuts, so it should never happen on a stray tap.
 */
@Composable
internal fun AppIconDialog(
    selected: String,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var staged by rememberSaveable(selected) { mutableStateOf(selected) }

    FlowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_item_app_icon)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.app_icon_picker_warning),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(WarningSpacing))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(ICON_COLUMNS),
                    modifier = Modifier.heightIn(max = IconGridMaxHeight),
                    horizontalArrangement = Arrangement.spacedBy(IconGridSpacing),
                    verticalArrangement = Arrangement.spacedBy(IconGridSpacing),
                ) {
                    items(appIconOptions, key = { it.suffix }) { option ->
                        AppIconCell(
                            option = option,
                            selected = option.suffix == staged,
                            onClick = { staged = option.suffix },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = staged != selected,
                onClick = {
                    onApply(staged)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.settings_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun AppIconCell(
    option: AppIconOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(if (selected) colors.secondaryContainer else Color.Transparent)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(IconCellPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(IconLabelSpacing),
    ) {
        AsyncImage(
            model = option.iconRes,
            contentDescription = null,
            modifier =
                Modifier
                    .size(IconSize)
                    .clip(CircleShape),
        )
        Text(
            text = stringResource(option.nameRes),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.onSecondaryContainer else colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

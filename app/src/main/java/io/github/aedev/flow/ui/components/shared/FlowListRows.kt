package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val RowHorizontalPadding = 20.dp
private val SelectionRowVerticalPadding = 16.dp
private val NavRowVerticalPadding = 14.dp
private val SwitchRowVerticalPadding = 6.dp
private val RowLeadingIconSize = 22.dp
private val NavRowTrailingIconSize = 20.dp
private val NavRowTrailingSpacing = 2.dp
private val SectionHeaderPadding =
    PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp)

private const val SELECTED_CONTAINER_ALPHA = 0.14f
private const val DISABLED_CONTENT_ALPHA = 0.4f

/**
 * A single-choice row: the container tint marks the choice, and [Role.RadioButton] is what tells a
 * screen reader this is one option out of a set rather than a plain button.
 */
@Composable
fun FlowSelectionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    leadingIcon: ImageVector? = null,
    showSelectedContainer: Boolean = true,
) {
    val supporting = rowSupportingContent(supportingText)
    val leading = rowLeadingContent(leadingIcon)
    val trailing: (@Composable () -> Unit)? =
        if (selected) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                )
            }
        } else {
            null
        }

    ListItem(
        modifier =
            modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = onClick,
                ),
        supportingContent = supporting,
        leadingContent = leading,
        trailingContent = trailing,
        shapes = ListItemDefaults.shapes(shape = RectangleShape),
        colors =
            ListItemDefaults.colors(
                containerColor =
                    if (selected && showSelectedContainer) {
                        MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_CONTAINER_ALPHA)
                    } else {
                        Color.Transparent
                    },
                contentColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                leadingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                trailingContentColor = MaterialTheme.colorScheme.primary,
                supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        contentPadding =
            PaddingValues(
                horizontal = RowHorizontalPadding,
                vertical = SelectionRowVerticalPadding,
            ),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** A row that opens another page: an optional current value, then a chevron. */
@Composable
fun FlowNavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    leadingIcon: ImageVector? = null,
    trailingText: String? = null,
) {
    val supporting = rowSupportingContent(supportingText)
    val leading = rowLeadingContent(leadingIcon)

    ListItem(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        supportingContent = supporting,
        leadingContent = leading,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!trailingText.isNullOrBlank()) {
                    Text(
                        text = trailingText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.width(NavRowTrailingSpacing))
                }
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(NavRowTrailingIconSize),
                )
            }
        },
        shapes = ListItemDefaults.shapes(shape = RectangleShape),
        colors =
            ListItemDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                leadingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                trailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        contentPadding =
            PaddingValues(
                horizontal = RowHorizontalPadding,
                vertical = NavRowVerticalPadding,
            ),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/**
 * A row whose whole width toggles its trailing [Switch]. The switch itself is not clickable so the
 * row is a single [Role.Switch] target instead of two competing ones.
 */
@Composable
fun FlowSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val supporting = rowSupportingContent(supportingText)
    val leading = rowLeadingContent(leadingIcon)

    ListItem(
        modifier =
            modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                ),
        enabled = enabled,
        supportingContent = supporting,
        leadingContent = leading,
        trailingContent = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = null,
            )
        },
        shapes = ListItemDefaults.shapes(shape = RectangleShape),
        colors =
            ListItemDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                leadingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                trailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledContainerColor = Color.Transparent,
                disabledContentColor =
                    MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_CONTENT_ALPHA),
                disabledLeadingContentColor =
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_CONTENT_ALPHA),
                disabledTrailingContentColor =
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_CONTENT_ALPHA),
                disabledSupportingContentColor =
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_CONTENT_ALPHA),
            ),
        contentPadding =
            PaddingValues(
                horizontal = RowHorizontalPadding,
                vertical = SwitchRowVerticalPadding,
            ),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** The section label above a group of [FlowSelectionRow]/[FlowNavRow]/[FlowSwitchRow]s. */
@Composable
fun FlowSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(SectionHeaderPadding),
    )
}

private fun rowSupportingContent(supportingText: String?): (@Composable () -> Unit)? =
    supportingText?.let { text ->
        {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

private fun rowLeadingContent(leadingIcon: ImageVector?): (@Composable () -> Unit)? =
    leadingIcon?.let { icon ->
        {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(RowLeadingIconSize),
            )
        }
    }

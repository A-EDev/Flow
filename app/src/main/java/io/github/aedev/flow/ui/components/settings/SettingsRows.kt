package io.github.aedev.flow.ui.components.settings

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.ui.components.shared.FlowNavRow
import io.github.aedev.flow.ui.components.shared.FlowSelectionRow
import io.github.aedev.flow.ui.components.shared.FlowSwitchRow
import io.github.aedev.flow.ui.components.shared.FlowToggleOption

/**
 * A switch for [entry]. [summary] replaces the entry's own summary when the row needs to say
 * something about the current state.
 */
fun SettingsGroupScope.switch(
    entry: SettingEntry,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    summary: String? = null,
    icon: ImageVector? = null,
    @DrawableRes iconRes: Int? = null,
) = row(entry.key) { shape ->
    FlowSwitchRow(
        title = stringResource(entry.title),
        supportingText = summary ?: entry.summaryText(),
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        leadingIcon = icon,
        leadingPainter = iconRes?.let { painterResource(it) },
        shape = shape,
    )
}

/**
 * A row that opens a page, dialog or sheet for [entry]. A [value] is shown under the title — the
 * current choice of a setting that opens a picker — and otherwise the entry's summary.
 */
fun SettingsGroupScope.nav(
    entry: SettingEntry,
    onClick: () -> Unit,
    value: String? = null,
    enabled: Boolean = true,
    showChevron: Boolean = true,
    selected: Boolean = false,
    icon: ImageVector? = null,
    @DrawableRes iconRes: Int? = null,
) = row(entry.key) { shape ->
    FlowNavRow(
        title = stringResource(entry.title),
        supportingText = value ?: entry.summaryText(),
        onClick = onClick,
        enabled = enabled,
        showChevron = showChevron,
        selected = selected,
        leadingIcon = icon,
        leadingPainter = iconRes?.let { painterResource(it) },
        shape = shape,
    )
}

/** One option of a single-choice list drawn inline on a page rather than in a dialog. */
fun SettingsGroupScope.option(
    key: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    supportingText: String? = null,
    enabled: Boolean = true,
) = row(key) { shape ->
    FlowSelectionRow(
        title = label,
        supportingText = supportingText,
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        shape = shape,
    )
}

/** A choice between two to four values, answered in place with a connected toggle group. */
fun <T> SettingsGroupScope.toggleGroup(
    entry: SettingEntry,
    options: List<FlowToggleOption<T>>,
    selected: T,
    onSelected: (T) -> Unit,
    enabled: Boolean = true,
    summary: String? = null,
) = row(entry.key) { shape ->
    SettingsToggleGroupRow(
        title = stringResource(entry.title),
        summary = summary ?: entry.summaryText(),
        options = options,
        selected = selected,
        onSelected = onSelected,
        enabled = enabled,
        shape = shape,
    )
}

/**
 * A numeric setting on a slider. The value is only written when the drag ends, so a DataStore
 * write never runs per frame.
 */
fun SettingsGroupScope.slider(
    entry: SettingEntry,
    value: Float,
    onValueCommitted: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: @Composable (Float) -> String,
    steps: Int = 0,
    enabled: Boolean = true,
    summary: String? = null,
) = row(entry.key) { shape ->
    SettingsSliderRow(
        title = stringResource(entry.title),
        summary = summary ?: entry.summaryText(),
        value = value,
        onValueCommitted = onValueCommitted,
        valueRange = valueRange,
        steps = steps,
        valueLabel = valueLabel,
        enabled = enabled,
        shape = shape,
    )
}

/** A notice between groups; see [SettingsNotice]. */
fun SettingsListScope.notice(
    key: String,
    text: @Composable () -> String,
    icon: ImageVector? = null,
    title: (@Composable () -> String)? = null,
    isWarning: Boolean = false,
) = item(key) {
    SettingsNotice(text = text(), icon = icon, title = title?.invoke(), isWarning = isWarning)
}

@Composable
private fun SettingEntry.summaryText(): String? = summary?.let { stringResource(it) }

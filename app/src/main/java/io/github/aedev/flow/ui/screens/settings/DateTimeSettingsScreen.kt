package io.github.aedev.flow.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.utils.DateContextMode
import io.github.aedev.flow.utils.DateDisplayMode
import io.github.aedev.flow.utils.DateDisplaySettings
import io.github.aedev.flow.utils.DateFormatStyle
import io.github.aedev.flow.utils.formatExactDate
import io.github.aedev.flow.utils.DateContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeSettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PlayerPreferences(context) }

    val globalMode by prefs.dateDisplayMode.collectAsState(initial = DateDisplayMode.RELATIVE)
    val formatStyle by prefs.dateFormatStyle.collectAsState(initial = DateFormatStyle.SYSTEM)
    val listsMode by prefs.dateModeLists.collectAsState(initial = DateContextMode.DEFAULT)
    val watchMode by prefs.dateModeWatch.collectAsState(initial = DateContextMode.DEFAULT)
    val descriptionMode by prefs.dateModeDescription.collectAsState(initial = DateContextMode.DEFAULT)

    val sampleTimestamp = remember { System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 215 }
    val sampleRelative = stringResource(R.string.datetime_sample_relative)

    val settings = DateDisplaySettings(globalMode, formatStyle, listsMode, watchMode, descriptionMode)

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.btn_back))
                    }
                    Text(
                        text = stringResource(R.string.datetime_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Live preview ──
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.datetime_preview_header),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.height(8.dp))
                        PreviewLine(stringResource(R.string.datetime_context_lists), settings.format(sampleRelative, DateContext.LISTS, sampleTimestamp))
                        PreviewLine(stringResource(R.string.datetime_context_watch), settings.format(sampleRelative, DateContext.WATCH, sampleTimestamp))
                        PreviewLine(stringResource(R.string.datetime_context_description), settings.format(sampleRelative, DateContext.DESCRIPTION, sampleTimestamp))
                    }
                }
            }

            item { SectionHeader(text = stringResource(R.string.datetime_mode_header)) }
            item {
                SettingsGroup {
                    val modes = listOf(
                        DateDisplayMode.RELATIVE to R.string.datetime_mode_relative,
                        DateDisplayMode.EXACT to R.string.datetime_mode_exact,
                        DateDisplayMode.BOTH to R.string.datetime_mode_both,
                    )
                    modes.forEachIndexed { index, (mode, labelRes) ->
                        DateRadioRow(
                            title = stringResource(labelRes),
                            selected = globalMode == mode,
                            onClick = { scope.launch { prefs.setDateDisplayMode(mode) } }
                        )
                        if (index < modes.size - 1) RowDivider()
                    }
                }
            }

            item { SectionHeader(text = stringResource(R.string.datetime_format_header)) }
            item {
                SettingsGroup {
                    val styles = DateFormatStyle.entries.toList()
                    styles.forEachIndexed { index, style ->
                        val sample = formatExactDate(sampleTimestamp, style)
                        val title = if (style == DateFormatStyle.SYSTEM) stringResource(R.string.datetime_format_system) else sample
                        val subtitle = when (style) {
                            DateFormatStyle.SYSTEM -> sample
                            DateFormatStyle.ISO -> stringResource(R.string.datetime_format_iso_hint)
                            else -> null
                        }
                        DateRadioRow(
                            title = title,
                            subtitle = subtitle,
                            selected = formatStyle == style,
                            onClick = { scope.launch { prefs.setDateFormatStyle(style) } }
                        )
                        if (index < styles.size - 1) RowDivider()
                    }
                }
            }

            item { SectionHeader(text = stringResource(R.string.datetime_overrides_header)) }
            item {
                Text(
                    text = stringResource(R.string.datetime_overrides_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            item {
                SettingsGroup {
                    OverrideRow(
                        label = stringResource(R.string.datetime_context_lists),
                        value = listsMode,
                        onSelect = { scope.launch { prefs.setDateModeLists(it) } }
                    )
                    RowDivider()
                    OverrideRow(
                        label = stringResource(R.string.datetime_context_watch),
                        value = watchMode,
                        onSelect = { scope.launch { prefs.setDateModeWatch(it) } }
                    )
                    RowDivider()
                    OverrideRow(
                        label = stringResource(R.string.datetime_context_description),
                        value = descriptionMode,
                        onSelect = { scope.launch { prefs.setDateModeDescription(it) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun DateRadioRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverrideRow(
    label: String,
    value: DateContextMode,
    onSelect: (DateContextMode) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        val options = listOf(
            DateContextMode.DEFAULT to R.string.datetime_override_default,
            DateContextMode.RELATIVE to R.string.datetime_override_relative,
            DateContextMode.EXACT to R.string.datetime_override_exact,
            DateContextMode.BOTH to R.string.datetime_override_both,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (mode, labelRes) ->
                SegmentedButton(
                    selected = value == mode,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    label = { Text(stringResource(labelRes), maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
}

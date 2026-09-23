package io.github.aedev.flow.ui.screens.settings.history

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AutoDelete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.ui.components.settings.choice
import io.github.aedev.flow.ui.components.settings.nav
import io.github.aedev.flow.ui.components.settings.switch
import io.github.aedev.flow.ui.components.shared.FlowAlertDialog
import io.github.aedev.flow.ui.components.shared.FlowChoice
import io.github.aedev.flow.ui.components.shared.FlowChoiceDialog
import io.github.aedev.flow.ui.screens.settings.index.HistoryIndex

private enum class HistoryDialog { MAX_SIZE, RETENTION, CLEAR }

private val HistorySizes = listOf(25, 50, 100, 200, 500)
private val RetentionPeriods =
    listOf(
        7 to R.string.period_one_week,
        30 to R.string.period_one_month,
        90 to R.string.period_three_months,
        180 to R.string.period_six_months,
        365 to R.string.period_one_year,
    )

/** What Flow remembers about your searches, for how long, and a way to forget it all. */
@Composable
internal fun HistorySettingsScreen(
    onBack: (() -> Unit)?,
    highlight: String?,
    viewModel: HistorySettingsViewModel = hiltViewModel(),
) {
    val historyEnabled by viewModel.historyEnabled.collectAsStateWithLifecycle()
    val maxSize by viewModel.maxSize.collectAsStateWithLifecycle()
    val autoDelete by viewModel.autoDelete.collectAsStateWithLifecycle()
    val retentionDays by viewModel.retentionDays.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<HistoryDialog?>(null) }

    val autoDeleteSummary =
        if (autoDelete) {
            pluralStringResource(R.plurals.delete_after_days_template, retentionDays, retentionDays)
        } else {
            stringResource(R.string.never_delete_automatically)
        }

    SettingsPage(
        title = stringResource(R.string.settings_history_title),
        onBack = onBack,
        highlight = highlight,
    ) {
        group(key = "history.search", header = R.string.search_history_title) {
            switch(HistoryIndex.save, historyEnabled, viewModel::setHistoryEnabled, icon = Icons.Outlined.History)
            switch(
                HistoryIndex.suggestions,
                viewModel.suggestionsEnabled,
                viewModel::setSuggestionsEnabled,
                icon = Icons.AutoMirrored.Outlined.TrendingUp,
            )
            choice(
                HistoryIndex.maxSize,
                onClick = { dialog = HistoryDialog.MAX_SIZE },
                enabled = historyEnabled,
                icon = Icons.Outlined.Storage,
            ) {
                pluralStringResource(R.plurals.currently_searches_template, maxSize, maxSize)
            }
        }
        group(key = "history.cleanup", header = R.string.auto_delete_history_title) {
            switch(
                HistoryIndex.autoDelete,
                autoDelete,
                viewModel::setAutoDelete,
                summary = autoDeleteSummary,
                icon = Icons.Outlined.AutoDelete,
            )
            if (autoDelete) {
                choice(HistoryIndex.retention, onClick = { dialog = HistoryDialog.RETENTION }, icon = Icons.Outlined.Schedule) {
                    pluralStringResource(R.plurals.delete_older_than_template, retentionDays, retentionDays)
                }
            }
            nav(
                HistoryIndex.clear,
                icon = Icons.AutoMirrored.Outlined.ManageSearch,
                showChevron = false,
                onClick = { dialog = HistoryDialog.CLEAR },
            )
        }
    }

    when (dialog) {
        HistoryDialog.MAX_SIZE -> {
            FlowChoiceDialog(
                title = stringResource(R.string.max_history_size_title),
                description = stringResource(R.string.choose_history_size),
                options = HistorySizes.map { FlowChoice(it, pluralStringResource(R.plurals.searches_count_template, it, it)) },
                selected = maxSize,
                onSelect = viewModel::setMaxSize,
                onDismiss = { dialog = null },
            )
        }

        HistoryDialog.RETENTION -> {
            FlowChoiceDialog(
                title = stringResource(R.string.retention_period_title),
                description = stringResource(R.string.delete_searches_older_than),
                options = RetentionPeriods.map { (days, label) -> FlowChoice(days, stringResource(label)) },
                selected = retentionDays,
                onSelect = viewModel::setRetentionDays,
                onDismiss = { dialog = null },
            )
        }

        HistoryDialog.CLEAR -> {
            FlowAlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(R.string.clear_history_dialog_title)) },
                text = { Text(stringResource(R.string.clear_history_dialog_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.clear()
                        dialog = null
                    }) { Text(stringResource(R.string.clear), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }

        null -> {
            Unit
        }
    }
}

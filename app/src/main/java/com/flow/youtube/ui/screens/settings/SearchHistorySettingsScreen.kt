package com.flow.youtube.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.flow.youtube.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchHistorySettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val searchHistoryRepo = remember { com.flow.youtube.data.local.SearchHistoryRepository(context) }
    
    // Search settings states
    val searchHistoryEnabled by searchHistoryRepo.isSearchHistoryEnabledFlow().collectAsState(initial = true)
    val searchSuggestionsEnabled by searchHistoryRepo.isSearchSuggestionsEnabledFlow().collectAsState(initial = true)
    val maxHistorySize by searchHistoryRepo.getMaxHistorySizeFlow().collectAsState(initial = 50)
    val autoDeleteHistory by searchHistoryRepo.isAutoDeleteHistoryEnabledFlow().collectAsState(initial = false)
    val historyRetentionDays by searchHistoryRepo.getHistoryRetentionDaysFlow().collectAsState(initial = 90)

    var showClearSearchDialog by remember { mutableStateOf(false) }
    var showHistorySizeDialog by remember { mutableStateOf(false) }
    var showRetentionDaysDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.btn_back))
                    }
                    Text(
                        text = stringResource(R.string.search_history_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = Icons.Outlined.History,
                        title = stringResource(R.string.save_search_history_title),
                        subtitle = stringResource(R.string.save_searches_subtitle),
                        checked = searchHistoryEnabled,
                        onCheckedChange = { coroutineScope.launch { searchHistoryRepo.setSearchHistoryEnabled(it) } }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.TrendingUp,
                        title = stringResource(R.string.search_suggestions_title),
                        subtitle = stringResource(R.string.show_suggestions_subtitle),
                        checked = searchSuggestionsEnabled,
                        onCheckedChange = { coroutineScope.launch { searchHistoryRepo.setSearchSuggestionsEnabled(it) } }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                        icon = Icons.Outlined.Storage,
                        title = stringResource(R.string.max_history_size_title),
                        subtitle = stringResource(R.string.currently_searches_template, maxHistorySize),
                        onClick = { showHistorySizeDialog = true }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.AutoDelete,
                        title = stringResource(R.string.auto_delete_history_title),
                        subtitle = if (autoDeleteHistory) stringResource(R.string.delete_after_days_template, historyRetentionDays) else stringResource(R.string.never_delete_automatically),
                        checked = autoDeleteHistory,
                        onCheckedChange = { coroutineScope.launch { searchHistoryRepo.setAutoDeleteHistory(it) } }
                    )
                    
                    if (autoDeleteHistory) {
                        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        SettingsItem(
                            icon = Icons.Outlined.Schedule,
                            title = stringResource(R.string.retention_period_title),
                            subtitle = stringResource(R.string.delete_older_than_template, historyRetentionDays),
                            onClick = { showRetentionDaysDialog = true }
                        )
                    }
                    
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                        icon = Icons.Outlined.ManageSearch,
                        title = stringResource(R.string.clear_history_item_title),
                        subtitle = stringResource(R.string.remove_all_queries),
                        onClick = { showClearSearchDialog = true }
                    )
                }
            }
        }
    }

    // Clear Search History Dialog
    if (showClearSearchDialog) {
        SimpleConfirmDialog(
            title = stringResource(R.string.clear_history_dialog_title),
            text = stringResource(R.string.clear_history_dialog_text),
            onConfirm = { coroutineScope.launch { searchHistoryRepo.clearSearchHistory(); showClearSearchDialog = false } },
            onDismiss = { showClearSearchDialog = false }
        )
    }

     // Max History Size Dialog
    if (showHistorySizeDialog) {
        var selectedSize by remember { mutableIntStateOf(maxHistorySize) }
        
        AlertDialog(
            onDismissRequest = { showHistorySizeDialog = false },
            icon = { Icon(Icons.Outlined.Storage, contentDescription = null) },
            title = { Text(stringResource(R.string.max_history_size_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.choose_history_size))
                    listOf(25, 50, 100, 200, 500).forEach { size ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedSize = size }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedSize == size, onClick = { selectedSize = size })
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.searches_count_template, size))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch { searchHistoryRepo.setMaxHistorySize(selectedSize); showHistorySizeDialog = false }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showHistorySizeDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    
    // Retention Days Dialog
    if (showRetentionDaysDialog) {
        var selectedDays by remember { mutableIntStateOf(historyRetentionDays) }
        
        AlertDialog(
            onDismissRequest = { showRetentionDaysDialog = false },
            icon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
            title = { Text(stringResource(R.string.retention_period_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.delete_searches_older_than))
                    listOf(7, 30, 90, 180, 365).forEach { days ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedDays = days }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedDays == days, onClick = { selectedDays = days })
                            Spacer(Modifier.width(8.dp))
                            Text(when (days) {
                                7 -> stringResource(R.string.period_one_week)
                                30 -> stringResource(R.string.period_one_month)
                                90 -> stringResource(R.string.period_three_months)
                                180 -> stringResource(R.string.period_six_months)
                                365 -> stringResource(R.string.period_one_year)
                                else -> stringResource(R.string.days_count_template, days)
                            })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch { searchHistoryRepo.setHistoryRetentionDays(selectedDays); showRetentionDaysDialog = false }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showRetentionDaysDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

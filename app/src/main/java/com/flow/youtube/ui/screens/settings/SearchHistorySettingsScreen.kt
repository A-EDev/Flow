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
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                    Text(
                        text = "Search & History",
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
                        title = "Save Search History",
                        subtitle = "Save your searches for quick access",
                        checked = searchHistoryEnabled,
                        onCheckedChange = { coroutineScope.launch { searchHistoryRepo.setSearchHistoryEnabled(it) } }
                    )
                    Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.TrendingUp,
                        title = "Search Suggestions",
                        subtitle = "Show suggestions while typing",
                        checked = searchSuggestionsEnabled,
                        onCheckedChange = { coroutineScope.launch { searchHistoryRepo.setSearchSuggestionsEnabled(it) } }
                    )
                    Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                        icon = Icons.Outlined.Storage,
                        title = "Max History Size",
                        subtitle = "Currently: $maxHistorySize searches",
                        onClick = { showHistorySizeDialog = true }
                    )
                    Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.AutoDelete,
                        title = "Auto-Delete History",
                        subtitle = if (autoDeleteHistory) "Delete after $historyRetentionDays days" else "Never delete automatically",
                        checked = autoDeleteHistory,
                        onCheckedChange = { coroutineScope.launch { searchHistoryRepo.setAutoDeleteHistory(it) } }
                    )
                    
                    if (autoDeleteHistory) {
                        Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        SettingsItem(
                            icon = Icons.Outlined.Schedule,
                            title = "Retention Period",
                            subtitle = "Delete searches older than $historyRetentionDays days",
                            onClick = { showRetentionDaysDialog = true }
                        )
                    }
                    
                    Divider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsItem(
                        icon = Icons.Outlined.ManageSearch,
                        title = "Clear Search History",
                        subtitle = "Remove all search queries",
                        onClick = { showClearSearchDialog = true }
                    )
                }
            }
        }
    }

    // Clear Search History Dialog
    if (showClearSearchDialog) {
        SimpleConfirmDialog(
            title = "Clear Search History?",
            text = "Permanently delete all search history.",
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
            title = { Text("Max History Size") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose how many searches to keep:")
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
                            Text("$size searches")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch { searchHistoryRepo.setMaxHistorySize(selectedSize); showHistorySizeDialog = false }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showHistorySizeDialog = false }) { Text("Cancel") } }
        )
    }
    
    // Retention Days Dialog
    if (showRetentionDaysDialog) {
        var selectedDays by remember { mutableIntStateOf(historyRetentionDays) }
        
        AlertDialog(
            onDismissRequest = { showRetentionDaysDialog = false },
            icon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
            title = { Text("Retention Period") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Delete searches older than:")
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
                                7 -> "1 week"
                                30 -> "1 month"
                                90 -> "3 months"
                                180 -> "6 months"
                                365 -> "1 year"
                                else -> "$days days"
                            })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch { searchHistoryRepo.setHistoryRetentionDays(selectedDays); showRetentionDaysDialog = false }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRetentionDaysDialog = false }) { Text("Cancel") } }
        )
    }
}

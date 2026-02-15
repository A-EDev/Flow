package com.flow.youtube.ui.screens.settings

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.flow.youtube.R
import androidx.hilt.navigation.compose.hiltViewModel
import java.util.Locale
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: TimeManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            // If granted, we could auto-enable, but for now we rely on user clicking again or simple flow
        }
    )

    // Helper to check and request permission
    val checkPermissionAndToggle: (Boolean, (Boolean) -> Unit) -> Unit = { checked, toggleFunc ->
        if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            
            if (hasPermission) {
                toggleFunc(true)
            } else {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                // We don't toggle yet, user must grant first. 
                // Or we toggle and let system deny notification? Better to ask.
                // For better UX, we could toggle it anyway so the UI updates, 
                // but the notification won't show without permission.
                toggleFunc(true) 
            }
        } else {
            toggleFunc(checked)
        }
    }
    
    // Dialog state for Break Frequency
    var showBreakFrequencyDialog by remember { mutableStateOf(false) }

    if (showBreakFrequencyDialog) {
        FrequencyPickerDialog(
            currentFrequency = uiState.breakFrequencyMinutes,
            onDismiss = { showBreakFrequencyDialog = false },
            onConfirm = { minutes ->
                viewModel.updateBreakFrequency(minutes)
                showBreakFrequencyDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.time_management_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Enhanced Stats Header
            StatsHeader(uiState)

            // Bar Chart
            if (uiState.chartData.isNotEmpty()) {
                 ChartSection(data = uiState.chartData)
            }

            // Stats Breakdown Card
            StatsBreakdownCard(uiState)
            
            Text(
                text = stringResource(R.string.stats_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Reminders Section
            Text(
                text = stringResource(R.string.tools_to_manage_time),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Bedtime Reminder
                ExpandableSettingsCard(
                    title = "Remind me when it's bedtime",
                    checked = uiState.bedtimeReminderEnabled,
                    onCheckedChange = { checkPermissionAndToggle(it, viewModel::toggleBedtimeReminder) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        TimePickerRow(
                            label = "Start time",
                            hour = uiState.bedtimeStartHour,
                            minute = uiState.bedtimeStartMinute,
                            context = context
                        ) { h, m -> 
                            viewModel.updateBedtimeSchedule(
                                startHour = h, 
                                startMinute = m, 
                                endHour = uiState.bedtimeEndHour, 
                                endMinute = uiState.bedtimeEndMinute
                            )
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))
                        
                        TimePickerRow(
                            label = "End time",
                            hour = uiState.bedtimeEndHour,
                            minute = uiState.bedtimeEndMinute,
                            context = context
                        ) { h, m -> 
                             viewModel.updateBedtimeSchedule(
                                startHour = uiState.bedtimeStartHour, 
                                startMinute = uiState.bedtimeStartMinute, 
                                endHour = h, 
                                endMinute = m
                            )
                        }
                        
                        Text(
                            text = stringResource(R.string.bedtime_notification_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                // Break Reminder
                ExpandableSettingsCard(
                    title = "Remind me to take a break",
                    checked = uiState.breakReminderEnabled,
                    onCheckedChange = { checkPermissionAndToggle(it, viewModel::toggleBreakReminder) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showBreakFrequencyDialog = true }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.reminder_frequency), 
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.every_min_template, uiState.breakFrequencyMinutes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown, 
                                    contentDescription = null, 
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatsHeader(uiState: TimeManagementState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.daily_average_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = uiState.dailyAverage.replace(" daily average", ""),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (uiState.trend.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = uiState.trend,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun StatsBreakdownCard(uiState: TimeManagementState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
         Column(modifier = Modifier.padding(16.dp)) {
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
              ) {
                  Text(stringResource(R.string.stats_today), style = MaterialTheme.typography.bodyLarge)
                  Text(uiState.todayWatchTime, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
              }
              HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
              ) {
                  Text(stringResource(R.string.stats_last_7_days), style = MaterialTheme.typography.bodyLarge)
                  Text(uiState.last7DaysWatchTime, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
              }
         }
    }
}

@Composable
fun ExpandableSettingsCard(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
            
            AnimatedVisibility(
                visible = checked,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                content()
            }
        }
    }
}

@Composable
fun TimePickerRow(
    label: String,
    hour: Int,
    minute: Int,
    context: Context,
    onTimeSelected: (Int, Int) -> Unit
) {
    val timeString = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                TimePickerDialog(
                    context,
                    { _, h, m -> onTimeSelected(h, m) },
                    hour,
                    minute,
                    true // is24HourView
                ).show()
            }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            timeString, 
            style = MaterialTheme.typography.bodyLarge, 
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun FrequencyPickerDialog(
    currentFrequency: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val options = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.frequency_dialog_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConfirm(minutes) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = minutes == currentFrequency,
                            onClick = { onConfirm(minutes) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.every_minutes_template, minutes))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun ChartSection(data: List<DailyStat>) {
    val maxVal = data.maxOfOrNull { it.durationH } ?: 1f
    val yMax = if (maxVal == 0f) 1f else maxVal * 1.2f 
    
    // Determine grid lines
    val gridStep = if (yMax > 4) 2 else 1
    val gridLines = (gridStep..yMax.toInt() step gridStep).toList()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Chart Area
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight()
        ) {
            // Grid Lines (Canvas)
            Canvas(modifier = Modifier.fillMaxSize()) {
                gridLines.forEach { hr ->
                     val y = size.height - (size.height * (hr / yMax))
                     drawLine(
                         color = Color.Gray.copy(alpha = 0.3f),
                         start = Offset(0f, y),
                         end = Offset(size.width, y),
                         strokeWidth = 2f,
                         pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                     )
                }
            }

            // Bars
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                data.forEach { stat ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                    ) {
                        val barHeightFraction = (stat.durationH / yMax).coerceIn(0.0f, 1f)
                        
                        // Bar
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false) // Don't fill, use height fraction
                                .fillMaxHeight(barHeightFraction)
                                .width(12.dp) 
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    if (stat.isToday) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.primaryContainer
                                )
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Day Label
                        Text(
                            text = stat.dayName.take(1),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (stat.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}


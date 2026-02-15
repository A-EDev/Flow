package com.flow.youtube.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.flow.youtube.R
import java.util.Locale

// ============================================================================
// Main Screen
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: TimeManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* Permission result handled by next toggle attempt */ }
    )

    val checkPermissionAndToggle: (Boolean, (Boolean) -> Unit) -> Unit = { checked, toggleFunc ->
        if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                toggleFunc(true)
            } else {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                toggleFunc(true)
            }
        } else {
            toggleFunc(checked)
        }
    }

    // Dialog states
    var showBreakFrequencyDialog by remember { mutableStateOf(false) }
    var showBedtimeStartPicker by remember { mutableStateOf(false) }
    var showBedtimeEndPicker by remember { mutableStateOf(false) }

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
                    Column {
                        Text(
                            stringResource(R.string.time_management_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.time_management_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Stats Hero ──
            StatsHeroCard(uiState)

            // ── Weekly Chart ──
            WeeklyChartCard(data = uiState.chartData)

            // ── Stats Breakdown ──
            StatsBreakdownCard(uiState)

            // ── Disclaimer ──
            Text(
                stringResource(R.string.stats_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // ── Tools Section ──
            ToolsSectionHeader()

            // Bedtime Reminder
            ReminderCard(
                icon = Icons.Outlined.Bedtime,
                iconTint = Color(0xFF5C6BC0),
                title = stringResource(R.string.bedtime_reminder_title),
                subtitle = stringResource(R.string.bedtime_reminder_subtitle),
                enabled = uiState.bedtimeReminderEnabled,
                onToggle = { checkPermissionAndToggle(it, viewModel::toggleBedtimeReminder) }
            ) {
                // Bedtime schedule
                TimeSlotRow(
                    label = stringResource(R.string.start_time),
                    hour = uiState.bedtimeStartHour,
                    minute = uiState.bedtimeStartMinute,
                    onClick = { showBedtimeStartPicker = true }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                TimeSlotRow(
                    label = stringResource(R.string.end_time),
                    hour = uiState.bedtimeEndHour,
                    minute = uiState.bedtimeEndMinute,
                    onClick = { showBedtimeEndPicker = true }
                )

                Spacer(Modifier.height(8.dp))

                BedtimeScheduleIndicator(
                    startHour = uiState.bedtimeStartHour,
                    startMinute = uiState.bedtimeStartMinute,
                    endHour = uiState.bedtimeEndHour,
                    endMinute = uiState.bedtimeEndMinute
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    stringResource(R.string.bedtime_notification_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // Break Reminder
            ReminderCard(
                icon = Icons.Outlined.FreeBreakfast,
                iconTint = Color(0xFFE57373),
                title = stringResource(R.string.break_reminder_title),
                subtitle = stringResource(R.string.break_reminder_subtitle),
                enabled = uiState.breakReminderEnabled,
                onToggle = { checkPermissionAndToggle(it, viewModel::toggleBreakReminder) }
            ) {
                FrequencySelector(
                    currentMinutes = uiState.breakFrequencyMinutes,
                    onClick = { showBreakFrequencyDialog = true }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // ── Dialogs ──

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

    // Material 3 Time Pickers
    if (showBedtimeStartPicker) {
        Material3TimePicker(
            initialHour = uiState.bedtimeStartHour,
            initialMinute = uiState.bedtimeStartMinute,
            title = stringResource(R.string.bedtime_start_title),
            onConfirm = { h, m ->
                viewModel.updateBedtimeSchedule(
                    startHour = h, startMinute = m,
                    endHour = uiState.bedtimeEndHour, endMinute = uiState.bedtimeEndMinute
                )
                showBedtimeStartPicker = false
            },
            onDismiss = { showBedtimeStartPicker = false }
        )
    }

    if (showBedtimeEndPicker) {
        Material3TimePicker(
            initialHour = uiState.bedtimeEndHour,
            initialMinute = uiState.bedtimeEndMinute,
            title = stringResource(R.string.bedtime_end_title),
            onConfirm = { h, m ->
                viewModel.updateBedtimeSchedule(
                    startHour = uiState.bedtimeStartHour, startMinute = uiState.bedtimeStartMinute,
                    endHour = h, endMinute = m
                )
                showBedtimeEndPicker = false
            },
            onDismiss = { showBedtimeEndPicker = false }
        )
    }
}

// ============================================================================
// Stats Hero Card
// ============================================================================

@Composable
private fun StatsHeroCard(uiState: TimeManagementState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Timer,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(R.string.daily_average_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(4.dp))

            // Main stat
            Text(
                text = uiState.dailyAverageFormatted,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Trend badge
            if (uiState.trend.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))

                val isPositiveTrend = uiState.trend.contains("↓") || uiState.trend.contains("less")
                val trendColor = if (isPositiveTrend)
                    Color(0xFF4CAF50) else Color(0xFFFF9800)

                Surface(
                    color = trendColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            if (isPositiveTrend) Icons.Filled.TrendingDown
                            else Icons.Filled.TrendingUp,
                            null,
                            tint = trendColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            uiState.trend,
                            style = MaterialTheme.typography.labelMedium,
                            color = trendColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// Weekly Chart Card
// ============================================================================

@Composable
private fun WeeklyChartCard(data: List<DailyStat>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.this_week),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                if (data.isNotEmpty()) {
                    val totalHours = data.sumOf { it.durationH.toDouble() }
                    Text(
                        stringResource(R.string.total_hours_template, String.format("%.1f", totalHours)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            if (data.isEmpty() || data.all { it.durationH == 0f }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.BarChart,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.no_watch_data),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                // Chart
                val maxVal = data.maxOf { it.durationH }.coerceAtLeast(0.5f)
                val yMax = maxVal * 1.2f
                val primaryColor = MaterialTheme.colorScheme.primary
                val containerColor = MaterialTheme.colorScheme.primaryContainer
                val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                val textColor = MaterialTheme.colorScheme.onSurfaceVariant

                Row(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                    // Y-axis labels
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(30.dp)
                            .padding(end = 4.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) {
                        val gridStep = when {
                            yMax > 8 -> 4
                            yMax > 4 -> 2
                            else -> 1
                        }
                        val labels = (yMax.toInt() downTo 0 step gridStep).toList()
                        labels.forEach { value ->
                            Text(
                                "${value}h",
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Chart area
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        // Grid lines
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val gridStep = when {
                                yMax > 8 -> 4
                                yMax > 4 -> 2
                                else -> 1
                            }
                            for (value in gridStep..yMax.toInt() step gridStep) {
                                val y = size.height - (size.height * (value / yMax))
                                drawLine(
                                    color = gridColor,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1f,
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(8f, 8f), 0f
                                    )
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
                                ChartBar(
                                    stat = stat,
                                    yMax = yMax,
                                    primaryColor = primaryColor,
                                    containerColor = containerColor,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
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
private fun ChartBar(
    stat: DailyStat,
    yMax: Float,
    primaryColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    val barFraction = (stat.durationH / yMax).coerceIn(0f, 1f)

    // Animate bar height on first appearance
    val animatedFraction by animateFloatAsState(
        targetValue = barFraction,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "bar_height"
    )

    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        // Value label above bar
        if (stat.isToday && stat.durationH > 0) {
            Text(
                String.format("%.1fh", stat.durationH),
                style = MaterialTheme.typography.labelSmall,
                color = primaryColor,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            )
            Spacer(Modifier.height(2.dp))
        }

        // Bar
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .fillMaxHeight(animatedFraction.coerceAtLeast(0.01f))
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                .background(
                    if (stat.isToday)
                        Brush.verticalGradient(
                            listOf(primaryColor, primaryColor.copy(alpha = 0.7f))
                        )
                    else
                        Brush.verticalGradient(
                            listOf(containerColor, containerColor.copy(alpha = 0.5f))
                        )
                )
        )

        Spacer(Modifier.height(6.dp))

        // Day label
        Text(
            stat.dayName.take(3),
            style = MaterialTheme.typography.labelSmall,
            color = if (stat.isToday) primaryColor
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (stat.isToday) FontWeight.Bold else FontWeight.Normal,
            fontSize = 11.sp
        )
    }
}

// ============================================================================
// Stats Breakdown
// ============================================================================

@Composable
private fun StatsBreakdownCard(uiState: TimeManagementState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            StatRow(
                icon = Icons.Outlined.Today,
                label = stringResource(R.string.stats_today),
                value = uiState.todayWatchTime,
                tint = MaterialTheme.colorScheme.primary
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            )

            StatRow(
                icon = Icons.Outlined.DateRange,
                label = stringResource(R.string.stats_last_7_days),
                value = uiState.last7DaysWatchTime,
                tint = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun StatRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ============================================================================
// Tools Section Header
// ============================================================================

@Composable
private fun ToolsSectionHeader() {
    Row(
        modifier = Modifier.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Column {
            Text(
                stringResource(R.string.tools_to_manage_time),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.tools_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============================================================================
// Reminder Card — Redesigned expandable card with icon
// ============================================================================

@Composable
private fun ReminderCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    expandedContent: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (enabled) 2.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(iconTint.copy(alpha = if (enabled) 0.15f else 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon, null,
                        tint = if (enabled) iconTint else iconTint.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.width(8.dp))

                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle
                )
            }

            AnimatedVisibility(
                visible = enabled,
                enter = expandVertically(animationSpec = spring(dampingRatio = 0.8f)) + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )
                    Spacer(Modifier.height(12.dp))
                    expandedContent()
                }
            }
        }
    }
}

// ============================================================================
// Time Slot Row (replaces TimePickerRow)
// ============================================================================

@Composable
private fun TimeSlotRow(
    label: String,
    hour: Int,
    minute: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            shape = RoundedCornerShape(10.dp),
            onClick = onClick
        ) {
            Text(
                String.format(Locale.getDefault(), "%02d:%02d", hour, minute),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

// ============================================================================
// Bedtime Schedule Visual Indicator
// ============================================================================

@Composable
private fun BedtimeScheduleIndicator(
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.primaryContainer

    val startTotal = startHour * 60 + startMinute
    val endTotal = endHour * 60 + endMinute
    val sleepDuration = if (endTotal > startTotal) endTotal - startTotal
    else (24 * 60 - startTotal) + endTotal

    val hours = sleepDuration / 60
    val minutes = sleepDuration % 60

    Surface(
        color = containerColor.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.NightsStay,
                null,
                tint = primaryColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                buildString {
                    append(stringResource(R.string.sleep_window))
                    append(" ")
                    if (hours > 0) append("${hours}h ")
                    if (minutes > 0) append("${minutes}m")
                },
                style = MaterialTheme.typography.labelMedium,
                color = primaryColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ============================================================================
// Frequency Selector
// ============================================================================

@Composable
private fun FrequencySelector(
    currentMinutes: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                stringResource(R.string.reminder_frequency),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                stringResource(R.string.frequency_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            shape = RoundedCornerShape(10.dp),
            onClick = onClick
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.every_min_template, currentMinutes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ============================================================================
// Material 3 Time Picker Dialog
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Material3TimePicker(
    initialHour: Int,
    initialMinute: Int,
    title: String,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, fontWeight = FontWeight.Bold)
        },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(state = timePickerState)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// ============================================================================
// Frequency Picker Dialog — Polished
// ============================================================================

@Composable
private fun FrequencyPickerDialog(
    currentFrequency: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val options = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.frequency_dialog_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                options.forEach { minutes ->
                    val isSelected = minutes == currentFrequency

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                        onClick = { onConfirm(minutes) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onConfirm(minutes) }
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    stringResource(R.string.every_minutes_template, minutes),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (minutes >= 60) {
                                    Text(
                                        "${minutes / 60}h${if (minutes % 60 > 0) " ${minutes % 60}m" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
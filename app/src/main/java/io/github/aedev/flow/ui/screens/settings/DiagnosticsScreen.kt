package io.github.aedev.flow.ui.screens.settings

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.utils.FlowDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Log-level colour helpers
// ---------------------------------------------------------------------------

private enum class LogLevel { ERROR, WARN, INFO, DEBUG, OTHER }

private fun detectLevel(line: String): LogLevel {
    // Logcat format: "MM-DD HH:MM:SS.mmm  PID  TID LEVEL TAG: message"
    return when {
        line.contains(Regex("""\s[EF]\s""")) -> LogLevel.ERROR
        line.contains(Regex("""\sW\s"""))    -> LogLevel.WARN
        line.contains(Regex("""\sI\s"""))    -> LogLevel.INFO
        line.contains(Regex("""\sD\s"""))    -> LogLevel.DEBUG
        else                                 -> LogLevel.OTHER
    }
}

@Composable
private fun levelColor(level: LogLevel): Color {
    val cs = MaterialTheme.colorScheme
    return when (level) {
        LogLevel.ERROR -> Color(0xFFFF6B6B)
        LogLevel.WARN  -> Color(0xFFFFD93D)
        LogLevel.INFO  -> cs.onSurface.copy(alpha = 0.85f)
        LogLevel.DEBUG -> cs.onSurface.copy(alpha = 0.50f)
        LogLevel.OTHER -> cs.onSurface.copy(alpha = 0.65f)
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onNavigateBack: () -> Unit) {

    val context       = LocalContext.current
    val scope         = rememberCoroutineScope()
    val clipboard     = LocalClipboardManager.current

    // Tab state
    var selectedTab by remember { mutableIntStateOf(0) }

    // Log content
    var sessionLines   by remember { mutableStateOf<List<String>>(emptyList()) }
    var crashText      by remember { mutableStateOf("") }
    var isLoadingLogs  by remember { mutableStateOf(true) }
    var isLoadingCrash by remember { mutableStateOf(true) }

    // UI feedback
    var showClearDialog  by remember { mutableStateOf(false) }
    var copiedSnackShown by remember { mutableStateOf(false) }
    val snackbarHost     = remember { SnackbarHostState() }
    val listState        = rememberLazyListState()

    val deviceInfo = remember { FlowDiagnostics.buildDeviceInfo(context) }

    // -----------------------------------------------------------------------
    // Load logs (once, on IO)
    // -----------------------------------------------------------------------
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val raw       = FlowDiagnostics.readSessionLogs()
            val lines     = raw.lines()
            val crashRaw  = FlowDiagnostics.getCrashLogs(context)
            withContext(Dispatchers.Main) {
                sessionLines   = lines
                isLoadingLogs  = false
                crashText      = crashRaw
                isLoadingCrash = false
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helper – copy current tab's content to clipboard
    // -----------------------------------------------------------------------
    fun copyCurrentTab() {
        val text = when (selectedTab) {
            0    -> FlowDiagnostics.buildFullReport(context, sessionLines.joinToString("\n"))
            else -> "$deviceInfo\n\n$crashText"
        }
        clipboard.setText(AnnotatedString(text))
        if (!copiedSnackShown) {
            copiedSnackShown = true
            scope.launch {
                snackbarHost.showSnackbar(
                    message  = context.getString(R.string.diagnostics_copied),
                    duration = SnackbarDuration.Short
                )
                delay(2500)
                copiedSnackShown = false
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helper – share full report via system share sheet
    // -----------------------------------------------------------------------
    fun shareReport() {
        val report = FlowDiagnostics.buildFullReport(context, sessionLines.joinToString("\n"))
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Flow Diagnostics Report")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.diagnostics_share)))
    }

    // -----------------------------------------------------------------------
    // UI
    // -----------------------------------------------------------------------
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = stringResource(R.string.diagnostics_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back))
                    }
                },
                actions = {
                    // Share
                    IconButton(onClick = ::shareReport) {
                        Icon(Icons.Outlined.Share,
                            contentDescription = stringResource(R.string.diagnostics_share))
                    }
                    // Copy
                    IconButton(onClick = ::copyCurrentTab) {
                        Icon(Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.diagnostics_copy))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ----------------------------------------------------------------
            // Device info card (always visible)
            // ----------------------------------------------------------------
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.PhoneAndroid,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text  = deviceInfo,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ----------------------------------------------------------------
            // Tab row
            // ----------------------------------------------------------------
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = MaterialTheme.colorScheme.background,
                divider          = {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick  = { selectedTab = 0 },
                    text     = {
                        Text(stringResource(R.string.diagnostics_tab_session),
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
                    },
                    icon = { Icon(Icons.Outlined.BugReport, contentDescription = null, Modifier.size(16.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick  = { selectedTab = 1 },
                    text     = {
                        Text(stringResource(R.string.diagnostics_tab_crashes),
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                    },
                    icon = { Icon(Icons.Outlined.Warning, contentDescription = null, Modifier.size(16.dp)) }
                )
            }

            // ----------------------------------------------------------------
            // Content area
            // ----------------------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                when {
                    // Session logs tab
                    selectedTab == 0 && isLoadingLogs -> LoadingState()

                    selectedTab == 0 -> {
                        LogList(
                            lines     = sessionLines,
                            listState = listState
                        )
                    }

                    // Crash reports tab
                    selectedTab == 1 && isLoadingCrash -> LoadingState()

                    selectedTab == 1 -> {
                        CrashContent(
                            text             = crashText,
                            onClearRequest   = { showClearDialog = true }
                        )
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Clear crash logs confirmation dialog
    // -----------------------------------------------------------------------
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon  = { Icon(Icons.Outlined.DeleteForever, null,
                tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.diagnostics_clear_confirm_title),
                fontWeight = FontWeight.Bold) },
            text  = { Text(stringResource(R.string.diagnostics_clear_confirm_body),
                style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(
                    onClick = {
                        FlowDiagnostics.clearCrashLogs(context)
                        crashText       = "No crash logs"
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Sub-composables
// ---------------------------------------------------------------------------

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                text  = stringResource(R.string.diagnostics_loading),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Scrollable list rendering one row per log line, coloured by log level. */
@Composable
private fun LogList(
    lines: List<String>,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    val surfaceColor = MaterialTheme.colorScheme.surface

    if (lines.isEmpty() || (lines.size == 1 && lines[0].isBlank())) {
        EmptyLogsState(stringResource(R.string.diagnostics_no_session_logs))
        return
    }

    // Pre-compute levels once so recomposition doesn't re-evaluate regexes
    val parsed = remember(lines) {
        lines.map { line -> Pair(line, detectLevel(line)) }
    }

    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state           = listState,
            modifier        = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            itemsIndexed(parsed, key = { index, _ -> index }) { _, (line, level) ->
                Text(
                    text     = line,
                    style    = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp,
                        fontSize   = 11.sp
                    ),
                    color    = levelColor(level),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                )
            }
        }
    }
}

/** Displays crash log text with a 'Clear' action button at the bottom. */
@Composable
private fun CrashContent(
    text: String,
    onClearRequest: () -> Unit
) {
    val noCrashes = text.isBlank() || text.trim() == "No crash logs"

    Column(Modifier.fillMaxSize()) {
        if (noCrashes) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                EmptyLogsState(stringResource(R.string.diagnostics_no_crashes))
            }
        } else {
            val lines = remember(text) { text.lines() }
            Card(
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    items(lines) { line ->
                        Text(
                            text  = line,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp,
                                fontSize   = 11.sp
                            ),
                            color = when {
                                line.contains("Exception") || line.contains("CRASH") ->
                                    Color(0xFFFF6B6B)
                                line.startsWith("=") ->
                                    MaterialTheme.colorScheme.primary
                                else ->
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick  = onClearRequest,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Outlined.DeleteForever, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.diagnostics_clear_crashes))
            }
        }
    }
}

@Composable
private fun EmptyLogsState(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector      = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint             = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier         = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text  = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

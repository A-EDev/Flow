package com.flow.youtube.ui.screens.personality

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flow.youtube.data.recommendation.FlowNeuroEngine
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowPersonalityScreen(
    onNavigateBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var userBrain by remember { mutableStateOf<FlowNeuroEngine.UserBrain?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    
    // Load Brain
    LaunchedEffect(Unit) {
        FlowNeuroEngine.initialize(context)
        userBrain = FlowNeuroEngine.getBrainSnapshot()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            "Neural Engine", 
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "Algorithm Tuning & Visualization", 
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(Icons.Default.Refresh, "Reset Brain")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (userBrain == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 1. Stats Overview
                StatsOverviewCard(brain = userBrain!!)

                // 2. Interest Radar Visualization
                Text(
                    "Cognitive Vector Map",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                RadarChart(
                    shortTerm = userBrain!!.shortTermVector,
                    longTerm = userBrain!!.longTermVector
                )
                
                // 3. Interest Bubbles
                Text(
                    "Dominant Interests",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                InterestBubbleChart(brain = userBrain!!)

                // 4. Controls
                TuningControls(
                    onReset = { showResetDialog = true }
                )
                
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = { Icon(Icons.Default.SettingsBackupRestore, null) },
            title = { Text("Reset Neural Profile?") },
            text = { Text("This will wipe all learned preferences, long-term memory, and topic weights. Your recommendation feed will return to a generic state.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            FlowNeuroEngine.resetBrain(context)
                            userBrain = FlowNeuroEngine.getBrainSnapshot() // Refresh
                            showResetDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun StatsOverviewCard(brain: FlowNeuroEngine.UserBrain) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha=0.4f))
    ) {
        Row(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                value = brain.totalInteractions.toString(),
                label = "Interactions",
                color = MaterialTheme.colorScheme.primary
            )
            StatItem(
                value = brain.longTermVector.topics.size.toString(),
                label = "Topics",
                color = MaterialTheme.colorScheme.secondary
            )
            StatItem(
                value = String.format("%.2f", brain.shortTermVector.pacing),
                label = "Avg Pace",
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
fun StatItem(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ==========================================
// RADAR CHART VISUALIZATION
// ==========================================

@Composable
fun RadarChart(
    shortTerm: FlowNeuroEngine.ContentVector,
    longTerm: FlowNeuroEngine.ContentVector
) {
    val labels = listOf("Pacing", "Complexity", "Duration", "Live Factor", "Variety")
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.onSurface 

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.2f)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f), RoundedCornerShape(24.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = minOf(size.width, size.height) / 2
            
            // Draw Web
            val rings = 4
            for (i in 1..rings) {
                drawCircle(
                    color = surfaceColor.copy(alpha = 0.1f),
                    radius = radius * (i / rings.toFloat()),
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            
            // Draw Axis Lines
            val angleStep = (2 * Math.PI / labels.size).toFloat()
            for (i in labels.indices) {
                val angle = i * angleStep - Math.PI.toFloat() / 2 // Start at top
                val end = Offset(
                    center.x + radius * cos(angle),
                    center.y + radius * sin(angle)
                )
                drawLine(
                    color = surfaceColor.copy(alpha = 0.2f),
                    start = center,
                    end = end,
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Draw Data - Short Term (Blue)
            val stValues = listOf(
                shortTerm.pacing,
                shortTerm.complexity,
                shortTerm.duration,
                shortTerm.isLive,
                (shortTerm.topics.size / 20.0).coerceAtMost(1.0)
            )
            drawRadarPolygon(center, radius, stValues, primaryColor, angleStep, "Short Term")

            // Draw Data - Long Term (Purple)
            val ltValues = listOf(
                longTerm.pacing,
                longTerm.complexity,
                longTerm.duration,
                longTerm.isLive,
                (longTerm.topics.size / 50.0).coerceAtMost(1.0)
            )
            drawRadarPolygon(center, radius, ltValues, secondaryColor, angleStep, "Long Term")
        }
        
        // Legend
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LegendItem("Short Term", primaryColor)
            LegendItem("Long Term", secondaryColor)
        }
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRadarPolygon(
    center: Offset,
    radius: Float,
    values: List<Double>,
    color: Color,
    angleStep: Float,
    label: String // Not used in drawing but good for debug
) {
    val path = Path()
    values.forEachIndexed { i, value ->
        val angle = i * angleStep - Math.PI.toFloat() / 2
        val r = radius * value.toFloat()
        val point = Offset(
            center.x + r * cos(angle),
            center.y + r * sin(angle)
        )
        if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    
    drawPath(path, color.copy(alpha = 0.3f))
    drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
}

@Composable
fun LegendItem(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ==========================================
// BUBBLE CHART
// ==========================================

@Composable
fun InterestBubbleChart(brain: FlowNeuroEngine.UserBrain) {
    // Get top 8 topics
    val topTopics = brain.longTermVector.topics.entries
        .sortedByDescending { it.value }
        .take(8)
    
    if (topTopics.isEmpty()) {
        Text(
            "No data yet. Watch some videos!",
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    // Simple Bubble Layout (FlowRow-ish but simpler)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // First Row (Big ones)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            topTopics.take(3).forEach { (topic, score) ->
                InterestBubble(topic, score, MaterialTheme.colorScheme.primary)
            }
        }
        // Second Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            topTopics.drop(3).take(3).forEach { (topic, score) ->
                InterestBubble(topic, score, MaterialTheme.colorScheme.secondary)
            }
        }
         // Third Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            topTopics.drop(6).forEach { (topic, score) ->
                InterestBubble(topic, score, MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
fun InterestBubble(topic: String, score: Double, color: Color) {
    val size = 60.dp + (100.dp * score.toFloat())
    
    Box(
        modifier = Modifier
            .padding(8.dp)
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.2f + (score * 0.5f).toFloat()))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = topic.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ==========================================
// TUNING SECTION
// ==========================================

@Composable
fun TuningControls(onReset: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Maintenance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            ListItem(
                headlineContent = { Text("Reset Recommendation History") },
                supportingContent = { Text("Clear all learned patterns and start fresh.") },
                trailingContent = {
                    Button(onClick = onReset, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.error)) {
                        Text("Reset")
                    }
                }
            )
        }
    }
}
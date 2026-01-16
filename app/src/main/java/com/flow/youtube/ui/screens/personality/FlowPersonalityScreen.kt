package com.flow.youtube.ui.screens.personality

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.youtube.data.recommendation.FlowNeuroEngine
import androidx.compose.ui.text.drawText
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FlowPersonalityScreen(
    onNavigateBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var userBrain by remember { mutableStateOf<FlowNeuroEngine.UserBrain?>(null) }
    
    LaunchedEffect(Unit) {
        FlowNeuroEngine.initialize(context)
        userBrain = FlowNeuroEngine.getBrainSnapshot()
    }

    val scrollState = rememberScrollState()
    val persona = if (userBrain != null) FlowNeuroEngine.getPersona(userBrain!!) else FlowNeuroEngine.FlowPersona.INITIATE
    
    // Bubble Animation State
    val infiniteTransition = rememberInfiniteTransition()
    val bubblePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Neural Profile", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
        ) {
            // 1. HERO SECTION: PERSONA
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = persona.icon,
                        fontSize = 64.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = persona.title,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = persona.description,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 2. INTERACTIVE BUBBLE GRAPH
            Text(
                "Interest Cloud",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                color = MaterialTheme.colorScheme.primary
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp) // Slightly taller for more room
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.1f)
                            )
                        )
                    )
            ) {
                val topics = userBrain?.longTermVector?.topics?.entries
                    ?.sortedByDescending { it.value }
                    ?.take(15) // Show more topics
                    ?: emptyList()
                
                val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
                val onSurface = MaterialTheme.colorScheme.onSurface
                val primaryColor = MaterialTheme.colorScheme.primary

                if (topics.isNotEmpty()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerX = size.width / 2
                        val centerY = size.height / 2
                        
                        topics.forEachIndexed { index, (topic, score) ->
                            // More organic "orbital" physics
                            val baseAngle = index * 1.5f + (bubblePhase * (0.8f + index * 0.05f))
                            val orbitalRadius = (index * 22.dp.toPx()) + 40.dp.toPx()
                            
                            // Floating offset (secondary wave)
                            val floatX = sin(bubblePhase * 1.2f + index) * 15f
                            val floatY = cos(bubblePhase * 0.8f + index) * 15f
                            
                            val x = centerX + cos(baseAngle) * (orbitalRadius * 0.5f) + floatX
                            val y = centerY + sin(baseAngle) * (orbitalRadius * 0.5f) + floatY
                            
                            // Dynamic size: exponential-ish scale for more contrast
                            val baseSize = (score.toFloat() * 160.dp.toPx()).coerceIn(45.dp.toPx(), 140.dp.toPx())
                            val pulse = 1f + sin(bubblePhase * 2f + index) * 0.03f
                            val radius = (baseSize * pulse) / 2
                            
                            // Color based on index for variety
                            val color = when(index % 4) {
                                0 -> primaryColor
                                1 -> Color(0xFF9C27B0) // Purple
                                2 -> Color(0xFF00BCD4) // Cyan
                                else -> Color(0xFFFF5722) // Orange
                            }.copy(alpha = 0.15f + (score.toFloat() * 0.15f))

                            // 1. Draw Glow/Shadow
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(color, Color.Transparent),
                                    center = Offset(x, y),
                                    radius = radius * 1.4f
                                ),
                                radius = radius * 1.4f,
                                center = Offset(x, y)
                            )

                            // 2. Draw Bubble Body
                            drawCircle(
                                color = color,
                                radius = radius,
                                center = Offset(x, y)
                            )
                            
                            // 3. Draw Border
                            drawCircle(
                                color = color.copy(alpha = 0.6f),
                                radius = radius,
                                center = Offset(x, y),
                                style = Stroke(width = 1.5.dp.toPx())
                            )

                            // 4. Draw Label Inside
                            if (radius > 25.dp.toPx()) {
                                val label = if (topic.length > 10) topic.take(8) + ".." else topic
                                val fontSizePx = radius * 0.25f
                                val textLayout = textMeasurer.measure(
                                    text = label,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontSize = (fontSizePx / density).coerceIn(8f, 14f).sp,
                                        fontWeight = FontWeight.Bold,
                                        color = onSurface.copy(alpha = 0.8f),
                                        textAlign = TextAlign.Center
                                    )
                                )
                                
                                drawText(
                                    textLayoutResult = textLayout,
                                    topLeft = Offset(
                                        x - textLayout.size.width / 2,
                                        y - textLayout.size.height / 2
                                    )
                                )
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Decoding your neural patterns...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            
            // Chips as legend/interaction
            FlowRow(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                userBrain?.longTermVector?.topics?.entries?.sortedByDescending { it.value }?.take(12)?.forEach { (topic, score) ->
                    AssistChip(
                        onClick = {},
                        label = { Text("#$topic") },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }
            }

            // 3. DETAILED METRICS
            Text(
                "Neural Metrics",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )
            
            if (userBrain != null) {
                Column(Modifier.padding(16.dp)) {
                    MetricRow("Attention Span", userBrain!!.longTermVector.duration, "Short Form", "Long Form")
                    Spacer(Modifier.height(16.dp))
                    MetricRow("Pacing Preference", userBrain!!.longTermVector.pacing, "Chill", "Hyper")
                    Spacer(Modifier.height(16.dp))
                    MetricRow("Complexity", userBrain!!.longTermVector.complexity, "Simple", "Academic")
                }
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun MetricRow(label: String, value: Double, leftLabel: String, rightLabel: String) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text("${(value * 100).toInt()}%", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = value.toFloat(),
            modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.secondaryContainer,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(leftLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(rightLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

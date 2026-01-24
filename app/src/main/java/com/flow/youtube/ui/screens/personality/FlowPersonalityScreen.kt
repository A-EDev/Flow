package com.flow.youtube.ui.screens.personality

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.youtube.data.recommendation.FlowNeuroEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.*
import kotlin.random.Random

// ============================================================================
// 🧠 FLOW NEURO CONTROL CENTER - Premium Redesign
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowPersonalityScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var userBrain by remember { mutableStateOf<FlowNeuroEngine.UserBrain?>(null) }
    var persona by remember { mutableStateOf<FlowNeuroEngine.FlowPersona?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var isLoaded by remember { mutableStateOf(false) }

    // Load Brain
    LaunchedEffect(Unit) {
        FlowNeuroEngine.initialize(context)
        userBrain = FlowNeuroEngine.getBrainSnapshot()
        userBrain?.let { persona = FlowNeuroEngine.getPersona(it) }
        delay(300) // Smooth entrance
        isLoaded = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Animated Neural Background
        if (userBrain != null) {
            NeuralNetworkBackground()
        }

        Scaffold(
            topBar = {
                LargeTopAppBar(
                    title = {
                        Column {
                            Text(
                                "Flow Control Center",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-0.5).sp
                                )
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            scope.launch {
                                userBrain = FlowNeuroEngine.getBrainSnapshot()
                                userBrain?.let { persona = FlowNeuroEngine.getPersona(it) }
                            }
                        }) {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            if (userBrain == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Loading Neural Matrix...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                AnimatedVisibility(
                    visible = isLoaded,
                    enter = fadeIn() + slideInVertically { it / 3 }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Spacer(Modifier.height(8.dp))

                        // 1. PERSONA HERO CARD
                        PersonaHeroCard(brain = userBrain!!, persona = persona)

                        // 2. QUICK STATS ROW
                        QuickStatsRow(brain = userBrain!!)

                        // 3. NEURAL BUBBLE CLOUD (Animated Canvas)
                        SectionHeader(
                            icon = Icons.Outlined.AutoAwesome,
                            title = "Interest Nebula",
                            subtitle = "Your neural interest map"
                        )
                        NeuralBubbleCloud(brain = userBrain!!)

                        // 4. SPIDER/RADAR CHART
                        SectionHeader(
                            icon = Icons.Outlined.TrackChanges,
                            title = "Cognitive Fingerprint",
                            subtitle = "Your content DNA"
                        )
                        AdvancedRadarChart(brain = userBrain!!)

                        // 5. TOPIC STRENGTH BARS
                        SectionHeader(
                            icon = Icons.Outlined.Equalizer,
                            title = "Topic Weights",
                            subtitle = "Full transparency on what drives your feed"
                        )
                        TopicStrengthChart(brain = userBrain!!)

                        // 6. TIME CONTEXT CARDS
                        SectionHeader(
                            icon = Icons.Outlined.Schedule,
                            title = "Temporal Patterns",
                            subtitle = "How your interests shift by time of day"
                        )
                        TimeContextCards(brain = userBrain!!)

                        // 7. CHANNEL AFFINITY
                        SectionHeader(
                            icon = Icons.Outlined.Subscriptions,
                            title = "Channel Memory",
                            subtitle = "Implicit feedback from your viewing history"
                        )
                        ChannelAffinitySection(brain = userBrain!!)

                        // 8. ALGORITHM TRANSPARENCY
                        SectionHeader(
                            icon = Icons.Outlined.Code,
                            title = "Algorithm Insights",
                            subtitle = "Under the hood"
                        )
                        AlgorithmInsightsCard(brain = userBrain!!)

                        // 9. MAINTENANCE
                        MaintenanceSection(onReset = { showResetDialog = true })

                        Spacer(Modifier.height(40.dp))
                    }
                }
            }
        }
    }

    // Reset Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            MaterialTheme.colorScheme.errorContainer,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.DeleteForever,
                        null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = { Text("Reset Neural Profile?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will permanently erase:\n\n" +
                    "• All learned preferences\n" +
                    "• Long-term personality vector\n" +
                    "• Time-context patterns\n" +
                    "• Channel affinity scores\n\n" +
                    "Your feed will return to a cold-start state.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            FlowNeuroEngine.resetBrain(context)
                            userBrain = FlowNeuroEngine.getBrainSnapshot()
                            userBrain?.let { persona = FlowNeuroEngine.getPersona(it) }
                            showResetDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.DeleteForever, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Erase Everything")
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

// ============================================================================
// SECTION HEADER
// ============================================================================

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============================================================================
// 1. ANIMATED NEURAL BACKGROUND
// ============================================================================

@Composable
private fun NeuralNetworkBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "neural")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(60000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .alpha(0.15f)
            .blur(40.dp)
    ) {
        val center = Offset(size.width * 0.7f, size.height * 0.2f)
        
        rotate(rotation, center) {
            // Gradient orbs
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = pulse), Color.Transparent),
                    center = center,
                    radius = size.width * 0.5f
                ),
                center = center,
                radius = size.width * 0.5f
            )
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tertiaryColor.copy(alpha = pulse * 0.7f), Color.Transparent),
                    center = Offset(size.width * 0.2f, size.height * 0.8f),
                    radius = size.width * 0.4f
                ),
                center = Offset(size.width * 0.2f, size.height * 0.8f),
                radius = size.width * 0.4f
            )
        }
    }
}

// ============================================================================
// 2. PERSONA HERO CARD
// ============================================================================

@Composable
private fun PersonaHeroCard(
    brain: FlowNeuroEngine.UserBrain,
    persona: FlowNeuroEngine.FlowPersona?
) {
    val displayPersona = persona ?: FlowNeuroEngine.FlowPersona.INITIATE
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.05f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Persona Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        displayPersona.icon,
                        style = MaterialTheme.typography.displayMedium
                    )
                }
                
                Spacer(Modifier.height(16.dp))
                
                Text(
                    displayPersona.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(Modifier.height(8.dp))
                
                Text(
                    displayPersona.description,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                Spacer(Modifier.height(16.dp))
                
                // Experience Level Bar
                val progress = (brain.totalInteractions / 500f).coerceIn(0f, 1f)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${brain.totalInteractions} interactions • Level ${(brain.totalInteractions / 100) + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ============================================================================
// 3. QUICK STATS ROW
// ============================================================================

@Composable
private fun QuickStatsRow(brain: FlowNeuroEngine.UserBrain) {
    val currentVector = getCurrentContextVector(brain)
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickStatCard(
            modifier = Modifier.weight(1f),
            value = brain.totalInteractions.toString(),
            label = "Interactions",
            icon = Icons.Outlined.TouchApp,
            color = MaterialTheme.colorScheme.primary
        )
        QuickStatCard(
            modifier = Modifier.weight(1f),
            value = brain.globalVector.topics.size.toString(),
            label = "Topics",
            icon = Icons.Outlined.Category,
            color = MaterialTheme.colorScheme.secondary
        )
        QuickStatCard(
            modifier = Modifier.weight(1f),
            value = brain.channelScores.size.toString(),
            label = "Channels",
            icon = Icons.Outlined.VideoLibrary,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

@Composable
private fun QuickStatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============================================================================
// 4. NEURAL BUBBLE CLOUD (Animated Canvas)
// ============================================================================

// ============================================================================
// 4. NEURAL BUBBLE CLOUD (Animated Canvas with Physics)
// ============================================================================

private class BubbleState(
    val topic: String,
    val score: Double,
    var x: Float,
    var y: Float,
    val radius: Float,
    val color: Color,
    var velocityX: Float = 0f,
    var velocityY: Float = 0f
)

@Composable
private fun NeuralBubbleCloud(brain: FlowNeuroEngine.UserBrain) {
    val topics = brain.globalVector.topics.entries
        .sortedByDescending { it.value }
        .take(12)
    
    if (topics.isEmpty()) {
        EmptyStateCard("Watch some videos to populate your interest nebula!")
        return
    }
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val density = LocalDensity.current
    
    // Initialize bubbles with random positions
    val bubbles = remember(topics) {
        topics.mapIndexed { index, entry ->
            val colorIndex = index % 3
            val color = when (colorIndex) {
                0 -> primaryColor
                1 -> secondaryColor
                else -> tertiaryColor
            }
            // More dynamic sizing: 40dp base + up to 80dp bonus based on score
            val baseRadius = 40f + (entry.value.toFloat() * 80f)
            
            BubbleState(
                topic = entry.key,
                score = entry.value,
                x = 0f, // Will be set in layout
                y = 0f,
                radius = baseRadius,
                color = color
            )
        }
    }
    
    // Physics Loop
    var time by remember { mutableLongStateOf(0L) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(350.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val width = constraints.maxWidth.toFloat()
            val height = constraints.maxHeight.toFloat()
            val centerX = width / 2f
            val centerY = height / 2f
            
            // Initialize positions centered if first run
            LaunchedEffect(width, height) {
                bubbles.forEach { bubble ->
                    if (bubble.x == 0f) {
                        bubble.x = centerX + Random.nextFloat() * 100f - 50f
                        bubble.y = centerY + Random.nextFloat() * 100f - 50f
                    }
                }
            }
            
            // Physics Engine
            LaunchedEffect(Unit) {
                while (true) {
                    withFrameNanos { nanos ->
                        time = nanos
                        
                        // Physics Constants
                        val repulsionStrength = 1500f
                        val centerPullStrength = 0.02f
                        val damping = 0.95f
                        val maxSpeed = 3f
                        
                        // 1. Apply Forces
                        for (i in bubbles.indices) {
                            val b1 = bubbles[i]
                            
                            // Center Pull (Gravity)
                            val dxCenter = centerX - b1.x
                            val dyCenter = centerY - b1.y
                            b1.velocityX += dxCenter * centerPullStrength * 0.1f
                            b1.velocityY += dyCenter * centerPullStrength * 0.1f
                            
                            // Repulsion from other bubbles
                            for (j in bubbles.indices) {
                                if (i == j) continue
                                val b2 = bubbles[j]
                                val dx = b1.x - b2.x
                                val dy = b1.y - b2.y
                                val distSq = dx*dx + dy*dy
                                val minDist = b1.radius + b2.radius + 10f // 10f padding
                                
                                if (distSq < minDist * minDist && distSq > 0.1f) {
                                    val dist = sqrt(distSq)
                                    val overlap = minDist - dist
                                    val force = overlap * 0.5f // Spring force
                                    
                                    val fx = (dx / dist) * force
                                    val fy = (dy / dist) * force
                                    
                                    b1.velocityX += fx
                                    b1.velocityY += fy
                                }
                            }
                        }
                        
                        // 2. Update Positions
                        bubbles.forEach { b ->
                            // Apply damping
                            b.velocityX *= damping
                            b.velocityY *= damping
                            
                            // Limit speed
                            b.velocityX = b.velocityX.coerceIn(-maxSpeed, maxSpeed)
                            b.velocityY = b.velocityY.coerceIn(-maxSpeed, maxSpeed)
                            
                            b.x += b.velocityX
                            b.y += b.velocityY
                            
                            // Boundary checks (keep inside box with padding)
                            val padding = b.radius
                            if (b.x < padding) { b.x = padding; b.velocityX *= -0.5f }
                            if (b.x > width - padding) { b.x = width - padding; b.velocityX *= -0.5f }
                            if (b.y < padding) { b.y = padding; b.velocityY *= -0.5f }
                            if (b.y > height - padding) { b.y = height - padding; b.velocityY *= -0.5f }
                        }
                    }
                }
            }
            
            // Draw
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Ensure recomposition on physics update
                val t = time 
                
                bubbles.forEach { bubble ->
                    // Draw glow
                    drawCircle(
                        color = bubble.color.copy(alpha = 0.2f),
                        radius = bubble.radius * 1.2f,
                        center = Offset(bubble.x, bubble.y)
                    )
                    
                    // Draw bubble body
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                bubble.color.copy(alpha = 0.8f),
                                bubble.color.copy(alpha = 0.4f)
                            ),
                            center = Offset(bubble.x - bubble.radius * 0.3f, bubble.y - bubble.radius * 0.3f),
                            radius = bubble.radius * 1.5f
                        ),
                        radius = bubble.radius,
                        center = Offset(bubble.x, bubble.y)
                    )
                    
                    // Draw highlight
                    drawCircle(
                        color = Color.White.copy(alpha = 0.3f),
                        radius = bubble.radius * 0.3f,
                        center = Offset(bubble.x - bubble.radius * 0.3f, bubble.y - bubble.radius * 0.3f)
                    )
                }
                
                // Draw connections
                for (i in bubbles.indices) {
                    for (j in i + 1 until bubbles.size) {
                        val b1 = bubbles[i]
                        val b2 = bubbles[j]
                        val dx = b1.x - b2.x
                        val dy = b1.y - b2.y
                        val dist = sqrt(dx*dx + dy*dy)
                        
                        if (dist < 200f) {
                            val alpha = (1f - dist / 200f) * 0.15f
                            drawLine(
                                color = b1.color.copy(alpha = alpha),
                                start = Offset(b1.x, b1.y),
                                end = Offset(b2.x, b2.y),
                                strokeWidth = 1f
                            )
                        }
                    }
                }
            }
            
            // Labels (Overlay Composable for text crispness)
            bubbles.forEach { bubble ->
                // Ensure recomposition
                val t = time 
                
                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { (bubble.x - bubble.radius).toDp() },
                            y = with(density) { (bubble.y - bubble.radius).toDp() }
                        )
                        .size(with(density) { (bubble.radius * 2).toDp() }),
                    contentAlignment = Alignment.Center
                ) {
                    if (bubble.radius > 40f) {
                        Text(
                            bubble.topic.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = (10 + (bubble.score * 4)).sp,
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    offset = Offset(0f, 2f),
                                    blurRadius = 4f
                                )
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 5. ADVANCED RADAR CHART
// ============================================================================

@Composable
private fun AdvancedRadarChart(brain: FlowNeuroEngine.UserBrain) {
    val currentVector = getCurrentContextVector(brain)
    val personalityVector = brain.globalVector
    
    val labels = listOf("Pacing", "Complexity", "Duration", "Live", "Breadth")
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.onSurface
    
    // Animation
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "radar"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.1f),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = minOf(size.width, size.height) / 2 * 0.85f
                val angleStep = (2 * PI / labels.size).toFloat()
                
                // Draw gradient background rings
                for (i in 4 downTo 1) {
                    val ringRadius = radius * (i / 4f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.02f * i),
                                Color.Transparent
                            ),
                            center = center,
                            radius = ringRadius
                        ),
                        radius = ringRadius,
                        center = center
                    )
                    drawCircle(
                        color = surfaceColor.copy(alpha = 0.1f),
                        radius = ringRadius,
                        center = center,
                        style = Stroke(width = 1f)
                    )
                }
                
                // Draw axis lines with labels
                for (i in labels.indices) {
                    val angle = i * angleStep - (PI / 2).toFloat()
                    val endPoint = Offset(
                        center.x + radius * cos(angle),
                        center.y + radius * sin(angle)
                    )
                    drawLine(
                        color = surfaceColor.copy(alpha = 0.15f),
                        start = center,
                        end = endPoint,
                        strokeWidth = 1f
                    )
                }
                
                // Current context polygon (blue)
                val contextValues = listOf(
                    currentVector.pacing,
                    currentVector.complexity,
                    currentVector.duration,
                    currentVector.isLive,
                    (currentVector.topics.size / 30.0).coerceAtMost(1.0)
                ).map { it * animatedProgress }
                
                drawRadarPolygonAdvanced(
                    center = center,
                    radius = radius,
                    values = contextValues,
                    color = primaryColor,
                    angleStep = angleStep
                )
                
                // Personality polygon (purple)
                val personalityValues = listOf(
                    personalityVector.pacing,
                    personalityVector.complexity,
                    personalityVector.duration,
                    personalityVector.isLive,
                    (personalityVector.topics.size / 50.0).coerceAtMost(1.0)
                ).map { it * animatedProgress }
                
                drawRadarPolygonAdvanced(
                    center = center,
                    radius = radius,
                    values = personalityValues,
                    color = tertiaryColor,
                    angleStep = angleStep
                )
                
                // Draw data points
                contextValues.forEachIndexed { i, value ->
                    val angle = i * angleStep - (PI / 2).toFloat()
                    val point = Offset(
                        center.x + (radius * value.toFloat()) * cos(angle),
                        center.y + (radius * value.toFloat()) * sin(angle)
                    )
                    drawCircle(primaryColor, 6f, point)
                    drawCircle(Color.White, 3f, point)
                }
            }
            
            // Labels
            val labelPositions = listOf(
                Alignment.TopCenter,
                Alignment.TopEnd,
                Alignment.BottomEnd,
                Alignment.BottomStart,
                Alignment.TopStart
            )
            
            labels.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentAlignment = labelPositions[index]
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // Legend at bottom
            Row(
                modifier = Modifier.align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LegendChip("Now", primaryColor)
                LegendChip("Personality", tertiaryColor)
            }
        }
    }
}

private fun DrawScope.drawRadarPolygonAdvanced(
    center: Offset,
    radius: Float,
    values: List<Double>,
    color: Color,
    angleStep: Float
) {
    val path = Path()
    values.forEachIndexed { i, value ->
        val angle = i * angleStep - (PI / 2).toFloat()
        val r = radius * value.toFloat()
        val point = Offset(
            center.x + r * cos(angle),
            center.y + r * sin(angle)
        )
        if (i == 0) path.moveTo(point.x, point.y)
        else path.lineTo(point.x, point.y)
    }
    path.close()
    
    // Fill with gradient
    drawPath(
        path = path,
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.4f), color.copy(alpha = 0.1f)),
            center = center,
            radius = radius
        )
    )
    
    // Stroke
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

@Composable
private fun LegendChip(text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

// ============================================================================
// 6. TOPIC STRENGTH BARS
// ============================================================================

@Composable
private fun TopicStrengthChart(brain: FlowNeuroEngine.UserBrain) {
    val topics = brain.globalVector.topics.entries
        .sortedByDescending { it.value }
        .take(15)
    
    if (topics.isEmpty()) {
        EmptyStateCard("No topics tracked yet. Start watching!")
        return
    }
    
    val maxScore = topics.maxOfOrNull { it.value } ?: 1.0
    val primaryColor = MaterialTheme.colorScheme.primary
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            topics.forEachIndexed { index, entry ->
                val normalizedValue = (entry.value / maxScore).toFloat()
                val barColor = when {
                    index < 3 -> primaryColor
                    index < 7 -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.tertiary
                }
                
                val animatedWidth by animateFloatAsState(
                    targetValue = normalizedValue,
                    animationSpec = tween(800, delayMillis = index * 50),
                    label = "bar$index"
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        entry.key.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(100.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(Modifier.width(12.dp))
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(barColor.copy(alpha = 0.1f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedWidth)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            barColor.copy(alpha = 0.6f),
                                            barColor
                                        )
                                    )
                                )
                        )
                    }
                    
                    Spacer(Modifier.width(8.dp))
                    
                    Text(
                        String.format("%.1f%%", entry.value * 100),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(48.dp),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

// ============================================================================
// 7. TIME CONTEXT CARDS
// ============================================================================

@Composable
private fun TimeContextCards(brain: FlowNeuroEngine.UserBrain) {
    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val currentPeriod = when (currentHour) {
        in 6..11 -> 0
        in 12..17 -> 1
        in 18..23 -> 2
        else -> 3
    }
    
    val periods = listOf(
        Triple("Morning", "6AM - 12PM", brain.morningVector),
        Triple("Afternoon", "12PM - 6PM", brain.afternoonVector),
        Triple("Evening", "6PM - 12AM", brain.eveningVector),
        Triple("Night", "12AM - 6AM", brain.nightVector)
    )
    
    val icons = listOf("🌅", "☀️", "🌆", "🌙")
    
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(periods.size) { index ->
            val (name, time, vector) = periods[index]
            val isActive = index == currentPeriod
            val topTopic = vector.topics.entries.maxByOrNull { it.value }?.key ?: "None"
            
            Card(
                modifier = Modifier
                    .width(140.dp)
                    .then(
                        if (isActive) Modifier.border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(20.dp)
                        ) else Modifier
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(icons[index], style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                    )
                    Text(
                        time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            topTopic.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${vector.topics.size} topics",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// ============================================================================
// 8. CHANNEL AFFINITY SECTION
// ============================================================================

@Composable
private fun ChannelAffinitySection(brain: FlowNeuroEngine.UserBrain) {
    val channels = brain.channelScores.entries
        .sortedByDescending { it.value }
        .take(10)
    
    if (channels.isEmpty()) {
        EmptyStateCard("Channel preferences will appear as you watch videos.")
        return
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            channels.forEach { (channelId, score) ->
                val sentiment = when {
                    score > 0.7 -> Pair("🟢", "Positive")
                    score > 0.4 -> Pair("🟡", "Neutral")
                    else -> Pair("🔴", "Negative")
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(sentiment.first)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            channelId.take(20) + if (channelId.length > 20) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                        Text(
                            sentiment.second,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        String.format("%.0f%%", score * 100),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                if (channels.indexOf(channels.find { it.key == channelId }) < channels.size - 1) {
                    Divider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

// ============================================================================
// 9. ALGORITHM INSIGHTS
// ============================================================================

@Composable
private fun AlgorithmInsightsCard(brain: FlowNeuroEngine.UserBrain) {
    val context = LocalContext.current
    var discoveryQueries by remember { mutableStateOf<List<String>>(emptyList()) }
    
    LaunchedEffect(brain) {
        discoveryQueries = FlowNeuroEngine.generateDiscoveryQueries()
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Discovery Queries
            Text(
                "Current Discovery Queries",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "These are the queries FlowNeuro generates to find your next videos:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(discoveryQueries) { query ->
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "\"$query\"",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(20.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(Modifier.height(20.dp))
            
            // Algorithm stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AlgorithmStat("Pacing", String.format("%.2f", brain.globalVector.pacing))
                AlgorithmStat("Duration", String.format("%.2f", brain.globalVector.duration))
                AlgorithmStat("Complexity", String.format("%.2f", brain.globalVector.complexity))
                AlgorithmStat("Live Factor", String.format("%.2f", brain.globalVector.isLive))
            }
        }
    }
}

@Composable
private fun AlgorithmStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ============================================================================
// 10. MAINTENANCE SECTION
// ============================================================================

@Composable
private fun MaintenanceSection(onReset: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onReset() }
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.DeleteForever,
                    null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Reset Neural Profile",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Erase all learned preferences and start fresh",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============================================================================
// HELPER COMPONENTS
// ============================================================================

@Composable
private fun EmptyStateCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.Explore,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Helper function to get the correct vector for "Now"
private fun getCurrentContextVector(brain: FlowNeuroEngine.UserBrain): FlowNeuroEngine.ContentVector {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 6..11 -> brain.morningVector
        in 12..17 -> brain.afternoonVector
        in 18..23 -> brain.eveningVector
        else -> brain.nightVector
    }
}
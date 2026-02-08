package com.flow.youtube.ui.screens.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.flow.youtube.R
import com.flow.youtube.data.recommendation.FlowNeuroEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var selectedTopics by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }
    
    var showContent by remember { mutableStateOf(false) }
    val headerAlpha by animateFloatAsState(
        targetValue = if (showContent) 1f else 0f,
        animationSpec = tween(800, easing = EaseOutCubic),
        label = "headerAlpha"
    )
    
    LaunchedEffect(Unit) {
        delay(100)
        showContent = true
    }
    
    val canContinue = selectedTopics.size >= 3
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // =============================================
            // HEADER SECTION
            // =============================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp)
                    .alpha(headerAlpha),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_notification_logo),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    text = stringResource(R.string.welcome_to_flow),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = stringResource(R.string.onboarding_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = stringResource(R.string.onboarding_instructions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (canContinue) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            if (canContinue) Icons.Default.Check else Icons.Outlined.TouchApp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (canContinue) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (canContinue) 
                                stringResource(R.string.selected_count_template, selectedTopics.size)
                            else 
                                stringResource(R.string.selected_with_more_needed_template, selectedTopics.size, 3 - selectedTopics.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (canContinue) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // =============================================
            // TOPIC CATEGORIES LIST
            // =============================================
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(
                    items = FlowNeuroEngine.TOPIC_CATEGORIES,
                    key = { it.name }
                ) { category ->
                    TopicCategoryCard(
                        category = category,
                        selectedTopics = selectedTopics,
                        onTopicToggle = { topic ->
                            selectedTopics = if (selectedTopics.contains(topic)) {
                                selectedTopics - topic
                            } else {
                                selectedTopics + topic
                            }
                        }
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
        
        // =============================================
        // FLOATING CONTINUE BUTTON
        // =============================================
        AnimatedVisibility(
            visible = showContent,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            FlowNeuroEngine.completeOnboarding(context, emptySet())
                            onComplete()
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.btn_skip_for_now),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        if (canContinue && !isLoading) {
                            isLoading = true
                            coroutineScope.launch {
                                FlowNeuroEngine.completeOnboarding(context, selectedTopics)
                                onComplete()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = canContinue && !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_btn_continue),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicCategoryCard(
    category: FlowNeuroEngine.TopicCategory,
    selectedTopics: Set<String>,
    onTopicToggle: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }
    val selectedCount = category.topics.count { selectedTopics.contains(it) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = category.icon,
                    fontSize = 28.sp
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(getCategoryNameResId(category.name)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (selectedCount > 0) {
                        Text(
                            text = stringResource(R.string.selected_count_template, selectedCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = stringResource(
                            if (isExpanded) R.string.cd_collapse_category else R.string.cd_expand_category
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Topics Grid
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    category.topics.forEach { topic ->
                        TopicChip(
                            topic = topic,
                            isSelected = selectedTopics.contains(topic),
                            onClick = { onTopicToggle(topic) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicChip(
    topic: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "chipScale"
    )
    
    Surface(
        onClick = onClick,
        modifier = Modifier.scale(scale),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) 
            MaterialTheme.colorScheme.primaryContainer 
        else 
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = if (isSelected) 
            androidx.compose.foundation.BorderStroke(
                2.dp, 
                MaterialTheme.colorScheme.primary
            ) 
        else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Text(
                text = topic,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) 
                    MaterialTheme.colorScheme.onPrimaryContainer 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun getCategoryNameResId(categoryName: String): Int {
    return when {
        categoryName.contains("Gaming") -> R.string.category_gaming
        categoryName.contains("Music") -> R.string.category_music
        categoryName.contains("Technology") -> R.string.category_technology
        categoryName.contains("Entertainment") -> R.string.category_entertainment
        categoryName.contains("Education") -> R.string.category_education
        categoryName.contains("Health & Fitness") -> R.string.category_health_fitness
        categoryName.contains("Lifestyle") -> R.string.category_lifestyle
        categoryName.contains("Creative") -> R.string.category_creative
        categoryName.contains("Science & Nature") -> R.string.category_science_nature
        else -> R.string.category_news_current_events
    }
}

package com.flow.youtube.ui.screens.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.youtube.R
import com.flow.youtube.data.recommendation.FlowNeuroEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MIN_TOPICS = 3
private const val STAGGER_DELAY_MS = 60L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    var selectedTopics by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }

    var visibleItems by remember { mutableStateOf(0) }
    val totalCategories = FlowNeuroEngine.TOPIC_CATEGORIES.size

    LaunchedEffect(Unit) {
        for (i in 1..totalCategories) {
            delay(STAGGER_DELAY_MS)
            visibleItems = i
        }
    }

    val canContinue = selectedTopics.size >= MIN_TOPICS
    val selectionProgress = (selectedTopics.size.toFloat() / MIN_TOPICS).coerceAtMost(1f)

    val animatedProgress by animateFloatAsState(
        targetValue = selectionProgress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "progress"
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            OnboardingBottomBar(
                canContinue = canContinue,
                isLoading = isLoading,
                progress = animatedProgress,
                selectedCount = selectedTopics.size,
                onContinue = {
                    if (canContinue && !isLoading) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isLoading = true
                        coroutineScope.launch {
                            FlowNeuroEngine.completeOnboarding(context, selectedTopics)
                            onComplete()
                        }
                    }
                },
                onSkip = {
                    coroutineScope.launch {
                        FlowNeuroEngine.completeOnboarding(context, emptySet())
                        onComplete()
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                OnboardingHeader(
                    modifier = Modifier.animateItem()
                )
            }

            items(
                items = FlowNeuroEngine.TOPIC_CATEGORIES,
                key = { it.name }
            ) { category ->
                val index = FlowNeuroEngine.TOPIC_CATEGORIES.indexOf(category)
                val isVisible = index < visibleItems

                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(
                        animationSpec = tween(300, easing = EaseOutCubic)
                    ) + slideInVertically(
                        initialOffsetY = { it / 3 },
                        animationSpec = tween(350, easing = EaseOutCubic)
                    ),
                ) {
                    TopicCategoryCard(
                        category = category,
                        selectedTopics = selectedTopics,
                        initiallyExpanded = index == 0,
                        onTopicToggle = { topic ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedTopics = if (selectedTopics.contains(topic)) {
                                selectedTopics - topic
                            } else {
                                selectedTopics + topic
                            }
                        }
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// HEADER
// ═══════════════════════════════════════════════════════
@Composable
private fun OnboardingHeader(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_notification_logo),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.welcome_to_flow),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.onboarding_instructions),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

// ═══════════════════════════════════════════════════════
// BOTTOM BAR
// ═══════════════════════════════════════════════════════
@Composable
private fun OnboardingBottomBar(
    canContinue: Boolean,
    isLoading: Boolean,
    progress: Float,
    selectedCount: Int,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        color = surfaceColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = progress)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (canContinue) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (canContinue) {
                            stringResource(
                                R.string.selected_count_template,
                                selectedCount
                            )
                        } else {
                            stringResource(
                                R.string.selected_with_more_needed_template,
                                selectedCount,
                                MIN_TOPICS - selectedCount
                            )
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (canContinue)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!canContinue) {
                        Text(
                            text = stringResource(R.string.btn_skip_for_now),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.6f
                            ),
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(
                                    interactionSource = remember {
                                        MutableInteractionSource()
                                    },
                                    indication = LocalIndication.current,
                                    onClick = onSkip
                                )
                                .padding(vertical = 4.dp)
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onContinue,
                    enabled = canContinue && !isLoading,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme
                            .surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme
                            .onSurfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.onboarding_btn_continue),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// CATEGORY CARD
// ═══════════════════════════════════════════════════════
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicCategoryCard(
    category: FlowNeuroEngine.TopicCategory,
    selectedTopics: Set<String>,
    initiallyExpanded: Boolean,
    onTopicToggle: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    val selectedCount = category.topics.count { selectedTopics.contains(it) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(
                            if (isExpanded) R.string.cd_collapse_category
                            else R.string.cd_expand_category
                        )
                    ) { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = category.icon,
                    fontSize = 24.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            getCategoryNameResId(category.name)
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (selectedCount > 0) {
                        Text(
                            text = stringResource(
                                R.string.selected_count_template,
                                selectedCount
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Icon(
                    if (isExpanded) Icons.Outlined.ExpandLess
                    else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .alpha(0.6f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Chips
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(
                    animationSpec = tween(250, easing = EaseOutCubic)
                ) + fadeIn(animationSpec = tween(200)),
                exit = shrinkVertically(
                    animationSpec = tween(200, easing = EaseInCubic)
                ) + fadeOut(animationSpec = tween(150))
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

// ═══════════════════════════════════════════════════════
// TOPIC CHIP
// ═══════════════════════════════════════════════════════
@Composable
private fun TopicChip(
    topic: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = tween(200),
        label = "chipColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.onPrimaryContainer
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "chipContentColor"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = if (isSelected) BorderStroke(
            1.5.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        ) else BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.semantics {
            stateDescription = if (isSelected) "Selected" else "Not selected"
            role = Role.Checkbox
        }
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 8.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn(tween(150)) + expandHorizontally(
                    expandFrom = Alignment.Start
                ),
                exit = fadeOut(tween(100)) + shrinkHorizontally(
                    shrinkTowards = Alignment.Start
                )
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = topic,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Medium
                    else FontWeight.Normal,
                color = contentColor,
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
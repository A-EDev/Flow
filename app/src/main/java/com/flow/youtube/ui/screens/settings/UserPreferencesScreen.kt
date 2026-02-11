package com.flow.youtube.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.flow.youtube.R
import com.flow.youtube.data.recommendation.FlowNeuroEngine
import com.flow.youtube.data.local.PlayerPreferences
import com.flow.youtube.ui.theme.extendedColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun UserPreferencesScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val playerPreferences = remember { PlayerPreferences(context) }
    val isShortsShelfEnabled by playerPreferences.shortsShelfEnabled.collectAsState(initial = true)
    
    // State
    var preferredTopics by remember { mutableStateOf<Set<String>>(emptySet()) }
    var blockedTopics by remember { mutableStateOf<Set<String>>(emptySet()) }
    var newBlockedTopic by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    
    val pagerState = rememberPagerState(pageCount = { 3 })
    
    // Load data on first composition
    LaunchedEffect(Unit) {
        preferredTopics = FlowNeuroEngine.getPreferredTopics()
        blockedTopics = FlowNeuroEngine.getBlockedTopics()
        isLoading = false
    }
    
    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.content_preferences_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, stringResource(R.string.btn_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
                
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 16.dp,
                    indicator = { tabPositions ->
                        if (pagerState.currentPage < tabPositions.size) {
                            TabRowDefaults.Indicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                color = MaterialTheme.colorScheme.primary,
                                height = 3.dp
                            )
                        }
                    },
                    divider = {}
                ) {
                    val tabs = listOf(
                        Triple(stringResource(R.string.interests_tab), Icons.Outlined.Favorite, 0),
                        Triple(stringResource(R.string.blocked_tab), Icons.Outlined.Block, 1),
                        Triple("Display", Icons.Outlined.ViewQuilt, 2)
                    )
                    
                    for ((title, icon, index) in tabs) {
                        val isSelected = pagerState.currentPage == index
                        Tab(
                            selected = isSelected,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            icon = {
                                Icon(
                                    icon, 
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            verticalAlignment = Alignment.Top
        ) { pageIndex ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (pageIndex) {
                    0 -> {
                        // =============================================
                        // PREFERRED TOPICS PAGE
                        // =============================================
                        item {
                            InfoCard(
                                icon = Icons.Outlined.TipsAndUpdates,
                                title = stringResource(R.string.your_interests_title),
                                description = stringResource(R.string.your_interests_desc),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                iconTint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        
                        if (preferredTopics.isNotEmpty()) {
                            item {
                                PreferencesSectionHeader(
                                    title = stringResource(R.string.currently_following),
                                    subtitle = stringResource(R.string.topics_count_template, preferredTopics.size)
                                )
                            }
                            
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ) {
                                    FlowRow(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (topic in preferredTopics) {
                                            PreferredTopicChip(
                                                topic = topic,
                                                onRemove = {
                                                    coroutineScope.launch {
                                                        FlowNeuroEngine.removePreferredTopic(context, topic)
                                                        preferredTopics = FlowNeuroEngine.getPreferredTopics()
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        item {
                            PreferencesSectionHeader(
                                title = stringResource(R.string.add_topics),
                                subtitle = stringResource(R.string.browse_by_category)
                            )
                        }
                        
                        items(
                            items = FlowNeuroEngine.TOPIC_CATEGORIES,
                            key = { it.name }
                        ) { category ->
                            TopicCategoryExpandableCard(
                                category = category,
                                selectedTopics = preferredTopics,
                                onTopicToggle = { topic ->
                                    coroutineScope.launch {
                                        if (preferredTopics.contains(topic)) {
                                            FlowNeuroEngine.removePreferredTopic(context, topic)
                                        } else {
                                            FlowNeuroEngine.addPreferredTopic(context, topic)
                                        }
                                        preferredTopics = FlowNeuroEngine.getPreferredTopics()
                                    }
                                }
                            )
                        }
                    }
                    
                    1 -> {
                        // =============================================
                        // BLOCKED TOPICS PAGE
                        // =============================================
                        item {
                            InfoCard(
                                icon = Icons.Outlined.Security,
                                title = stringResource(R.string.hidden_content_title),
                                description = stringResource(R.string.hidden_content_desc),
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                iconTint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        
                        item {
                            PreferencesSectionHeader(
                                title = stringResource(R.string.block_topic_title),
                                subtitle = stringResource(R.string.enter_keywords_to_hide)
                            )
                        }
                        
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    OutlinedTextField(
                                        value = newBlockedTopic,
                                        onValueChange = { newBlockedTopic = it },
                                        label = { Text(stringResource(R.string.block_topic_placeholder)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true,
                                        trailingIcon = {
                                            IconButton(
                                                onClick = {
                                                    if (newBlockedTopic.isNotBlank()) {
                                                        coroutineScope.launch {
                                                            FlowNeuroEngine.addBlockedTopic(context, newBlockedTopic.trim().lowercase())
                                                            blockedTopics = FlowNeuroEngine.getBlockedTopics()
                                                            newBlockedTopic = ""
                                                            focusManager.clearFocus()
                                                        }
                                                    }
                                                },
                                                enabled = newBlockedTopic.isNotBlank()
                                            ) {
                                                Icon(
                                                    Icons.Default.AddCircle,
                                                    contentDescription = stringResource(R.string.create),
                                                    tint = if (newBlockedTopic.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = {
                                            if (newBlockedTopic.isNotBlank()) {
                                                coroutineScope.launch {
                                                    FlowNeuroEngine.addBlockedTopic(context, newBlockedTopic.trim().lowercase())
                                                    blockedTopics = FlowNeuroEngine.getBlockedTopics()
                                                    newBlockedTopic = ""
                                                    focusManager.clearFocus()
                                                }
                                            }
                                        })
                                    )
                                }
                            }
                        }
                        
                        item {
                            PreferencesSectionHeader(
                                title = stringResource(R.string.quick_add),
                                subtitle = stringResource(R.string.common_topics_to_block)
                            )
                        }
                        
                        item {
                            val suggestions = listOf(
                                "ASMR", "Unboxing", "Reaction", "Vlogs", "News", "Politics", "Gaming",
                                "clickbait", "drama", "gossip", "challenge", "family vlog"
                            ).filter { !blockedTopics.contains(it) }
                            
                            if (suggestions.isNotEmpty()) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (topic in suggestions) {
                                        SuggestionChip(
                                            topic = topic,
                                            onClick = {
                                                coroutineScope.launch {
                                                    FlowNeuroEngine.addBlockedTopic(context, topic.lowercase())
                                                    blockedTopics = FlowNeuroEngine.getBlockedTopics()
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        
                        if (blockedTopics.isNotEmpty()) {
                            item {
                                PreferencesSectionHeader(
                                    title = stringResource(R.string.currently_blocked),
                                    subtitle = stringResource(R.string.topics_blocked_count_plural, blockedTopics.size, if (blockedTopics.size > 1) "s" else "")
                                )
                            }
                            
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ) {
                                    FlowRow(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (topic in blockedTopics) {
                                            BlockedTopicChip(
                                                topic = topic,
                                                onRemove = {
                                                    coroutineScope.launch {
                                                        FlowNeuroEngine.removeBlockedTopic(context, topic)
                                                        blockedTopics = FlowNeuroEngine.getBlockedTopics()
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    2 -> {
                        // =============================================
                        // DISPLAY PAGE
                        // =============================================
                         item {
                            InfoCard(
                                icon = Icons.Outlined.ViewQuilt,
                                title = "Display Settings",
                                description = "Fine-tune how your subscription feed looks and feels.",
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                iconTint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                         item {
                            PreferencesSectionHeader(
                                title = "Layout & Visualization"
                            )
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Column {
                                    SettingsSwitchItem(
                                         title = "Show Shorts shelf in Grid",
                                         subtitle = "Display the horizontal Shorts shelf in your subscription feed.",
                                         checked = isShortsShelfEnabled,
                                         onCheckedChange = { 
                                             coroutineScope.launch {
                                                 playerPreferences.setShortsShelfEnabled(it)
                                             }
                                         }
                                    )
                                }
                            }
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}


@Composable
private fun InfoCard(
    icon: ImageVector,
    title: String,
    description: String,
    containerColor: Color,
    iconTint: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = containerColor.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, containerColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = containerColor.copy(alpha = 0.2f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun PreferencesSectionHeader(
    title: String,
    subtitle: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.5.sp
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicCategoryExpandableCard(
    category: FlowNeuroEngine.TopicCategory,
    selectedTopics: Set<String>,
    onTopicToggle: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val selectedCount = category.topics.count { selectedTopics.contains(it) }
    
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "rotation"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            if (selectedCount > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) 
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = category.icon,
                            fontSize = 22.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.name.replace(Regex("^[^a-zA-Z]+"), "").trim(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (selectedCount > 0) 
                            stringResource(R.string.selected_count_template, selectedCount) 
                        else 
                            stringResource(R.string.topics_count_template, category.topics.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selectedCount > 0) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selectedCount > 0) FontWeight.Bold else FontWeight.Normal
                    )
                }
                
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer(rotationZ = rotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (topic in category.topics) {
                        SelectableTopicChip(
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
private fun SelectableTopicChip(
    topic: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primaryContainer 
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        label = "bg"
    )
    val contentColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer 
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "content"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = if (isSelected) 
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) 
        else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isSelected) {
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
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

@Composable
private fun PreferredTopicChip(
    topic: String,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Outlined.Favorite,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = topic,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.desc_remove_topic, topic),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun BlockedTopicChip(
    topic: String,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Outlined.Block,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = topic,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.desc_unblock_topic, topic),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SuggestionChip(
    topic: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = topic,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            thumbContent = if (checked) {
                {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            } else null
        )
    }
}

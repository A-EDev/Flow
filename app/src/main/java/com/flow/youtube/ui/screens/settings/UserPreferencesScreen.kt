package com.flow.youtube.ui.screens.settings

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.flow.youtube.R
import com.flow.youtube.data.recommendation.FlowNeuroEngine
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserPreferencesScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    // State
    var preferredTopics by remember { mutableStateOf<Set<String>>(emptySet()) }
    var blockedTopics by remember { mutableStateOf<Set<String>>(emptySet()) }
    var newBlockedTopic by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var selectedSection by remember { mutableStateOf(0) } // 0 = Preferred, 1 = Blocked
    
    // Load data on first composition
    LaunchedEffect(Unit) {
        preferredTopics = FlowNeuroEngine.getPreferredTopics()
        blockedTopics = FlowNeuroEngine.getBlockedTopics()
        isLoading = false
    }
    
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.content_preferences_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.content_preferences_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.btn_back))
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
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
            // =============================================
            // SECTION TABS
            // =============================================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SectionTab(
                        title = stringResource(R.string.interests_tab),
                        subtitle = stringResource(R.string.topics_count_template, preferredTopics.size),
                        icon = Icons.Outlined.Favorite,
                        isSelected = selectedSection == 0,
                        onClick = { selectedSection = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    SectionTab(
                        title = stringResource(R.string.blocked_tab),
                        subtitle = stringResource(R.string.hidden_count_template, blockedTopics.size),
                        icon = Icons.Outlined.Block,
                        isSelected = selectedSection == 1,
                        onClick = { selectedSection = 1 },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            // =============================================
            // CONTENT BASED ON SELECTED SECTION
            // =============================================
            when (selectedSection) {
                0 -> {
                    // =============================================
                    // PREFERRED TOPICS SECTION
                    // =============================================
                    item {
                        InfoCard(
                            icon = Icons.Outlined.TipsAndUpdates,
                            title = stringResource(R.string.your_interests_title),
                            description = stringResource(R.string.your_interests_desc),
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            iconTint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    if (preferredTopics.isNotEmpty()) {
                        item {
                            PreferencesSectionHeader(
                                title = stringResource(R.string.currently_following),
                                subtitle = stringResource(R.string.tap_to_remove)
                            )
                        }
                        
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                FlowRow(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    preferredTopics.forEach { topic ->
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
                    // BLOCKED TOPICS SECTION
                    // =============================================
                    item {
                        InfoCard(
                            icon = Icons.Outlined.VisibilityOff,
                            title = stringResource(R.string.hidden_content_title),
                            description = stringResource(R.string.hidden_content_desc),
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            iconTint = MaterialTheme.colorScheme.error
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
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = newBlockedTopic,
                                    onValueChange = { newBlockedTopic = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { 
                                        Text(
                                            stringResource(R.string.block_topic_placeholder),
                                            style = MaterialTheme.typography.bodyMedium
                                        ) 
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Block,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingIcon = {
                                        if (newBlockedTopic.isNotBlank()) {
                                            IconButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        FlowNeuroEngine.addBlockedTopic(context, newBlockedTopic.trim())
                                                        blockedTopics = FlowNeuroEngine.getBlockedTopics()
                                                        newBlockedTopic = ""
                                                        focusManager.clearFocus()
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = stringResource(R.string.desc_add_topic),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            if (newBlockedTopic.isNotBlank()) {
                                                coroutineScope.launch {
                                                    FlowNeuroEngine.addBlockedTopic(context, newBlockedTopic.trim())
                                                    blockedTopics = FlowNeuroEngine.getBlockedTopics()
                                                    newBlockedTopic = ""
                                                    focusManager.clearFocus()
                                                }
                                            }
                                        }
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
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
                            "makeup", "roblox", "fortnite", "kids", "asmr", "mukbang",
                            "reaction", "prank", "tiktok", "unboxing", "slime", "toy",
                            "clickbait", "drama", "gossip", "challenge", "family vlog"
                        ).filter { !blockedTopics.contains(it) }
                        
                        if (suggestions.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                FlowRow(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    suggestions.take(12).forEach { topic ->
                                        SuggestionChip(
                                            topic = topic,
                                            onClick = {
                                                coroutineScope.launch {
                                                    FlowNeuroEngine.addBlockedTopic(context, topic)
                                                    blockedTopics = FlowNeuroEngine.getBlockedTopics()
                                                }
                                            }
                                        )
                                    }
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
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                FlowRow(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    blockedTopics.forEach { topic ->
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
            }
            
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SectionTab(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) 
            MaterialTheme.colorScheme.primaryContainer 
        else 
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (isSelected)
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) 
                        MaterialTheme.colorScheme.onPrimaryContainer 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(28.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
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
                Text(
                    text = category.icon,
                    fontSize = 24.sp
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.name.replace(Regex("^[^a-zA-Z]+"), "").trim(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (selectedCount > 0) stringResource(R.string.selected_count_template, selectedCount) else stringResource(R.string.topics_count_template, category.topics.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selectedCount > 0) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Icon(
                    if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    category.topics.forEach { topic ->
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
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) 
            MaterialTheme.colorScheme.primaryContainer 
        else 
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = if (isSelected) 
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) 
        else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AnimatedVisibility(visible = isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = topic,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) 
                    MaterialTheme.colorScheme.onPrimaryContainer 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
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
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Outlined.Favorite,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = topic,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
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
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Outlined.Block,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = topic,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
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
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
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
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = topic,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

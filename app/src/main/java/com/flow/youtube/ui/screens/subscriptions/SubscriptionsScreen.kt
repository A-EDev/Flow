package com.flow.youtube.ui.screens.subscriptions


import androidx.compose.animation.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.flow.youtube.R
import com.flow.youtube.data.model.Channel
import com.flow.youtube.data.model.Video
import com.flow.youtube.ui.components.*
import com.flow.youtube.ui.theme.extendedColors
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    onVideoClick: (Video) -> Unit,
    onShortClick: (String) -> Unit = {},
    onChannelClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SubscriptionsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // Import Launcher
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            viewModel.importNewPipeBackup(it, context)
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.importing_from_backup))
            }
        }
    }
    
    var isManagingSubs by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Initialize view model
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }
    
    val subscribedChannels = uiState.subscribedChannels
    val videos = uiState.recentVideos

    Scaffold(
        topBar = {
            if (isManagingSubs) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(androidx.compose.ui.res.stringResource(R.string.subscriptions_search_placeholder), style = MaterialTheme.typography.bodyLarge) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { isManagingSubs = false; searchQuery = "" }) {
                            Icon(Icons.Default.ArrowBack, stringResource(R.string.close))
                        }
                    },
                    actions = {
                        IconButton(onClick = { launcher.launch("application/json") }) {
                            Icon(Icons.Default.Upload, stringResource(R.string.import_newpipe_backup))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(R.string.top_bar_subscriptions_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Row {
                            IconButton(
                                onClick = { viewModel.toggleViewMode() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (uiState.isFullWidthView) Icons.Default.ViewList else Icons.Default.GridView,
                                    contentDescription = stringResource(R.string.toggle_view_mode),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            IconButton(
                                onClick = { isManagingSubs = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Search,
                                    stringResource(R.string.search_subscriptions),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            
            AnimatedContent(targetState = isManagingSubs) { manageMode ->
                if (manageMode) {
                    // MANAGEMENT MODE
                    val filteredChannels = remember(subscribedChannels, searchQuery) {
                        if (searchQuery.isBlank()) subscribedChannels
                        else subscribedChannels.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Text(
                                text = pluralStringResource(
                                    id = R.plurals.channels_count,
                                    count = filteredChannels.size,
                                    filteredChannels.size
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(filteredChannels) { channel ->
                            SubscriptionManagerItem(
                                channel = channel,
                                onClick = { 
                                    onChannelClick("https://youtube.com/channel/${channel.id}") 
                                },
                                onUnsubscribe = {
                                    scope.launch {
                                        val sub = viewModel.getSubscriptionOnce(channel.id)
                                        viewModel.unsubscribe(channel.id)
                                        val result = snackbarHostState.showSnackbar(
                                            context.getString(R.string.unsubscribed_from_template, channel.name),
                                            actionLabel = context.getString(R.string.undo),
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            sub?.let { viewModel.subscribeChannel(it) }
                                        }
                                    }
                                }
                            )
                        }
                    }
                } else {


                    // FEED MODE
                    PullToRefreshBox(
                        isRefreshing = uiState.isLoading,
                        onRefresh = { viewModel.refreshFeed() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (subscribedChannels.isEmpty()) {
                            EmptySubscriptionsState(modifier = Modifier.fillMaxSize())
                        } else {
                            LazyVerticalGrid(
                                columns = if (uiState.isFullWidthView) GridCells.Adaptive(320.dp) else GridCells.Fixed(1),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Channel Chips Row
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Column {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            LazyRow(
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(start = 16.dp, end = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                items(subscribedChannels.take(10)) { channel ->
                                                    ChannelAvatarItem(
                                                        channel = channel,
                                                        isSelected = false,
                                                        onClick = {
                                                            onChannelClick("https://youtube.com/channel/${channel.id}")
                                                        }
                                                    )
                                                }
                                            }
                                            
                                            // View All Button
                                            TextButton(
                                                onClick = { isManagingSubs = true },
                                                modifier = Modifier.padding(end = 8.dp)
                                            ) {
                                                Text(androidx.compose.ui.res.stringResource(R.string.view_all_button_label), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        
                                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    }
                                }
    
                                if (uiState.isShortsShelfEnabled && uiState.shorts.isNotEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Column {
                                            
                                            ShortsShelf(
                                                shorts = uiState.shorts,
                                                onShortClick = { short -> onShortClick(short.id) }
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            HorizontalDivider(thickness = 4.dp, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        }
                                    }
                                }
    
                                items(videos) { video ->
                                    if (uiState.isFullWidthView) {
                                        VideoCardFullWidth(
                                            video = video,
                                            onClick = { onVideoClick(video) }
                                        )
                                    } else {
                                        VideoCardHorizontal(
                                            video = video,
                                            onClick = { onVideoClick(video) }
                                        )
                                    }
                                }
                                
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Spacer(modifier = Modifier.height(80.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelAvatarItem(
    channel: Channel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .then(if (isSelected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) else Modifier),
            contentAlignment = Alignment.Center
        ) {
             AsyncImage(
                model = channel.thumbnailUrl,
                contentDescription = channel.name,
                modifier = Modifier
                    .size(if (isSelected) 48.dp else 56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            if (isSelected) {
                Box(
                    modifier = Modifier.matchParentSize().clip(CircleShape).background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, null, tint = Color.White)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = channel.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SubscriptionManagerItem(
    channel: Channel,
    onClick: () -> Unit,
    onUnsubscribe: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = channel.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = channel.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        FilledTonalButton(
            onClick = onUnsubscribe,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(androidx.compose.ui.res.stringResource(R.string.subscribed))
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.Notifications, null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun EmptySubscriptionsState(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Subscriptions,
            contentDescription = null,
            modifier = Modifier.size(80.dp).padding(bottom = 16.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Text(
            text = context.getString(R.string.no_subscriptions_yet),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = context.getString(R.string.empty_subscriptions_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.extendedColors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}


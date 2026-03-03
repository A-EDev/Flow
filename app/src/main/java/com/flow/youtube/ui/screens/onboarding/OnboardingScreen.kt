package com.flow.youtube.ui.screens.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.flow.youtube.R
import com.flow.youtube.data.local.ChannelSubscription
import com.flow.youtube.data.local.SubscriptionRepository
import com.flow.youtube.data.recommendation.FlowNeuroEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import androidx.activity.ComponentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flow.youtube.ui.screens.settings.ImportViewModel
import com.flow.youtube.ui.screens.settings.ImportProgressBanner

// ─────────────────────────────────────────────────────────────
// Constants & types
// ─────────────────────────────────────────────────────────────

private const val MIN_TOPICS = 3
private const val STAGGER_DELAY_MS = 50L

private enum class OnboardingStep(val index: Int, val label: String) {
    INTERESTS(0, "Interests"),
    CHANNELS(1, "Channels"),
    IMPORT(2, "Import")
}

data class ChannelSearchResult(
    val channelId: String,
    val name: String,
    val thumbnailUrl: String,
    val subscriberCount: Long = -1L
)

// ─────────────────────────────────────────────────────────────
// Root screen
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val subscriptionRepo = remember { SubscriptionRepository.getInstance(context) }
    val importViewModel: ImportViewModel = hiltViewModel(context as ComponentActivity)

    var currentStep by remember { mutableStateOf(OnboardingStep.INTERESTS) }

    // Step 1 — interests
    var selectedTopics by remember { mutableStateOf<Set<String>>(emptySet()) }
    var visibleCategories by remember { mutableStateOf(0) }
    val totalCategories = FlowNeuroEngine.TOPIC_CATEGORIES.size

    LaunchedEffect(Unit) {
        for (i in 1..totalCategories) {
            delay(STAGGER_DELAY_MS)
            visibleCategories = i
        }
    }

    // Step 2 — channel search
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ChannelSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var subscribedInSession by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // Step 3 — feedback
    var importMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val importState by importViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(importState) {
        when (val s = importState) {
            is ImportViewModel.State.Success -> {
                importMessage = if (s.count > 0)
                    "Imported ${s.count} ${s.label.lowercase()}"
                else
                    "${s.label} imported"
                importViewModel.dismiss()
            }
            is ImportViewModel.State.Error -> {
                importMessage = "Import failed: ${s.message}"
                importViewModel.dismiss()
            }
            else -> {}
        }
    }

    LaunchedEffect(importMessage) {
        importMessage?.let {
            snackbarHostState.showSnackbar(it)
            importMessage = null
        }
    }

    val newPipeImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { importViewModel.importNewPipe(it) } }

    val youtubeImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { importViewModel.importYouTube(it) } }

    val youtubeHistoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { importViewModel.importYouTubeWatchHistory(it) } }

    fun finish() {
        scope.launch {
            FlowNeuroEngine.completeOnboarding(context, selectedTopics)
            onComplete()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            StepIndicatorBar(currentStep = currentStep)
        },
        bottomBar = {
            OnboardingBottomBar(
                currentStep = currentStep,
                canAdvance = when (currentStep) {
                    OnboardingStep.INTERESTS -> selectedTopics.size >= MIN_TOPICS
                    else -> true
                },
                isFirstStep = currentStep == OnboardingStep.INTERESTS,
                isLastStep = currentStep == OnboardingStep.IMPORT,
                onBack = {
                    val prev = OnboardingStep.entries.getOrNull(currentStep.index - 1)
                    if (prev != null) currentStep = prev
                },
                onNext = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val next = OnboardingStep.entries.getOrNull(currentStep.index + 1)
                    if (next != null) currentStep = next else finish()
                },
                onSkip = {
                    val next = OnboardingStep.entries.getOrNull(currentStep.index + 1)
                    if (next != null) currentStep = next else finish()
                }
            )
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                val forward = targetState.index > initialState.index
                val enter = if (forward)
                    slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(250))
                else
                    slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(250))
                val exit = if (forward)
                    slideOutHorizontally(tween(250)) { -it / 4 } + fadeOut(tween(200))
                else
                    slideOutHorizontally(tween(250)) { it / 4 } + fadeOut(tween(200))
                enter togetherWith exit
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            label = "step_content"
        ) { step ->
            when (step) {
                OnboardingStep.INTERESTS -> InterestsStep(
                    selectedTopics = selectedTopics,
                    visibleCategories = visibleCategories,
                    onTopicToggle = { topic ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedTopics = if (selectedTopics.contains(topic))
                            selectedTopics - topic
                        else
                            selectedTopics + topic
                    }
                )
                OnboardingStep.CHANNELS -> ChannelsStep(
                    searchQuery = searchQuery,
                    searchResults = searchResults,
                    isSearching = isSearching,
                    subscribedInSession = subscribedInSession,
                    onQueryChange = { q ->
                        searchQuery = q
                        searchJob?.cancel()
                        if (q.isBlank()) {
                            searchResults = emptyList()
                            isSearching = false
                            return@ChannelsStep
                        }
                        searchJob = scope.launch {
                            delay(400)
                            isSearching = true
                            searchResults = searchChannels(q)
                            isSearching = false
                        }
                    },
                    onSubscribeToggle = { result ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        scope.launch {
                            if (subscribedInSession.contains(result.channelId)) {
                                subscriptionRepo.unsubscribe(result.channelId)
                                subscribedInSession = subscribedInSession - result.channelId
                            } else {
                                subscriptionRepo.subscribe(
                                    ChannelSubscription(
                                        channelId = result.channelId,
                                        channelName = result.name,
                                        channelThumbnail = result.thumbnailUrl,
                                        subscribedAt = System.currentTimeMillis()
                                    )
                                )
                                subscribedInSession = subscribedInSession + result.channelId
                            }
                        }
                    }
                )
                OnboardingStep.IMPORT -> ImportStep(
                    importState = importState,
                    onImportNewPipe = {
                        newPipeImportLauncher.launch(arrayOf("application/json"))
                    },
                    onImportYouTube = {
                        youtubeImportLauncher.launch(
                            arrayOf("text/comma-separated-values", "text/csv", "text/plain")
                        )
                    },
                    onImportYouTubeHistory = {
                        youtubeHistoryLauncher.launch(
                            arrayOf("text/html", "application/octet-stream", "*/*")
                        )
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Step indicator bar
// ─────────────────────────────────────────────────────────────

@Composable
private fun StepIndicatorBar(currentStep: OnboardingStep) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OnboardingStep.entries.forEach { step ->
                val isActive = step == currentStep
                val isPast = step.index < currentStep.index

                val trackColor by animateColorAsState(
                    targetValue = when {
                        isPast || isActive -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    animationSpec = tween(300),
                    label = "track_${step.name}"
                )
                val labelColor by animateColorAsState(
                    targetValue = when {
                        isPast || isActive -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    },
                    animationSpec = tween(300),
                    label = "label_${step.name}"
                )

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(trackColor)
                    )
                    Text(
                        text = step.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        color = labelColor
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Bottom navigation bar
// ─────────────────────────────────────────────────────────────

@Composable
private fun OnboardingBottomBar(
    currentStep: OnboardingStep,
    canAdvance: Boolean,
    isFirstStep: Boolean,
    isLastStep: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            thickness = 0.5.dp
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isFirstStep) {
                TextButton(
                    onClick = onBack,
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Back", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Skip",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onSkip
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )

                Button(
                    onClick = onNext,
                    enabled = canAdvance,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Text(
                        text = if (isLastStep) "Finish" else "Continue",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!isLastStep) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Step 1 — Interests
// ─────────────────────────────────────────────────────────────

@Composable
private fun InterestsStep(
    selectedTopics: Set<String>,
    visibleCategories: Int,
    onTopicToggle: (String) -> Unit
) {
    val remaining = (MIN_TOPICS - selectedTopics.size).coerceAtLeast(0)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            StepHeader(
                title = "What do you enjoy?",
                subtitle = if (remaining > 0)
                    "Pick at least $MIN_TOPICS topics to personalise your feed. $remaining more to go."
                else
                    "Great selection. You can always update this later in settings."
            )
        }

        items(
            items = FlowNeuroEngine.TOPIC_CATEGORIES,
            key = { it.name }
        ) { category ->
            val index = FlowNeuroEngine.TOPIC_CATEGORIES.indexOf(category)
            AnimatedVisibility(
                visible = index < visibleCategories,
                enter = fadeIn(tween(280)) + slideInVertically(tween(300)) { it / 4 },
                modifier = Modifier.animateItem()
            ) {
                CategoryCard(
                    category = category,
                    selectedTopics = selectedTopics,
                    initiallyExpanded = index < 2,
                    onTopicToggle = onTopicToggle
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────
// Step 2 — Channel search
// ─────────────────────────────────────────────────────────────

@Composable
private fun ChannelsStep(
    searchQuery: String,
    searchResults: List<ChannelSearchResult>,
    isSearching: Boolean,
    subscribedInSession: Set<String>,
    onQueryChange: (String) -> Unit,
    onSubscribeToggle: (ChannelSearchResult) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        delay(200)
        focusRequester.requestFocus()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            StepHeader(
                title = "Find channels",
                subtitle = "Search for channels you already follow and subscribe in one tap."
            )
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        "Search channels…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = if (isSearching) {
                    { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
        }

        if (searchQuery.isNotBlank() && searchResults.isEmpty() && !isSearching) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No channels found for \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                }
            }
        }

        if (searchQuery.isBlank()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Type a channel name to search",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }

        items(searchResults, key = { it.channelId }) { result ->
            ChannelResultRow(
                result = result,
                isSubscribed = subscribedInSession.contains(result.channelId),
                onToggle = { onSubscribeToggle(result) }
            )
        }

        if (subscribedInSession.isNotEmpty()) {
            item {
                Text(
                    text = "${subscribedInSession.size} channel${if (subscribedInSession.size > 1) "s" else ""} added",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ChannelResultRow(
    result: ChannelSearchResult,
    isSubscribed: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = result.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.ic_notification_logo),
            error = painterResource(R.drawable.ic_notification_logo)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (result.subscriberCount > 0) {
                Text(
                    text = formatSubscriberCount(result.subscriberCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        AnimatedContent(
            targetState = isSubscribed,
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
            label = "sub_btn_${result.channelId}"
        ) { subscribed ->
            if (subscribed) {
                FilledTonalButton(
                    onClick = onToggle,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Subscribed", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                OutlinedButton(
                    onClick = onToggle,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Subscribe", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Step 3 — Import
// ─────────────────────────────────────────────────────────────

@Composable
private fun ImportStep(
    importState: ImportViewModel.State,
    onImportNewPipe: () -> Unit,
    onImportYouTube: () -> Unit,
    onImportYouTubeHistory: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            StepHeader(
                title = "Import your data",
                subtitle = "Bring your subscriptions and history from other apps. Everything is optional."
            )
        }
        item { ImportProgressBanner(importState) }

        item {
            Text(
                text = "Subscriptions",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )
        }

        item {
            ImportCard(
                painter = painterResource(id = R.drawable.ic_newpipe),
                title = stringResource(R.string.import_from_newpipe),
                description = "Import your NewPipe subscriptions from a JSON backup file.",
                onClick = onImportNewPipe
            )
        }

        item {
            ImportCard(
                painter = painterResource(id = R.drawable.ic_youtube),
                title = stringResource(R.string.import_from_youtube),
                description = "Import your YouTube subscriptions from a Google Takeout CSV file.",
                onClick = onImportYouTube
            )
        }

        item {
            Text(
                text = "Watch history",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
            )
        }

        item {
            ImportCard(
                painter = painterResource(id = R.drawable.ic_youtube),
                title = stringResource(R.string.import_yt_watch_history),
                description = stringResource(R.string.import_yt_watch_history_desc),
                onClick = onImportYouTubeHistory
            )
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ImportCard(
    painter: androidx.compose.ui.graphics.painter.Painter,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.Unspecified
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
            Icon(
                Icons.Outlined.FileDownload,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .alpha(0.45f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Shared composables
// ─────────────────────────────────────────────────────────────

@Composable
private fun StepHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            letterSpacing = (-0.5).sp
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryCard(
    category: FlowNeuroEngine.TopicCategory,
    selectedTopics: Set<String>,
    initiallyExpanded: Boolean,
    onTopicToggle: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    val selectedCount = category.topics.count { selectedTopics.contains(it) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selectedCount > 0)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { isExpanded = !isExpanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = category.icon,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(end = 10.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(getCategoryNameResId(category.name)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (selectedCount > 0) {
                        Text(
                            text = "$selectedCount selected",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .alpha(0.5f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(tween(220)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(130))
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = tween(180),
        label = "chip_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "chip_fg"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = if (isSelected)
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        else
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.semantics {
            stateDescription = if (isSelected) "Selected" else "Not selected"
            role = Role.Checkbox
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn(tween(120)) + expandHorizontally(expandFrom = Alignment.Start),
                exit = fadeOut(tween(100)) + shrinkHorizontally(shrinkTowards = Alignment.Start)
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = topic,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Channel search
// ─────────────────────────────────────────────────────────────

private suspend fun searchChannels(query: String): List<ChannelSearchResult> =
    withContext(Dispatchers.IO) {
        try {
            val extractor = ServiceList.YouTube.getSearchExtractor(query, listOf("channels"), null)
            extractor.fetchPage()
            extractor.initialPage.items
                .filterIsInstance<ChannelInfoItem>()
                .take(15)
                .mapNotNull { item ->
                    val channelId = try {
                        val url = item.url
                        when {
                            url.contains("/channel/") ->
                                url.substringAfter("/channel/").substringBefore("/").substringBefore("?")
                            url.contains("/@") ->
                                url.substringAfter("/@").substringBefore("/").substringBefore("?")
                            else ->
                                url.substringAfterLast("/").substringBefore("?")
                        }
                    } catch (e: Exception) { "" }

                    if (channelId.isEmpty() || item.name.isNullOrEmpty()) return@mapNotNull null

                    ChannelSearchResult(
                        channelId = channelId,
                        name = item.name ?: "",
                        thumbnailUrl = item.thumbnails
                            .sortedByDescending { it.height }
                            .firstOrNull()?.url ?: "",
                        subscriberCount = item.subscriberCount
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

// ─────────────────────────────────────────────────────────────
// Utilities
// ─────────────────────────────────────────────────────────────

private fun formatSubscriberCount(count: Long): String = when {
    count >= 1_000_000 -> "${count / 1_000_000}M subscribers"
    count >= 1_000 -> "${count / 1_000}K subscribers"
    else -> "$count subscribers"
}

private fun getCategoryNameResId(categoryName: String): Int = when {
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
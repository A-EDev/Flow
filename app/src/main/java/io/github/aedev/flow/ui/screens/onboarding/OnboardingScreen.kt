package io.github.aedev.flow.ui.screens.onboarding

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.BackupRepository
import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.ui.screens.settings.ImportViewModel
import io.github.aedev.flow.ui.theme.FlowMotion
import io.github.aedev.flow.ui.theme.rememberFlowReduceMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val reduceMotion = rememberFlowReduceMotion()

    val subscriptionRepo = remember { SubscriptionRepository.getInstance(context) }
    val backupRepo = remember { BackupRepository(context) }
    val importViewModel: ImportViewModel = hiltViewModel(context as ComponentActivity)

    var currentStep by remember { mutableStateOf(OnboardingStep.INTERESTS) }

    var selectedTopics by remember { mutableStateOf<Set<String>>(emptySet()) }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ChannelSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var subscribedInSession by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    var importMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val importState by importViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(importState) {
        when (val state = importState) {
            is ImportViewModel.State.Success -> {
                importMessage =
                    state.message
                        ?: if ((state.count ?: 0) > 0) {
                            context.getString(
                                R.string.onboarding_imported_count,
                                state.count,
                                state.label.lowercase(),
                            )
                        } else {
                            context.getString(R.string.onboarding_imported, state.label)
                        }
                importViewModel.dismiss()
            }

            is ImportViewModel.State.Error -> {
                importMessage = context.getString(R.string.onboarding_import_failed_template, state.message)
                importViewModel.dismiss()
            }

            else -> {}
        }
    }

    LaunchedEffect(importMessage) {
        importMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            importMessage = null
        }
    }

    val flowImportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let {
                scope.launch {
                    val result = backupRepo.importData(it)
                    importMessage =
                        if (result.isSuccess) {
                            context.getString(R.string.import_flow_backup_success)
                        } else {
                            context.getString(
                                R.string.import_flow_backup_failed_template,
                                result.exceptionOrNull()?.message ?: context.getString(R.string.unknown),
                            )
                        }
                }
            }
        }

    val newPipeImportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let { importViewModel.importNewPipe(it) } }

    val youtubeImportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let { importViewModel.importYouTube(it) } }

    val youtubeHistoryLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let { importViewModel.importYouTubeWatchHistory(it) } }

    val freeTubeHistoryLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let { importViewModel.importFreeTubeWatchHistory(it) } }

    val newPipeHistoryLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let { importViewModel.importNewPipeWatchHistory(it) } }

    val libreTubeImportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let { importViewModel.importLibreTube(it) } }

    val masterBackupImportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let { importViewModel.importMasterBackup(it) } }

    val metrolistImportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let { importViewModel.importMetrolist(it) } }

    val newPipePlaylistImportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let { importViewModel.importNewPipePlaylists(it) } }

    val libreTubePlaylistImportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let { importViewModel.importLibreTubePlaylists(it) } }

    val youtubeTakeoutImportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let { importViewModel.importYouTubeTakeout(it) } }

    val youtubePlaylistImportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let {
                scope.launch {
                    val result = backupRepo.importYouTubePlaylist(it)
                    importMessage =
                        if (result.isSuccess) {
                            val (name, count) = result.getOrNull()!!
                            context.resources.getQuantityString(
                                R.plurals.import_yt_playlist_success_template,
                                count,
                                name,
                                count,
                            )
                        } else {
                            context.getString(
                                R.string.import_yt_playlist_failed_template,
                                result.exceptionOrNull()?.message ?: context.getString(R.string.unknown),
                            )
                        }
                }
            }
        }

    val youtubeMusicPlaylistImportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let {
                scope.launch {
                    val result = backupRepo.importYouTubePlaylist(it, isMusic = true)
                    importMessage =
                        if (result.isSuccess) {
                            val (name, count) = result.getOrNull()!!
                            context.resources.getQuantityString(
                                R.plurals.import_yt_playlist_success_template,
                                count,
                                name,
                                count,
                            )
                        } else {
                            context.getString(
                                R.string.import_yt_playlist_failed_template,
                                result.exceptionOrNull()?.message ?: context.getString(R.string.unknown),
                            )
                        }
                }
            }
        }

    val importEngineLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let {
                scope.launch {
                    val success =
                        context.contentResolver.openInputStream(it)?.use { input ->
                            FlowNeuroEngine.importBrainFromStream(context, input)
                        } ?: false
                    importMessage =
                        context.getString(
                            if (success) R.string.import_engine_success else R.string.import_engine_failed,
                        )
                }
            }
        }

    fun finish() {
        scope.launch {
            FlowNeuroEngine.completeOnboarding(context, selectedTopics)
            onComplete()
        }
    }

    fun advance() {
        val next = OnboardingStep.entries.getOrNull(currentStep.index + 1)
        if (next != null) currentStep = next else finish()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { StepIndicatorBar(currentStep = currentStep) },
        bottomBar = {
            OnboardingBottomBar(
                isFirstStep = currentStep == OnboardingStep.INTERESTS,
                isLastStep = currentStep == OnboardingStep.IMPORT,
                canAdvance =
                    when (currentStep) {
                        OnboardingStep.INTERESTS -> selectedTopics.size >= MIN_TOPICS
                        else -> true
                    },
                onBack = {
                    OnboardingStep.entries.getOrNull(currentStep.index - 1)?.let { currentStep = it }
                },
                onNext = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    advance()
                },
                onSkip = { advance() },
            )
        },
    ) { innerPadding ->
        val enterDuration = FlowMotion.durationFor(FlowMotion.CONTENT_DURATION_MILLIS, reduceMotion)
        val fadeDuration = FlowMotion.durationFor(FlowMotion.ENTER_DURATION_MILLIS, reduceMotion)
        val exitDuration = FlowMotion.durationFor(FlowMotion.EXIT_DURATION_MILLIS, reduceMotion)

        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                val forward = targetState.index > initialState.index
                val enter =
                    if (forward) {
                        slideInHorizontally(
                            animationSpec = tween(enterDuration, easing = FlowMotion.EnterEasing),
                        ) { width -> width / 6 } +
                            fadeIn(tween(fadeDuration, easing = FlowMotion.EnterEasing))
                    } else {
                        slideInHorizontally(
                            animationSpec = tween(enterDuration, easing = FlowMotion.EnterEasing),
                        ) { width -> -width / 6 } +
                            fadeIn(tween(fadeDuration, easing = FlowMotion.EnterEasing))
                    }
                val exit =
                    if (forward) {
                        slideOutHorizontally(
                            animationSpec = tween(exitDuration, easing = FlowMotion.ExitEasing),
                        ) { width -> -width / 8 } +
                            fadeOut(tween(exitDuration, easing = FlowMotion.ExitEasing))
                    } else {
                        slideOutHorizontally(
                            animationSpec = tween(exitDuration, easing = FlowMotion.ExitEasing),
                        ) { width -> width / 8 } +
                            fadeOut(tween(exitDuration, easing = FlowMotion.ExitEasing))
                    }
                enter togetherWith exit
            },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            label = "onboardingStep",
        ) { step ->
            when (step) {
                OnboardingStep.INTERESTS -> {
                    InterestsStep(
                        selectedTopics = selectedTopics,
                        onTopicToggle = { topic ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedTopics =
                                if (selectedTopics.contains(topic)) {
                                    selectedTopics - topic
                                } else {
                                    selectedTopics + topic
                                }
                        },
                    )
                }

                OnboardingStep.CHANNELS -> {
                    ChannelsStep(
                        searchQuery = searchQuery,
                        searchResults = searchResults,
                        isSearching = isSearching,
                        subscribedInSession = subscribedInSession,
                        onQueryChange = { query ->
                            searchQuery = query
                            searchJob?.cancel()
                            if (query.isBlank()) {
                                searchResults = emptyList()
                                isSearching = false
                                return@ChannelsStep
                            }
                            searchJob =
                                scope.launch {
                                    delay(400)
                                    isSearching = true
                                    searchResults = searchChannels(query)
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
                                            subscribedAt = System.currentTimeMillis(),
                                        ),
                                    )
                                    subscribedInSession = subscribedInSession + result.channelId
                                }
                            }
                        },
                    )
                }

                OnboardingStep.IMPORT -> {
                    ImportStep(
                        importState = importState,
                        onImportFlowBackup = { flowImportLauncher.launch(arrayOf("application/json")) },
                        onImportMasterBackup = {
                            masterBackupImportLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                        },
                        onImportEngineData = { importEngineLauncher.launch(arrayOf("application/json")) },
                        onImportNewPipe = { newPipeImportLauncher.launch(arrayOf("application/json")) },
                        onImportYouTube = {
                            youtubeImportLauncher.launch(arrayOf("text/comma-separated-values", "text/csv", "text/plain"))
                        },
                        onImportYouTubeHistory = {
                            youtubeHistoryLauncher.launch(arrayOf("text/html", "application/octet-stream", "*/*"))
                        },
                        onImportFreeTubeHistory = {
                            freeTubeHistoryLauncher.launch(
                                arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"),
                            )
                        },
                        onImportNewPipeHistory = {
                            newPipeHistoryLauncher.launch(
                                arrayOf("application/zip", "application/octet-stream", "application/x-sqlite3", "*/*"),
                            )
                        },
                        onImportLibreTube = { libreTubeImportLauncher.launch(arrayOf("application/json")) },
                        onImportMetrolist = {
                            metrolistImportLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        },
                        onImportNewPipePlaylists = {
                            newPipePlaylistImportLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        },
                        onImportLibreTubePlaylists = {
                            libreTubePlaylistImportLauncher.launch(arrayOf("application/json"))
                        },
                        onImportYouTubeTakeout = {
                            youtubeTakeoutImportLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        },
                        onImportYouTubePlaylist = {
                            youtubePlaylistImportLauncher.launch(
                                arrayOf("text/comma-separated-values", "text/csv", "text/plain"),
                            )
                        },
                        onImportYouTubeMusicPlaylist = {
                            youtubeMusicPlaylistImportLauncher.launch(
                                arrayOf("text/comma-separated-values", "text/csv", "text/plain"),
                            )
                        },
                    )
                }
            }
        }
    }
}

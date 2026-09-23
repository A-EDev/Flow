package io.github.aedev.flow.ui.screens.onboarding

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.data.backup.BackupOperation
import io.github.aedev.flow.data.backup.ImportKind

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val importOperation by viewModel.importOperation.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.completed) { if (state.completed) onComplete() }
    BackHandler(enabled = state.step.index > 0) { viewModel.back() }

    LaunchedEffect(importOperation) {
        val message =
            when (val operation = importOperation) {
                is BackupOperation.Succeeded -> operation.message
                is BackupOperation.Failed -> operation.message
                else -> return@LaunchedEffect
            }
        viewModel.dismissImport()
        snackbarHostState.showSnackbar(message)
    }

    var pendingImport by rememberSaveable { mutableStateOf<ImportKind?>(null) }
    val importPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val kind = pendingImport
            pendingImport = null
            if (uri != null && kind != null) viewModel.startImport(kind, uri)
        }

    fun pick(kind: ImportKind) {
        pendingImport = kind
        importPicker.launch(kind.mimeTypes)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { StepIndicatorBar(currentStep = state.step) },
        bottomBar = {
            OnboardingBottomBar(
                isFirstStep = state.step.index == 0,
                isLastStep = state.step == OnboardingStep.entries.last(),
                canAdvance = state.canAdvance,
                onBack = { viewModel.back() },
                onNext = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    viewModel.next()
                },
                onSkip = { viewModel.next() },
            )
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                val forward = targetState.index > initialState.index
                val enter =
                    if (forward) {
                        slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(250))
                    } else {
                        slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(250))
                    }
                val exit =
                    if (forward) {
                        slideOutHorizontally(tween(250)) { -it / 4 } + fadeOut(tween(200))
                    } else {
                        slideOutHorizontally(tween(250)) { it / 4 } + fadeOut(tween(200))
                    }
                enter togetherWith exit
            },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            label = "step_content",
        ) { step ->
            when (step) {
                OnboardingStep.INTERESTS -> {
                    InterestsStep(
                        selectedTopics = state.topics,
                        onTopicToggle = { topic ->
                            val selecting = topic !in state.topics
                            haptic.performHapticFeedback(if (selecting) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                            viewModel.toggleTopic(topic)
                        },
                    )
                }

                OnboardingStep.CHANNELS -> {
                    ChannelsStep(
                        searchQuery = state.query,
                        searchResults = state.results,
                        isSearching = state.searching,
                        isSubscribed = state::isSubscribed,
                        subscribedCount = state.subscribed.size,
                        onQueryChange = viewModel::search,
                        onSubscribeToggle = { channel ->
                            haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            viewModel.toggleSubscription(channel)
                        },
                    )
                }

                OnboardingStep.IMPORT -> {
                    ImportStep(
                        importOperation = importOperation,
                        onImportFlowBackup = { pick(ImportKind.FLOW_BACKUP) },
                        onImportMasterBackup = { pick(ImportKind.MASTER) },
                        onImportEngineData = { pick(ImportKind.ENGINE) },
                        onImportNewPipe = { pick(ImportKind.NEWPIPE_SUBSCRIPTIONS) },
                        onImportYouTube = { pick(ImportKind.YOUTUBE_SUBSCRIPTIONS) },
                        onImportYouTubeHistory = { pick(ImportKind.YOUTUBE_HISTORY) },
                        onImportFreeTubeHistory = { pick(ImportKind.FREETUBE_HISTORY) },
                        onImportNewPipeHistory = { pick(ImportKind.NEWPIPE_HISTORY) },
                        onImportLibreTube = { pick(ImportKind.LIBRETUBE_SUBSCRIPTIONS) },
                        onImportMetrolist = { pick(ImportKind.METROLIST) },
                        onImportNewPipePlaylists = { pick(ImportKind.NEWPIPE_PLAYLISTS) },
                        onImportLibreTubePlaylists = { pick(ImportKind.LIBRETUBE_PLAYLISTS) },
                        onImportYouTubeTakeout = { pick(ImportKind.TAKEOUT) },
                        onImportYouTubePlaylist = { pick(ImportKind.YOUTUBE_PLAYLIST) },
                        onImportYouTubeMusicPlaylist = { pick(ImportKind.YOUTUBE_MUSIC_PLAYLIST) },
                    )
                }
            }
        }
    }
}

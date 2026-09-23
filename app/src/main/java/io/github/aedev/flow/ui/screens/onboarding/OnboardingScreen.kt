package io.github.aedev.flow.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.data.backup.BackupOperation
import io.github.aedev.flow.data.backup.ImportKind

private const val HERO_KEY = "onboarding-hero"
private val StepPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val importOperation by viewModel.importOperation.collectAsStateWithLifecycle()
    val newVideoAlerts by viewModel.newVideoAlerts.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    var interestsRevealed by rememberSaveable { mutableStateOf(false) }

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

    // The step the backdrop and hero last settled on; a new step morphs out of it.
    var shownStep by remember { mutableStateOf(state.step) }
    var backdropFrom by remember { mutableStateOf(state.step) }
    val backdrop = remember { Animatable(1f) }
    val backdropSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val heroFrom = shownStep
    LaunchedEffect(state.step) {
        if (state.step == shownStep) return@LaunchedEffect
        backdropFrom = shownStep
        shownStep = state.step
        backdrop.snapTo(0f)
        backdrop.animateTo(1f, backdropSpec)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Spacer(
            Modifier
                .fillMaxSize()
                .onboardingBackdrop(backdropFrom, shownStep, MaterialTheme.colorScheme.surfaceContainer) { backdrop.value },
        )
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0.dp),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = { OnboardingTopBar(step = state.step, onSkip = viewModel::next) },
            bottomBar = {
                OnboardingBottomBar(
                    state = state,
                    onBack = { viewModel.back() },
                    onNext = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        viewModel.next()
                    },
                    onRestore = { viewModel.goTo(OnboardingStep.IMPORT) },
                )
            },
        ) { innerPadding ->
            SharedTransitionLayout(Modifier.fillMaxSize().padding(innerPadding)) {
                StepContent(
                    state = state,
                    heroFrom = heroFrom,
                    importOperation = importOperation,
                    newVideoAlerts = newVideoAlerts,
                    interestsRevealed = interestsRevealed,
                    onInterestsRevealed = { interestsRevealed = true },
                    viewModel = viewModel,
                    onPick = ::pick,
                    onTopicToggle = { topic ->
                        val selecting = topic !in state.topics
                        haptic.performHapticFeedback(if (selecting) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                        viewModel.toggleTopic(topic)
                    },
                )
            }
        }
    }
}

@Composable
private fun SharedTransitionScope.StepContent(
    state: OnboardingUiState,
    heroFrom: OnboardingStep,
    importOperation: BackupOperation,
    newVideoAlerts: Boolean,
    interestsRevealed: Boolean,
    onInterestsRevealed: () -> Unit,
    viewModel: OnboardingViewModel,
    onPick: (ImportKind) -> Unit,
    onTopicToggle: (String) -> Unit,
) {
    val slide = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val bounds = MaterialTheme.motionScheme.slowSpatialSpec<Rect>()
    AnimatedContent(
        targetState = state.step,
        transitionSpec = {
            val direction = if (targetState.index > initialState.index) 1 else -1
            (slideInHorizontally(slide) { direction * it / 4 } + fadeIn(fade)) togetherWith
                (slideOutHorizontally(slide) { -direction * it / 4 } + fadeOut(fade))
        },
        modifier = Modifier.fillMaxSize(),
        label = "onboardingStep",
    ) { step ->
        val hero: HeroSlot = { size ->
            OnboardingHero(
                step = step,
                fromStep = heroFrom,
                size = size,
                petals = (state.topics.size.toFloat() / MIN_TOPICS).coerceAtMost(1f),
                modifier = heroModifier(this@AnimatedContent, BoundsTransform { _, _ -> bounds }),
            )
        }
        when (step) {
            OnboardingStep.WELCOME -> {
                WelcomeStep(hero = hero, contentPadding = StepPadding)
            }

            OnboardingStep.INTERESTS -> {
                InterestsStep(
                    selectedTopics = state.topics,
                    onTopicToggle = onTopicToggle,
                    hero = hero,
                    revealed = interestsRevealed,
                    onRevealed = onInterestsRevealed,
                    contentPadding = StepPadding,
                )
            }

            OnboardingStep.CHANNELS -> {
                ChannelsStep(
                    state = state,
                    onQueryChange = viewModel::search,
                    onSubscribeToggle = viewModel::toggleSubscription,
                    onNotificationsChange = viewModel::setChannelNotifications,
                    hero = hero,
                    contentPadding = StepPadding,
                )
            }

            OnboardingStep.ALERTS -> {
                AlertsStep(
                    hero = hero,
                    newVideoAlerts = newVideoAlerts,
                    onNewVideoAlertsChange = viewModel::setNewVideoAlerts,
                    contentPadding = StepPadding,
                )
            }

            OnboardingStep.IMPORT -> {
                ImportStep(
                    hero = hero,
                    importOperation = importOperation,
                    importedSources = state.importedSources,
                    onImport = onPick,
                    contentPadding = StepPadding,
                )
            }

            OnboardingStep.READY -> {
                ReadyStep(state = state, hero = hero, onEdit = viewModel::goTo, contentPadding = StepPadding)
            }
        }
    }
}

@Composable
private fun SharedTransitionScope.heroModifier(
    scope: AnimatedContentScope,
    bounds: BoundsTransform,
): Modifier =
    Modifier.sharedElement(
        sharedContentState = rememberSharedContentState(HERO_KEY),
        animatedVisibilityScope = scope,
        boundsTransform = bounds,
    )

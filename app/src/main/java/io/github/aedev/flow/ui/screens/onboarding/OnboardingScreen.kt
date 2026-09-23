package io.github.aedev.flow.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
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
                OnboardingSteps(
                    state = state,
                    heroFrom = heroFrom,
                    importOperation = importOperation,
                    newVideoAlerts = newVideoAlerts,
                    interestsRevealed = interestsRevealed,
                    actions =
                        StepActions(
                            viewModel = viewModel,
                            onPick = ::pick,
                            onTopicToggle = { topic ->
                                val selecting = topic !in state.topics
                                haptic.performHapticFeedback(if (selecting) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                                viewModel.toggleTopic(topic)
                            },
                            onInterestsRevealed = { interestsRevealed = true },
                        ),
                )
            }
        }
    }
}

package io.github.aedev.flow.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.backup.BackupOperation
import io.github.aedev.flow.data.backup.ImportKind
import io.github.aedev.flow.ui.components.shared.FlowMaxContentWidth
import io.github.aedev.flow.ui.utils.LocalWindowSizeClass
import io.github.aedev.flow.ui.utils.isExpandedWidth

private const val HERO_KEY = "onboarding-hero"
private const val SIDE_PANE_WEIGHT = 0.4f
private val StepPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
private val PanePadding = PaddingValues(horizontal = 40.dp, vertical = 8.dp)

/** What a step needs from the screen besides its state. */
internal class StepActions(
    val viewModel: OnboardingViewModel,
    val onPick: (ImportKind) -> Unit,
    val onTopicToggle: (String) -> Unit,
    val onInterestsRevealed: () -> Unit,
)

/**
 * The step on screen, sliding between steps while the hero flies from one to the next. From 840 dp
 * the middle steps put their hero, title and subtitle in a start pane beside the list.
 */
@Composable
internal fun SharedTransitionScope.OnboardingSteps(
    state: OnboardingUiState,
    heroFrom: OnboardingStep,
    importOperation: BackupOperation,
    newVideoAlerts: Boolean,
    interestsRevealed: Boolean,
    actions: StepActions,
) {
    val slide = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val bounds = MaterialTheme.motionScheme.slowSpatialSpec<Rect>()
    val twoPane = LocalWindowSizeClass.current.isExpandedWidth
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
        when {
            step == OnboardingStep.WELCOME -> {
                Readable { WelcomeStep(hero = hero, contentPadding = StepPadding) }
            }

            step == OnboardingStep.READY -> {
                Readable { ReadyStep(state = state, hero = hero, onEdit = actions.viewModel::goTo, contentPadding = StepPadding) }
            }

            twoPane -> {
                val copy = stepCopy(state, step)
                Row(Modifier.fillMaxSize()) {
                    StepSidePane(hero, copy, Modifier.weight(SIDE_PANE_WEIGHT).fillMaxHeight())
                    Box(Modifier.weight(1f - SIDE_PANE_WEIGHT).fillMaxHeight()) {
                        Readable { StepBody(step, state, importOperation, newVideoAlerts, interestsRevealed, actions, {}, PanePadding) }
                    }
                }
            }

            else -> {
                val copy = stepCopy(state, step)
                StepBody(
                    step,
                    state,
                    importOperation,
                    newVideoAlerts,
                    interestsRevealed,
                    actions,
                    { stepHeader(hero, copy.title, copy.subtitle) },
                    StepPadding,
                )
            }
        }
    }
}

@Composable
private fun StepBody(
    step: OnboardingStep,
    state: OnboardingUiState,
    importOperation: BackupOperation,
    newVideoAlerts: Boolean,
    interestsRevealed: Boolean,
    actions: StepActions,
    header: LazyListScope.() -> Unit,
    contentPadding: PaddingValues,
) {
    val viewModel = actions.viewModel
    when (step) {
        OnboardingStep.INTERESTS -> {
            InterestsStep(
                selectedTopics = state.topics,
                onTopicToggle = actions.onTopicToggle,
                header = header,
                revealed = interestsRevealed,
                onRevealed = actions.onInterestsRevealed,
                contentPadding = contentPadding,
            )
        }

        OnboardingStep.CHANNELS -> {
            ChannelsStep(
                state = state,
                onQueryChange = viewModel::search,
                onSubscribeToggle = viewModel::toggleSubscription,
                onNotificationsChange = viewModel::setChannelNotifications,
                header = header,
                contentPadding = contentPadding,
            )
        }

        OnboardingStep.ALERTS -> {
            AlertsStep(
                header = header,
                newVideoAlerts = newVideoAlerts,
                onNewVideoAlertsChange = viewModel::setNewVideoAlerts,
                contentPadding = contentPadding,
            )
        }

        OnboardingStep.IMPORT -> {
            ImportStep(
                header = header,
                importOperation = importOperation,
                importedSources = state.importedSources,
                onImport = actions.onPick,
                contentPadding = contentPadding,
            )
        }

        OnboardingStep.WELCOME, OnboardingStep.READY -> {
            Unit
        }
    }
}

/** Caps [content] at a readable width and centres it, for tablets and unfolded foldables. */
@Composable
private fun Readable(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = FlowMaxContentWidth).fillMaxSize()) { content() }
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

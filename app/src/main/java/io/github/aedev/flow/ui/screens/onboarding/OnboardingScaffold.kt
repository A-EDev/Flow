@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.aedev.flow.ui.screens.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowActionButton
import io.github.aedev.flow.ui.components.shared.drawSegmentedProgress

/** Draws the current step's hero at the given size; the screen builds it so it can be shared between steps. */
internal typealias HeroSlot = @Composable (size: Dp) -> Unit

private val TopBarHeight = 64.dp
private val TopBarStartPadding = 24.dp
private val TopBarEndPadding = 8.dp
private val ProgressHeight = 6.dp
private val BarSpacing = 12.dp
private val BottomBarPadding = 20.dp
private val HeaderSpacing = 12.dp
private val HeaderBottomPadding = 20.dp
private val SidePanePadding = 40.dp
private val SidePaneSpacing = 18.dp
private const val CURRENT_SEGMENT_WEIGHT = 2.4f

@Composable
internal fun OnboardingTopBar(
    step: OnboardingStep,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(TopBarHeight)
                .padding(start = TopBarStartPadding, end = TopBarEndPadding),
        horizontalArrangement = Arrangement.spacedBy(BarSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!step.showsProgress) return@Row
        StepProgress(step, Modifier.weight(1f))
        TextButton(onClick = onSkip) { Text(stringResource(R.string.onboarding_btn_skip)) }
    }
}

@Composable
private fun StepProgress(
    step: OnboardingStep,
    modifier: Modifier,
) {
    val steps = OnboardingStep.progressSteps
    val current = steps.indexOf(step)
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val weights =
        steps.indices.map { index ->
            animateFloatAsState(
                if (index ==
                    current
                ) {
                    CURRENT_SEGMENT_WEIGHT
                } else {
                    1f
                },
                spec,
                label = "segment",
            )
        }
    val label = stringResource(R.string.onboarding_progress, current + 1, steps.size)
    Box(
        modifier
            .height(ProgressHeight)
            .semantics { contentDescription = label }
            .drawSegmentedProgress(
                count = steps.size,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                weight = { weights[it].value },
            ) { index -> if (index <= current) 1f else 0f },
    )
}

@Composable
internal fun OnboardingBottomBar(
    state: OnboardingUiState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(BottomBarPadding),
        horizontalArrangement = Arrangement.spacedBy(BarSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.step == OnboardingStep.WELCOME) {
            TextButton(onClick = onRestore, modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight)) {
                Text(stringResource(R.string.onboarding_restore_backup))
            }
            FlowActionButton(
                stringResource(R.string.onboarding_get_started),
                onNext,
                Modifier.weight(1f),
                trailing = Icons.AutoMirrored.Outlined.ArrowForward,
            )
            return@Row
        }
        FilledTonalIconButton(
            onClick = onBack,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.onboarding_btn_back))
        }
        when {
            state.step == OnboardingStep.READY -> {
                FlowActionButton(
                    stringResource(R.string.onboarding_start_watching),
                    onNext,
                    Modifier.weight(1f),
                    leading = Icons.Rounded.PlayArrow,
                )
            }

            !state.canAdvance -> {
                FlowActionButton(
                    text = pluralStringResource(R.plurals.onboarding_pick_more, state.topicsLeft, state.topicsLeft),
                    onClick = onNext,
                    modifier = Modifier.weight(1f),
                    enabled = false,
                )
            }

            else -> {
                FlowActionButton(
                    stringResource(R.string.onboarding_btn_continue),
                    onNext,
                    Modifier.weight(1f),
                    trailing = Icons.AutoMirrored.Outlined.ArrowForward,
                )
            }
        }
    }
}

/** The hero, title and subtitle at the top of a step's list. */
internal fun LazyListScope.stepHeader(
    hero: HeroSlot,
    title: String,
    subtitle: String,
) {
    item(key = "header") {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = HeaderBottomPadding),
            verticalArrangement = Arrangement.spacedBy(HeaderSpacing),
        ) {
            hero(HeroSmall)
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal data class StepCopy(
    val title: String,
    val subtitle: String,
)

/** The title and subtitle a step shows, in its list header or beside it in the start pane. */
@Composable
internal fun stepCopy(
    state: OnboardingUiState,
    step: OnboardingStep,
): StepCopy =
    when (step) {
        OnboardingStep.INTERESTS -> {
            StepCopy(
                stringResource(R.string.onboarding_interests_title),
                if (state.topicsLeft > 0) {
                    stringResource(R.string.onboarding_interests_hint, MIN_TOPICS, state.topicsLeft)
                } else {
                    stringResource(R.string.onboarding_interests_ready)
                },
            )
        }

        OnboardingStep.CHANNELS -> {
            StepCopy(stringResource(R.string.onboarding_channels_title), stringResource(R.string.onboarding_channels_subtitle))
        }

        OnboardingStep.ALERTS -> {
            StepCopy(stringResource(R.string.onboarding_alerts_title), stringResource(R.string.onboarding_alerts_subtitle))
        }

        OnboardingStep.IMPORT -> {
            StepCopy(stringResource(R.string.onboarding_import_title), stringResource(R.string.onboarding_import_subtitle))
        }

        OnboardingStep.WELCOME, OnboardingStep.READY -> {
            StepCopy("", "")
        }
    }

/** The start pane of the two-pane layout: the hero, a large title and the subtitle. */
@Composable
internal fun StepSidePane(
    hero: HeroSlot,
    copy: StepCopy,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(SidePanePadding),
        verticalArrangement = Arrangement.spacedBy(SidePaneSpacing),
    ) {
        hero(HeroSide)
        Text(
            text = copy.title,
            style = MaterialTheme.typography.displaySmallEmphasized,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = copy.subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

package io.github.aedev.flow.ui.screens.onboarding

import io.github.aedev.flow.R

internal const val MIN_TOPICS = 3
internal const val STAGGER_DELAY_MS = 50L

internal enum class OnboardingStep(
    val index: Int,
    val labelRes: Int,
) {
    INTERESTS(0, R.string.onboarding_step_interests),
    CHANNELS(1, R.string.onboarding_step_channels),
    IMPORT(2, R.string.onboarding_step_import),
}

data class ChannelSearchResult(
    val channelId: String,
    val name: String,
    val thumbnailUrl: String,
    val subscriberCount: Long = -1L,
)

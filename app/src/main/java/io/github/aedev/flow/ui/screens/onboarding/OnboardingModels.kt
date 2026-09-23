package io.github.aedev.flow.ui.screens.onboarding

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import io.github.aedev.flow.R
import io.github.aedev.flow.data.backup.ImportSource
import io.github.aedev.flow.data.model.Channel

internal const val MIN_TOPICS = 3
internal const val STAGGER_DELAY_MS = 50L

enum class OnboardingStep(
    @StringRes val labelRes: Int,
) {
    INTERESTS(R.string.onboarding_step_interests),
    CHANNELS(R.string.onboarding_step_channels),
    IMPORT(R.string.onboarding_step_import),
    ;

    val index: Int get() = ordinal
}

@Immutable
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.entries.first(),
    val topics: Set<String> = emptySet(),
    val query: String = "",
    val results: List<Channel> = emptyList(),
    val searching: Boolean = false,
    val subscribed: List<Channel> = emptyList(),
    val importedSources: Set<ImportSource> = emptySet(),
    val completed: Boolean = false,
) {
    val canAdvance: Boolean get() = step != OnboardingStep.INTERESTS || topics.size >= MIN_TOPICS

    fun isSubscribed(channelId: String): Boolean = subscribed.any { it.id == channelId }
}

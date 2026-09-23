package io.github.aedev.flow.ui.screens.onboarding

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import io.github.aedev.flow.R
import io.github.aedev.flow.data.backup.ImportSource
import io.github.aedev.flow.data.model.Channel

internal const val MIN_TOPICS = 3
internal const val STAGGER_DELAY_MS = 50L

/** The setup steps in order. Welcome and Ready bookend the four steps the progress bar counts. */
enum class OnboardingStep(
    @StringRes val labelRes: Int?,
) {
    WELCOME(null),
    INTERESTS(R.string.onboarding_step_interests),
    CHANNELS(R.string.onboarding_step_channels),
    ALERTS(R.string.onboarding_step_alerts),
    IMPORT(R.string.onboarding_step_import),
    READY(null),
    ;

    val index: Int get() = ordinal

    val showsProgress: Boolean get() = labelRes != null

    companion object {
        val progressSteps: List<OnboardingStep> = entries.filter { it.showsProgress }
    }
}

@Immutable
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.entries.first(),
    val topics: Set<String> = emptySet(),
    val query: String = "",
    val results: List<Channel> = emptyList(),
    val searching: Boolean = false,
    val subscribed: List<Channel> = emptyList(),
    val notifying: Set<String> = emptySet(),
    val importedSources: Set<ImportSource> = emptySet(),
    val completed: Boolean = false,
) {
    val topicsLeft: Int get() = (MIN_TOPICS - topics.size).coerceAtLeast(0)

    val canAdvance: Boolean get() = step != OnboardingStep.INTERESTS || topicsLeft == 0

    fun isSubscribed(channelId: String): Boolean = subscribed.any { it.id == channelId }
}

package io.github.aedev.flow.ui.screens.onboarding

internal const val MIN_TOPICS = 3
internal const val STAGGER_DELAY_MS = 50L

internal enum class OnboardingStep(val index: Int, val label: String) {
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

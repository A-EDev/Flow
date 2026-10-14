package io.github.aedev.flow.ui.screens.home

/**
 * What Home does with the channels the viewer follows. With subscriptions on Home they are fetched
 * and ranked up ([boosted]); with it off none are fetched or boosted, and every one is [hidden].
 */
internal data class HomeSubscriptionScope(
    val boosted: Set<String>,
    val hidden: Set<String>,
) {
    companion object {
        fun of(
            subscriptions: Set<String>,
            showOnHome: Boolean,
        ): HomeSubscriptionScope =
            if (showOnHome) {
                HomeSubscriptionScope(boosted = subscriptions, hidden = emptySet())
            } else {
                HomeSubscriptionScope(boosted = emptySet(), hidden = subscriptions)
            }
    }
}

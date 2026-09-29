package io.github.aedev.flow.data.shorts.feed

/**
 * How many of a page's [slots] each lane may take. SUBSCRIPTIONS is a cap, never a floor; the
 * remainder after rounding goes to EXPLORE, the lane that never runs dry.
 */
internal fun shortsFeedQuotas(
    slots: Int,
    profile: ShortsFeedProfile,
): Map<ShortsFeedLane, Int> {
    val total = slots.coerceAtLeast(0)
    if (total == 0) return ShortsFeedLane.entries.associateWith { 0 }

    val shares =
        when {
            profile.isColdStart && profile.hasSubscriptions -> {
                Shares(
                    related = 0.10,
                    explore = 0.40,
                    discovery = 0.20,
                    subscriptions = 0.30,
                )
            }

            profile.isColdStart -> {
                Shares(related = 0.15, explore = 0.55, discovery = 0.30, subscriptions = 0.0)
            }

            profile.hasSubscriptions -> {
                Shares(related = 0.35, explore = 0.30, discovery = 0.20, subscriptions = 0.15)
            }

            else -> {
                Shares(related = 0.40, explore = 0.35, discovery = 0.25, subscriptions = 0.0)
            }
        }
    val related = (total * shares.related).toInt()
    val discovery = (total * shares.discovery).toInt()
    val subscriptions = (total * shares.subscriptions).toInt()
    val explore = (total - related - discovery - subscriptions).coerceAtLeast(0)
    return mapOf(
        ShortsFeedLane.RELATED to related,
        ShortsFeedLane.EXPLORE to explore,
        ShortsFeedLane.DISCOVERY to discovery,
        ShortsFeedLane.SUBSCRIPTIONS to subscriptions,
    )
}

private data class Shares(
    val related: Double,
    val explore: Double,
    val discovery: Double,
    val subscriptions: Double,
)

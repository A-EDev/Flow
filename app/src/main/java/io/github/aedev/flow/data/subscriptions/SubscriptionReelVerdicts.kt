package io.github.aedev.flow.data.subscriptions

import io.github.aedev.flow.data.local.entity.SubscriptionFeedEntity

/**
 * The stored reel verdicts a refresh may reuse instead of asking a channel's Shorts tab again.
 *
 * A pass that read a channel in full stamps it with the same time it writes its rows, so a row cached
 * after its channel's stamp came from a pass that could not classify it: a timed-out Shorts lookup, a
 * failed tab pass, or the background new-upload check. Trusting those rows would keep a reel filed as
 * an ordinary upload for good, since a reused verdict is never asked about again.
 */
internal fun trustedReelVerdicts(
    rows: List<SubscriptionFeedEntity>,
    lastFeedFetchAt: Map<String, Long>,
): Map<String, Boolean> =
    rows
        .filter { row -> row.cachedAt <= (lastFeedFetchAt[row.channelId] ?: 0L) }
        .associate { it.videoId to it.isShort }

package io.github.aedev.flow.data.shorts.queue

import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.shorts.ShortsRepository
import io.github.aedev.flow.data.subscriptions.SubscriptionFeedRepository
import io.github.aedev.flow.data.subscriptions.SubscriptionWatchedVideos
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShortsQueueLoaderFactory
    @Inject
    constructor(
        private val shortsRepository: ShortsRepository,
        private val playlistRepository: PlaylistRepository,
        private val subscriptionFeedRepository: SubscriptionFeedRepository,
        private val subscriptionRepository: SubscriptionRepository,
        private val subscriptionWatchedVideos: SubscriptionWatchedVideos,
        private val playerPreferences: PlayerPreferences,
        private val handoff: ShortsQueueHandoff,
    ) {
        fun create(source: ShortsQueueSource): ShortsQueueController {
            val resolved = resolve(source)
            return ShortsQueueController(
                primary = loaderFor(resolved),
                continuations = continuationsFor(resolved),
                acceptsDiscovery = resolved.isAlgorithmicFeed,
            )
        }

        fun resolve(source: ShortsQueueSource): ShortsQueueSource =
            if (source is ShortsQueueSource.Snapshot && handoff.peek(source.token) == null) {
                source.startVideoId
                    .takeIf { it.isNotBlank() }
                    ?.let { ShortsQueueSource.SeededFeed(it) }
                    ?: ShortsQueueSource.Feed
            } else {
                source
            }

        /**
         * What follows the source once it runs out, in order.
         *
         * The subscriptions queue gets the subscribed channels' own older reels first: the feed it is
         * built from reaches back only as far as each channel's last handful of uploads, so running
         * out of it is not the same thing as having watched everything the subscriptions hold.
         */
        private fun continuationsFor(source: ShortsQueueSource): List<ShortsQueueLoader> =
            buildList {
                if (source is ShortsQueueSource.Subscriptions) add(deepSubscriptionLoader())
                if (source.continuesIntoFeed) add(optionalFeedLoader())
            }

        private fun deepSubscriptionLoader(): ShortsQueueLoader =
            SubscriptionDeepShortsLoader(
                subscriptionFeedRepository = subscriptionFeedRepository,
                subscriptionRepository = subscriptionRepository,
                playerPreferences = playerPreferences,
                watchedVideos = subscriptionWatchedVideos,
            )

        private fun optionalFeedLoader(): ShortsQueueLoader =
            GatedShortsLoader(
                enabled = { playerPreferences.shortsQueueContinuesIntoFeed.first() },
                delegate = AlgorithmicFeedLoader(shortsRepository),
            )

        private fun loaderFor(source: ShortsQueueSource): ShortsQueueLoader =
            when (source) {
                ShortsQueueSource.Feed -> {
                    AlgorithmicFeedLoader(shortsRepository)
                }

                is ShortsQueueSource.SeededFeed -> {
                    AlgorithmicFeedLoader(shortsRepository, source.startVideoId)
                }

                is ShortsQueueSource.Saved -> {
                    SavedShortsLoader(playlistRepository)
                }

                is ShortsQueueSource.Subscriptions -> {
                    SubscriptionShortsLoader(
                        subscriptionFeedRepository = subscriptionFeedRepository,
                        playerPreferences = playerPreferences,
                        watchedVideos = subscriptionWatchedVideos,
                        anchorVideoId = source.startVideoId.takeIf { it.isNotBlank() },
                    )
                }

                is ShortsQueueSource.Channel -> {
                    ChannelShortsLoader(source.channelUrl, source.sortIndex)
                }

                is ShortsQueueSource.Snapshot -> {
                    // resolve() already guaranteed the token is present.
                    SnapshotLoader(handoff.peek(source.token).orEmpty())
                }
            }
    }

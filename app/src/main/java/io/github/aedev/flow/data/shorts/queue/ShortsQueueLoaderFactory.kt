package io.github.aedev.flow.data.shorts.queue

import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.shorts.ShortsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShortsQueueLoaderFactory
    @Inject
    constructor(
        private val shortsRepository: ShortsRepository,
        private val playlistRepository: PlaylistRepository,
        private val handoff: ShortsQueueHandoff,
    ) {
        fun create(source: ShortsQueueSource): ShortsQueueController {
            val resolved = resolve(source)
            return ShortsQueueController(
                primary = loaderFor(resolved),
                continuation = if (resolved.continuesIntoFeed) AlgorithmicFeedLoader(shortsRepository) else null,
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

                is ShortsQueueSource.Channel -> {
                    ChannelShortsLoader(source.channelUrl)
                }

                is ShortsQueueSource.Snapshot -> {
                    // resolve() already guaranteed the token is present.
                    SnapshotLoader(handoff.peek(source.token).orEmpty())
                }
            }
    }

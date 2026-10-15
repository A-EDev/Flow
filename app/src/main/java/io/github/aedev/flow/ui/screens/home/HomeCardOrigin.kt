package io.github.aedev.flow.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video

/** Why a card is on Home, named on its Details page. [detail] is the seed video's title or the search. */
data class HomeCardOrigin(
    val kind: Kind,
    val detail: String? = null,
) {
    enum class Kind { SUBSCRIPTION, RELATED, SEARCH, CHANNEL_MEMORY }

    companion object {
        val subscription = HomeCardOrigin(Kind.SUBSCRIPTION)
        val channelMemory = HomeCardOrigin(Kind.CHANNEL_MEMORY)

        fun related(seedTitle: String?) = HomeCardOrigin(Kind.RELATED, seedTitle)

        fun search(query: String) = HomeCardOrigin(Kind.SEARCH, query)
    }
}

@Composable
internal fun HomeCardOrigin.label(): String =
    when (kind) {
        HomeCardOrigin.Kind.SUBSCRIPTION -> {
            stringResource(R.string.home_origin_subscription)
        }

        HomeCardOrigin.Kind.RELATED -> {
            detail?.let { stringResource(R.string.home_origin_related, it) }
                ?: stringResource(R.string.home_origin_related_watched)
        }

        HomeCardOrigin.Kind.SEARCH -> {
            stringResource(R.string.home_origin_search, detail.orEmpty())
        }

        HomeCardOrigin.Kind.CHANNEL_MEMORY -> {
            stringResource(R.string.home_origin_channel_memory)
        }
    }

/** The search each video came from; a video several searches returned keeps the first. */
internal fun queriesById(searches: List<Pair<String, List<Video>>>): Map<String, String> =
    buildMap { searches.forEach { (query, videos) -> videos.forEach { putIfAbsent(it.id, query) } } }

/**
 * The origin of each of [videos]: the lane [sourceOf] names, or, for a card no lane claims, the
 * seed or search that found it, or a followed channel.
 */
internal fun pageOrigins(
    videos: List<Video>,
    sourceOf: (String) -> FeedSource?,
    seedTitles: Map<String, String>,
    queries: Map<String, String>,
    followed: Set<String>,
): Map<String, HomeCardOrigin> =
    videos
        .mapNotNull { video ->
            val origin =
                when (sourceOf(video.id)) {
                    FeedSource.RELATED -> {
                        HomeCardOrigin.related(seedTitles[video.id])
                    }

                    FeedSource.DISCOVERY -> {
                        queries[video.id]?.let(HomeCardOrigin::search)
                    }

                    FeedSource.CHANNEL_MEMORY -> {
                        HomeCardOrigin.channelMemory
                    }

                    FeedSource.SUBS -> {
                        HomeCardOrigin.subscription
                    }

                    null -> {
                        when {
                            video.id in seedTitles -> HomeCardOrigin.related(seedTitles[video.id])
                            video.id in queries -> HomeCardOrigin.search(queries.getValue(video.id))
                            video.channelId in followed -> HomeCardOrigin.subscription
                            else -> null
                        }
                    }
                }
            origin?.let { video.id to it }
        }.toMap()

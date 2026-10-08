package io.github.aedev.flow.ui.screens.home.chips

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.InterestChip

/** One chip above the Home feed. [key] is stable across loads and survives process death. */
internal sealed interface HomeChip {
    val key: String

    /** How long a loaded result stays fresh, in milliseconds. */
    val ttlMs: Long

    data object All : HomeChip {
        override val key = "all"
        override val ttlMs = 0L
    }

    data class Interest(
        val interest: InterestChip,
    ) : HomeChip {
        override val key = "interest:${interest.representative}"
        override val ttlMs = 2 * HOUR
    }

    data object NewToYou : HomeChip {
        override val key = "new"
        override val ttlMs = 2 * HOUR
    }

    data object RecentlyUploaded : HomeChip {
        override val key = "recent"
        override val ttlMs = HOUR
    }

    data object Mixes : HomeChip {
        override val key = "mixes"
        override val ttlMs = 6 * HOUR
    }

    data object Live : HomeChip {
        override val key = "live"
        override val ttlMs = 10 * MINUTE
    }

    data object Watched : HomeChip {
        override val key = "watched"
        override val ttlMs = 15 * MINUTE
    }

    companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}

/** A mix: the seed video, then what plays well after it. Played as a queue. */
internal data class HomeMix(
    val seed: Video,
    val videos: List<Video>,
)

internal sealed interface ChipFeed {
    data object Loading : ChipFeed

    data class Videos(
        val videos: List<Video>,
    ) : ChipFeed

    data class Mixes(
        val mixes: List<HomeMix>,
    ) : ChipFeed

    data object Empty : ChipFeed

    data object Failed : ChipFeed
}

internal data class HomeChipsState(
    val chips: List<HomeChip> = visibleChips(emptyList(), emptySet(), hasMixSeeds = false, hasWatched = false, selected = HomeChip.All.key),
    val selected: String = HomeChip.All.key,
    val feed: ChipFeed? = null,
    val isRefreshing: Boolean = false,
) {
    val isAll: Boolean get() = selected == HomeChip.All.key
}

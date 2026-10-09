package io.github.aedev.flow.ui.screens.categories

import io.github.aedev.flow.R
import io.github.aedev.flow.innertube.pages.explore.ExploreDestination

/** One tab of the Explore screen, and everything the ViewModel needs to load it. */
data class CategoryTab(
    val destination: ExploreDestination,
    val labelRes: Int,
)

/**
 * The tabs Explore shows, in the order they ship.
 *
 * Trending, Podcasts, Movies and Shopping are all absent on purpose: each answers 400, 429 or an
 * empty page for an anonymous client. See [ExploreDestination].
 */
val CATEGORY_TABS: List<CategoryTab> =
    listOf(
        CategoryTab(ExploreDestination.LIVE, R.string.category_live),
        CategoryTab(ExploreDestination.GAMING, R.string.category_gaming),
        CategoryTab(ExploreDestination.MUSIC, R.string.category_music),
        CategoryTab(ExploreDestination.NEWS, R.string.category_news),
        CategoryTab(ExploreDestination.SPORTS, R.string.category_sports),
        CategoryTab(ExploreDestination.LEARNING, R.string.category_learning),
        CategoryTab(ExploreDestination.FASHION, R.string.category_fashion),
    )

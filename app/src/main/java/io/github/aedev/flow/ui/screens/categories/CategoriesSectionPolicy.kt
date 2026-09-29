package io.github.aedev.flow.ui.screens.categories

import io.github.aedev.flow.R
import io.github.aedev.flow.innertube.pages.explore.ExploreDestination
import io.github.aedev.flow.innertube.pages.explore.chartsCountryOrFallback

/** One tab of the Explore screen, and everything the ViewModel needs to load it. */
data class CategoryTab(
    val destination: ExploreDestination,
    val labelRes: Int,
)

/**
 * The tabs Explore shows, in the order they ship.
 *
 * Trending, Podcasts, the Movies destination channel and Shopping are all absent on purpose: the
 * first three answer 400 or an empty page for an anonymous client, and Movies comes from the charts
 * instead. See [ExploreDestination].
 */
val CATEGORY_TABS: List<CategoryTab> =
    listOf(
        CategoryTab(ExploreDestination.LIVE, R.string.category_live),
        CategoryTab(ExploreDestination.GAMING, R.string.category_gaming),
        CategoryTab(ExploreDestination.MUSIC, R.string.category_music),
        CategoryTab(ExploreDestination.MOVIES, R.string.category_movies),
        CategoryTab(ExploreDestination.NEWS, R.string.category_news),
        CategoryTab(ExploreDestination.SPORTS, R.string.category_sports),
        CategoryTab(ExploreDestination.LEARNING, R.string.category_learning),
        CategoryTab(ExploreDestination.FASHION, R.string.category_fashion),
    )

/**
 * Where a destination takes its region from. Charts name the country in the request body and serve
 * only 61 of them; the browse destinations read `context.client.gl`, which is set globally.
 */
fun ExploreDestination.chartCountryFor(region: String): String? = chartType?.let { chartsCountryOrFallback(region) }

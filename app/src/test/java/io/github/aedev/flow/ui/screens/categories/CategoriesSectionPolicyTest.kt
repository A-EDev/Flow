package io.github.aedev.flow.ui.screens.categories

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.innertube.pages.explore.ExploreDestination
import io.github.aedev.flow.innertube.pages.explore.ExploreSectionKind
import org.junit.Test

class CategoriesSectionPolicyTest {
    @Test
    fun `every shipping tab maps to a destination that serves content anonymously`() {
        val destinations = CATEGORY_TABS.map { it.destination }

        assertThat(destinations)
            .containsExactly(
                ExploreDestination.LIVE,
                ExploreDestination.GAMING,
                ExploreDestination.MUSIC,
                ExploreDestination.MOVIES,
                ExploreDestination.NEWS,
                ExploreDestination.SPORTS,
                ExploreDestination.LEARNING,
                ExploreDestination.FASHION,
            ).inOrder()
    }

    @Test
    fun `each destination declares the shape its first page actually arrives in`() {
        assertThat(ExploreDestination.LIVE.kind).isEqualTo(ExploreSectionKind.SHELVES)
        assertThat(ExploreDestination.NEWS.kind).isEqualTo(ExploreSectionKind.SHELVES)
        assertThat(ExploreDestination.GAMING.kind).isEqualTo(ExploreSectionKind.GRID)
        assertThat(ExploreDestination.MUSIC.kind).isEqualTo(ExploreSectionKind.CHART)
        assertThat(ExploreDestination.MOVIES.kind).isEqualTo(ExploreSectionKind.CHART)
    }

    @Test
    fun `only gaming needs a params token the response never supplies`() {
        assertThat(ExploreDestination.GAMING.params).isEqualTo("Egh0cmVuZGluZw%3D%3D")
        assertThat(CATEGORY_TABS.map { it.destination }.filter { it.params != null })
            .containsExactly(ExploreDestination.GAMING)
    }

    @Test
    fun `the two charts differ only by chart type`() {
        assertThat(ExploreDestination.MUSIC.chartType).isEqualTo("TRENDING_VIDEOS")
        assertThat(ExploreDestination.MOVIES.chartType).isEqualTo("TRENDING_MOVIES")
        assertThat(ExploreDestination.MUSIC.browseId).isEqualTo(ExploreDestination.MOVIES.browseId)
    }

    @Test
    fun `a browse destination takes no chart country`() {
        assertThat(ExploreDestination.LIVE.chartCountryFor("GB")).isNull()
        assertThat(ExploreDestination.NEWS.chartCountryFor("ZZ")).isNull()
    }

    @Test
    fun `a chart falls back when the region is one it does not serve`() {
        assertThat(ExploreDestination.MUSIC.chartCountryFor("GB")).isEqualTo("GB")
        assertThat(ExploreDestination.MUSIC.chartCountryFor("ZZ")).isEqualTo("US")
    }

    @Test
    fun `no tab points at a surface that answers 400 or an empty page`() {
        val shipping = CATEGORY_TABS.map { it.destination.browseId }

        assertThat(shipping).containsNoneOf("FEtrending", "FEexplore", "FEpodcasts", "FEpodcasts_destination")
        assertThat(shipping).doesNotContain("UClgRkhTL3_hImCAmdLfDE4g")
    }
}

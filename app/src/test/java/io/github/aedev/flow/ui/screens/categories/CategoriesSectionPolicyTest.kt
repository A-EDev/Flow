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
    }

    @Test
    fun `only gaming needs a params token the response never supplies`() {
        assertThat(ExploreDestination.GAMING.params).isEqualTo("Egh0cmVuZGluZw%3D%3D")
        assertThat(CATEGORY_TABS.map { it.destination }.filter { it.params != null })
            .containsExactly(ExploreDestination.GAMING)
    }

    @Test
    fun `music reads youtube music charts, not the rate limited analytics host`() {
        assertThat(ExploreDestination.MUSIC.browseId).isEqualTo("FEmusic_charts")
        assertThat(CATEGORY_TABS.map { it.destination.browseId }).doesNotContain("FEmusic_analytics_charts_home")
    }

    @Test
    fun `no tab points at a surface that answers 400 or an empty page`() {
        val shipping = CATEGORY_TABS.map { it.destination.browseId }

        assertThat(shipping).containsNoneOf("FEtrending", "FEexplore", "FEpodcasts", "FEpodcasts_destination")
        assertThat(shipping).doesNotContain("UClgRkhTL3_hImCAmdLfDE4g")
    }
}

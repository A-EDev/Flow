package io.github.aedev.flow.ui.components.music.search

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.innertube.YouTube.SearchFilter
import org.junit.Test

class MusicSearchFiltersTest {
    @Test
    fun `the chips follow what YouTube offers for the query, in its order`() {
        val offered =
            listOf(
                "EgWKAQIQAWoQEAQQAxAJEAUQEBAKEBUQEQ%3D%3D",
                "EgWKAQIIAWoQEAQQAxAJEAUQEBAKEBUQEQ%3D%3D",
                "EgWKAQJIAWoQEAQQAxAJEAUQEBAKEBUQEQ%3D%3D",
                "EgeKAQQoAEABahAQBBADEAkQBRAQEAoQFRAR",
                "EgWKAQJYAWoQEAQQAxAJEAUQEBAKEBUQEQ%3D%3D",
                "EgWKAQJQAWoQEAQQAxAJEAUQEBAKEBUQEQ%3D%3D",
            )

        assertThat(musicSearchFilters(offered))
            .containsExactly(
                SearchFilter.FILTER_VIDEO,
                SearchFilter.FILTER_SONG,
                SearchFilter.FILTER_EPISODE,
                SearchFilter.FILTER_COMMUNITY_PLAYLIST,
                SearchFilter.FILTER_PROFILE,
                SearchFilter.FILTER_PODCAST,
            ).inOrder()
    }

    @Test
    fun `featured and community playlists are told apart, and unknown chips are left out`() {
        val offered = listOf("EgeKAQQoADgBahIQChAOEAMQERAJEAUQBBAQEBU%3D", "EgeKAQQoAEABahAQBBADEAkQBRAQEAoQFRAR", "EgWKAQJwAWoQ")

        assertThat(musicSearchFilters(offered))
            .containsExactly(SearchFilter.FILTER_FEATURED_PLAYLIST, SearchFilter.FILTER_COMMUNITY_PLAYLIST)
            .inOrder()
    }

    @Test
    fun `with no chip bar every filter shows`() {
        assertThat(musicSearchFilters(emptyList())).isEqualTo(DefaultMusicSearchFilters)
    }
}

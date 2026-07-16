package io.github.aedev.flow.ui.tv.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TvDestinationTest {
    @Test
    fun `primary destinations keep a stable order`() {
        assertThat(TvDestination.primary).containsExactly(
            TvDestination.HOME,
            TvDestination.SUBSCRIPTIONS,
            TvDestination.SEARCH,
            TvDestination.LIBRARY,
            TvDestination.SETTINGS,
        ).inOrder()
    }

    @Test
    fun `route lookup falls back to home`() {
        assertThat(TvDestination.fromRoute("search")).isEqualTo(TvDestination.SEARCH)
        assertThat(TvDestination.fromRoute("missing")).isEqualTo(TvDestination.HOME)
        assertThat(TvDestination.fromRoute(null)).isEqualTo(TvDestination.HOME)
    }
}

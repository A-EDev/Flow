package io.github.aedev.flow.ui.tv.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TvBackModelTest {
    @Test
    fun `detail routes pop regardless of tab or rail focus`() {
        TvDestination.entries.forEach { tab ->
            assertThat(TvBackModel.resolve(isOnDetailRoute = true, currentTab = tab, railHasFocus = false))
                .isEqualTo(TvBackAction.POP_DETAIL)
            assertThat(TvBackModel.resolve(isOnDetailRoute = true, currentTab = tab, railHasFocus = true))
                .isEqualTo(TvBackAction.POP_DETAIL)
        }
    }

    @Test
    fun `non-home tabs converge on home`() {
        TvDestination.entries
            .filterNot { it == TvDestination.HOME }
            .forEach { tab ->
                assertThat(TvBackModel.resolve(isOnDetailRoute = false, currentTab = tab, railHasFocus = false))
                    .isEqualTo(TvBackAction.GO_HOME)
                assertThat(TvBackModel.resolve(isOnDetailRoute = false, currentTab = tab, railHasFocus = true))
                    .isEqualTo(TvBackAction.GO_HOME)
            }
    }

    @Test
    fun `home moves focus to the rail before exiting`() {
        assertThat(TvBackModel.resolve(isOnDetailRoute = false, currentTab = TvDestination.HOME, railHasFocus = false))
            .isEqualTo(TvBackAction.FOCUS_RAIL)
    }

    @Test
    fun `home with rail focused exits`() {
        assertThat(TvBackModel.resolve(isOnDetailRoute = false, currentTab = TvDestination.HOME, railHasFocus = true))
            .isEqualTo(TvBackAction.EXIT)
    }
}

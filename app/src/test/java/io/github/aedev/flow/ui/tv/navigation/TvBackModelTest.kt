package io.github.aedev.flow.ui.tv.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TvBackModelTest {
    @Test
    fun `detail routes pop regardless of tab, history, or rail focus`() {
        TvDestination.entries.forEach { tab ->
            listOf(true, false).forEach { history ->
                listOf(true, false).forEach { rail ->
                    assertThat(
                        TvBackModel.resolve(
                            isOnDetailRoute = true,
                            hasTabHistory = history,
                            currentTab = tab,
                            railHasFocus = rail,
                        )
                    ).isEqualTo(TvBackAction.POP_DETAIL)
                }
            }
        }
    }

    @Test
    fun `tab history wins over converging on home`() {
        TvDestination.entries.forEach { tab ->
            assertThat(
                TvBackModel.resolve(
                    isOnDetailRoute = false,
                    hasTabHistory = true,
                    currentTab = tab,
                    railHasFocus = false,
                )
            ).isEqualTo(TvBackAction.POP_TAB)
        }
    }

    @Test
    fun `non-home tabs without history converge on home`() {
        TvDestination.entries
            .filterNot { it == TvDestination.HOME }
            .forEach { tab ->
                listOf(true, false).forEach { rail ->
                    assertThat(
                        TvBackModel.resolve(
                            isOnDetailRoute = false,
                            hasTabHistory = false,
                            currentTab = tab,
                            railHasFocus = rail,
                        )
                    ).isEqualTo(TvBackAction.GO_HOME)
                }
            }
    }

    @Test
    fun `home moves focus to the rail before exiting`() {
        assertThat(
            TvBackModel.resolve(
                isOnDetailRoute = false,
                hasTabHistory = false,
                currentTab = TvDestination.HOME,
                railHasFocus = false,
            )
        ).isEqualTo(TvBackAction.FOCUS_RAIL)
    }

    @Test
    fun `home with rail focused exits`() {
        assertThat(
            TvBackModel.resolve(
                isOnDetailRoute = false,
                hasTabHistory = false,
                currentTab = TvDestination.HOME,
                railHasFocus = true,
            )
        ).isEqualTo(TvBackAction.EXIT)
    }
}

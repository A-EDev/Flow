package io.github.aedev.flow.ui.tv.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TvBackModelTest {
    private val booleans = listOf(true, false)

    @Test
    fun `detail routes pop regardless of tab, history, or rail focus`() {
        booleans.forEach { startTab ->
            booleans.forEach { history ->
                booleans.forEach { rail ->
                    assertThat(
                        TvBackModel.resolve(
                            isOnDetailRoute = true,
                            hasTabHistory = history,
                            isOnStartTab = startTab,
                            railHasFocus = rail,
                        ),
                    ).isEqualTo(TvBackAction.POP_DETAIL)
                }
            }
        }
    }

    @Test
    fun `tab history wins over converging on the start tab`() {
        booleans.forEach { startTab ->
            assertThat(
                TvBackModel.resolve(
                    isOnDetailRoute = false,
                    hasTabHistory = true,
                    isOnStartTab = startTab,
                    railHasFocus = false,
                ),
            ).isEqualTo(TvBackAction.POP_TAB)
        }
    }

    @Test
    fun `other roots without history converge on the start tab`() {
        booleans.forEach { rail ->
            assertThat(
                TvBackModel.resolve(
                    isOnDetailRoute = false,
                    hasTabHistory = false,
                    isOnStartTab = false,
                    railHasFocus = rail,
                ),
            ).isEqualTo(TvBackAction.GO_START)
        }
    }

    @Test
    fun `the start tab moves focus to the rail before exiting`() {
        assertThat(
            TvBackModel.resolve(
                isOnDetailRoute = false,
                hasTabHistory = false,
                isOnStartTab = true,
                railHasFocus = false,
            ),
        ).isEqualTo(TvBackAction.FOCUS_RAIL)
    }

    @Test
    fun `the start tab with the rail focused exits`() {
        assertThat(
            TvBackModel.resolve(
                isOnDetailRoute = false,
                hasTabHistory = false,
                isOnStartTab = true,
                railHasFocus = true,
            ),
        ).isEqualTo(TvBackAction.EXIT)
    }
}

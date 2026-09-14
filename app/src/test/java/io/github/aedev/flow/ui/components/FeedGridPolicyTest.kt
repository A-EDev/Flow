package io.github.aedev.flow.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FeedGridPolicyTest {
    @Test
    fun `a phone column never forms a grid`() {
        assertThat(feedCardsFormGrid(columns = 1, itemCount = 12)).isFalse()
    }

    @Test
    fun `a lone card on a wide window falls back to the list variant`() {
        assertThat(feedCardsFormGrid(columns = 3, itemCount = 1)).isFalse()
        assertThat(feedCardsFormGrid(columns = 3, itemCount = 2)).isTrue()
    }

    @Test
    fun `a phone shelf previews four rows`() {
        assertThat(feedShelfPreviewCount(columns = 1, itemCount = 20)).isEqualTo(4)
    }

    @Test
    fun `a grid shelf previews two full rows`() {
        assertThat(feedShelfPreviewCount(columns = 3, itemCount = 20)).isEqualTo(6)
        assertThat(feedShelfPreviewCount(columns = 2, itemCount = 20)).isEqualTo(4)
    }

    @Test
    fun `a lone item on a wide window keeps the list preview`() {
        assertThat(feedShelfPreviewCount(columns = 3, itemCount = 1)).isEqualTo(4)
    }
}

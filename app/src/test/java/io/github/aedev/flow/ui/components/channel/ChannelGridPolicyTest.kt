package io.github.aedev.flow.ui.components.channel

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChannelGridPolicyTest {
    @Test
    fun `a phone column never forms a grid`() {
        assertThat(channelCardsFormGrid(columns = 1, itemCount = 12)).isFalse()
    }

    @Test
    fun `a lone card on a wide window falls back to the list variant`() {
        assertThat(channelCardsFormGrid(columns = 3, itemCount = 1)).isFalse()
        assertThat(channelCardsFormGrid(columns = 3, itemCount = 2)).isTrue()
    }

    @Test
    fun `a phone shelf previews four rows`() {
        assertThat(channelShelfPreviewCount(columns = 1, itemCount = 20)).isEqualTo(4)
    }

    @Test
    fun `a grid shelf previews two full rows`() {
        assertThat(channelShelfPreviewCount(columns = 3, itemCount = 20)).isEqualTo(6)
        assertThat(channelShelfPreviewCount(columns = 2, itemCount = 20)).isEqualTo(4)
    }

    @Test
    fun `a lone item on a wide window keeps the list preview`() {
        assertThat(channelShelfPreviewCount(columns = 3, itemCount = 1)).isEqualTo(4)
    }
}

package io.github.aedev.flow.ui.screens.channel

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.innertube.pages.channel.ChannelHeader
import org.junit.Test

class ChannelLoadGuardTest {
    private val url = "https://www.youtube.com/channel/UCx"
    private val loaded = ChannelUiState(header = ChannelHeader(id = "UCx", title = "Channel"))

    @Test
    fun `the first open of a channel loads it`() {
        assertThat(shouldLoadChannel(ChannelUiState(), requestedUrl = null, url = url)).isTrue()
    }

    @Test
    fun `coming back to a loaded channel keeps what is there`() {
        assertThat(shouldLoadChannel(loaded, requestedUrl = url, url = url)).isFalse()
    }

    @Test
    fun `a channel that is still loading is not requested twice`() {
        assertThat(shouldLoadChannel(ChannelUiState(isLoading = true), requestedUrl = url, url = url)).isFalse()
    }

    @Test
    fun `retrying after a failed load loads again`() {
        assertThat(shouldLoadChannel(ChannelUiState(error = "offline"), requestedUrl = url, url = url)).isTrue()
    }

    @Test
    fun `a different channel loads`() {
        assertThat(shouldLoadChannel(loaded, requestedUrl = url, url = "https://www.youtube.com/channel/UCy")).isTrue()
    }
}

package io.github.aedev.flow.player

import androidx.media3.common.C
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.IOException
import java.net.SocketException
import java.net.UnknownHostException

class MusicLoadErrorPolicyTest {
    @Test
    fun `a lost connection is waited out, a little longer each time, then given up`() {
        assertThat(MusicLoadErrorPolicy.retryDelayMs(errorCount = 1, connectionLost = true)).isEqualTo(1_000L)
        assertThat(MusicLoadErrorPolicy.retryDelayMs(errorCount = 9, connectionLost = true)).isEqualTo(5_000L)
        assertThat(MusicLoadErrorPolicy.retryDelayMs(errorCount = MusicLoadErrorPolicy.MAX_CONNECTION_RETRIES, connectionLost = true))
            .isEqualTo(5_000L)
        assertThat(MusicLoadErrorPolicy.retryDelayMs(errorCount = MusicLoadErrorPolicy.MAX_CONNECTION_RETRIES + 1, connectionLost = true))
            .isEqualTo(C.TIME_UNSET)
    }

    @Test
    fun `any other error gives up after Media3's usual three retries`() {
        assertThat(MusicLoadErrorPolicy.retryDelayMs(errorCount = 2, connectionLost = false)).isNull()
        assertThat(MusicLoadErrorPolicy.retryDelayMs(errorCount = 4, connectionLost = false)).isEqualTo(C.TIME_UNSET)
    }

    @Test
    fun `a lost connection is recognised however deep the platform wrapped it`() {
        assertThat(MusicLoadErrorPolicy.isConnectionLoss(IOException(IOException(UnknownHostException("www.youtube.com"))))).isTrue()
        assertThat(MusicLoadErrorPolicy.isConnectionLoss(IOException(SocketException("Software caused connection abort")))).isTrue()
        assertThat(MusicLoadErrorPolicy.isConnectionLoss(IOException("Response code: 403"))).isFalse()
    }
}

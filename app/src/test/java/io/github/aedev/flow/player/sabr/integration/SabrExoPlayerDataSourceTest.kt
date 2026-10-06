package io.github.aedev.flow.player.sabr.integration

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test

class SabrExoPlayerDataSourceTest {
    @Test
    fun `buffer reads are reported as local transfers`() {
        val networkFlags = mutableListOf<Boolean>()
        val buffer = SabrSegmentBuffer()
        buffer.appendSegment(byteArrayOf(1, 2, 3))
        val source = SabrExoPlayerDataSource(buffer)
        source.addTransferListener(
            object : TransferListener {
                override fun onTransferInitializing(
                    source: DataSource,
                    dataSpec: DataSpec,
                    isNetwork: Boolean,
                ) {
                    networkFlags.add(isNetwork)
                }

                override fun onTransferStart(
                    source: DataSource,
                    dataSpec: DataSpec,
                    isNetwork: Boolean,
                ) {
                    networkFlags.add(isNetwork)
                }

                override fun onBytesTransferred(
                    source: DataSource,
                    dataSpec: DataSpec,
                    isNetwork: Boolean,
                    bytesTransferred: Int,
                ) {
                    assertThat(bytesTransferred).isEqualTo(3)
                    networkFlags.add(isNetwork)
                }

                override fun onTransferEnd(
                    source: DataSource,
                    dataSpec: DataSpec,
                    isNetwork: Boolean,
                ) {
                    networkFlags.add(isNetwork)
                }
            },
        )
        source.open(DataSpec.Builder().setUri(mockk<Uri>()).build())
        assertThat(source.read(ByteArray(3), 0, 3)).isEqualTo(3)
        source.close()
        assertThat(networkFlags).containsExactly(false, false, false, false)
    }
}

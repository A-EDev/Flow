package io.github.aedev.flow.data.download

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertThrows
import org.junit.Test

class RefusedStreamRetryDataSourceTest {
    private val spec =
        DataSpec
            .Builder()
            .setUri(mockk<Uri>(relaxed = true))
            .setKey("song")
            .build()
    private val refused = mutableListOf<String>()
    private val upstream = mockk<DataSource>(relaxed = true)
    private val source = RefusedStreamRetryDataSource(upstream) { mediaId, _ -> refused += mediaId }

    private fun httpError(code: Int) = HttpDataSource.InvalidResponseCodeException(code, null, null, emptyMap(), spec, ByteArray(0))

    @Test
    fun `a refused url is dropped and the song opens again with a fresh one`() {
        every { upstream.open(spec) } throws httpError(403) andThen 1_024L

        assertThat(source.open(spec)).isEqualTo(1_024L)
        assertThat(refused).containsExactly("song")
        verify(exactly = 1) { upstream.close() }
    }

    @Test
    fun `a second refusal is the player's to handle`() {
        every { upstream.open(spec) } throws httpError(403)

        assertThrows(HttpDataSource.InvalidResponseCodeException::class.java) { source.open(spec) }
        assertThat(refused).hasSize(1)
    }

    @Test
    fun `other responses are not retried here`() {
        every { upstream.open(spec) } throws httpError(416)

        assertThrows(HttpDataSource.InvalidResponseCodeException::class.java) { source.open(spec) }
        assertThat(refused).isEmpty()
    }
}

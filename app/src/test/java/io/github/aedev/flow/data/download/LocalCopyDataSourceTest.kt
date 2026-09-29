package io.github.aedev.flow.data.download

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

/** Plain JVM: the data source reads only a URI's scheme and host, so mocks stand in for the framework's Uri. */
class LocalCopyDataSourceTest {
    private class RecordingSource(
        private val name: String,
    ) : DataSource {
        var opened: DataSpec? = null
        var closed = false

        override fun addTransferListener(transferListener: TransferListener) = Unit

        override fun open(dataSpec: DataSpec): Long {
            opened = dataSpec
            return 10
        }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int = name.length

        override fun getUri(): Uri? = opened?.uri

        override fun close() {
            closed = true
        }
    }

    private val local = RecordingSource("local")
    private val remote = RecordingSource("remote")
    private val file = uri("file", host = null)

    private fun uri(
        scheme: String,
        host: String?,
    ): Uri =
        mockk {
            every { this@mockk.scheme } returns scheme
            every { this@mockk.host } returns host
        }

    private fun source(downloaded: Set<String> = setOf("abc")) =
        LocalCopyDataSource(local, remote) { id -> file.takeIf { id in downloaded } }

    private fun spec(
        uri: Uri,
        key: String? = null,
    ) = DataSpec
        .Builder()
        .setUri(uri)
        .setKey(key)
        .setPosition(4)
        .build()

    @Test
    fun `a downloaded song opens its file, without the stream's cache key`() {
        source().open(spec(uri("music", "abc"), key = "abc"))

        assertThat(local.opened?.uri).isSameInstanceAs(file)
        assertThat(local.opened?.key).isNull()
        assertThat(local.opened?.position).isEqualTo(4)
        assertThat(remote.opened).isNull()
    }

    @Test
    fun `a song without a download resolves through the stream chain unchanged`() {
        val spec = spec(uri("music", "xyz"), key = "xyz")

        source().open(spec)

        assertThat(remote.opened).isSameInstanceAs(spec)
        assertThat(local.opened).isNull()
    }

    @Test
    fun `an item with no cache key is looked up by its id`() {
        source().open(spec(uri("music", "abc")))

        assertThat(local.opened?.uri).isSameInstanceAs(file)
    }

    @Test
    fun `device files skip the caches`() {
        val content = uri("content", "media")

        source(downloaded = emptySet()).open(spec(content, key = "local_7"))

        assertThat(local.opened?.uri).isSameInstanceAs(content)
        assertThat(remote.opened).isNull()
    }

    @Test
    fun `reads and close go to the source that was opened`() {
        val sut = source()
        sut.open(spec(uri("https", "example.com")))

        assertThat(sut.read(ByteArray(8), 0, 8)).isEqualTo("remote".length)
        sut.close()

        assertThat(remote.closed).isTrue()
        assertThat(local.closed).isFalse()
        assertThat(sut.uri).isNull()
    }
}

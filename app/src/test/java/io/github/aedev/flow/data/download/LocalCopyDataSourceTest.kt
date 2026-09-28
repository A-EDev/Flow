package io.github.aedev.flow.data.download

import android.app.Application
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
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
    private val file = Uri.parse("file:///storage/Music/Flow/song.m4a")

    private fun source(downloaded: Set<String> = setOf("abc")) =
        LocalCopyDataSource(local, remote) { id -> file.takeIf { id in downloaded } }

    private fun spec(
        uri: String,
        key: String? = null,
    ) = DataSpec
        .Builder()
        .setUri(uri)
        .setKey(key)
        .setPosition(4)
        .build()

    @Test
    fun `a downloaded song opens its file, without the stream's cache key`() {
        source().open(spec("music://abc", key = "abc"))

        assertThat(local.opened?.uri).isEqualTo(file)
        assertThat(local.opened?.key).isNull()
        assertThat(local.opened?.position).isEqualTo(4)
        assertThat(remote.opened).isNull()
    }

    @Test
    fun `a song without a download resolves through the stream chain unchanged`() {
        val spec = spec("music://xyz", key = "xyz")

        source().open(spec)

        assertThat(remote.opened).isSameInstanceAs(spec)
        assertThat(local.opened).isNull()
    }

    @Test
    fun `an item with no cache key is looked up by its id`() {
        source().open(spec("music://abc"))

        assertThat(local.opened?.uri).isEqualTo(file)
    }

    @Test
    fun `device files skip the caches`() {
        val sut = source(downloaded = emptySet())

        sut.open(spec("content://media/external/audio/media/7", key = "local_7"))

        assertThat(local.opened?.uri.toString()).isEqualTo("content://media/external/audio/media/7")
        assertThat(remote.opened).isNull()
    }

    @Test
    fun `reads and close go to the source that was opened`() {
        val sut = source()
        sut.open(spec("https://example.com/a"))

        assertThat(sut.read(ByteArray(8), 0, 8)).isEqualTo("remote".length)
        sut.close()

        assertThat(remote.closed).isTrue()
        assertThat(local.closed).isFalse()
        assertThat(sut.uri).isNull()
    }
}

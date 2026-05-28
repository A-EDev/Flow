package io.github.aedev.flow.player.sabr.integration

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec

@UnstableApi
class SabrExoPlayerDataSource(
    private val buffer: SabrSegmentBuffer
) : BaseDataSource(true) {

    private var opened = false
    private var uri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        opened = true
        transferInitializing(dataSpec)
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (!opened) return C.RESULT_END_OF_INPUT
        val bytesRead = buffer.read(target, offset, length)
        if (bytesRead == -1) return C.RESULT_END_OF_INPUT
        if (bytesRead > 0) bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    class Factory(
        private val audioBuffer: SabrSegmentBuffer,
        private val videoBuffer: SabrSegmentBuffer
    ) : DataSource.Factory {
        @Volatile
        private var isAudio = false

        fun setAudio(audio: Boolean): Factory {
            isAudio = audio
            return this
        }

        override fun createDataSource(): DataSource {
            return SabrExoPlayerDataSource(if (isAudio) audioBuffer else videoBuffer)
        }
    }
}

package io.github.aedev.flow.data.download

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource

/**
 * Opens a song again, once, when the stream host refuses the url it was resolved to. [onRefused]
 * drops that url and demotes its client, so the second open resolves a fresh one. The song keeps
 * playing instead of failing: a failed player releases its decoder, and on some vendor builds
 * releasing a decoder right after a flush aborts the whole process inside the platform.
 */
@UnstableApi
internal class RefusedStreamRetryDataSource(
    private val upstream: DataSource,
    private val onRefused: (mediaId: String, url: String) -> Unit,
) : DataSource by upstream {
    override fun open(dataSpec: DataSpec): Long =
        try {
            upstream.open(dataSpec)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            val mediaId = dataSpec.key
            if (e.responseCode != HTTP_FORBIDDEN || mediaId == null) throw e
            upstream.close()
            onRefused(mediaId, e.dataSpec.uri.toString())
            upstream.open(dataSpec)
        }

    class Factory(
        private val upstream: DataSource.Factory,
        private val onRefused: (mediaId: String, url: String) -> Unit,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = RefusedStreamRetryDataSource(upstream.createDataSource(), onRefused)
    }

    private companion object {
        const val HTTP_FORBIDDEN = 403
    }
}

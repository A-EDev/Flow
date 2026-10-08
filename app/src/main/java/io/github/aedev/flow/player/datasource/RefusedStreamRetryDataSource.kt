package io.github.aedev.flow.player.datasource

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource

/**
 * Opens a stream again, once, when the stream host refuses the url it was resolved to. [onRefused]
 * gets the refused request and the url GVS turned down, arranges for the second open to reach a
 * fresh one, and says whether that is worth trying. The media keeps playing instead of failing: a
 * failed player releases its decoder, and on some vendor builds releasing a decoder right after a
 * flush aborts the whole process inside the platform.
 */
@UnstableApi
internal class RefusedStreamRetryDataSource(
    private val upstream: DataSource,
    private val onRefused: (dataSpec: DataSpec, url: String) -> Boolean,
) : DataSource by upstream {
    override fun open(dataSpec: DataSpec): Long =
        try {
            upstream.open(dataSpec)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            if (e.responseCode != HTTP_FORBIDDEN) throw e
            upstream.close()
            if (!onRefused(dataSpec, e.dataSpec.uri.toString())) throw e
            upstream.open(dataSpec)
        }

    class Factory(
        private val upstream: DataSource.Factory,
        private val onRefused: (dataSpec: DataSpec, url: String) -> Boolean,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = RefusedStreamRetryDataSource(upstream.createDataSource(), onRefused)
    }

    private companion object {
        const val HTTP_FORBIDDEN = 403
    }
}

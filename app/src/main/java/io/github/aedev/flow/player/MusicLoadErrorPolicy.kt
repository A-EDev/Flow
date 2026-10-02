package io.github.aedev.flow.player

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.min

/**
 * The music player waits out a dropped connection instead of failing: it keeps buffering and
 * resumes by itself for a few minutes. A failed player releases its decoder, and on some vendor
 * builds a decoder released right after the stall's flush aborts the process inside the platform.
 * Every other error is retried as Media3 does by default.
 */
@UnstableApi
internal class MusicLoadErrorPolicy(
    private val isOffline: () -> Boolean,
) : DefaultLoadErrorHandlingPolicy(MAX_CONNECTION_RETRIES) {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val error = loadErrorInfo.exception
        val connectionLost = isConnectionLoss(error) || (error is IOException && isOffline())
        return retryDelayMs(loadErrorInfo.errorCount, connectionLost) ?: super.getRetryDelayMsFor(loadErrorInfo)
    }

    companion object {
        const val MAX_CONNECTION_RETRIES = 60
        private const val RETRY_STEP_MS = 1_000L
        private const val MAX_RETRY_DELAY_MS = 5_000L

        /**
         * The wait before retry [errorCount], [C.TIME_UNSET] to give up, or null to leave the
         * decision to Media3's default.
         */
        fun retryDelayMs(
            errorCount: Int,
            connectionLost: Boolean,
        ): Long? =
            when {
                connectionLost && errorCount <= MAX_CONNECTION_RETRIES -> min(errorCount * RETRY_STEP_MS, MAX_RETRY_DELAY_MS)

                connectionLost -> C.TIME_UNSET

                // The raised retry count is for lost connections only; anything else gives up as before.
                errorCount > DefaultLoadErrorHandlingPolicy.DEFAULT_MIN_LOADABLE_RETRY_COUNT -> C.TIME_UNSET

                else -> null
            }

        fun isConnectionLoss(error: Throwable): Boolean =
            generateSequence(error) { it.cause }.take(MAX_CAUSE_DEPTH).any {
                it is UnknownHostException ||
                    it is ConnectException ||
                    it is NoRouteToHostException ||
                    it is SocketTimeoutException ||
                    it is SocketException
            }

        private const val MAX_CAUSE_DEPTH = 8
    }
}

package io.github.aedev.flow.player.datasource

import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.error.PlayerDiagnostics
import io.github.aedev.flow.player.error.StreamDenialClassifier
import io.github.aedev.flow.player.error.StreamDenialKind
import io.github.aedev.flow.player.stream.InFlightRequestCoalescer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps a video playing when GVS refuses one of its stream URLs mid-playback (#921), by fetching
 * the same file from a source that still serves it and reopening the same byte range there. The
 * player never sees the 403, so nothing is torn down, the buffer keeps playing while this runs,
 * and it works with no screen attached.
 *
 * Runs on the loader thread that hit the refusal, which is why the resolve is bounded.
 */
@UnstableApi
internal class RefusedStreamSwapper(
    private val table: StreamSwapTable,
    private val reportDenied: (url: String) -> StreamDenialKind,
    private val resolve: suspend (videoId: String, kind: StreamDenialKind) -> List<PlayerResponse.StreamingData.Format>?,
    private val timeoutMs: Long = RESOLVE_TIMEOUT_MS,
) {
    private val spentVideos: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val inFlight =
        InFlightRequestCoalescer<String, List<PlayerResponse.StreamingData.Format>?>(
            CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )

    fun register(
        videoId: String,
        urls: Collection<String?>,
    ) = table.register(videoId, urls)

    fun resolveDataSpec(dataSpec: DataSpec): DataSpec =
        table.rewrite(dataSpec.uri.toString())?.let { dataSpec.withUri(Uri.parse(it)) } ?: dataSpec

    /** Whether reopening [dataSpec] is worth it: true once a replacement for [url]'s file is recorded. */
    fun onRefused(
        dataSpec: DataSpec,
        url: String,
    ): Boolean {
        if (table.rewrite(url) != null) return true
        val videoId = table.videoFor(url) ?: return false
        val kind = reportDenied(url)
        if (kind == StreamDenialKind.UNKNOWN) return false
        if (!table.hasSwapsLeft(videoId)) {
            // Every chunk retry lands here; one line per video keeps the diagnostics report readable.
            if (spentVideos.add(videoId)) log("$kind: $videoId has used its swaps, leaving it to the player")
            return false
        }
        val replacements =
            runBlocking {
                withTimeoutOrNull(timeoutMs) { inFlight.run(videoId) { resolve(videoId, kind) } }
            }
        val swapped = replacements != null && table.record(url, replacements)
        val from = StreamDenialClassifier.clientOf(url)
        val message =
            if (swapped) {
                "$kind: swapped $videoId itag=${StreamDenialClassifier.itagOf(url)} from c=$from at byte ${dataSpec.position}"
            } else {
                "$kind: no replacement for $videoId itag=${StreamDenialClassifier.itagOf(url)} from c=$from, leaving it to the player"
            }
        log(message)
        return swapped
    }

    private fun log(message: String) {
        Log.w(TAG, message)
        PlayerDiagnostics.logWarning(TAG, message)
    }

    private companion object {
        // Ends well inside the 10-25 s a wall usually leaves buffered.
        const val RESOLVE_TIMEOUT_MS = 15_000L

        // Deliberately the old tag: these lines are what a user's diagnostics dump is read for.
        const val TAG = "PlayerErrorHandler"
    }
}

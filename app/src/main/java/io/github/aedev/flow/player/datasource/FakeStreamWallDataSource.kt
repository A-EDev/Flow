package io.github.aedev.flow.player.datasource

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.player.error.StreamDenialClassifier
import java.io.File

/**
 * Debug builds only: refuses media the way GVS walls a visitor (#921), so the recovery can be
 * exercised on a network YouTube does not wall. A walled visitor gets a 403 for any request
 * starting past [FakeStreamWall.WALL_SECONDS] of stream position on the walled clients, with the
 * URL's deadline still valid, and a fresh visitor is served again.
 *
 * Switched on with `adb shell run-as <package> touch files/fake_stream_wall`, off with `rm`. The
 * visitor in use when the switch is first seen is the walled one. `files/fake_stream_wall_all`
 * instead walls every client for every visitor, so nothing can be swapped in and the player's own
 * reload and give-up are what run.
 */
@UnstableApi
internal class FakeStreamWallDataSource(
    private val upstream: DataSource,
    private val wall: FakeStreamWall,
) : DataSource by upstream {
    override fun open(dataSpec: DataSpec): Long {
        val url = dataSpec.uri.toString()
        if (wall.refuses(url, dataSpec.position)) {
            Log.w(TAG, "fake wall refused c=${StreamDenialClassifier.clientOf(url)} at byte ${dataSpec.position}")
            throw HttpDataSource.InvalidResponseCodeException(403, "Forbidden", null, emptyMap(), dataSpec, ByteArray(0))
        }
        return upstream.open(dataSpec)
    }

    class Factory(
        private val upstream: DataSource.Factory,
        private val wall: FakeStreamWall,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = FakeStreamWallDataSource(upstream.createDataSource(), wall)
    }

    private companion object {
        const val TAG = "FakeStreamWall"
    }
}

internal class FakeStreamWall(
    private val isSwitchedOn: () -> Boolean,
    private val currentVisitor: () -> String?,
    private val wallsEveryone: () -> Boolean = { false },
) {
    @Volatile
    private var walledVisitor: String? = null

    fun refuses(
        url: String,
        position: Long,
    ): Boolean {
        if (wallsEveryone()) return isPastWall(url, position, anyClient = true)
        if (!isSwitchedOn()) {
            walledVisitor = null
            return false
        }
        val visitor = currentVisitor() ?: return false
        val walled = walledVisitor ?: visitor.also { walledVisitor = it }
        return visitor == walled && isPastWall(url, position)
    }

    companion object {
        const val WALL_SECONDS = 60.0
        private val WALLED_CLIENTS = setOf("VISIONOS", "ANDROID_VR")

        /** The byte offset of [WALL_SECONDS] in the file, read from the URL's own `clen` and `dur`. */
        fun isPastWall(
            url: String,
            position: Long,
            anyClient: Boolean = false,
        ): Boolean {
            if (!anyClient && StreamDenialClassifier.clientOf(url) !in WALLED_CLIENTS) return false
            val length = StreamDenialClassifier.queryParam(url, "clen")?.toLongOrNull() ?: return false
            val duration = StreamDenialClassifier.queryParam(url, "dur")?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return false
            if (duration <= WALL_SECONDS) return false
            return position >= (length * WALL_SECONDS / duration).toLong()
        }

        fun forDebugBuild(context: Context): FakeStreamWall {
            val switchFile = File(context.filesDir, "fake_stream_wall")
            val everyoneFile = File(context.filesDir, "fake_stream_wall_all")
            return FakeStreamWall(
                isSwitchedOn = switchFile::exists,
                currentVisitor = { YouTube.visitorData },
                wallsEveryone = everyoneFile::exists,
            )
        }
    }
}
